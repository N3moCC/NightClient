use iced::Task;

pub fn apply_window_blur<M: Clone + Send + 'static>(
    width: i32,
    height: i32,
    radius: i32,
    is_dark: bool,
    on_done: M,
) -> Task<M> {
    iced::window::latest().and_then(move |id| {
        let done_msg = on_done.clone();
        let _ = (width, height, radius, is_dark);
        iced::window::run(id, move |_window| {
            #[cfg(target_os = "linux")]
            {
                use raw_window_handle::{RawDisplayHandle, RawWindowHandle};
                if let (Ok(display_handle), Ok(window_handle)) =
                    (_window.display_handle(), _window.window_handle())
                    && let (
                        RawDisplayHandle::Wayland(display_wl),
                        RawWindowHandle::Wayland(window_wl),
                    ) = (display_handle.as_raw(), window_handle.as_raw())
                {
                    unsafe {
                        linux_wayland::apply_blur(
                            display_wl.display.as_ptr().cast(),
                            window_wl.surface.as_ptr().cast(),
                            width,
                            height,
                            radius,
                        );
                    }
                }
            }

            #[cfg(target_os = "windows")]
            {
                use raw_window_handle::RawWindowHandle;
                if let Ok(window_handle) = _window.window_handle()
                    && let RawWindowHandle::Win32(win32) = window_handle.as_raw()
                {
                    unsafe {
                        win32_blur::apply_blur(win32.hwnd.get() as *mut _, radius, is_dark);
                    }
                }
            }

            done_msg
        })
    })
}

#[cfg(target_os = "linux")]
mod linux_wayland {
    use std::cell::RefCell;
    use std::ffi::c_void;
    use std::mem::ManuallyDrop;

    use wayland_client::{
        Connection, Dispatch, EventQueue, Proxy, QueueHandle,
        backend::{Backend, ObjectId},
        globals::{GlobalListContents, registry_queue_init},
        protocol::{wl_compositor, wl_region, wl_registry, wl_surface},
    };
    use wayland_protocols::ext::background_effect::v1::client::{
        ext_background_effect_manager_v1::ExtBackgroundEffectManagerV1,
        ext_background_effect_surface_v1::ExtBackgroundEffectSurfaceV1,
    };
    use wayland_protocols_plasma::blur::client::{
        org_kde_kwin_blur::OrgKdeKwinBlur, org_kde_kwin_blur_manager::OrgKdeKwinBlurManager,
    };

    struct BlurState;

    enum BlurSlot {
        Uninitialized,
        Active(Box<BlurHandle>),
        Unsupported(*mut c_void),
    }

    thread_local! {
        static BLUR_HANDLE: RefCell<BlurSlot> = const { RefCell::new(BlurSlot::Uninitialized) };
    }

    enum BlurEffect {
        Ext(ExtBackgroundEffectSurfaceV1),
        Legacy(OrgKdeKwinBlur),
    }

    struct BlurHandle {
        conn: ManuallyDrop<Connection>,
        queue: ManuallyDrop<EventQueue<BlurState>>,
        compositor: wl_compositor::WlCompositor,
        surface: wl_surface::WlSurface,
        effect: BlurEffect,
        region: Option<wl_region::WlRegion>,
        surface_ptr: *mut c_void,
        last_geom: Option<(i32, i32, i32)>,
    }

    impl Drop for BlurHandle {
        fn drop(&mut self) {
            unsafe {
                let conn = ManuallyDrop::take(&mut self.conn);
                let queue = ManuallyDrop::take(&mut self.queue);
                std::mem::forget(conn);
                std::mem::forget(queue);
            }
        }
    }

    impl Dispatch<wl_registry::WlRegistry, GlobalListContents> for BlurState {
        fn event(
            _: &mut Self,
            _: &wl_registry::WlRegistry,
            _: wl_registry::Event,
            _: &GlobalListContents,
            _: &Connection,
            _: &QueueHandle<Self>,
        ) {
        }
    }
    impl Dispatch<wl_compositor::WlCompositor, ()> for BlurState {
        fn event(
            _: &mut Self,
            _: &wl_compositor::WlCompositor,
            _: wl_compositor::Event,
            _: &(),
            _: &Connection,
            _: &QueueHandle<Self>,
        ) {
        }
    }
    impl Dispatch<wl_region::WlRegion, ()> for BlurState {
        fn event(
            _: &mut Self,
            _: &wl_region::WlRegion,
            _: wl_region::Event,
            _: &(),
            _: &Connection,
            _: &QueueHandle<Self>,
        ) {
        }
    }
    impl Dispatch<OrgKdeKwinBlurManager, ()> for BlurState {
        fn event(
            _: &mut Self,
            _: &OrgKdeKwinBlurManager,
            _: <OrgKdeKwinBlurManager as Proxy>::Event,
            _: &(),
            _: &Connection,
            _: &QueueHandle<Self>,
        ) {
        }
    }
    impl Dispatch<OrgKdeKwinBlur, ()> for BlurState {
        fn event(
            _: &mut Self,
            _: &OrgKdeKwinBlur,
            _: <OrgKdeKwinBlur as Proxy>::Event,
            _: &(),
            _: &Connection,
            _: &QueueHandle<Self>,
        ) {
        }
    }
    impl Dispatch<ExtBackgroundEffectManagerV1, ()> for BlurState {
        fn event(
            _: &mut Self,
            _: &ExtBackgroundEffectManagerV1,
            _: <ExtBackgroundEffectManagerV1 as Proxy>::Event,
            _: &(),
            _: &Connection,
            _: &QueueHandle<Self>,
        ) {
        }
    }
    impl Dispatch<ExtBackgroundEffectSurfaceV1, ()> for BlurState {
        fn event(
            _: &mut Self,
            _: &ExtBackgroundEffectSurfaceV1,
            _: <ExtBackgroundEffectSurfaceV1 as Proxy>::Event,
            _: &(),
            _: &Connection,
            _: &QueueHandle<Self>,
        ) {
        }
    }

    pub unsafe fn apply_blur(
        display_ptr: *mut c_void,
        surface_ptr: *mut c_void,
        width: i32,
        height: i32,
        corner_radius: i32,
    ) {
        BLUR_HANDLE.with(|cell| {
            let mut slot = cell.borrow_mut();
            match &mut *slot {
                BlurSlot::Active(existing) if existing.surface_ptr == surface_ptr => {
                    existing.set_region(width, height, corner_radius);
                    return;
                }
                BlurSlot::Unsupported(ptr) if *ptr == surface_ptr => {
                    return;
                }
                BlurSlot::Active(existing) => {
                    if let Some(old) = existing.region.take() {
                        old.destroy();
                    }
                    *slot = BlurSlot::Uninitialized;
                }
                _ => {}
            }

            if let Ok(mut created) = unsafe { BlurHandle::new(display_ptr, surface_ptr) } {
                created.set_region(width, height, corner_radius);
                *slot = BlurSlot::Active(Box::new(created));
            } else {
                *slot = BlurSlot::Unsupported(surface_ptr);
            }
        });
    }

    impl BlurHandle {
        unsafe fn new(display_ptr: *mut c_void, surface_ptr: *mut c_void) -> Result<Self, String> {
            let backend = unsafe {
                Backend::from_foreign_display(display_ptr as *mut wayland_sys::client::wl_display)
            };
            let conn = ManuallyDrop::new(Connection::from_backend(backend));
            let (globals, queue) = registry_queue_init::<BlurState>(&conn)
                .map_err(|e| format!("wayland registry init failed: {e}"))?;
            let mut queue = ManuallyDrop::new(queue);
            let qh = queue.handle();
            let mut state = BlurState;
            let compositor: wl_compositor::WlCompositor = globals
                .bind(&qh, 1..=6, ())
                .map_err(|e| format!("wl_compositor bind failed: {e}"))?;
            queue
                .roundtrip(&mut state)
                .map_err(|e| format!("wayland roundtrip failed: {e}"))?;

            let surface_id = unsafe {
                ObjectId::from_ptr(
                    wl_surface::WlSurface::interface(),
                    surface_ptr as *mut wayland_sys::client::wl_proxy,
                )
            }
            .map_err(|e| format!("surface wrap failed: {e}"))?;
            let surface = wl_surface::WlSurface::from_id(&conn, surface_id)
                .map_err(|e| format!("WlSurface::from_id failed: {e}"))?;

            if let Ok(mgr) = globals.bind::<ExtBackgroundEffectManagerV1, _, _>(&qh, 1..=1, ()) {
                let effect = mgr.get_background_effect(&surface, &qh, ());
                std::mem::forget(mgr);
                return Ok(Self {
                    conn,
                    queue,
                    compositor,
                    surface,
                    effect: BlurEffect::Ext(effect),
                    region: None,
                    surface_ptr,
                    last_geom: None,
                });
            }

            let mgr: OrgKdeKwinBlurManager = globals
                .bind(&qh, 1..=1, ())
                .map_err(|_| "no compositor blur protocol advertised".to_string())?;
            let blur = mgr.create(&surface, &qh, ());
            std::mem::forget(mgr);
            Ok(Self {
                conn,
                queue,
                compositor,
                surface,
                effect: BlurEffect::Legacy(blur),
                region: None,
                surface_ptr,
                last_geom: None,
            })
        }

        fn set_region(&mut self, width: i32, height: i32, corner_radius: i32) {
            if width <= 0 || height <= 0 {
                return;
            }
            let geom = (width, height, corner_radius);
            if self.last_geom == Some(geom) {
                return;
            }
            self.last_geom = Some(geom);
            let qh = self.queue.handle();
            let region = self.compositor.create_region(&qh, ());
            let r = corner_radius.max(0).min(width / 2).min(height / 2);
            if r == 0 {
                region.add(0, 0, width, height);
            } else {
                region.add(0, r, width, height - 2 * r);
                for y in 1..r {
                    let dy = (r - y) as f64 - 0.5;
                    let dx = ((r as f64).powi(2) - dy.powi(2)).max(0.0).sqrt();
                    let inset = (r as f64 - dx).ceil() as i32 + 1;
                    let w = (width - 2 * inset).max(0);
                    if w > 0 {
                        region.add(inset, y, w, 1);
                        region.add(inset, height - 1 - y, w, 1);
                    }
                }
            }

            match &self.effect {
                BlurEffect::Ext(effect) => {
                    effect.set_blur_region(Some(&region));
                    self.surface.commit();
                }
                BlurEffect::Legacy(blur) => {
                    blur.set_region(Some(&region));
                    blur.commit();
                    self.surface.commit();
                }
            }
            let _ = self.conn.flush();
            if let Some(old) = self.region.replace(region) {
                old.destroy();
            }
        }
    }
}

#[cfg(target_os = "windows")]
mod win32_blur {
    use std::ffi::c_void;
    use std::sync::OnceLock;

    #[repr(C)]
    struct AccentPolicy {
        accent_state: u32,
        accent_flags: u32,
        gradient_color: u32,
        animation_id: u32,
    }

    #[repr(C)]
    struct WindowCompositionAttributeData {
        attribute: u32,
        data: *mut c_void,
        size_of_data: usize,
    }

    #[link(name = "user32")]
    unsafe extern "system" {
        fn GetModuleHandleA(lp_module_name: *const i8) -> *mut c_void;
        fn GetProcAddress(h_module: *mut c_void, lp_proc_name: *const i8) -> *mut c_void;
    }

    #[link(name = "dwmapi")]
    unsafe extern "system" {
        fn DwmSetWindowAttribute(
            hwnd: *mut c_void,
            dw_attribute: u32,
            pv_attribute: *const c_void,
            cb_attribute: u32,
        ) -> i32;
    }

    pub unsafe fn apply_blur(hwnd: *mut c_void, radius: i32, is_dark: bool) {
        let dark_mode: u32 = if is_dark { 1 } else { 0 };
        let _ = unsafe {
            DwmSetWindowAttribute(hwnd, 20, (&dark_mode as *const u32).cast(), 4)
        };
        let corner_pref: u32 = if radius > 0 { 2 } else { 1 };
        let _ = unsafe {
            DwmSetWindowAttribute(hwnd, 33, (&corner_pref as *const u32).cast(), 4)
        };

        type SetWcaFn =
            unsafe extern "system" fn(*mut c_void, *mut WindowCompositionAttributeData) -> i32;
        static SET_WCA: OnceLock<Option<SetWcaFn>> = OnceLock::new();
        let set_wca = SET_WCA.get_or_init(|| unsafe {
            let user32 = GetModuleHandleA(c"user32.dll".as_ptr().cast());
            if user32.is_null() {
                return None;
            }
            let proc = GetProcAddress(user32, c"SetWindowCompositionAttribute".as_ptr().cast());
            if proc.is_null() {
                None
            } else {
                Some(std::mem::transmute::<*mut c_void, SetWcaFn>(proc))
            }
        });
        if let Some(set_wca) = set_wca {
            let mut policy = AccentPolicy {
                accent_state: 3,
                accent_flags: 2,
                gradient_color: 0,
                animation_id: 0,
            };
            let mut data = WindowCompositionAttributeData {
                attribute: 19,
                data: (&mut policy as *mut AccentPolicy).cast(),
                size_of_data: std::mem::size_of::<AccentPolicy>(),
            };
            let _ = unsafe { set_wca(hwnd, &mut data) };
        }
    }
}
