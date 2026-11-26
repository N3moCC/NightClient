state {
  search = ""; expanded = false; sidebarCollapsed = false; settingsSection = "appearance"
  scriptsOpen = false; profileCreateOpen = false
  waypointManager = ""; waypointDraftId = ""; waypointDraftName = ""; waypointDraftX = ""; waypointDraftY = ""; waypointDraftZ = ""; waypointDraftColor = "#BBF7D0FF"; waypointDraftDimension = "minecraft:overworld"; waypointScope = "world"; waypointDeleteConfirm = false
  accountManager = ""; accountSearch = ""; accountSelectedId = ""; accountDraftId = ""; accountDraftName = ""; accountDraftNote = ""; accountDraftSkin = ""
  cosmeticsTab = "capes"; cosmeticsCapeFilter = "all"; cosmeticsCapeQuery = ""; cosmeticsNickDraft = ""; cosmeticsCapePathDraft = ""; cosmeticsSkinPathDraft = ""; cosmeticsWeaponPathDraft = ""; cosmeticsArmorPathDraft = ""
}

style element menu {
  let m-w = "min(616,viewportWidth-20)"
  let m-h = "min(388,viewportHeight-20)"
  let m-compact = "ui.sidebarCollapsed != (viewportWidth<560)"
  let m-rail = "vars.m_compact ? 48 : 148"
  let m-sheet-x = "vars.m_rail"
  let m-sheet-w = "max(0,vars.m_w-vars.m_rail-6)"
  let m-sheet-h = "max(0,vars.m_h-12)"
  let m-nav-step = "28"
  let m-prefix = "page=='all' ? 'all-' : page=='player' ? 'player-' : page=='hud' ? 'hud-' : ''"
  viewportMinWidth = 320px; viewportMinHeight = 240px
  position = absolute; x = 50%; y = 50%; anchorX = .5; anchorY = .5
  width = "{{= vars.m_w}}"; height = "{{= vars.m_h}}"; layout = absolute; padding = 0px; overflow = hidden
  background = "{{= vars.d_bg}}"; backgroundOpacity = "{{= menu.opacity*theme.backgroundOpacity}}"; tintOpacity = -1
  borderRadius = "{{= vars.r_outer}}"; borderWidth = .65px; borderColor = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}"
  fontFamily = studio-sans; fontSize = 8px; color = "{{= vars.d_text}}"
  enterScale = .985; enterOpacity = 0%; enterOffsetY = 6px; enterDuration = 210ms; exitDuration = 140ms
  transitionDuration = 170ms; easing = "cubic-bezier(0.22,1,0.36,1)"; animateLayout = false; pressScale = 1
}

style element panel { blur = false; layout = column; padding = 0px; gap = 0px; background = #00000000; borderWidth = 0px; borderRadius = "{{= vars.r_card}}"; enterOpacity = 100%; enterOffsetY = 0px; animateLayout = false }
style element text { padding = 0px; fontFamily = studio-sans; fontSize = 7.2px; color = "{{= vars.d_text}}"; textOverflow = ellipsis; enterOpacity = 100%; enterOffsetY = 0px; animateLayout = false }
style element icon { color = "{{= (theme.mode=='custom' ? lerpColor(theme.text,theme.surface,.3) : theme.mode=='light' ? '#52525B' : '#A3A3AA')}}"; strokeWidth = 1.55px; enterOpacity = 100%; enterOffsetY = 0px; animateLayout = false }
style element button {
  blur = false; height = 24px; padding = "0px 8px"; borderRadius = "{{= vars.r_ctrl}}"; borderWidth = 0px
  background = #00000000; color = "{{= vars.d_text}}"; fontFamily = studio-sans; fontSize = 7.2px; textAlign = left
  enterOpacity = 100%; enterOffsetY = 0px; hoverScale = 1; pressScale = .985; animateLayout = false
  transitionDuration = 150ms; easing = "cubic-bezier(0.22,1,0.36,1)"
  hover { background = "{{= alphaColor(vars.d_text,.045)}}" }
  active { background = "{{= alphaColor(vars.d_text,.07)}}" }
}
style element input {
  height = 24px; width = 100%; padding = "0px 8px"; fontSize = 7px; color = "{{= vars.d_text}}"
  background = "{{= vars.d_raised}}"; borderRadius = "{{= vars.r_ctrl}}"; borderWidth = .6px; borderColor = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}"
  enterOpacity = 100%; enterOffsetY = 0px; animateLayout = false
  focus { borderColor = "{{= alphaColor(vars.d_accent,.65)}}"; background = "{{= (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.045) : theme.mode=='light' ? '#FFFFFF' : '#242429')}}" }
}
style element toggle { height = 22px; padding = 0px; borderWidth = 0px; fontSize = 7.1px; color = "{{= vars.d_text}}"; enterOpacity = 100%; enterOffsetY = 0px }
style element slider { height = 28px; padding = 0px; borderWidth = 0px; fontSize = 7px; color = "{{= vars.d_text}}"; enterOpacity = 100%; enterOffsetY = 0px }
style element choice { height = 22px; padding = 0px; borderWidth = 0px; fontSize = 6.9px; color = "{{= vars.d_text}}"; enterOpacity = 100%; enterOffsetY = 0px }
style element color { height = 24px; padding = 0px; borderWidth = 0px; fontSize = 6.9px; color = "{{= vars.d_text}}"; enterOpacity = 100%; enterOffsetY = 0px }
style element pixel-grid { height = 112px; width = 112px; borderWidth = 0px; pressScale = 1 }

style class nc-nav-btn {
  position = absolute; x = 8px; width = "{{= max(0,boxWidth-16)}}"; height = 26px; layout = absolute; padding = 0px
  borderRadius = "{{= vars.r_ctrl}}"; borderWidth = 0px; background = #00000000; pressScale = .985
  transitionDuration = 150ms; easing = "cubic-bezier(0.22,1,0.36,1)"; animateLayout = true
  hover { background = "{{= alphaColor(vars.d_text,.045)}}" }
  active { background = #00000000 }
}
style class nc-nav-label {
  position = absolute; x = 32px; y = 0px; width = "{{= max(0,boxWidth-38)}}"; height = 100%
  fontSize = 7.6px; fontFamily = studio-sans-medium; contentOpacity = "{{= vars.m_compact ? 0 : 1}}"
  animateValue = true; transitionDuration = 130ms
}
style class nc-scroll {
  layoutReflow = true; position = absolute; x = 0px; y = 44px; width = 100%; height = "{{= max(0,boxHeight-44)}}"
  layout = column; padding = "10px 14px 12px 14px"; gap = 8px; overflow = scroll; scrollbarWidth = 2px
  scrollStep = 34px; scrollDuration = 180ms
  animateEnter = true; enterOpacity = 0%; enterOffsetY = 4px; enterDuration = 190ms
  transitionDuration = 180ms; easing = "cubic-bezier(0.22,1,0.36,1)"; animateLayout = true
}
style class nc-drawer {
  width = 100%; layout = column; padding = "8px 10px 10px 10px"; gap = 7px
  background = #00000000
  borderRadius = 0px; borderWidth = 0px
  animateEnter = true; enterOpacity = 0%; enterOffsetY = -3px; enterDuration = 170ms
  transitionDuration = 190ms; easing = "cubic-bezier(0.22,1,0.36,1)"; animateLayout = true
}
style class nc-controls { width = 100%; layout = grid; columns = 1; gap = 8px; padding = 0px; animationStagger = 0ms }
style class nc-sub { width = 100%; layout = column; gap = 6px; padding = "7px 8px"; background = "{{= vars.d_raised}}"; borderWidth = .55px; borderColor = "{{= vars.d_border}}"; borderRadius = "{{= vars.r_ctrl}}" }
style class nc-section { height = 14px; fontSize = 6.7px; fontFamily = studio-sans-medium; color = "{{= vars.d_muted}}" }
style class nc-caption { height = 9px; fontSize = 6.3px; color = "{{= vars.d_muted}}" }
style class nc-actions { width = 100%; height = 24px; layout = row; align = center; gap = 5px; padding = 0px }
style class nc-btn {
  height = 24px; grow = 1; layout = row; align = center; justify = center; gap = 5px; textAlign = center
  fontSize = 6.9px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}"
  background = "{{= (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E')}}"; borderWidth = .55px; borderColor = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}"; borderRadius = "{{= vars.r_ctrl}}"
  hover { background = "{{= lerpColor((theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E'),vars.d_text,.06)}}" }
}
style class nc-btn-primary {
  background = "{{= vars.d_accent}}"; color = "{{= vars.d_on_accent}}"; borderWidth = 0px
  hover { background = "{{= lerpColor(vars.d_accent,vars.d_surface,.1)}}" }
}
style class nc-icon-btn {
  width = 24px; height = 24px; padding = 0px; layout = row; justify = center; align = center
  borderRadius = "{{= vars.r_ctrl}}"; borderWidth = .55px; borderColor = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}"; background = "{{= (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E')}}"
  hover { background = "{{= lerpColor((theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E'),vars.d_text,.06)}}" }
}
style class nc-bind-row {
  width = 100%; height = 24px; layout = row; align = center; padding = "0px 8px"; gap = 6px
  background = "{{= vars.d_well}}"; borderWidth = .55px; borderColor = "{{= vars.d_border}}"; borderRadius = "{{= vars.r_ctrl}}"
  hover { background = "{{= vars.d_raised}}" }
}
style class nc-keycap {
  minWidth = 32px; maxWidth = 48%; height = 16px; padding = "0px 6px"
  fontFamily = studio-sans-medium; fontSize = 6.3px; textAlign = center
  background = "{{= vars.d_well}}"; borderColor = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}"; borderWidth = .55px; borderRadius = "{{= vars.r_sm}}"; color = "{{= vars.d_text}}"
}
style class nc-menu-key {
  width = "{{= min(max(40,boxWidth*.5),max(40,textWidth(menuBindingLabel,6.6,'studio-sans-medium')+14))}}"
  height = 18px; padding = "0px 6px"; fontFamily = studio-sans-medium; fontSize = 6.6px; textAlign = center
  background = "{{= vars.d_well}}"; borderColor = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}"; borderWidth = .55px; borderRadius = "{{= vars.r_sm}}"; color = "{{= vars.d_text}}"
}
style class nc-binding {
  width = 100%; height = 32px; layout = row; align = center; padding = "0px 10px"; gap = 8px
  background = "{{= vars.d_raised}}"; borderWidth = .6px; borderColor = "{{= vars.d_border}}"; borderRadius = "{{= vars.r_card}}"
  hover { background = "{{= (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.045) : theme.mode=='light' ? '#FFFFFF' : '#242429')}}" }
}
style class nc-card { width = 100%; layout = column; gap = 7px; padding = "10px"; background = "{{= vars.d_raised}}"; borderRadius = "{{= vars.r_card}}"; borderWidth = .6px; borderColor = "{{= vars.d_border}}" }
style class nc-seg-tab { grow = 1; height = 100%; padding = 0px; textAlign = center; fontFamily = studio-sans-medium; fontSize = 6.7px; borderRadius = "{{= vars.r_sm}}"; animateValue = true; transitionDuration = 150ms }

style class nc-pill { grow = 1; height = 21px; padding = "0px 6px"; fontSize = 6.5px; textAlign = center; borderRadius = "{{= vars.r_ctrl}}"; borderWidth = .55px; borderColor = "{{= vars.d_border}}"; background = "{{= (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E')}}" }
style class nc-filter { grow = 1; height = 18px; padding = "0px 3px"; fontSize = 6.1px; textAlign = center; borderRadius = "{{= vars.r_sm}}"; borderWidth = 0px; background = #00000000 }
style class nc-cape-grid { width = 100%; layout = grid; columns = 2; min-cell-width = 92px; gap = 6px; padding = 0px; animationStagger = 0ms }
style class nc-cape-card { width = 100%; height = 46px; layout = absolute; padding = 0px; borderRadius = "{{= vars.r_card}}"; borderWidth = .6px; background = "{{= vars.d_raised}}"; pressScale = .985; animateLayout = false; hover { background = "{{= (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.045) : theme.mode=='light' ? '#FFFFFF' : '#242429')}}" } }
style class nc-mini-icon { width = 19px; height = 19px; padding = 0px; borderRadius = "{{= vars.r_sm}}"; borderWidth = .55px; borderColor = "{{= vars.d_border}}"; background = "{{= (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E')}}"; layout = row; align = center; justify = center }
style class nc-swatch { width = 18px; height = 18px; padding = 0px; borderRadius = "{{= vars.r_sm}}"; borderWidth = .75px; borderColor = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}"; pressScale = .92 }

style class nc-modal {
  position = overlay; x = 50%; y = 50%; anchorX = .5; anchorY = .5; width = 284px; maxWidth = 92%; height = auto; maxHeight = 88%
  layout = column; padding = 13px; gap = 9px; background = "{{= setAlphaColor(vars.d_surface,1)}}"; backgroundOpacity = 100%; surfaceOpacity = 100%; tintOpacity = -1
  borderColor = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}"; borderWidth = .65px; borderRadius = "{{= vars.r_outer}}"; zIndex = 60
  animateEnter = true; enterOpacity = 0%; enterOffsetY = 6px; enterDuration = 200ms; transitionDuration = 200ms; animateLayout = true
}
style class nc-modal-head { width = 100%; height = 22px; layout = row; align = center; gap = 7px }
style class nc-modal-title { height = 100%; grow = 1; fontSize = 8.8px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
style class nc-scrim { position = absolute; x = 0px; y = 0px; width = 100%; height = 100%; background = #00000072; borderWidth = 0px; borderRadius = "{{= vars.r_outer}}"; zIndex = 80 }
style class nc-wp-modal {
  position = absolute; x = 50%; y = 50%; anchorX = .5; anchorY = .5; layout = column; padding = 13px; gap = 8px
  blur = "{{= theme.effects.backgroundBlur}}"; background = "{{= vars.d_surface}}"; backgroundOpacity = 94%; surfaceOpacity = 100%; tintOpacity = -1
  borderWidth = .65px; borderColor = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}"; borderRadius = "{{= vars.r_outer}}"; zIndex = 81
  enterOpacity = 0%; enterOffsetY = 6px; enterDuration = 200ms; animateLayout = true
}
style class nc-wp-close { width = 22px; height = 22px; padding = 0px; borderWidth = 0px; borderRadius = "{{= vars.r_ctrl}}"; layout = row; align = center; justify = center }
style class nc-wp-scroll { height = 0px; grow = 1; layout = column; gap = 5px; padding = 0px; overflow = scroll; scrollbarWidth = 2px; scrollStep = 30px; scrollDuration = 180ms }
style class nc-wp-row { layout = row; height = 38px; width = 100%; align = center; gap = 7px; padding = "0px 8px"; background = "{{= vars.d_raised}}"; borderWidth = .55px; borderColor = "{{= vars.d_border}}"; borderRadius = "{{= vars.r_card}}" }
style class nc-wp-swatch { width = 4px; height = 18px; radius = 2px }
style class nc-wp-copy { layout = column; grow = 1; height = 100%; justify = center; gap = 2.5px; overflow = hidden }
style class nc-wp-name { height = 9.5px; fontSize = 7.4px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
style class nc-wp-meta { height = 7.5px; fontSize = 6px; color = "{{= vars.d_muted}}" }
style class nc-wp-icon { width = 22px; height = 22px; padding = 0px; borderWidth = .5px; borderColor = "{{= vars.d_border}}"; borderRadius = "{{= vars.r_ctrl}}"; background = "{{= (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E')}}"; layout = row; align = center; justify = center }
style class nc-wp-footer { layout = row; height = 24px; width = 100%; gap = 6px; padding = 0px }
style class nc-wp-form { layout = column; width = 100%; height = 0px; grow = 1; gap = 8px; padding = 0px; overflow = scroll; scrollbarWidth = 0px }
style class nc-wp-field { layout = column; width = 100%; gap = 3px; padding = 0px }
style class nc-wp-label { height = 9px; fontSize = 6.4px; color = "{{= vars.d_muted}}" }
style class nc-wp-color-row { layout = row; width = 100%; height = 24px; align = center; gap = 7px }
style class nc-wp-color { width = 18px; height = 18px; radius = "{{= vars.r_sm}}" }
style class nc-wp-world-bar { layout = row; width = 100%; height = 22px; align = center; gap = 5px; padding = "0px 2px" }
style class nc-wp-scope-tabs { layout = row; width = 132px; height = 21px; gap = 2px; padding = 2px; background = "{{= vars.d_well}}"; borderRadius = "{{= vars.r_ctrl}}"; borderWidth = .55px; borderColor = "{{= vars.d_border}}" }
style class nc-wp-scope-tab { grow = 1; height = 17px; padding = "0px 4px"; fontSize = 6.2px; textAlign = center; borderRadius = "{{= vars.r_sm}}"; borderWidth = 0px }

style class nc-acc-scrim { position = absolute; x = 0px; y = 0px; width = 100%; height = 100%; background = #00000072; borderWidth = 0px; borderRadius = "{{= vars.r_outer}}"; zIndex = 84 }
style class nc-acc-modal {
  position = absolute; x = 50%; y = 50%; anchorX = .5; anchorY = .5; layout = column; padding = 13px; gap = 7px
  blur = "{{= theme.effects.backgroundBlur}}"; background = "{{= vars.d_surface}}"; backgroundOpacity = 96%; surfaceOpacity = 100%; tintOpacity = -1
  borderWidth = .65px; borderColor = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}"; borderRadius = "{{= vars.r_outer}}"; zIndex = 85
  enterOpacity = 0%; enterOffsetY = 6px; enterDuration = 200ms; animateLayout = true
}
style class nc-acc-mode-tabs { layout = row; width = 80px; height = 20px; gap = 2px; padding = 2px; background = "{{= vars.d_well}}"; borderRadius = "{{= vars.r_ctrl}}"; borderWidth = .55px; borderColor = "{{= vars.d_border}}" }
style class nc-acc-mode-tab { grow = 1; height = 16px; padding = "0px 3px"; fontSize = 6px; textAlign = center; borderRadius = "{{= vars.r_sm}}"; borderWidth = 0px }
style class nc-acc-stage { width = 148px; height = 100%; layout = column; padding = 8px; gap = 5px; background = "{{= vars.d_raised}}"; borderRadius = "{{= vars.r_card}}"; borderWidth = .55px; borderColor = "{{= vars.d_border}}" }
style class nc-acc-row { layout = row; height = 34px; width = 100%; align = center; gap = 7px; padding = "0px 7px"; background = "{{= vars.d_raised}}"; borderWidth = .55px; borderColor = "{{= vars.d_border}}"; borderRadius = "{{= vars.r_card}}"; pressScale = .99 }
style class nc-acc-face-frame { width = 20px; height = 20px; padding = 0px; background = "{{= vars.d_well}}"; borderRadius = "{{= vars.r_sm}}"; borderWidth = .5px; borderColor = "{{= vars.d_border}}"; layout = absolute }
style class nc-acc-face { position = absolute; x = 2px; y = 2px; width = 16px; height = 16px }
style class nc-acc-pill { height = 13px; padding = "0px 5px"; fontSize = 5.6px; textAlign = center; borderRadius = "{{= vars.r_sm}}" }
style class nc-acc-icon { width = 20px; height = 20px; padding = 0px; borderWidth = .5px; borderColor = "{{= vars.d_border}}"; borderRadius = "{{= vars.r_sm}}"; background = "{{= (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E')}}"; layout = row; align = center; justify = center }

menu (id = "menu") {
  panel (id = "topbar") { position = absolute; x = 0px; y = 0px; width = 100%; height = 46px; layout = absolute; background = #00000000; borderWidth = 0px }

  button (id = "sidebar-toggle", action = "ui-toggle:sidebarCollapsed", tooltip = "{{i18n.ui.toggle_sidebar}}") {
    layoutReflow = true; position = absolute; x = 8px; y = 10px; width = "{{= max(0,vars.m_rail-16)}}"; height = 30px
    layout = absolute; padding = 0px; borderRadius = "{{= vars.r_ctrl}}"; borderWidth = 0px; background = #00000000; pressScale = .98
    animateLayout = true; transitionDuration = 190ms; easing = "cubic-bezier(0.22,1,0.36,1)"
    hover { background = "{{= alphaColor(vars.d_text,.045)}}" }
    icon (src = "nightclient-mark") { position = absolute; x = 8px; y = 7px; width = 16px; height = 16px; color = "{{= vars.d_text}}" }
    text "NightClient" {
      position = absolute; x = 32px; y = 0px; width = "{{= max(0,boxWidth-36)}}"; height = 100%
      fontFamily = studio-sans-medium; fontSize = 8.4px; color = "{{= vars.d_text}}"
      contentOpacity = "{{= vars.m_compact ? 0 : 1}}"; animateValue = true; transitionDuration = 130ms
    }
  }

  panel (id = "navigation") {
    layoutReflow = true; position = absolute; x = 0px; y = 46px; width = "{{= vars.m_rail}}"; height = "{{= max(0,vars.m_h-46)}}"
    layout = absolute; overflow = hidden; padding = 0px; background = #00000000
    animateLayout = true; transitionDuration = 190ms; easing = "cubic-bezier(0.22,1,0.36,1)"

    column (id = "sidebar-scroll") {
      position = absolute; x = 0px; y = 2px; width = 100%; height = "{{= max(0,boxHeight-44)}}"
      padding = 0px; overflow = scroll; scrollbarWidth = 0px; scrollStep = 28px; scrollDuration = 180ms
      panel {
        width = 100%; height = "{{= (hwhelper.enabled && hwhelper.events.enabled ? 9.4 : 8.4)*vars.m_nav_step}}"; layout = absolute
        panel (id = "sidebar-selection", if = "{{= page!='cosmetics'}}") {
          position = absolute; x = 8px
          y = "{{= (page=='all' ? 0 : page=='features' ? 1 : page=='camera' ? 2 : page=='world' ? 3 : page=='player' ? 4 : page=='waypoints' ? 5.32 : page=='events' ? 6.32 : page=='hud' ? (hwhelper.enabled && hwhelper.events.enabled ? 7.32 : 6.32) : (hwhelper.enabled && hwhelper.events.enabled ? 8.32 : 7.32))*vars.m_nav_step}}"
          width = "{{= max(0,boxWidth-16)}}"; height = 26px; borderRadius = "{{= vars.r_ctrl}}"
          background = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.08) : theme.mode=='light' ? '#FFFFFF' : '#FFFFFF12')}}"; borderWidth = .55px; borderColor = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.12) : theme.mode=='light' ? '#18181B18' : '#FFFFFF18')}}"
          animateLayout = true; transitionDuration = 170ms; easing = "cubic-bezier(0.22,1,0.36,1)"
        }

        button (id = "nav-all", action = "page:all", style = "nc-nav-btn", tooltip = "{{i18n.ui.all}}") { y = "{{= vars.m_nav_step*0}}"
          icon (src = "grid") { position = absolute; x = 10px; y = 7px; width = 12px; height = 12px; color = "{{= page=='all' ? vars.d_text : vars.d_muted}}" }
          text "{{i18n.ui.all}}" (style = "nc-nav-label") { color = "{{= page=='all' ? vars.d_text : (theme.mode=='custom' ? lerpColor(theme.text,theme.surface,.3) : theme.mode=='light' ? '#52525B' : '#A3A3AA')}}" }
        }
        button (id = "nav0", action = "page:features", style = "nc-nav-btn", tooltip = "{{i18n.ui.visuals}}") { y = "{{= vars.m_nav_step*1}}"
          icon (src = "eye") { position = absolute; x = 10px; y = 7px; width = 12px; height = 12px; color = "{{= page=='features' ? vars.d_text : vars.d_muted}}" }
          text "{{i18n.ui.visuals}}" (style = "nc-nav-label") { color = "{{= page=='features' ? vars.d_text : (theme.mode=='custom' ? lerpColor(theme.text,theme.surface,.3) : theme.mode=='light' ? '#52525B' : '#A3A3AA')}}" }
        }
        button (id = "nav-camera", action = "page:camera", style = "nc-nav-btn", tooltip = "{{i18n.ui.camera}}") { y = "{{= vars.m_nav_step*2}}"
          icon (src = "camera") { position = absolute; x = 10px; y = 7px; width = 12px; height = 12px; color = "{{= page=='camera' ? vars.d_text : vars.d_muted}}" }
          text "{{i18n.ui.camera}}" (style = "nc-nav-label") { color = "{{= page=='camera' ? vars.d_text : (theme.mode=='custom' ? lerpColor(theme.text,theme.surface,.3) : theme.mode=='light' ? '#52525B' : '#A3A3AA')}}" }
        }
        button (id = "nav-world", action = "page:world", style = "nc-nav-btn", tooltip = "{{i18n.ui.world}}") { y = "{{= vars.m_nav_step*3}}"
          icon (src = "world") { position = absolute; x = 10px; y = 7px; width = 12px; height = 12px; color = "{{= page=='world' ? vars.d_text : vars.d_muted}}" }
          text "{{i18n.ui.world}}" (style = "nc-nav-label") { color = "{{= page=='world' ? vars.d_text : (theme.mode=='custom' ? lerpColor(theme.text,theme.surface,.3) : theme.mode=='light' ? '#52525B' : '#A3A3AA')}}" }
        }
        button (id = "nav-player", action = "page:player", style = "nc-nav-btn", tooltip = "{{i18n.ui.player}}") { y = "{{= vars.m_nav_step*4}}"
          icon (src = "user") { position = absolute; x = 10px; y = 7px; width = 12px; height = 12px; color = "{{= page=='player' ? vars.d_text : vars.d_muted}}" }
          text "{{i18n.ui.player}}" (style = "nc-nav-label") { color = "{{= page=='player' ? vars.d_text : (theme.mode=='custom' ? lerpColor(theme.text,theme.surface,.3) : theme.mode=='light' ? '#52525B' : '#A3A3AA')}}" }
        }

        rect { position = absolute; x = 14px; y = "{{= vars.m_nav_step*5.12}}"; width = "{{= max(0,boxWidth-28)}}"; height = .55px; fill = "{{= vars.d_border}}" }

        button (id = "nav-waypoints", action = "page:waypoints", style = "nc-nav-btn", tooltip = "{{i18n.module.waypoints.label}}") { y = "{{= vars.m_nav_step*5.32}}"
          icon (src = "anchor") { position = absolute; x = 10px; y = 7px; width = 12px; height = 12px; color = "{{= page=='waypoints' ? vars.d_text : vars.d_muted}}" }
          text "{{i18n.module.waypoints.label}}" (style = "nc-nav-label") { color = "{{= page=='waypoints' ? vars.d_text : (theme.mode=='custom' ? lerpColor(theme.text,theme.surface,.3) : theme.mode=='light' ? '#52525B' : '#A3A3AA')}}" }
        }
        button (id = "nav-events", if = "{{= hwhelper.enabled && hwhelper.events.enabled}}", action = "page:events", style = "nc-nav-btn", tooltip = "{{i18n.hwEvents.title}}") { y = "{{= vars.m_nav_step*6.32}}"
          icon (src = "events") { position = absolute; x = 10px; y = 7px; width = 12px; height = 12px; color = "{{= page=='events' ? vars.d_text : vars.d_muted}}" }
          text "{{i18n.hwEvents.tab}}" (style = "nc-nav-label") { color = "{{= page=='events' ? vars.d_text : (theme.mode=='custom' ? lerpColor(theme.text,theme.surface,.3) : theme.mode=='light' ? '#52525B' : '#A3A3AA')}}" }
        }
        button (id = "nav2", action = "page:hud", style = "nc-nav-btn", tooltip = "{{i18n.section.hud}}") { y = "{{= vars.m_nav_step*(hwhelper.enabled && hwhelper.events.enabled ? 7.32 : 6.32)}}"
          icon (src = "layout") { position = absolute; x = 10px; y = 7px; width = 12px; height = 12px; color = "{{= page=='hud' ? vars.d_text : vars.d_muted}}" }
          text "{{i18n.section.hud}}" (style = "nc-nav-label") { color = "{{= page=='hud' ? vars.d_text : (theme.mode=='custom' ? lerpColor(theme.text,theme.surface,.3) : theme.mode=='light' ? '#52525B' : '#A3A3AA')}}" }
        }
        button (id = "nav-settings", action = "page:settings", style = "nc-nav-btn", tooltip = "{{i18n.ui.settings}}") { y = "{{= vars.m_nav_step*(hwhelper.enabled && hwhelper.events.enabled ? 8.32 : 7.32)}}"
          icon (src = "sliders") { position = absolute; x = 10px; y = 7px; width = 12px; height = 12px; color = "{{= page=='settings' ? vars.d_text : vars.d_muted}}" }
          text "{{i18n.ui.settings}}" (style = "nc-nav-label") { color = "{{= page=='settings' ? vars.d_text : (theme.mode=='custom' ? lerpColor(theme.text,theme.surface,.3) : theme.mode=='light' ? '#52525B' : '#A3A3AA')}}" }
        }
      }
    }

    button (id = "local-profile", action = "cosmetics-open", tooltip = "{{i18n.ui.cosmetics}}") {
      position = absolute; x = 8px; bottom = 8px; width = "{{= max(0,boxWidth-16)}}"; height = 32px
      layout = absolute; padding = 0px; borderRadius = "{{= vars.r_ctrl}}"; borderWidth = .55px
      borderColor = "{{= page=='cosmetics' ? (theme.mode=='custom' ? alphaColor(theme.text,.12) : theme.mode=='light' ? '#18181B18' : '#FFFFFF18') : '#00000000'}}"
      background = "{{= page=='cosmetics' ? (theme.mode=='custom' ? alphaColor(theme.text,.08) : theme.mode=='light' ? '#FFFFFF' : '#FFFFFF12') : '#00000000'}}"
      hover { background = "{{= alphaColor(vars.d_text,.05)}}" }
      entity-head (id = "local-player-head", source = "localPlayerData") { position = absolute; x = 7px; y = 7px; width = 18px; height = 18px; radius = "{{= vars.r_sm}}" }
      text "{{= protect.enabled ? protect.replacement : localPlayerData.name}}" (id = "local-player-name") {
        position = absolute; x = 32px; y = 0px; width = "{{= max(0,boxWidth-38)}}"; height = 100%
        fontSize = 7.6px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}"
        contentOpacity = "{{= vars.m_compact ? 0 : 1}}"; animateValue = true; transitionDuration = 130ms
      }
    }
  }

  panel (id = "main-sheet") {
    layoutReflow = true
    position = absolute; x = "{{= vars.m_sheet_x}}"; y = 6px
    width = "{{= vars.m_sheet_w}}"; height = "{{= vars.m_sheet_h}}"
    layout = absolute; overflow = hidden
    background = "{{= vars.d_surface}}"
    borderWidth = .65px; borderColor = "{{= vars.d_border}}"
    borderRadius = "{{= vars.r_card}}"
    animateLayout = true; transitionDuration = 190ms; easing = "cubic-bezier(0.22,1,0.36,1)"

    panel (id = "menu-header") {
      layoutReflow = true; position = absolute; x = 0px; y = 0px; width = 100%; height = 44px; layout = absolute; padding = 0px
      animateLayout = true; transitionDuration = 190ms; easing = "cubic-bezier(0.22,1,0.36,1)"

      text "{{= page=='all' ? i18n.ui.all : page=='features' ? i18n.ui.visuals : page=='camera' ? i18n.ui.camera : page=='world' ? i18n.ui.world : page=='player' ? i18n.ui.player : page=='waypoints' ? i18n.module.waypoints.label : page=='events' ? i18n.hwEvents.title : page=='hud' ? i18n.nav.hud : page=='cosmetics' ? i18n.cosmetics.title : i18n.ui.settings}}" (id = "page-title") {
        position = absolute; x = 14px; y = 0px
        width = "{{= max(0, boxWidth - (page=='settings' ? min(244,boxWidth-16) : page=='hud' ? min(278,boxWidth-16) : (page=='cosmetics' || page=='waypoints' || page=='events') ? 28 : min(198,boxWidth-16)))}}"
        height = 100%; fontFamily = studio-sans-medium; fontSize = 11.2px; color = "{{= vars.d_text}}"
      }

      panel (id = "search-wrap", if = "{{= page!='settings' && page!='cosmetics' && page!='waypoints' && page!='events'}}") {
        position = absolute; right = "{{= page=='hud' ? (boxWidth<240 ? 78 : 96) : 14}}"; y = 10px
        width = "{{= min(168,max(64,boxWidth-(page=='hud' ? (boxWidth<240 ? 92 : 178) : 112)))}}"; height = 24px; layout = absolute
        input (id = "module-search", bind = "ui.search", placeholder = "{{i18n.ui.find_a_feature}}", maxlength = "96", search-input = "true", search-pages = "all features camera world player hud", search-page = "all") {
          position = absolute; x = 0px; y = 0px; width = 100%; height = 24px
          padding = "0px 22px 0px 24px"; borderRadius = "{{= vars.r_ctrl}}"; fontSize = 7px
          background = "{{= vars.d_raised}}"; borderColor = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}"
        }
        icon (src = "search") { position = absolute; x = 8px; y = 7.5px; width = 9px; height = 9px; color = "{{= vars.d_muted}}" }
        button (id = "search-clear", if = "{{= len(ui.search)>0}}", action = "ui-set:search:") {
          position = absolute; right = 4px; y = 4px; width = 16px; height = 16px; padding = 0px; borderRadius = "{{= vars.r_sm}}"
          layout = row; align = center; justify = center
          icon (src = "close") { width = 7.5px; height = 7.5px; color = "{{= vars.d_muted}}" }
        }
      }

      button "{{i18n.ui.hud_editor}}" (id = "edit-hud", if = "page=hud", action = "editor-open", style = "nc-btn nc-btn-primary") {
        position = absolute; right = 14px; y = 10px; width = "{{= boxWidth<240 ? 60 : 76}}"; height = 24px; grow = 0; fontSize = 6.8px
      }

      panel (if = "page=settings") {
        position = absolute; right = 14px; y = 10px; width = "{{= min(216,max(108,boxWidth-28))}}"; height = 24px
        layout = row; padding = 2px; gap = 2px
        background = "{{= vars.d_well}}"; borderRadius = "{{= vars.r_ctrl}}"; borderWidth = .55px; borderColor = "{{= vars.d_border}}"
        button "{{i18n.ui.appearance}}" (id = "settings-appearance", action = "ui-set:settingsSection:appearance", style = "nc-seg-tab") {
          background = "{{= ui.settingsSection=='appearance' ? vars.d_accent : '#00000000'}}"; color = "{{= ui.settingsSection=='appearance' ? vars.d_on_accent : vars.d_muted}}"
        }
        button "{{i18n.ui.interface}}" (id = "settings-interface", action = "ui-set:settingsSection:interface", style = "nc-seg-tab") {
          background = "{{= ui.settingsSection=='interface' ? vars.d_accent : '#00000000'}}"; color = "{{= ui.settingsSection=='interface' ? vars.d_on_accent : vars.d_muted}}"
        }
        button "{{i18n.ui.settings_profiles}}" (id = "settings-profiles", action = "ui-set:settingsSection:profiles", style = "nc-seg-tab") {
          background = "{{= ui.settingsSection=='profiles' ? vars.d_accent : '#00000000'}}"; color = "{{= ui.settingsSection=='profiles' ? vars.d_on_accent : vars.d_muted}}"
        }
      }

      rect { position = absolute; x = 0px; bottom = 0px; width = 100%; height = .55px; fill = "{{= vars.d_border}}" }
    }

    column (id = "modules-list", if = "{{= page!='settings' && page!='cosmetics' && page!='waypoints' && page!='events'}}", style = "nc-scroll") {
      text "{{i18n.ui.no_results}}" (if = "{{= retainedCount('categoryModules')==0}}", style = "nc-caption") { height = 28px; fontSize = 7.2px; textAlign = center }
      repeat (source = "categoryModules", as = "module", key = "{{module.id}}") {
        width = 100%; layout = masonry; columns = 2; minCellWidth = 180px; masonryStable = true; gap = 8px; padding = "0px 0px 8px 0px"
        panel (id = "card.{{module.id}}", scope = "module.{{module.id}}", selection-state = "expanded") {
          width = 100%; layout = column; gap = 0px; padding = 0px; overflow = hidden
          background = "{{= (module.enabled || local.expanded) ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.045) : theme.mode=='light' ? '#FFFFFF' : '#242429') : vars.d_raised}}"
          borderRadius = "{{= vars.r_card}}"; borderWidth = .6px
          borderColor = "{{= (local.expanded || module.enabled) ? (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24') : vars.d_border}}"
          animateEnter = true; enterOpacity = 0%; enterOffsetY = 4px; enterDuration = 200ms; animationStagger = 14ms
          transitionDuration = 220ms; easing = "cubic-bezier(0.22,1,0.36,1)"; animateLayout = true; animateValue = true

          button (id = "{{= vars.m_prefix}}{{module.id}}", action = "toggle:{{module.bind}}", secondary-action = "local-toggle:expanded", middle-action = "bind-module:{{module.id}}", tooltip = "{{module.label}}") {
            width = 100%; height = 60px; layout = absolute; padding = 0px; borderRadius = "{{= vars.r_card}}"; borderWidth = 0px; background = #00000000; pressScale = .992
            hover { background = "{{= alphaColor(vars.d_text,.025)}}" }

            rect {
              position = absolute; x = 10px; y = 10px; width = 24px; height = 24px; radius = "{{= vars.r_sm}}"
              fill = "{{= module.enabled ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E') : vars.d_well}}"
              stroke = "{{= module.enabled ? (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24') : vars.d_border}}"; strokeWidth = .5px
            }
            icon (src = "{{module.icon}}") {
              position = absolute; x = 16px; y = 16px; width = 12px; height = 12px; strokeWidth = 1.5px
              color = "{{= module.enabled ? vars.d_text : vars.d_muted}}"
            }

            text "{{module.label}}" {
              position = absolute; x = 42px; y = 10px; width = "{{= max(0,boxWidth-86)}}"; height = 11px
              fontFamily = studio-sans-medium; fontSize = 8.2px; color = "{{= vars.d_text}}"
            }
            text "{{module.summary}}" {
              position = absolute; x = 42px; y = 23px; width = "{{= max(0,boxWidth-86)}}"; height = 10px
              fontSize = 6.5px; color = "{{= vars.d_muted}}"
            }

            rect {
              position = absolute; right = 10px; y = 14px; width = 26px; height = 14px; radius = 7px
              fillTopLeft = "{{= module.enabled ? (theme.mode=='custom' ? (theme.accent==theme.text ? '#070709' : lerpColor(theme.accent,'#000000',.25)) : theme.mode=='light' ? '#0F0F12' : '#070709') : (theme.mode=='custom' ? lerpColor(theme.background,'#000000',.22) : theme.mode=='light' ? '#D4D4CF' : '#0B0B0D')}}"
              fillTopRight = "{{= module.enabled ? (theme.mode=='custom' ? (theme.accent==theme.text ? '#070709' : lerpColor(theme.accent,'#000000',.25)) : theme.mode=='light' ? '#0F0F12' : '#070709') : (theme.mode=='custom' ? lerpColor(theme.background,'#000000',.22) : theme.mode=='light' ? '#D4D4CF' : '#0B0B0D')}}"
              fillBottomLeft = "{{= module.enabled ? (theme.mode=='custom' ? (theme.accent==theme.text ? '#18181D' : theme.accent) : theme.mode=='light' ? '#26262B' : '#18181D') : (theme.mode=='custom' ? lerpColor(theme.surface,theme.raised,.35) : theme.mode=='light' ? '#EFEFEB' : '#1E1E23')}}"
              fillBottomRight = "{{= module.enabled ? (theme.mode=='custom' ? (theme.accent==theme.text ? '#18181D' : theme.accent) : theme.mode=='light' ? '#26262B' : '#18181D') : (theme.mode=='custom' ? lerpColor(theme.surface,theme.raised,.35) : theme.mode=='light' ? '#EFEFEB' : '#1E1E23')}}"
              stroke = "{{= module.enabled ? (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24') : vars.d_border}}"; strokeWidth = .55px
              animateValue = true; transitionDuration = 160ms
            }
            rect {
              position = absolute; right = "{{= module.enabled ? 12 : 24}}"; y = 17px; width = 10px; height = 10px; radius = 5px
              fill = "{{= (theme.mode=='light' ? '#00000028' : '#00000058')}}"
              animateLayout = true; transitionDuration = 170ms; easing = "cubic-bezier(0.22,1,0.36,1)"
            }
            rect {
              position = absolute; right = "{{= module.enabled ? 12 : 24}}"; y = 16px; width = 10px; height = 10px; radius = 5px
              fillTopLeft = "{{= module.enabled ? (theme.mode=='custom' ? (theme.accent==theme.text ? theme.text : '#FFFFFF') : theme.mode=='light' ? '#3E3E45' : '#F8F8F5') : (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.28) : theme.mode=='light' ? '#FFFFFF' : '#62626B')}}"
              fillTopRight = "{{= module.enabled ? (theme.mode=='custom' ? (theme.accent==theme.text ? theme.text : '#FFFFFF') : theme.mode=='light' ? '#3E3E45' : '#F8F8F5') : (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.28) : theme.mode=='light' ? '#FFFFFF' : '#62626B')}}"
              fillBottomLeft = "{{= module.enabled ? (theme.mode=='custom' ? (theme.accent==theme.text ? lerpColor(theme.text,theme.surface,.14) : lerpColor('#FFFFFF',theme.accent,.12)) : theme.mode=='light' ? '#28282E' : '#DDDDD8') : (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.14) : theme.mode=='light' ? '#F2F2EF' : '#474750')}}"
              fillBottomRight = "{{= module.enabled ? (theme.mode=='custom' ? (theme.accent==theme.text ? lerpColor(theme.text,theme.surface,.14) : lerpColor('#FFFFFF',theme.accent,.12)) : theme.mode=='light' ? '#28282E' : '#DDDDD8') : (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.14) : theme.mode=='light' ? '#F2F2EF' : '#474750')}}"
              stroke = "{{= (theme.mode=='light' ? '#FFFFFF42' : '#FFFFFF34')}}"; strokeWidth = .45px
              animateLayout = true; animateValue = true; transitionDuration = 170ms; easing = "cubic-bezier(0.22,1,0.36,1)"
            }

            rect { position = absolute; x = 10px; y = 40px; width = "{{= max(0,boxWidth-20)}}"; height = .5px; fill = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.06) : theme.mode=='light' ? '#18181B0D' : '#FFFFFF0C')}}" }

            rect (if = "{{= len(module.cardBindingLabel)>0}}") {
              position = absolute; x = 10px; y = 44px; width = "{{= min(74,max(24,textWidth(module.cardBindingLabel,5.8,'studio-sans-medium')+12))}}"; height = 12px; radius = "{{= min(vars.r_sm,4)}}"
              fill = "{{= vars.d_well}}"; stroke = "{{= vars.d_border}}"; strokeWidth = .5px
            }
            text "{{module.cardBindingLabel}}" (if = "{{= len(module.cardBindingLabel)>0}}") {
              position = absolute; x = 10px; y = 44px; width = "{{= min(74,max(24,textWidth(module.cardBindingLabel,5.8,'studio-sans-medium')+12))}}"; height = 12px
              fontFamily = studio-sans-medium; fontSize = 5.8px; textAlign = center; color = "{{= vars.d_muted}}"
            }

            icon (src = "{{= local.expanded ? 'minus' : 'sliders'}}") {
              position = absolute; right = 10px; y = 46px; width = 8px; height = 8px
              color = "{{= local.expanded ? vars.d_text : vars.d_muted}}"
            }
            text "{{= local.expanded ? i18n.ui.close : i18n.ui.settings}}" {
              position = absolute; right = 21px; y = 44px; width = 56px; height = 12px
              fontSize = 6.1px; textAlign = right; color = "{{= local.expanded ? vars.d_text : vars.d_muted}}"
            }
            rect (if = "local.expanded") { position = absolute; x = 10px; bottom = 0px; width = "{{= max(0,boxWidth-20)}}"; height = .55px; fill = "{{= vars.d_border}}" }
          }

          button (id = "module-toggle.{{module.id}}", action = "toggle:{{module.bind}}", secondary-action = "local-toggle:expanded", middle-action = "bind-module:{{module.id}}") {
            position = absolute; right = 8px; y = 11px; width = 30px; height = 20px; padding = 0px; borderWidth = 0px; background = #00000000; hoverScale = 1; pressScale = 1
            hover { background = #00000000 }
          }
          button (id = "expand.{{module.id}}", action = "local-toggle:expanded", secondary-action = "local-toggle:expanded", middle-action = "bind-module:{{module.id}}", tooltip = "{{= local.expanded ? i18n.ui.collapse_module : i18n.ui.expand_module}}") {
            position = absolute; right = 6px; y = 40px; width = 76px; height = 20px; padding = 0px; borderWidth = 0px; background = #00000000; hoverScale = 1; pressScale = 1
            hover { background = #00000000 }
          }

          column (if = "local.expanded", style = "nc-drawer") {
                button (id = "binding-zoom", if = "module.id=zoom", action = "bind-key:zoom", style = "nc-bind-row") {
                  icon (src = "studio-bind") { width = 9px; height = 9px; color = "{{= vars.d_muted}}" }
                  text "{{i18n.ui.hold_keybind}}" { height = 100%; grow = 1; padding = 0px; fontSize = 7.2px; textOverflow = ellipsis }
                  text "{{zoomBindingLabel}}" (style = "nc-keycap")
                }
                button (id = "binding-freelook", if = "module.id=freelook", action = "bind-key:freelook", style = "nc-bind-row") {
                  icon (src = "studio-bind") { width = 9px; height = 9px; color = "{{= vars.d_muted}}" }
                  text "{{i18n.ui.hold_keybind}}" { height = 100%; grow = 1; padding = 0px; fontSize = 7.2px; textOverflow = ellipsis }
                  text "{{freelookBindingLabel}}" (style = "nc-keycap")
                }
                button (id = "binding-waypoint", if = "module.id=waypoints", action = "bind-key:waypoint", style = "nc-bind-row") {
                  icon (src = "studio-bind") { width = 9px; height = 9px; color = "{{= vars.d_muted}}" }
                  text "{{i18n.ui.quick_waypoint_key}}" { height = 100%; grow = 1; padding = 0px; fontSize = 7.2px; textOverflow = ellipsis }
                  text "{{waypointBindingLabel}}" (style = "nc-keycap")
                }
                button (id = "binding-mapSettings", if = "{{= module.id=='minimap' || module.id=='worldMap'}}", action = "bind-key:mapSettings", style = "nc-bind-row") {
                  icon (src = "studio-bind") { width = 9px; height = 9px; color = "{{= vars.d_muted}}" }
                  text "{{i18n.ui.map_settings_key}}" { height = 100%; grow = 1; padding = 0px; fontSize = 7.2px; textOverflow = ellipsis }
                  text "{{mapSettingsBindingLabel}}" (style = "nc-keycap")
                }
                button (id = "binding-mapEnlarge", if = "module.id=minimap", action = "bind-key:mapEnlarge", style = "nc-bind-row") {
                  icon (src = "studio-bind") { width = 9px; height = 9px; color = "{{= vars.d_muted}}" }
                  text "{{i18n.ui.map_enlarge_key}}" { height = 100%; grow = 1; padding = 0px; fontSize = 7.2px; textOverflow = ellipsis }
                  text "{{mapEnlargeBindingLabel}}" (style = "nc-keycap")
                }
                button (id = "binding-worldMap", if = "module.id=worldMap", action = "bind-key:worldMap", style = "nc-bind-row") {
                  icon (src = "studio-bind") { width = 9px; height = 9px; color = "{{= vars.d_muted}}" }
                  text "{{i18n.ui.world_map_key}}" { height = 100%; grow = 1; padding = 0px; fontSize = 7.2px; textOverflow = ellipsis }
                  text "{{worldMapBindingLabel}}" (style = "nc-keycap")
                }
                button (id = "bind-module.{{module.id}}", action = "bind-module:{{module.id}}", style = "nc-bind-row") {
                  icon (src = "studio-bind") { width = 9px; height = 9px; color = "{{= vars.d_muted}}" }
                  text "{{i18n.ui.module_keybind}}" { height = 100%; grow = 1; padding = 0px; fontSize = 7.2px; textOverflow = ellipsis }
                  text "{{= bindingCaptureModule==module.id ? i18n.ui.press_a_key : module.bindingLabel}}" (style = "nc-keycap")
                }
                button (id = "binding-waypointsMenu", if = "module.id=waypoints", action = "bind-key:waypointsMenu", style = "nc-bind-row") {
                  icon (src = "studio-bind") { width = 9px; height = 9px; color = "{{= vars.d_muted}}" }
                  text "{{i18n.ui.waypoints_menu_key}}" { height = 100%; grow = 1; padding = 0px; fontSize = 7.2px; textOverflow = ellipsis }
                  text "{{waypointsMenuBindingLabel}}" (style = "nc-keycap")
                }
                text "{{i18n.ui.escape_cancels_delete_unbinds}}" (if = "{{= bindingCaptureModule==module.id || (module.id=='zoom' && bindingCapture=='zoom') || (module.id=='freelook' && bindingCapture=='freelook') || (module.id=='waypoints' && (bindingCapture=='waypoint' || bindingCapture=='waypointsMenu')) || (module.id=='minimap' && (bindingCapture=='mapSettings' || bindingCapture=='mapEnlarge')) || (module.id=='worldMap' && (bindingCapture=='worldMap' || bindingCapture=='mapSettings'))}}", style = "nc-caption")
                column (if = "module.id=crosshair") { width = 100%; gap = 7px; align = center
                  row (if = "{{= crosshair.targetShapeEnabled && crosshair.targetShape=='custom'}}", style = "nc-actions") {
                    button "{{i18n.crosshair.layer_normal}}" (id = "crosshair-layer-normal", style = "nc-btn", action = "pixel-layer:normal") {
                      background = "{{= ui.crosshairLayer!='target' ? alphaColor(vars.d_accent,.2) : vars.d_raised}}"
                      color = "{{= ui.crosshairLayer!='target' ? vars.d_text : vars.d_muted}}"
                    }
                    button "{{i18n.crosshair.layer_target}}" (id = "crosshair-layer-target", style = "nc-btn", action = "pixel-layer:target") {
                      background = "{{= ui.crosshairLayer=='target' ? alphaColor(vars.d_accent,.2) : vars.d_raised}}"
                      color = "{{= ui.crosshairLayer=='target' ? vars.d_text : vars.d_muted}}"
                    }
                    button "{{i18n.crosshair.copy_to_target}}" (id = "crosshair-copy-target", style = "nc-btn", action = "pixel-copy-to-target")
                  }
                  row (style = "nc-actions") {
                    button "15" (style = "nc-btn", action = "pixel-size:15")
                    button "21" (style = "nc-btn", action = "pixel-size:21")
                    button "31" (style = "nc-btn", action = "pixel-size:31")
                    button (style = "nc-btn", action = "pixel-clear", tooltip = "{{i18n.ui.delete}}") { icon (src = "trash") { width = 9px; height = 9px } }
                  }
                  pixel-grid (id = "pixel-grid")
                  color "{{i18n.ui.brush_colour}}" (if = "crosshair.colorMode=pixels", bind = "ui.brushColor")
                  row (style = "nc-actions") {
                    button "MC" (style = "nc-btn", action = "pixel-preset:vanilla", tooltip = "{{i18n.ui.minecraft}}")
                    button "•" (style = "nc-btn", action = "pixel-preset:dot")
                    button "+" (style = "nc-btn", action = "pixel-preset:cross")
                    button "×" (style = "nc-btn", action = "pixel-preset:x")
                    button (style = "nc-btn", action = "pixel-preset:ring") { icon (src = "crosshair") { width = 9px; height = 9px } }
                    button (style = "nc-btn", action = "pixel-preset:diamond") { icon (src = "grid") { width = 9px; height = 9px } }
                    button "[ ]" (style = "nc-btn", action = "pixel-preset:brackets")
                  }
                  column (if = "{{= crosshair.targetShapeEnabled || crosshair.targetHighlight}}") { width = 100%; gap = 4px; align = center
                    text "{{i18n.crosshair.target_preview}}" (style = "nc-caption")
                    crosshair (id = "crosshair-target-preview", source = "target-preview") { width = 112px; height = 64px }
                  }
                }
                column (if = "module.id=waypoints") { width = 100%; gap = 7px
                  toggle "{{i18n.waypoint.death_points}}" (bind = "waypoints.deathPoints", visual = "compact")
                  slider "{{i18n.field.badgeSize}}" (bind = "waypoints.badge", min = ".5", max = "2", step = ".05") { height = 32px }
                  toggle "{{i18n.field.waypoints.chatCoordinates}}" (bind = "waypoints.chatCoordinates", visual = "compact")
                  toggle "{{i18n.field.waypoints.hideNearby}}" (bind = "waypoints.hideNearby", visual = "compact")
                  slider "{{i18n.field.waypoints.hideNearbyRadius}}" (if = "waypoints.hideNearby", bind = "waypoints.hideNearbyRadius", min = "1", max = "100", step = "1") { height = 32px }
                  button "{{i18n.waypoint.open_manager}}" (id = "waypoints-open-tab", style = "nc-btn", action = "page:waypoints") { height = 24px; fontSize = 7px }
                }
                column (if = "module.id=tweaksy", style = "nc-controls") {
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.commandFix}}" (bind = "tweaksy.commandFix.enabled", visual = "compact")
                    toggle "{{i18n.field.mainLeftFix}}" (bind = "tweaksy.mainLeftFix.enabled", visual = "compact")
                    toggle "{{i18n.field.cursorFix}}" (bind = "tweaksy.cursorFix.enabled", visual = "compact")
                  }
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.ownNametag}}" (bind = "tweaksy.ownNametag.enabled", visual = "compact")
                    toggle "{{i18n.field.invisibleArmorNametag}}" (bind = "tweaksy.invisibleArmorNametag.enabled", visual = "compact")
                    toggle "{{i18n.field.invisibleArmorHitbox}}" (if = "tweaksy.invisibleArmorNametag.enabled", bind = "tweaksy.invisibleArmorNametag.hitbox", visual = "compact")
                  }
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.fullBright}}" (bind = "tweaksy.fullBright.enabled", visual = "compact")
                    slider "{{i18n.field.fullBrightStrength}}" (if = "tweaksy.fullBright.enabled", bind = "tweaksy.fullBright.strength", min = "0", max = "1", step = ".01")
                  }
                }
                column (if = "module.id=atmosphere", style = "nc-controls") {
                  choice "{{i18n.field.atmosphere.mode}}" (id = "atmosphere.mode", bind = "atmosphere.mode", options = "dawn|dust|snow|sakura|autumn|fireflies|mystic", option-labels = "{{i18n.choice.atmosphere.mode.dawn}}|{{i18n.choice.atmosphere.mode.dust}}|{{i18n.choice.atmosphere.mode.snow}}|{{i18n.choice.atmosphere.mode.sakura}}|{{i18n.choice.atmosphere.mode.autumn}}|{{i18n.choice.atmosphere.mode.fireflies}}|{{i18n.choice.atmosphere.mode.mystic}}", tooltip = "{{i18n.desc.atmosphere.mode}}")
                  slider "{{i18n.field.atmosphere.density}}" (id = "atmosphere.density", if = "atmosphere.mode=dawn", bind = "atmosphere.density", min = "0.05", max = "0.8", step = "0.01", tooltip = "{{i18n.desc.atmosphere.density}}")
                  slider "{{i18n.field.atmosphere.scatterHeight}}" (id = "atmosphere.scatterHeight", if = "atmosphere.mode=dawn", bind = "atmosphere.scatterHeight", min = "60", max = "120", step = "1", tooltip = "{{i18n.desc.atmosphere.scatterHeight}}")
                  slider "{{i18n.field.atmosphere.godRays}}" (id = "atmosphere.godRays", if = "atmosphere.mode=dawn", bind = "atmosphere.godRays", min = "0", max = "100", step = "1", tooltip = "{{i18n.desc.atmosphere.godRays}}")
                  slider "{{i18n.field.atmosphere.softness}}" (id = "atmosphere.softness", if = "atmosphere.mode=dawn", bind = "atmosphere.softness", min = "0", max = "100", step = "1", tooltip = "{{i18n.desc.atmosphere.softness}}")
                  color "{{i18n.field.atmosphere.dawnColor}}" (id = "atmosphere.dawnColor", if = "atmosphere.mode=dawn", bind = "atmosphere.dawnColor", tooltip = "{{i18n.desc.atmosphere.dawnColor}}")
                  slider "{{i18n.field.atmosphere.amount}}" (id = "atmosphere.amount", if = "{{= atmosphere.mode!='dawn'}}", bind = "atmosphere.amount", min = "0", max = "100", step = "1", tooltip = "{{i18n.desc.atmosphere.amount}}")
                  slider "{{i18n.field.atmosphere.ground}}" (id = "atmosphere.ground", if = "{{= atmosphere.mode=='snow' || atmosphere.mode=='sakura' || atmosphere.mode=='autumn' || atmosphere.mode=='mystic'}}", bind = "atmosphere.ground", min = "0", max = "200", step = "5", tooltip = "{{i18n.desc.atmosphere.ground}}")
                  slider "{{i18n.field.atmosphere.speed}}" (id = "atmosphere.speed", if = "{{= atmosphere.mode!='dawn'}}", bind = "atmosphere.speed", min = "10", max = "300", step = "5", tooltip = "{{i18n.desc.atmosphere.speed}}")
                  slider "{{i18n.field.atmosphere.haze}}" (id = "atmosphere.haze", if = "{{= atmosphere.mode!='dawn'}}", bind = "atmosphere.haze", min = "0", max = "100", step = "1", tooltip = "{{i18n.desc.atmosphere.haze}}")
                  slider "{{i18n.field.atmosphere.grade}}" (id = "atmosphere.grade", if = "{{= atmosphere.mode!='dawn'}}", bind = "atmosphere.grade", min = "0", max = "100", step = "1", tooltip = "{{i18n.desc.atmosphere.grade}}")
                  slider "{{i18n.field.atmosphere.vignette}}" (id = "atmosphere.vignette", if = "{{= atmosphere.mode!='dawn'}}", bind = "atmosphere.vignette", min = "0", max = "100", step = "1", tooltip = "{{i18n.desc.atmosphere.vignette}}")
                  choice "{{i18n.field.atmosphere.quality}}" (id = "atmosphere.quality", if = "atmosphere.mode=mystic", bind = "atmosphere.quality", options = "low|medium|high", option-labels = "{{i18n.choice.atmosphere.quality.low}}|{{i18n.choice.atmosphere.quality.medium}}|{{i18n.choice.atmosphere.quality.high}}", tooltip = "{{i18n.desc.atmosphere.quality}}")
                  panel (id = "atmo.snowCover", if = "atmosphere.mode=snow", scope = "atmo.snowCover", selection-state = "expanded", style = "nc-sub") {
                    button (id = "atmo.head.snowCover", action = "local-toggle:expanded", secondary-action = "local-toggle:expanded", tooltip = "{{i18n.group.atmosphere.snowCover.desc}}") { width = 100%; height = 24px; layout = absolute; padding = 0px; borderRadius = "{{= vars.r_ctrl}}"; background = #00000000
                      text "{{i18n.group.atmosphere.snowCover.title}}" { position = absolute; x = 2px; y = 1px; width = "{{= max(0,boxWidth-20)}}"; height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
                      text "{{i18n.group.atmosphere.snowCover.desc}}" { position = absolute; x = 2px; y = 13px; width = "{{= max(0,boxWidth-20)}}"; height = 9px; fontSize = 6.1px; color = "{{= vars.d_muted}}" }
                      icon (src = "{{= local.expanded ? 'arrow-up' : 'chevron-down'}}") { position = absolute; right = 3px; y = 7.5px; width = 8px; height = 8px; color = "{{= vars.d_muted}}" }
                    }
                    column (if = "local.expanded", style = "nc-controls") { padding = "3px 0px 1px 0px"; gap = 8px
                      slider "{{i18n.field.atmosphere.snowCover.frost}}" (id = "atmosphere.snowCover.frost", bind = "atmosphere.snowCover.frost", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.snowCover.frost}}")
                      slider "{{i18n.field.atmosphere.snowCover.shelter}}" (id = "atmosphere.snowCover.shelter", bind = "atmosphere.snowCover.shelter", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.snowCover.shelter}}")
                      slider "{{i18n.field.atmosphere.snowCover.coldShadows}}" (id = "atmosphere.snowCover.coldShadows", bind = "atmosphere.snowCover.coldShadows", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.snowCover.coldShadows}}")
                      slider "{{i18n.field.atmosphere.snowCover.drifts}}" (id = "atmosphere.snowCover.drifts", bind = "atmosphere.snowCover.drifts", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.snowCover.drifts}}")
                      slider "{{i18n.field.atmosphere.snowCover.thickness}}" (id = "atmosphere.snowCover.thickness", bind = "atmosphere.snowCover.thickness", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.snowCover.thickness}}")
                      slider "{{i18n.field.atmosphere.snowCover.texture}}" (id = "atmosphere.snowCover.texture", bind = "atmosphere.snowCover.texture", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.snowCover.texture}}")
                      slider "{{i18n.field.atmosphere.snowCover.sparkle}}" (id = "atmosphere.snowCover.sparkle", bind = "atmosphere.snowCover.sparkle", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.snowCover.sparkle}}")
                      slider "{{i18n.field.atmosphere.snowCover.leaves}}" (id = "atmosphere.snowCover.leaves", bind = "atmosphere.snowCover.leaves", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.snowCover.leaves}}")
                      slider "{{i18n.field.atmosphere.snowCover.ice}}" (id = "atmosphere.snowCover.ice", bind = "atmosphere.snowCover.ice", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.snowCover.ice}}")
                      slider "{{i18n.field.atmosphere.snowCover.cloudShadows}}" (id = "atmosphere.snowCover.cloudShadows", bind = "atmosphere.snowCover.cloudShadows", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.snowCover.cloudShadows}}")
                      toggle "{{i18n.field.atmosphere.snowCover.daytime}}" (id = "atmosphere.snowCover.daytime", bind = "atmosphere.snowCover.daytime", tooltip = "{{i18n.desc.atmosphere.snowCover.daytime}}", visual = "compact")
                    }
                  }
                  panel (id = "atmo.snowfall", if = "atmosphere.mode=snow", scope = "atmo.snowfall", selection-state = "expanded", style = "nc-sub") {
                    button (id = "atmo.head.snowfall", action = "local-toggle:expanded", secondary-action = "local-toggle:expanded", tooltip = "{{i18n.group.atmosphere.snowfall.desc}}") { width = 100%; height = 24px; layout = absolute; padding = 0px; borderRadius = "{{= vars.r_ctrl}}"; background = #00000000
                      text "{{i18n.group.atmosphere.snowfall.title}}" { position = absolute; x = 2px; y = 1px; width = "{{= max(0,boxWidth-20)}}"; height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
                      text "{{i18n.group.atmosphere.snowfall.desc}}" { position = absolute; x = 2px; y = 13px; width = "{{= max(0,boxWidth-20)}}"; height = 9px; fontSize = 6.1px; color = "{{= vars.d_muted}}" }
                      icon (src = "{{= local.expanded ? 'arrow-up' : 'chevron-down'}}") { position = absolute; right = 3px; y = 7.5px; width = 8px; height = 8px; color = "{{= vars.d_muted}}" }
                    }
                    column (if = "local.expanded", style = "nc-controls") { padding = "3px 0px 1px 0px"; gap = 8px
                      slider "{{i18n.field.atmosphere.snowfall.flakeSize}}" (id = "atmosphere.snowfall.flakeSize", bind = "atmosphere.snowfall.flakeSize", min = "50", max = "200", step = "5", tooltip = "{{i18n.desc.atmosphere.snowfall.flakeSize}}")
                      slider "{{i18n.field.atmosphere.snowfall.clumps}}" (id = "atmosphere.snowfall.clumps", bind = "atmosphere.snowfall.clumps", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.snowfall.clumps}}")
                      slider "{{i18n.field.atmosphere.snowfall.wind}}" (id = "atmosphere.snowfall.wind", bind = "atmosphere.snowfall.wind", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.snowfall.wind}}")
                      slider "{{i18n.field.atmosphere.snowfall.spindrift}}" (id = "atmosphere.snowfall.spindrift", bind = "atmosphere.snowfall.spindrift", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.snowfall.spindrift}}")
                      slider "{{i18n.field.atmosphere.snowfall.farSnow}}" (id = "atmosphere.snowfall.farSnow", bind = "atmosphere.snowfall.farSnow", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.snowfall.farSnow}}")
                    }
                  }
                  panel (id = "atmo.sakura", if = "atmosphere.mode=sakura", scope = "atmo.sakura", selection-state = "expanded", style = "nc-sub") {
                    button (id = "atmo.head.sakura", action = "local-toggle:expanded", secondary-action = "local-toggle:expanded", tooltip = "{{i18n.group.atmosphere.sakura.desc}}") { width = 100%; height = 24px; layout = absolute; padding = 0px; borderRadius = "{{= vars.r_ctrl}}"; background = #00000000
                      text "{{i18n.group.atmosphere.sakura.title}}" { position = absolute; x = 2px; y = 1px; width = "{{= max(0,boxWidth-20)}}"; height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
                      text "{{i18n.group.atmosphere.sakura.desc}}" { position = absolute; x = 2px; y = 13px; width = "{{= max(0,boxWidth-20)}}"; height = 9px; fontSize = 6.1px; color = "{{= vars.d_muted}}" }
                      icon (src = "{{= local.expanded ? 'arrow-up' : 'chevron-down'}}") { position = absolute; right = 3px; y = 7.5px; width = 8px; height = 8px; color = "{{= vars.d_muted}}" }
                    }
                    column (if = "local.expanded", style = "nc-controls") { padding = "3px 0px 1px 0px"; gap = 8px
                      slider "{{i18n.field.atmosphere.sakura.blossom}}" (id = "atmosphere.sakura.blossom", bind = "atmosphere.sakura.blossom", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.sakura.blossom}}")
                      color "{{i18n.field.atmosphere.sakura.blossomColor}}" (id = "atmosphere.sakura.blossomColor", bind = "atmosphere.sakura.blossomColor", tooltip = "{{i18n.desc.atmosphere.sakura.blossomColor}}")
                      slider "{{i18n.field.atmosphere.sakura.wholeFlowers}}" (id = "atmosphere.sakura.wholeFlowers", bind = "atmosphere.sakura.wholeFlowers", min = "0", max = "30", step = "1", tooltip = "{{i18n.desc.atmosphere.sakura.wholeFlowers}}")
                      slider "{{i18n.field.atmosphere.sakura.gusts}}" (id = "atmosphere.sakura.gusts", bind = "atmosphere.sakura.gusts", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.sakura.gusts}}")
                      slider "{{i18n.field.atmosphere.sakura.nearTrees}}" (id = "atmosphere.sakura.nearTrees", bind = "atmosphere.sakura.nearTrees", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.sakura.nearTrees}}")
                      slider "{{i18n.field.atmosphere.sakura.hotaru}}" (id = "atmosphere.sakura.hotaru", bind = "atmosphere.sakura.hotaru", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.sakura.hotaru}}")
                    }
                  }
                  panel (id = "atmo.sakuraWater", if = "atmosphere.mode=sakura", scope = "atmo.sakuraWater", selection-state = "expanded", style = "nc-sub") {
                    button (id = "atmo.head.sakuraWater", action = "local-toggle:expanded", secondary-action = "local-toggle:expanded", tooltip = "{{i18n.group.atmosphere.sakuraWater.desc}}") { width = 100%; height = 24px; layout = absolute; padding = 0px; borderRadius = "{{= vars.r_ctrl}}"; background = #00000000
                      text "{{i18n.group.atmosphere.sakuraWater.title}}" { position = absolute; x = 2px; y = 1px; width = "{{= max(0,boxWidth-20)}}"; height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
                      text "{{i18n.group.atmosphere.sakuraWater.desc}}" { position = absolute; x = 2px; y = 13px; width = "{{= max(0,boxWidth-20)}}"; height = 9px; fontSize = 6.1px; color = "{{= vars.d_muted}}" }
                      icon (src = "{{= local.expanded ? 'arrow-up' : 'chevron-down'}}") { position = absolute; right = 3px; y = 7.5px; width = 8px; height = 8px; color = "{{= vars.d_muted}}" }
                    }
                    column (if = "local.expanded", style = "nc-controls") { padding = "3px 0px 1px 0px"; gap = 8px
                      slider "{{i18n.field.atmosphere.sakuraWater.petals}}" (id = "atmosphere.sakuraWater.petals", bind = "atmosphere.sakuraWater.petals", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.sakuraWater.petals}}")
                      slider "{{i18n.field.atmosphere.sakuraWater.moonPath}}" (id = "atmosphere.sakuraWater.moonPath", bind = "atmosphere.sakuraWater.moonPath", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.sakuraWater.moonPath}}")
                      slider "{{i18n.field.atmosphere.sakuraWater.reflections}}" (id = "atmosphere.sakuraWater.reflections", bind = "atmosphere.sakuraWater.reflections", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.sakuraWater.reflections}}")
                      slider "{{i18n.field.atmosphere.sakuraWater.mist}}" (id = "atmosphere.sakuraWater.mist", bind = "atmosphere.sakuraWater.mist", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.sakuraWater.mist}}")
                    }
                  }
                  panel (id = "atmo.sakuraSky", if = "atmosphere.mode=sakura", scope = "atmo.sakuraSky", selection-state = "expanded", style = "nc-sub") {
                    button (id = "atmo.head.sakuraSky", action = "local-toggle:expanded", secondary-action = "local-toggle:expanded", tooltip = "{{i18n.group.atmosphere.sakuraSky.desc}}") { width = 100%; height = 24px; layout = absolute; padding = 0px; borderRadius = "{{= vars.r_ctrl}}"; background = #00000000
                      text "{{i18n.group.atmosphere.sakuraSky.title}}" { position = absolute; x = 2px; y = 1px; width = "{{= max(0,boxWidth-20)}}"; height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
                      text "{{i18n.group.atmosphere.sakuraSky.desc}}" { position = absolute; x = 2px; y = 13px; width = "{{= max(0,boxWidth-20)}}"; height = 9px; fontSize = 6.1px; color = "{{= vars.d_muted}}" }
                      icon (src = "{{= local.expanded ? 'arrow-up' : 'chevron-down'}}") { position = absolute; right = 3px; y = 7.5px; width = 8px; height = 8px; color = "{{= vars.d_muted}}" }
                    }
                    column (if = "local.expanded", style = "nc-controls") { padding = "3px 0px 1px 0px"; gap = 8px
                      toggle "{{i18n.field.atmosphere.sakuraSky.volcano}}" (id = "atmosphere.sakuraSky.volcano", bind = "atmosphere.sakuraSky.volcano", tooltip = "{{i18n.desc.atmosphere.sakuraSky.volcano}}", visual = "compact")
                      toggle "{{i18n.field.atmosphere.sakuraSky.pagoda}}" (id = "atmosphere.sakuraSky.pagoda", bind = "atmosphere.sakuraSky.pagoda", tooltip = "{{i18n.desc.atmosphere.sakuraSky.pagoda}}", visual = "compact")
                      slider "{{i18n.field.atmosphere.sakuraSky.clouds}}" (id = "atmosphere.sakuraSky.clouds", bind = "atmosphere.sakuraSky.clouds", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.sakuraSky.clouds}}")
                      slider "{{i18n.field.atmosphere.sakuraSky.stars}}" (id = "atmosphere.sakuraSky.stars", bind = "atmosphere.sakuraSky.stars", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.sakuraSky.stars}}")
                    }
                  }
                  panel (id = "atmo.autumnTrees", if = "atmosphere.mode=autumn", scope = "atmo.autumnTrees", selection-state = "expanded", style = "nc-sub") {
                    button (id = "atmo.head.autumnTrees", action = "local-toggle:expanded", secondary-action = "local-toggle:expanded", tooltip = "{{i18n.group.atmosphere.autumnTrees.desc}}") { width = 100%; height = 24px; layout = absolute; padding = 0px; borderRadius = "{{= vars.r_ctrl}}"; background = #00000000
                      text "{{i18n.group.atmosphere.autumnTrees.title}}" { position = absolute; x = 2px; y = 1px; width = "{{= max(0,boxWidth-20)}}"; height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
                      text "{{i18n.group.atmosphere.autumnTrees.desc}}" { position = absolute; x = 2px; y = 13px; width = "{{= max(0,boxWidth-20)}}"; height = 9px; fontSize = 6.1px; color = "{{= vars.d_muted}}" }
                      icon (src = "{{= local.expanded ? 'arrow-up' : 'chevron-down'}}") { position = absolute; right = 3px; y = 7.5px; width = 8px; height = 8px; color = "{{= vars.d_muted}}" }
                    }
                    column (if = "local.expanded", style = "nc-controls") { padding = "3px 0px 1px 0px"; gap = 8px
                      slider "{{i18n.field.atmosphere.autumnTrees.treeColors}}" (id = "atmosphere.autumnTrees.treeColors", bind = "atmosphere.autumnTrees.treeColors", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.autumnTrees.treeColors}}")
                      toggle "{{i18n.field.atmosphere.autumnTrees.evergreen}}" (id = "atmosphere.autumnTrees.evergreen", bind = "atmosphere.autumnTrees.evergreen", tooltip = "{{i18n.desc.atmosphere.autumnTrees.evergreen}}", visual = "compact")
                      slider "{{i18n.field.atmosphere.autumnTrees.bareTrees}}" (id = "atmosphere.autumnTrees.bareTrees", bind = "atmosphere.autumnTrees.bareTrees", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.autumnTrees.bareTrees}}")
                    }
                  }
                  panel (id = "atmo.autumnGround", if = "atmosphere.mode=autumn", scope = "atmo.autumnGround", selection-state = "expanded", style = "nc-sub") {
                    button (id = "atmo.head.autumnGround", action = "local-toggle:expanded", secondary-action = "local-toggle:expanded", tooltip = "{{i18n.group.atmosphere.autumnGround.desc}}") { width = 100%; height = 24px; layout = absolute; padding = 0px; borderRadius = "{{= vars.r_ctrl}}"; background = #00000000
                      text "{{i18n.group.atmosphere.autumnGround.title}}" { position = absolute; x = 2px; y = 1px; width = "{{= max(0,boxWidth-20)}}"; height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
                      text "{{i18n.group.atmosphere.autumnGround.desc}}" { position = absolute; x = 2px; y = 13px; width = "{{= max(0,boxWidth-20)}}"; height = 9px; fontSize = 6.1px; color = "{{= vars.d_muted}}" }
                      icon (src = "{{= local.expanded ? 'arrow-up' : 'chevron-down'}}") { position = absolute; right = 3px; y = 7.5px; width = 8px; height = 8px; color = "{{= vars.d_muted}}" }
                    }
                    column (if = "local.expanded", style = "nc-controls") { padding = "3px 0px 1px 0px"; gap = 8px
                      slider "{{i18n.field.atmosphere.autumnGround.piles}}" (id = "atmosphere.autumnGround.piles", bind = "atmosphere.autumnGround.piles", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.autumnGround.piles}}")
                      slider "{{i18n.field.atmosphere.autumnGround.dryGrass}}" (id = "atmosphere.autumnGround.dryGrass", bind = "atmosphere.autumnGround.dryGrass", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.autumnGround.dryGrass}}")
                      slider "{{i18n.field.atmosphere.autumnGround.puddles}}" (id = "atmosphere.autumnGround.puddles", bind = "atmosphere.autumnGround.puddles", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.autumnGround.puddles}}")
                      slider "{{i18n.field.atmosphere.autumnGround.dew}}" (id = "atmosphere.autumnGround.dew", bind = "atmosphere.autumnGround.dew", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.autumnGround.dew}}")
                    }
                  }
                  panel (id = "atmo.autumnAir", if = "atmosphere.mode=autumn", scope = "atmo.autumnAir", selection-state = "expanded", style = "nc-sub") {
                    button (id = "atmo.head.autumnAir", action = "local-toggle:expanded", secondary-action = "local-toggle:expanded", tooltip = "{{i18n.group.atmosphere.autumnAir.desc}}") { width = 100%; height = 24px; layout = absolute; padding = 0px; borderRadius = "{{= vars.r_ctrl}}"; background = #00000000
                      text "{{i18n.group.atmosphere.autumnAir.title}}" { position = absolute; x = 2px; y = 1px; width = "{{= max(0,boxWidth-20)}}"; height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
                      text "{{i18n.group.atmosphere.autumnAir.desc}}" { position = absolute; x = 2px; y = 13px; width = "{{= max(0,boxWidth-20)}}"; height = 9px; fontSize = 6.1px; color = "{{= vars.d_muted}}" }
                      icon (src = "{{= local.expanded ? 'arrow-up' : 'chevron-down'}}") { position = absolute; right = 3px; y = 7.5px; width = 8px; height = 8px; color = "{{= vars.d_muted}}" }
                    }
                    column (if = "local.expanded", style = "nc-controls") { padding = "3px 0px 1px 0px"; gap = 8px
                      toggle "{{i18n.field.atmosphere.autumnAir.leafShapes}}" (id = "atmosphere.autumnAir.leafShapes", bind = "atmosphere.autumnAir.leafShapes", tooltip = "{{i18n.desc.atmosphere.autumnAir.leafShapes}}", visual = "compact")
                      slider "{{i18n.field.atmosphere.autumnAir.leafGlow}}" (id = "atmosphere.autumnAir.leafGlow", bind = "atmosphere.autumnAir.leafGlow", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.autumnAir.leafGlow}}")
                      slider "{{i18n.field.atmosphere.autumnAir.whirls}}" (id = "atmosphere.autumnAir.whirls", bind = "atmosphere.autumnAir.whirls", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.autumnAir.whirls}}")
                      slider "{{i18n.field.atmosphere.autumnAir.skitter}}" (id = "atmosphere.autumnAir.skitter", bind = "atmosphere.autumnAir.skitter", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.autumnAir.skitter}}")
                      slider "{{i18n.field.atmosphere.autumnAir.gossamer}}" (id = "atmosphere.autumnAir.gossamer", bind = "atmosphere.autumnAir.gossamer", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.autumnAir.gossamer}}")
                    }
                  }
                  panel (id = "atmo.autumnSky", if = "atmosphere.mode=autumn", scope = "atmo.autumnSky", selection-state = "expanded", style = "nc-sub") {
                    button (id = "atmo.head.autumnSky", action = "local-toggle:expanded", secondary-action = "local-toggle:expanded", tooltip = "{{i18n.group.atmosphere.autumnSky.desc}}") { width = 100%; height = 24px; layout = absolute; padding = 0px; borderRadius = "{{= vars.r_ctrl}}"; background = #00000000
                      text "{{i18n.group.atmosphere.autumnSky.title}}" { position = absolute; x = 2px; y = 1px; width = "{{= max(0,boxWidth-20)}}"; height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
                      text "{{i18n.group.atmosphere.autumnSky.desc}}" { position = absolute; x = 2px; y = 13px; width = "{{= max(0,boxWidth-20)}}"; height = 9px; fontSize = 6.1px; color = "{{= vars.d_muted}}" }
                      icon (src = "{{= local.expanded ? 'arrow-up' : 'chevron-down'}}") { position = absolute; right = 3px; y = 7.5px; width = 8px; height = 8px; color = "{{= vars.d_muted}}" }
                    }
                    column (if = "local.expanded", style = "nc-controls") { padding = "3px 0px 1px 0px"; gap = 8px
                      slider "{{i18n.field.atmosphere.autumnSky.clouds}}" (id = "atmosphere.autumnSky.clouds", bind = "atmosphere.autumnSky.clouds", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.autumnSky.clouds}}")
                      slider "{{i18n.field.atmosphere.autumnSky.birds}}" (id = "atmosphere.autumnSky.birds", bind = "atmosphere.autumnSky.birds", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.autumnSky.birds}}")
                      toggle "{{i18n.field.atmosphere.autumnSky.farForest}}" (id = "atmosphere.autumnSky.farForest", bind = "atmosphere.autumnSky.farForest", tooltip = "{{i18n.desc.atmosphere.autumnSky.farForest}}", visual = "compact")
                      slider "{{i18n.field.atmosphere.autumnSky.mist}}" (id = "atmosphere.autumnSky.mist", bind = "atmosphere.autumnSky.mist", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.autumnSky.mist}}")
                      slider "{{i18n.field.atmosphere.autumnSky.sunHeight}}" (id = "atmosphere.autumnSky.sunHeight", bind = "atmosphere.autumnSky.sunHeight", min = "5", max = "35", step = "1", tooltip = "{{i18n.desc.atmosphere.autumnSky.sunHeight}}")
                      slider "{{i18n.field.atmosphere.autumnSky.sunRays}}" (id = "atmosphere.autumnSky.sunRays", bind = "atmosphere.autumnSky.sunRays", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.autumnSky.sunRays}}")
                    }
                  }
                  panel (id = "atmo.mysticSky", if = "atmosphere.mode=mystic", scope = "atmo.mysticSky", selection-state = "expanded", style = "nc-sub") {
                    button (id = "atmo.head.mysticSky", action = "local-toggle:expanded", secondary-action = "local-toggle:expanded", tooltip = "{{i18n.group.atmosphere.mysticSky.desc}}") { width = 100%; height = 24px; layout = absolute; padding = 0px; borderRadius = "{{= vars.r_ctrl}}"; background = #00000000
                      text "{{i18n.group.atmosphere.mysticSky.title}}" { position = absolute; x = 2px; y = 1px; width = "{{= max(0,boxWidth-20)}}"; height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
                      text "{{i18n.group.atmosphere.mysticSky.desc}}" { position = absolute; x = 2px; y = 13px; width = "{{= max(0,boxWidth-20)}}"; height = 9px; fontSize = 6.1px; color = "{{= vars.d_muted}}" }
                      icon (src = "{{= local.expanded ? 'arrow-up' : 'chevron-down'}}") { position = absolute; right = 3px; y = 7.5px; width = 8px; height = 8px; color = "{{= vars.d_muted}}" }
                    }
                    column (if = "local.expanded", style = "nc-controls") { padding = "3px 0px 1px 0px"; gap = 8px
                      panel (id = "atmo.pair.mysticSky.moon", scope = "atmo.pair.mysticSky.moon") { width = 100%; layout = column; gap = 5px; padding = 0px
                        panel { width = 100%; height = 22px; layout = absolute
                          toggle "{{i18n.field.atmosphere.mysticSky.moon}}" (id = "atmosphere.mysticSky.moon", bind = "atmosphere.mysticSky.moon", tooltip = "{{i18n.desc.atmosphere.mysticSky.moon}}", visual = "compact") { position = absolute; x = 0px; y = 0px; width = "{{= max(0,boxWidth-34)}}"; height = 22px }
                          button (id = "atmosphere.mysticSky.moonColor.btn", action = "local-toggle:expanded", tooltip = "{{i18n.desc.atmosphere.mysticSky.moon}}") { position = absolute; right = 2px; y = 2.5px; width = 28px; height = 17px; padding = 0px; borderRadius = 7px; borderWidth = .5px; borderColor = "{{= vars.d_border}}"; background = "{{= vars.d_well}}"
                            color-swatch (id = "atmosphere.mysticSky.moonColor.preview", bind = "atmosphere.mysticSky.moonColor") { position = absolute; x = 2px; y = 2px; width = 24px; height = 13px; borderRadius = 5px }
                          }
                        }
                        column (if = "local.expanded") { width = 100%; padding = "6px 7px"; gap = 5px; background = "{{= vars.d_well}}"; borderRadius = 8px; borderWidth = .5px; borderColor = "{{= vars.d_border}}"
                          color-plane (id = "atmosphere.mysticSky.moonColor.plane", bind = "atmosphere.mysticSky.moonColor") { width = 100%; height = 56px; borderRadius = 6px; hoverScale = 1; pressScale = 1 }
                          hue-strip (id = "atmosphere.mysticSky.moonColor.hue", bind = "atmosphere.mysticSky.moonColor") { width = 100%; height = 7px; borderRadius = 3.5px; hoverScale = 1; pressScale = 1 }
                          input (id = "atmosphere.mysticSky.moonColor.hex", bind = "atmosphere.mysticSky.moonColor", format = "hex", maxlength = "9") { width = 100%; height = 20px; fontSize = 6.7px; borderRadius = 7px; background = "{{= vars.d_raised}}"; color = "{{= vars.d_text}}" }
                        }
                      }
                      slider "{{i18n.field.atmosphere.mysticSky.moonSize}}" (id = "atmosphere.mysticSky.moonSize", if = "atmosphere.mysticSky.moon", bind = "atmosphere.mysticSky.moonSize", min = "40", max = "250", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticSky.moonSize}}")
                      slider "{{i18n.field.atmosphere.mysticSky.moonHeight}}" (id = "atmosphere.mysticSky.moonHeight", if = "atmosphere.mysticSky.moon", bind = "atmosphere.mysticSky.moonHeight", min = "6", max = "60", step = "1", tooltip = "{{i18n.desc.atmosphere.mysticSky.moonHeight}}")
                      slider "{{i18n.field.atmosphere.mysticSky.halo}}" (id = "atmosphere.mysticSky.halo", if = "atmosphere.mysticSky.moon", bind = "atmosphere.mysticSky.halo", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticSky.halo}}")
                      slider "{{i18n.field.atmosphere.mysticSky.moonRays}}" (id = "atmosphere.mysticSky.moonRays", if = "atmosphere.mysticSky.moon", bind = "atmosphere.mysticSky.moonRays", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticSky.moonRays}}")
                      slider "{{i18n.field.atmosphere.mysticSky.stars}}" (id = "atmosphere.mysticSky.stars", bind = "atmosphere.mysticSky.stars", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticSky.stars}}")
                      slider "{{i18n.field.atmosphere.mysticSky.meteors}}" (id = "atmosphere.mysticSky.meteors", bind = "atmosphere.mysticSky.meteors", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticSky.meteors}}")
                      slider "{{i18n.field.atmosphere.mysticSky.clouds}}" (id = "atmosphere.mysticSky.clouds", bind = "atmosphere.mysticSky.clouds", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticSky.clouds}}")
                      slider "{{i18n.field.atmosphere.mysticSky.cloudShadows}}" (id = "atmosphere.mysticSky.cloudShadows", if = "{{= atmosphere.mysticSky.moon && atmosphere.mysticSky.clouds>0}}", bind = "atmosphere.mysticSky.cloudShadows", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticSky.cloudShadows}}")
                      slider "{{i18n.field.atmosphere.mysticSky.lightning}}" (id = "atmosphere.mysticSky.lightning", bind = "atmosphere.mysticSky.lightning", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticSky.lightning}}")
                      slider "{{i18n.field.atmosphere.mysticSky.silhouettes}}" (id = "atmosphere.mysticSky.silhouettes", bind = "atmosphere.mysticSky.silhouettes", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticSky.silhouettes}}")
                    }
                  }
                  panel (id = "atmo.mysticFog", if = "atmosphere.mode=mystic", scope = "atmo.mysticFog", selection-state = "expanded", style = "nc-sub") {
                    button (id = "atmo.head.mysticFog", action = "local-toggle:expanded", secondary-action = "local-toggle:expanded", tooltip = "{{i18n.group.atmosphere.mysticFog.desc}}") { width = 100%; height = 24px; layout = absolute; padding = 0px; borderRadius = "{{= vars.r_ctrl}}"; background = #00000000
                      text "{{i18n.group.atmosphere.mysticFog.title}}" { position = absolute; x = 2px; y = 1px; width = "{{= max(0,boxWidth-20)}}"; height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
                      text "{{i18n.group.atmosphere.mysticFog.desc}}" { position = absolute; x = 2px; y = 13px; width = "{{= max(0,boxWidth-20)}}"; height = 9px; fontSize = 6.1px; color = "{{= vars.d_muted}}" }
                      icon (src = "{{= local.expanded ? 'arrow-up' : 'chevron-down'}}") { position = absolute; right = 3px; y = 7.5px; width = 8px; height = 8px; color = "{{= vars.d_muted}}" }
                    }
                    column (if = "local.expanded", style = "nc-controls") { padding = "3px 0px 1px 0px"; gap = 8px
                      slider "{{i18n.field.atmosphere.mysticFog.groundFog}}" (id = "atmosphere.mysticFog.groundFog", bind = "atmosphere.mysticFog.groundFog", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticFog.groundFog}}")
                      slider "{{i18n.field.atmosphere.mysticFog.fogHeight}}" (id = "atmosphere.mysticFog.fogHeight", if = "{{= atmosphere.mysticFog.groundFog>0}}", bind = "atmosphere.mysticFog.fogHeight", min = "0.5", max = "6", step = "0.5", tooltip = "{{i18n.desc.atmosphere.mysticFog.fogHeight}}")
                      slider "{{i18n.field.atmosphere.mysticFog.fogTendrils}}" (id = "atmosphere.mysticFog.fogTendrils", if = "{{= atmosphere.mysticFog.groundFog>0}}", bind = "atmosphere.mysticFog.fogTendrils", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticFog.fogTendrils}}")
                      color "{{i18n.field.atmosphere.mysticFog.fogColor}}" (id = "atmosphere.mysticFog.fogColor", bind = "atmosphere.mysticFog.fogColor", tooltip = "{{i18n.desc.atmosphere.mysticFog.fogColor}}")
                    }
                  }
                  panel (id = "atmo.mysticWorld", if = "atmosphere.mode=mystic", scope = "atmo.mysticWorld", selection-state = "expanded", style = "nc-sub") {
                    button (id = "atmo.head.mysticWorld", action = "local-toggle:expanded", secondary-action = "local-toggle:expanded", tooltip = "{{i18n.group.atmosphere.mysticWorld.desc}}") { width = 100%; height = 24px; layout = absolute; padding = 0px; borderRadius = "{{= vars.r_ctrl}}"; background = #00000000
                      text "{{i18n.group.atmosphere.mysticWorld.title}}" { position = absolute; x = 2px; y = 1px; width = "{{= max(0,boxWidth-20)}}"; height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
                      text "{{i18n.group.atmosphere.mysticWorld.desc}}" { position = absolute; x = 2px; y = 13px; width = "{{= max(0,boxWidth-20)}}"; height = 9px; fontSize = 6.1px; color = "{{= vars.d_muted}}" }
                      icon (src = "{{= local.expanded ? 'arrow-up' : 'chevron-down'}}") { position = absolute; right = 3px; y = 7.5px; width = 8px; height = 8px; color = "{{= vars.d_muted}}" }
                    }
                    column (if = "local.expanded", style = "nc-controls") { padding = "3px 0px 1px 0px"; gap = 8px
                      toggle "{{i18n.field.atmosphere.mysticWorld.alwaysNight}}" (id = "atmosphere.mysticWorld.alwaysNight", bind = "atmosphere.mysticWorld.alwaysNight", tooltip = "{{i18n.desc.atmosphere.mysticWorld.alwaysNight}}", visual = "compact")
                      slider "{{i18n.field.atmosphere.mysticWorld.moonlight}}" (id = "atmosphere.mysticWorld.moonlight", bind = "atmosphere.mysticWorld.moonlight", min = "0", max = "200", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticWorld.moonlight}}")
                      slider "{{i18n.field.atmosphere.mysticWorld.coldShadows}}" (id = "atmosphere.mysticWorld.coldShadows", bind = "atmosphere.mysticWorld.coldShadows", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticWorld.coldShadows}}")
                      slider "{{i18n.field.atmosphere.mysticWorld.blueNight}}" (id = "atmosphere.mysticWorld.blueNight", bind = "atmosphere.mysticWorld.blueNight", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticWorld.blueNight}}")
                      slider "{{i18n.field.atmosphere.mysticWorld.wither}}" (id = "atmosphere.mysticWorld.wither", bind = "atmosphere.mysticWorld.wither", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticWorld.wither}}")
                      slider "{{i18n.field.atmosphere.mysticWorld.wetness}}" (id = "atmosphere.mysticWorld.wetness", bind = "atmosphere.mysticWorld.wetness", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticWorld.wetness}}")
                      slider "{{i18n.field.atmosphere.mysticWorld.puddles}}" (id = "atmosphere.mysticWorld.puddles", if = "{{= atmosphere.mysticWorld.wetness>0}}", bind = "atmosphere.mysticWorld.puddles", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticWorld.puddles}}")
                      slider "{{i18n.field.atmosphere.mysticWorld.reflections}}" (id = "atmosphere.mysticWorld.reflections", bind = "atmosphere.mysticWorld.reflections", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticWorld.reflections}}")
                    }
                  }
                  panel (id = "atmo.mysticLens", if = "atmosphere.mode=mystic", scope = "atmo.mysticLens", selection-state = "expanded", style = "nc-sub") {
                    button (id = "atmo.head.mysticLens", action = "local-toggle:expanded", secondary-action = "local-toggle:expanded", tooltip = "{{i18n.group.atmosphere.mysticLens.desc}}") { width = 100%; height = 24px; layout = absolute; padding = 0px; borderRadius = "{{= vars.r_ctrl}}"; background = #00000000
                      text "{{i18n.group.atmosphere.mysticLens.title}}" { position = absolute; x = 2px; y = 1px; width = "{{= max(0,boxWidth-20)}}"; height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
                      text "{{i18n.group.atmosphere.mysticLens.desc}}" { position = absolute; x = 2px; y = 13px; width = "{{= max(0,boxWidth-20)}}"; height = 9px; fontSize = 6.1px; color = "{{= vars.d_muted}}" }
                      icon (src = "{{= local.expanded ? 'arrow-up' : 'chevron-down'}}") { position = absolute; right = 3px; y = 7.5px; width = 8px; height = 8px; color = "{{= vars.d_muted}}" }
                    }
                    column (if = "local.expanded", style = "nc-controls") { padding = "3px 0px 1px 0px"; gap = 8px
                      slider "{{i18n.field.atmosphere.mysticLens.bokeh}}" (id = "atmosphere.mysticLens.bokeh", bind = "atmosphere.mysticLens.bokeh", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticLens.bokeh}}")
                      slider "{{i18n.field.atmosphere.mysticLens.grain}}" (id = "atmosphere.mysticLens.grain", bind = "atmosphere.mysticLens.grain", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticLens.grain}}")
                    }
                  }
                  panel (id = "atmo.mysticLife", if = "atmosphere.mode=mystic", scope = "atmo.mysticLife", selection-state = "expanded", style = "nc-sub") {
                    button (id = "atmo.head.mysticLife", action = "local-toggle:expanded", secondary-action = "local-toggle:expanded", tooltip = "{{i18n.group.atmosphere.mysticLife.desc}}") { width = 100%; height = 24px; layout = absolute; padding = 0px; borderRadius = "{{= vars.r_ctrl}}"; background = #00000000
                      text "{{i18n.group.atmosphere.mysticLife.title}}" { position = absolute; x = 2px; y = 1px; width = "{{= max(0,boxWidth-20)}}"; height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
                      text "{{i18n.group.atmosphere.mysticLife.desc}}" { position = absolute; x = 2px; y = 13px; width = "{{= max(0,boxWidth-20)}}"; height = 9px; fontSize = 6.1px; color = "{{= vars.d_muted}}" }
                      icon (src = "{{= local.expanded ? 'arrow-up' : 'chevron-down'}}") { position = absolute; right = 3px; y = 7.5px; width = 8px; height = 8px; color = "{{= vars.d_muted}}" }
                    }
                    column (if = "local.expanded", style = "nc-controls") { padding = "3px 0px 1px 0px"; gap = 8px
                      slider "{{i18n.field.atmosphere.mysticLife.bats}}" (id = "atmosphere.mysticLife.bats", bind = "atmosphere.mysticLife.bats", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticLife.bats}}")
                      slider "{{i18n.field.atmosphere.mysticLife.batColony}}" (id = "atmosphere.mysticLife.batColony", bind = "atmosphere.mysticLife.batColony", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticLife.batColony}}")
                      panel (id = "atmo.pair.mysticLife.wisps", scope = "atmo.pair.mysticLife.wisps") { width = 100%; layout = column; gap = 5px; padding = 0px
                        panel { width = 100%; height = 22px; layout = absolute
                          toggle "{{i18n.field.atmosphere.mysticLife.wisps}}" (id = "atmosphere.mysticLife.wisps", bind = "atmosphere.mysticLife.wisps", tooltip = "{{i18n.desc.atmosphere.mysticLife.wisps}}", visual = "compact") { position = absolute; x = 0px; y = 0px; width = "{{= max(0,boxWidth-34)}}"; height = 22px }
                          button (id = "atmosphere.mysticLife.wispsColor.btn", action = "local-toggle:expanded", tooltip = "{{i18n.desc.atmosphere.mysticLife.wisps}}") { position = absolute; right = 2px; y = 2.5px; width = 28px; height = 17px; padding = 0px; borderRadius = 7px; borderWidth = .5px; borderColor = "{{= vars.d_border}}"; background = "{{= vars.d_well}}"
                            color-swatch (id = "atmosphere.mysticLife.wispsColor.preview", bind = "atmosphere.mysticLife.wispsColor") { position = absolute; x = 2px; y = 2px; width = 24px; height = 13px; borderRadius = 5px }
                          }
                        }
                        column (if = "local.expanded") { width = 100%; padding = "6px 7px"; gap = 5px; background = "{{= vars.d_well}}"; borderRadius = 8px; borderWidth = .5px; borderColor = "{{= vars.d_border}}"
                          color-plane (id = "atmosphere.mysticLife.wispsColor.plane", bind = "atmosphere.mysticLife.wispsColor") { width = 100%; height = 56px; borderRadius = 6px; hoverScale = 1; pressScale = 1 }
                          hue-strip (id = "atmosphere.mysticLife.wispsColor.hue", bind = "atmosphere.mysticLife.wispsColor") { width = 100%; height = 7px; borderRadius = 3.5px; hoverScale = 1; pressScale = 1 }
                          input (id = "atmosphere.mysticLife.wispsColor.hex", bind = "atmosphere.mysticLife.wispsColor", format = "hex", maxlength = "9") { width = 100%; height = 20px; fontSize = 6.7px; borderRadius = 7px; background = "{{= vars.d_raised}}"; color = "{{= vars.d_text}}" }
                        }
                      }
                      slider "{{i18n.field.atmosphere.mysticLife.wispCount}}" (id = "atmosphere.mysticLife.wispCount", if = "atmosphere.mysticLife.wisps", bind = "atmosphere.mysticLife.wispCount", min = "1", max = "4", step = "1", tooltip = "{{i18n.desc.atmosphere.mysticLife.wispCount}}")
                      slider "{{i18n.field.atmosphere.mysticLife.webs}}" (id = "atmosphere.mysticLife.webs", bind = "atmosphere.mysticLife.webs", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticLife.webs}}")
                      slider "{{i18n.field.atmosphere.mysticLife.ghosts}}" (id = "atmosphere.mysticLife.ghosts", bind = "atmosphere.mysticLife.ghosts", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticLife.ghosts}}")
                    }
                  }
                  panel (id = "atmo.mysticWind", if = "atmosphere.mode=mystic", scope = "atmo.mysticWind", selection-state = "expanded", style = "nc-sub") {
                    button (id = "atmo.head.mysticWind", action = "local-toggle:expanded", secondary-action = "local-toggle:expanded", tooltip = "{{i18n.group.atmosphere.mysticWind.desc}}") { width = 100%; height = 24px; layout = absolute; padding = 0px; borderRadius = "{{= vars.r_ctrl}}"; background = #00000000
                      text "{{i18n.group.atmosphere.mysticWind.title}}" { position = absolute; x = 2px; y = 1px; width = "{{= max(0,boxWidth-20)}}"; height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
                      text "{{i18n.group.atmosphere.mysticWind.desc}}" { position = absolute; x = 2px; y = 13px; width = "{{= max(0,boxWidth-20)}}"; height = 9px; fontSize = 6.1px; color = "{{= vars.d_muted}}" }
                      icon (src = "{{= local.expanded ? 'arrow-up' : 'chevron-down'}}") { position = absolute; right = 3px; y = 7.5px; width = 8px; height = 8px; color = "{{= vars.d_muted}}" }
                    }
                    column (if = "local.expanded", style = "nc-controls") { padding = "3px 0px 1px 0px"; gap = 8px
                      slider "{{i18n.field.atmosphere.mysticWind.gusts}}" (id = "atmosphere.mysticWind.gusts", bind = "atmosphere.mysticWind.gusts", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticWind.gusts}}")
                      slider "{{i18n.field.atmosphere.mysticWind.skitter}}" (id = "atmosphere.mysticWind.skitter", bind = "atmosphere.mysticWind.skitter", min = "0", max = "100", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticWind.skitter}}")
                      slider "{{i18n.field.atmosphere.mysticWind.leafSize}}" (id = "atmosphere.mysticWind.leafSize", bind = "atmosphere.mysticWind.leafSize", min = "50", max = "200", step = "5", tooltip = "{{i18n.desc.atmosphere.mysticWind.leafSize}}")
                    }
                  }
                }
                column (if = "module.id=optimization", style = "nc-controls") {
                  panel (style = "nc-sub") {
                    choice "{{i18n.field.optimization.profile}}" (id = "optimization.profile", bind = "optimization.profile", options = "lossless|performance|competitive|custom", option-labels = "{{i18n.choice.optimization.profile.lossless}}|{{i18n.choice.optimization.profile.performance}}|{{i18n.choice.optimization.profile.competitive}}|{{i18n.choice.optimization.profile.custom}}", tooltip = "{{i18n.desc.optimization.profile}}")
                    toggle "{{i18n.field.optimization.autoCompatibility}}" (id = "optimization.autoCompatibility", bind = "optimization.autoCompatibility", tooltip = "{{i18n.desc.optimization.autoCompatibility}}", visual = "compact")
                  }
                  panel (id = "opt.world", scope = "opt.world", selection-state = "expanded", style = "nc-sub") {
                    button (id = "opt.head.world", action = "local-toggle:expanded", secondary-action = "local-toggle:expanded", tooltip = "{{i18n.group.optimization.world.desc}}") { width = 100%; height = 24px; layout = absolute; padding = 0px; borderRadius = "{{= vars.r_ctrl}}"; background = #00000000
                      text "{{i18n.group.optimization.world.title}}" { position = absolute; x = 2px; y = 1px; width = "{{= max(0,boxWidth-20)}}"; height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
                      text "{{i18n.group.optimization.world.desc}}" { position = absolute; x = 2px; y = 13px; width = "{{= max(0,boxWidth-20)}}"; height = 9px; fontSize = 6.1px; color = "{{= vars.d_muted}}" }
                      icon (src = "{{= local.expanded ? 'arrow-up' : 'chevron-down'}}") { position = absolute; right = 3px; y = 7.5px; width = 8px; height = 8px; color = "{{= vars.d_muted}}" }
                    }
                    column (if = "local.expanded", style = "nc-controls") { padding = "3px 0px 1px 0px"; gap = 8px
                      toggle "{{i18n.field.optimization.undergroundCaveCulling}}" (id = "optimization.undergroundCaveCulling", bind = "optimization.undergroundCaveCulling", tooltip = "{{i18n.desc.optimization.undergroundCaveCulling}}", visual = "compact")
                      toggle "{{i18n.field.optimization.retainedTerrainCache}}" (id = "optimization.retainedTerrainCache", bind = "optimization.retainedTerrainCache", tooltip = "{{i18n.desc.optimization.retainedTerrainCache}}", visual = "compact")
                      toggle "{{i18n.field.optimization.leafCulling}}" (id = "optimization.leafCulling", bind = "optimization.leafCulling", tooltip = "{{i18n.desc.optimization.leafCulling}}", visual = "compact")
                      toggle "{{i18n.field.optimization.fastModelRendering}}" (id = "optimization.fastModelRendering", bind = "optimization.fastModelRendering", tooltip = "{{i18n.desc.optimization.fastModelRendering}}", visual = "compact")
                      toggle "{{i18n.field.optimization.nativeGpuInstancing}}" (id = "optimization.nativeGpuInstancing", bind = "optimization.nativeGpuInstancing", tooltip = "{{i18n.desc.optimization.nativeGpuInstancing}}", visual = "compact")
                      toggle "{{i18n.field.optimization.nativePboUploads}}" (id = "optimization.nativePboUploads", bind = "optimization.nativePboUploads", tooltip = "{{i18n.desc.optimization.nativePboUploads}}", visual = "compact")
                      toggle "{{i18n.field.optimization.nativePacketCodec}}" (id = "optimization.nativePacketCodec", bind = "optimization.nativePacketCodec", tooltip = "{{i18n.desc.optimization.nativePacketCodec}}", visual = "compact")
                    }
                  }
                  panel (id = "opt.entities", scope = "opt.entities", selection-state = "expanded", style = "nc-sub") {
                    button (id = "opt.head.entities", action = "local-toggle:expanded", secondary-action = "local-toggle:expanded", tooltip = "{{i18n.group.optimization.entities.desc}}") { width = 100%; height = 24px; layout = absolute; padding = 0px; borderRadius = "{{= vars.r_ctrl}}"; background = #00000000
                      text "{{i18n.group.optimization.entities.title}}" { position = absolute; x = 2px; y = 1px; width = "{{= max(0,boxWidth-20)}}"; height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
                      text "{{i18n.group.optimization.entities.desc}}" { position = absolute; x = 2px; y = 13px; width = "{{= max(0,boxWidth-20)}}"; height = 9px; fontSize = 6.1px; color = "{{= vars.d_muted}}" }
                      icon (src = "{{= local.expanded ? 'arrow-up' : 'chevron-down'}}") { position = absolute; right = 3px; y = 7.5px; width = 8px; height = 8px; color = "{{= vars.d_muted}}" }
                    }
                    column (if = "local.expanded", style = "nc-controls") { padding = "3px 0px 1px 0px"; gap = 8px
                      toggle "{{i18n.field.optimization.occlusionCulling}}" (id = "optimization.occlusionCulling", bind = "optimization.occlusionCulling", tooltip = "{{i18n.desc.optimization.occlusionCulling}}", visual = "compact")
                      toggle "{{i18n.field.optimization.staticBlockEntityBatching}}" (id = "optimization.staticBlockEntityBatching", bind = "optimization.staticBlockEntityBatching", tooltip = "{{i18n.desc.optimization.staticBlockEntityBatching}}", visual = "compact")
                      toggle "{{i18n.field.optimization.staticEntityBatching}}" (id = "optimization.staticEntityBatching", bind = "optimization.staticEntityBatching", tooltip = "{{i18n.desc.optimization.staticEntityBatching}}", visual = "compact")
                      toggle "{{i18n.field.optimization.hologramTextCache}}" (id = "optimization.hologramTextCache", bind = "optimization.hologramTextCache", tooltip = "{{i18n.desc.optimization.hologramTextCache}}", visual = "compact")
                      toggle "{{i18n.field.optimization.emptyBlockEntitySkip}}" (id = "optimization.emptyBlockEntitySkip", bind = "optimization.emptyBlockEntitySkip", tooltip = "{{i18n.desc.optimization.emptyBlockEntitySkip}}", visual = "compact")
                      toggle "{{i18n.field.optimization.signTextCulling}}" (id = "optimization.signTextCulling", bind = "optimization.signTextCulling", tooltip = "{{i18n.desc.optimization.signTextCulling}}", visual = "compact")
                      slider "{{i18n.field.optimization.signTextDistance}}" (id = "optimization.signTextDistance", if = "optimization.signTextCulling", bind = "optimization.signTextDistance", min = "8", max = "96", step = "4", tooltip = "{{i18n.desc.optimization.signTextDistance}}")
                      toggle "{{i18n.field.optimization.itemClustering}}" (id = "optimization.itemClustering", bind = "optimization.itemClustering", tooltip = "{{i18n.desc.optimization.itemClustering}}", visual = "compact")
                      slider "{{i18n.field.optimization.itemClusterLimit}}" (id = "optimization.itemClusterLimit", if = "optimization.itemClustering", bind = "optimization.itemClusterLimit", min = "1", max = "16", step = "1", tooltip = "{{i18n.desc.optimization.itemClusterLimit}}")
                      toggle "{{i18n.field.nametagCulling}}" (id = "optimization.nametagCulling", bind = "optimization.nametagCulling", tooltip = "{{i18n.desc.optimization.nametagCulling}}", visual = "compact")
                      slider "{{i18n.field.nametagDistance}}" (id = "optimization.nametagDistance", if = "optimization.nametagCulling", bind = "optimization.nametagDistance", min = "8", max = "64", step = "2", tooltip = "{{i18n.desc.optimization.nametagDistance}}")
                      toggle "{{i18n.field.entityCulling}}" (id = "optimization.entityCulling", bind = "optimization.entityCulling", tooltip = "{{i18n.desc.optimization.entityCulling}}", visual = "compact")
                      slider "{{i18n.field.entityDistance}}" (id = "optimization.entityDistance", if = "optimization.entityCulling", bind = "optimization.entityDistance", min = "16", max = "128", step = "4", tooltip = "{{i18n.desc.optimization.entityDistance}}")
                      toggle "{{i18n.field.blockEntityCulling}}" (id = "optimization.blockEntityCulling", bind = "optimization.blockEntityCulling", tooltip = "{{i18n.desc.optimization.blockEntityCulling}}", visual = "compact")
                      slider "{{i18n.field.blockEntityDistance}}" (id = "optimization.blockEntityDistance", if = "optimization.blockEntityCulling", bind = "optimization.blockEntityDistance", min = "16", max = "128", step = "4", tooltip = "{{i18n.desc.optimization.blockEntityDistance}}")
                    }
                  }
                  panel (id = "opt.particlesHud", scope = "opt.particlesHud", selection-state = "expanded", style = "nc-sub") {
                    button (id = "opt.head.particlesHud", action = "local-toggle:expanded", secondary-action = "local-toggle:expanded", tooltip = "{{i18n.group.optimization.particlesHud.desc}}") { width = 100%; height = 24px; layout = absolute; padding = 0px; borderRadius = "{{= vars.r_ctrl}}"; background = #00000000
                      text "{{i18n.group.optimization.particlesHud.title}}" { position = absolute; x = 2px; y = 1px; width = "{{= max(0,boxWidth-20)}}"; height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
                      text "{{i18n.group.optimization.particlesHud.desc}}" { position = absolute; x = 2px; y = 13px; width = "{{= max(0,boxWidth-20)}}"; height = 9px; fontSize = 6.1px; color = "{{= vars.d_muted}}" }
                      icon (src = "{{= local.expanded ? 'arrow-up' : 'chevron-down'}}") { position = absolute; right = 3px; y = 7.5px; width = 8px; height = 8px; color = "{{= vars.d_muted}}" }
                    }
                    column (if = "local.expanded", style = "nc-controls") { padding = "3px 0px 1px 0px"; gap = 8px
                      toggle "{{i18n.field.particleCulling}}" (id = "optimization.particleCulling", bind = "optimization.particleCulling", tooltip = "{{i18n.desc.optimization.particleCulling}}", visual = "compact")
                      slider "{{i18n.field.particleDistance}}" (id = "optimization.particleDistance", if = "optimization.particleCulling", bind = "optimization.particleDistance", min = "8", max = "128", step = "4", tooltip = "{{i18n.desc.optimization.particleDistance}}")
                      slider "{{i18n.field.particleLimit}}" (id = "optimization.particleLimit", if = "optimization.particleCulling", bind = "optimization.particleLimit", min = "100", max = "4000", step = "100", tooltip = "{{i18n.desc.optimization.particleLimit}}")
                      toggle "{{i18n.field.optimization.fastParticlePhysics}}" (id = "optimization.fastParticlePhysics", bind = "optimization.fastParticlePhysics", tooltip = "{{i18n.desc.optimization.fastParticlePhysics}}", visual = "compact")
                      toggle "{{i18n.field.hudCache}}" (id = "optimization.hudCache", bind = "optimization.hudCache", tooltip = "{{i18n.desc.optimization.hudCache}}", visual = "compact")
                      toggle "{{i18n.field.optimization.vanillaHudCache}}" (id = "optimization.vanillaHudCache", bind = "optimization.vanillaHudCache", tooltip = "{{i18n.desc.optimization.vanillaHudCache}}", visual = "compact")
                      toggle "{{i18n.field.optimization.scoreboardCache}}" (id = "optimization.scoreboardCache", bind = "optimization.scoreboardCache", tooltip = "{{i18n.desc.optimization.scoreboardCache}}", visual = "compact")
                    }
                  }
                }
                column (if = "module.id=noRender", style = "nc-controls") {
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.noRender.fire}}" (id = "noRender.fire", bind = "noRender.fire", visual = "compact")
                    toggle "{{i18n.field.noRender.hideFire}}" (id = "noRender.hideFire", if = "noRender.fire", bind = "noRender.hideFire", visual = "compact")
                    slider "{{i18n.field.noRender.fireOffset}}" (id = "noRender.fireOffset", if = "{{= noRender.fire && !noRender.hideFire}}", bind = "noRender.fireOffset", min = "0", max = "1.5", step = ".01")
                  }
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.noRender.hurtCam}}" (id = "noRender.hurtCam", bind = "noRender.hurtCam", visual = "compact")
                    toggle "{{i18n.field.noRender.totem}}" (id = "noRender.totem", bind = "noRender.totem", visual = "compact")
                    toggle "{{i18n.field.noRender.nausea}}" (id = "noRender.nausea", bind = "noRender.nausea", visual = "compact")
                  }
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.noRender.pumpkin}}" (id = "noRender.pumpkin", bind = "noRender.pumpkin", visual = "compact")
                    toggle "{{i18n.field.noRender.powderSnow}}" (id = "noRender.powderSnow", bind = "noRender.powderSnow", visual = "compact")
                    toggle "{{i18n.field.noRender.blockOverlay}}" (id = "noRender.blockOverlay", bind = "noRender.blockOverlay", visual = "compact")
                    toggle "{{i18n.field.noRender.portal}}" (id = "noRender.portal", bind = "noRender.portal", visual = "compact")
                    toggle "{{i18n.field.noRender.vignette}}" (id = "noRender.vignette", bind = "noRender.vignette", visual = "compact")
                    toggle "{{i18n.field.noRender.bossBar}}" (id = "noRender.bossBar", bind = "noRender.bossBar", visual = "compact")
                  }
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.noRender.waterOverlay}}" (id = "noRender.waterOverlay", bind = "noRender.waterOverlay", visual = "compact")
                    toggle "{{i18n.field.noRender.waterFog}}" (id = "noRender.waterFog", bind = "noRender.waterFog", visual = "compact")
                    toggle "{{i18n.field.noRender.lavaFog}}" (id = "noRender.lavaFog", bind = "noRender.lavaFog", visual = "compact")
                    toggle "{{i18n.field.noRender.weather}}" (id = "noRender.weather", bind = "noRender.weather", visual = "compact")
                  }
                }
                column (if = "module.id=customSky", style = "nc-controls") {
                  panel (style = "nc-sub") {
                    choice "{{i18n.field.customSky.style}}" (id = "customSky.style", bind = "customSky.style", options = "vanilla|galaxy|aurora|sunset|anime|blood_moon|pastel", option-labels = "{{i18n.choice.customSky.style.vanilla}}|{{i18n.choice.customSky.style.galaxy}}|{{i18n.choice.customSky.style.aurora}}|{{i18n.choice.customSky.style.sunset}}|{{i18n.choice.customSky.style.anime}}|{{i18n.choice.customSky.style.blood_moon}}|{{i18n.choice.customSky.style.pastel}}")
                    choice "{{i18n.field.customSky.timeMode}}" (id = "customSky.timeMode", bind = "customSky.timeMode", options = "server|day|sunset|night|midnight|custom", option-labels = "{{i18n.choice.customSky.timeMode.server}}|{{i18n.choice.customSky.timeMode.day}}|{{i18n.choice.customSky.timeMode.sunset}}|{{i18n.choice.customSky.timeMode.night}}|{{i18n.choice.customSky.timeMode.midnight}}|{{i18n.choice.customSky.timeMode.custom}}")
                    slider "{{i18n.field.customSky.customTime}}" (id = "customSky.customTime", if = "customSky.timeMode=custom", bind = "customSky.customTime", min = "0", max = "24000", step = "250")
                  }
                  panel (if = "{{= customSky.style!='vanilla'}}", style = "nc-sub") {
                    toggle "{{i18n.field.customSky.useCustomColors}}" (id = "customSky.useCustomColors", bind = "customSky.useCustomColors", visual = "compact")
                    color "{{i18n.field.customSky.topColor}}" (id = "customSky.topColor", if = "customSky.useCustomColors", bind = "customSky.topColor")
                    color "{{i18n.field.customSky.horizonColor}}" (id = "customSky.horizonColor", if = "customSky.useCustomColors", bind = "customSky.horizonColor")
                    color "{{i18n.field.customSky.accentColor}}" (id = "customSky.accentColor", if = "customSky.useCustomColors", bind = "customSky.accentColor")
                    slider "{{i18n.field.customSky.brightness}}" (id = "customSky.brightness", bind = "customSky.brightness", min = "30", max = "160", step = "5")
                    slider "{{i18n.field.customSky.horizonGlow}}" (id = "customSky.horizonGlow", bind = "customSky.horizonGlow", min = "0", max = "100", step = "5")
                  }
                  panel (if = "{{= customSky.style!='vanilla'}}", style = "nc-sub") {
                    slider "{{i18n.field.customSky.stars}}" (id = "customSky.stars", bind = "customSky.stars", min = "0", max = "100", step = "5")
                    slider "{{i18n.field.customSky.clouds}}" (id = "customSky.clouds", bind = "customSky.clouds", min = "0", max = "100", step = "5")
                    slider "{{i18n.field.customSky.rotationSpeed}}" (id = "customSky.rotationSpeed", bind = "customSky.rotationSpeed", min = "0", max = "200", step = "5")
                  }
                  panel (if = "{{= customSky.style!='vanilla'}}", style = "nc-sub") {
                    toggle "{{i18n.field.customSky.celestial}}" (id = "customSky.celestial", bind = "customSky.celestial", visual = "compact")
                    slider "{{i18n.field.customSky.celestialSize}}" (id = "customSky.celestialSize", if = "customSky.celestial", bind = "customSky.celestialSize", min = "40", max = "220", step = "5")
                    slider "{{i18n.field.customSky.celestialHeight}}" (id = "customSky.celestialHeight", if = "customSky.celestial", bind = "customSky.celestialHeight", min = "5", max = "75", step = "1")
                    toggle "{{i18n.field.customSky.endAndNether}}" (id = "customSky.endAndNether", bind = "customSky.endAndNether", visual = "compact")
                  }
                }
                column (if = "module.id=safeCtLeave", style = "nc-controls") {
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.safeCtLeave.blockDisconnect}}" (id = "safeCtLeave.blockDisconnect", bind = "safeCtLeave.blockDisconnect", visual = "compact")
                    toggle "{{i18n.field.safeCtLeave.blockWindowClose}}" (id = "safeCtLeave.blockWindowClose", bind = "safeCtLeave.blockWindowClose", visual = "compact")
                    toggle "{{i18n.field.safeCtLeave.blockCommands}}" (id = "safeCtLeave.blockCommands", bind = "safeCtLeave.blockCommands", visual = "compact")
                    toggle "{{i18n.field.safeCtLeave.shiftBypass}}" (id = "safeCtLeave.shiftBypass", bind = "safeCtLeave.shiftBypass", visual = "compact")
                  }
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.safeCtLeave.showBanner}}" (id = "safeCtLeave.showBanner", bind = "safeCtLeave.showBanner", visual = "compact")
                    toggle "{{i18n.field.safeCtLeave.detectPvpHits}}" (id = "safeCtLeave.detectPvpHits", bind = "safeCtLeave.detectPvpHits", visual = "compact")
                    slider "{{i18n.field.safeCtLeave.fallbackSeconds}}" (id = "safeCtLeave.fallbackSeconds", if = "safeCtLeave.detectPvpHits", bind = "safeCtLeave.fallbackSeconds", min = "5", max = "30", step = "1")
                    toggle "{{i18n.field.safeCtLeave.dangerGuard}}" (id = "safeCtLeave.dangerGuard", bind = "safeCtLeave.dangerGuard", visual = "compact")
                    slider "{{i18n.field.safeCtLeave.dangerRadius}}" (id = "safeCtLeave.dangerRadius", if = "safeCtLeave.dangerGuard", bind = "safeCtLeave.dangerRadius", min = "3", max = "16", step = ".5")
                  }
                }
                column (if = "module.id=betterChat", style = "nc-controls") {
                  panel (style = "nc-sub") {
                    choice "{{i18n.field.betterChat.backgroundStyle}}" (id = "betterChat.backgroundStyle", bind = "betterChat.backgroundStyle", options = "rounded|compact|transparent|vanilla", option-labels = "{{i18n.choice.betterChat.bg.rounded}}|{{i18n.choice.betterChat.bg.compact}}|{{i18n.choice.betterChat.bg.transparent}}|{{i18n.choice.betterChat.bg.vanilla}}")
                    slider "{{i18n.field.betterChat.backgroundOpacity}}" (id = "betterChat.backgroundOpacity", if = "{{= betterChat.backgroundStyle!='transparent'}}", bind = "betterChat.backgroundOpacity", min = "0", max = "1", step = ".05")
                    toggle "{{i18n.field.betterChat.blur}}" (id = "betterChat.blur", if = "{{= betterChat.backgroundStyle=='rounded' || betterChat.backgroundStyle=='compact'}}", bind = "betterChat.blur", visual = "compact")
                  }
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.betterChat.animation}}" (id = "betterChat.animation", bind = "betterChat.animation", visual = "compact")
                    slider "{{i18n.field.betterChat.animationSpeed}}" (id = "betterChat.animationSpeed", if = "betterChat.animation", bind = "betterChat.animationSpeed", min = ".5", max = "2.5", step = ".1")
                    toggle "{{i18n.field.betterChat.stackDuplicates}}" (id = "betterChat.stackDuplicates", bind = "betterChat.stackDuplicates", visual = "compact")
                    toggle "{{i18n.field.betterChat.timestamps}}" (id = "betterChat.timestamps", bind = "betterChat.timestamps", visual = "compact")
                    toggle "{{i18n.field.betterChat.timestampSeconds}}" (id = "betterChat.timestampSeconds", if = "betterChat.timestamps", bind = "betterChat.timestampSeconds", visual = "compact")
                  }
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.betterChat.keepHistory}}" (id = "betterChat.keepHistory", bind = "betterChat.keepHistory", visual = "compact")
                    slider "{{i18n.field.betterChat.historyLimit}}" (id = "betterChat.historyLimit", bind = "betterChat.historyLimit", min = "100", max = "2000", step = "100")
                    toggle "{{i18n.field.betterChat.copyOnClick}}" (id = "betterChat.copyOnClick", bind = "betterChat.copyOnClick", visual = "compact")
                    toggle "{{i18n.field.betterChat.keepDraft}}" (id = "betterChat.keepDraft", bind = "betterChat.keepDraft", visual = "compact")
                    toggle "{{i18n.field.betterChat.hideSignatureBar}}" (id = "betterChat.hideSignatureBar", bind = "betterChat.hideSignatureBar", visual = "compact")
                  }
                }
                column (if = "module.id=blockOverlay", style = "nc-controls") {
                  panel (style = "nc-sub") {
                    choice "{{i18n.field.blockOverlay.mode}}" (id = "blockOverlay.mode", bind = "blockOverlay.mode", options = "both|outline|fill|corners", option-labels = "{{i18n.choice.blockOverlay.both}}|{{i18n.choice.blockOverlay.outline}}|{{i18n.choice.blockOverlay.fill}}|{{i18n.choice.blockOverlay.corners}}")
                    toggle "{{i18n.field.blockOverlay.useThemeAccent}}" (id = "blockOverlay.useThemeAccent", bind = "blockOverlay.useThemeAccent", visual = "compact")
                    color "{{i18n.field.color}}" (id = "blockOverlay.color", if = "!blockOverlay.useThemeAccent", bind = "blockOverlay.color")
                  }
                  panel (style = "nc-sub") {
                    slider "{{i18n.field.blockOverlay.outlineOpacity}}" (id = "blockOverlay.outlineOpacity", if = "{{= blockOverlay.mode!='fill'}}", bind = "blockOverlay.outlineOpacity", min = ".05", max = "1", step = ".05")
                    slider "{{i18n.field.lineWidth}}" (id = "blockOverlay.lineWidth", if = "{{= blockOverlay.mode!='fill'}}", bind = "blockOverlay.lineWidth", min = ".5", max = "5", step = ".1")
                    slider "{{i18n.field.blockOverlay.fillOpacity}}" (id = "blockOverlay.fillOpacity", if = "{{= blockOverlay.mode!='outline'}}", bind = "blockOverlay.fillOpacity", min = "0", max = ".6", step = ".02")
                  }
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.blockOverlay.smooth}}" (id = "blockOverlay.smooth", bind = "blockOverlay.smooth", visual = "compact")
                    slider "{{i18n.field.blockOverlay.smoothSpeed}}" (id = "blockOverlay.smoothSpeed", if = "blockOverlay.smooth", bind = "blockOverlay.smoothSpeed", min = "2", max = "35", step = "1")
                    toggle "{{i18n.field.blockOverlay.exactShape}}" (id = "blockOverlay.exactShape", bind = "blockOverlay.exactShape", visual = "compact")
                    toggle "{{i18n.field.blockOverlay.pulse}}" (id = "blockOverlay.pulse", bind = "blockOverlay.pulse", visual = "compact")
                  }
                }
                column (if = "module.id=enchantGlint", style = "nc-controls") {
                  panel (style = "nc-sub") {
                    choice "{{i18n.field.enchantGlint.colorMode}}" (id = "enchantGlint.colorMode", bind = "enchantGlint.colorMode", options = "custom|theme|preset|chroma", option-labels = "{{i18n.choice.enchantGlint.custom}}|{{i18n.choice.enchantGlint.theme}}|{{i18n.choice.enchantGlint.modePreset}}|{{i18n.choice.enchantGlint.chroma}}")
                    choice "{{i18n.field.preset}}" (id = "enchantGlint.preset", if = "enchantGlint.colorMode=preset", bind = "enchantGlint.preset", options = "amethyst|crimson|cyan|gold|emerald|sakura|white", option-labels = "{{i18n.choice.enchantGlint.preset.amethyst}}|{{i18n.choice.enchantGlint.preset.crimson}}|{{i18n.choice.enchantGlint.preset.cyan}}|{{i18n.choice.enchantGlint.preset.gold}}|{{i18n.choice.enchantGlint.preset.emerald}}|{{i18n.choice.enchantGlint.preset.sakura}}|{{i18n.choice.enchantGlint.preset.white}}")
                    color "{{i18n.field.color}}" (id = "enchantGlint.color", if = "enchantGlint.colorMode=custom", bind = "enchantGlint.color")
                  }
                  panel (style = "nc-sub") {
                    slider "{{i18n.field.enchantGlint.intensity}}" (id = "enchantGlint.intensity", bind = "enchantGlint.intensity", min = "0", max = "1.5", step = ".05")
                    slider "{{i18n.field.enchantGlint.speed}}" (id = "enchantGlint.speed", bind = "enchantGlint.speed", min = ".1", max = "3", step = ".1")
                  }
                }
                column (if = "module.id=wavyCapes", style = "nc-controls") {
                  panel (style = "nc-sub") {
                    choice "{{i18n.field.preset}}" (id = "wavyCapes.preset", bind = "wavyCapes.preset", options = "silk|classic|royal|ethereal|voxel|custom", option-labels = "{{i18n.choice.wavyCapes.preset.silk}}|{{i18n.choice.wavyCapes.preset.classic}}|{{i18n.choice.wavyCapes.preset.royal}}|{{i18n.choice.wavyCapes.preset.ethereal}}|{{i18n.choice.wavyCapes.preset.voxel}}|{{i18n.choice.wavyCapes.preset.custom}}")
                    choice "{{i18n.field.wavyCapes.style}}" (id = "wavyCapes.style", bind = "wavyCapes.style", options = "smooth|crisp|blocky|slim", option-labels = "{{i18n.choice.wavyCapes.style.smooth}}|{{i18n.choice.wavyCapes.style.crisp}}|{{i18n.choice.wavyCapes.style.blocky}}|{{i18n.choice.wavyCapes.style.slim}}")
                    choice "{{i18n.field.wavyCapes.quality}}" (id = "wavyCapes.quality", bind = "wavyCapes.quality", options = "low|medium|high", option-labels = "{{i18n.choice.wavyCapes.quality.low}}|{{i18n.choice.wavyCapes.quality.medium}}|{{i18n.choice.wavyCapes.quality.high}}")
                    choice "{{i18n.field.wavyCapes.players}}" (id = "wavyCapes.players", bind = "wavyCapes.players", options = "all|self", option-labels = "{{i18n.choice.wavyCapes.players.all}}|{{i18n.choice.wavyCapes.players.self}}")
                  }
                  panel (style = "nc-sub") {
                    slider "{{i18n.field.wavyCapes.gravity}}" (id = "wavyCapes.gravity", bind = "wavyCapes.gravity", min = ".2", max = "2.5", step = ".05")
                    slider "{{i18n.field.wavyCapes.stiffness}}" (id = "wavyCapes.stiffness", bind = "wavyCapes.stiffness", min = "0", max = "1", step = ".05")
                    slider "{{i18n.field.wavyCapes.damping}}" (id = "wavyCapes.damping", bind = "wavyCapes.damping", min = "0", max = "1", step = ".05")
                    slider "{{i18n.field.wavyCapes.drag}}" (id = "wavyCapes.drag", bind = "wavyCapes.drag", min = ".2", max = "2.5", step = ".05")
                    slider "{{i18n.field.wavyCapes.turnSwing}}" (id = "wavyCapes.turnSwing", bind = "wavyCapes.turnSwing", min = "0", max = "2.5", step = ".05")
                  }
                  panel (style = "nc-sub") {
                    slider "{{i18n.field.wavyCapes.wind}}" (id = "wavyCapes.wind", bind = "wavyCapes.wind", min = "0", max = "2.5", step = ".05")
                    slider "{{i18n.field.wavyCapes.flutter}}" (id = "wavyCapes.flutter", bind = "wavyCapes.flutter", min = "0", max = "2", step = ".05")
                    toggle "{{i18n.field.wavyCapes.weatherWind}}" (id = "wavyCapes.weatherWind", bind = "wavyCapes.weatherWind", visual = "compact")
                    toggle "{{i18n.field.wavyCapes.waterPhysics}}" (id = "wavyCapes.waterPhysics", bind = "wavyCapes.waterPhysics", visual = "compact")
                  }
                  panel (style = "nc-sub") {
                    slider "{{i18n.field.wavyCapes.thickness}}" (id = "wavyCapes.thickness", bind = "wavyCapes.thickness", min = ".2", max = "1.8", step = ".05")
                    slider "{{i18n.field.wavyCapes.widthScale}}" (id = "wavyCapes.widthScale", bind = "wavyCapes.widthScale", min = ".8", max = "1.35", step = ".05")
                    slider "{{i18n.field.wavyCapes.lengthScale}}" (id = "wavyCapes.lengthScale", bind = "wavyCapes.lengthScale", min = ".7", max = "1.35", step = ".05")
                    slider "{{i18n.field.wavyCapes.hemFlare}}" (id = "wavyCapes.hemFlare", bind = "wavyCapes.hemFlare", min = "0", max = "1", step = ".05")
                    slider "{{i18n.field.wavyCapes.foldShading}}" (id = "wavyCapes.foldShading", bind = "wavyCapes.foldShading", min = "0", max = "1", step = ".05")
                    toggle "{{i18n.field.wavyCapes.bodyCollision}}" (id = "wavyCapes.bodyCollision", bind = "wavyCapes.bodyCollision", visual = "compact")
                    slider "{{i18n.field.range}}" (id = "wavyCapes.maxDistance", if = "wavyCapes.players=all", bind = "wavyCapes.maxDistance", min = "16", max = "128", step = "8")
                  }
                }
                column (if = "module.id=explosionRing", style = "nc-controls") {
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.explosionRing.showTnt}}" (id = "explosionRing.showTnt", bind = "explosionRing.showTnt", visual = "compact")
                    toggle "{{i18n.field.explosionRing.showCrystals}}" (id = "explosionRing.showCrystals", bind = "explosionRing.showCrystals", visual = "compact")
                    toggle "{{i18n.field.explosionRing.showAnchors}}" (id = "explosionRing.showAnchors", bind = "explosionRing.showAnchors", visual = "compact")
                  }
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.explosionRing.showFill}}" (id = "explosionRing.showFill", bind = "explosionRing.showFill", visual = "compact")
                    slider "{{i18n.field.explosionRing.fillOpacity}}" (id = "explosionRing.fillOpacity", if = "explosionRing.showFill", bind = "explosionRing.fillOpacity", min = "0", max = ".45", step = ".02")
                    toggle "{{i18n.field.explosionRing.showFuseRing}}" (id = "explosionRing.showFuseRing", bind = "explosionRing.showFuseRing", visual = "compact")
                    toggle "{{i18n.field.explosionRing.showLethalRing}}" (id = "explosionRing.showLethalRing", bind = "explosionRing.showLethalRing", visual = "compact")
                    toggle "{{i18n.field.explosionRing.showDamageBadge}}" (id = "explosionRing.showDamageBadge", bind = "explosionRing.showDamageBadge", visual = "compact")
                  }
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.explosionRing.autoLearnRadius}}" (id = "explosionRing.autoLearnRadius", bind = "explosionRing.autoLearnRadius", visual = "compact")
                    slider "{{i18n.field.explosionRing.customTntRadius}}" (id = "explosionRing.customTntRadius", bind = "explosionRing.customTntRadius", min = "0", max = "24", step = ".5")
                    slider "{{i18n.field.explosionRing.damageMultiplier}}" (id = "explosionRing.damageMultiplier", bind = "explosionRing.damageMultiplier", min = ".5", max = "4", step = ".1")
                    slider "{{i18n.field.lineWidth}}" (id = "explosionRing.lineWidth", bind = "explosionRing.lineWidth", min = ".8", max = "5", step = ".2")
                    slider "{{i18n.field.range}}" (id = "explosionRing.maxDistance", bind = "explosionRing.maxDistance", min = "12", max = "96", step = "4")
                  }
                }
                column (if = "module.id=tooltips", style = "nc-controls") {
                  panel (style = "nc-sub") {
                    choice "{{i18n.field.tooltips.style}}" (id = "tooltips.style", bind = "tooltips.style", options = "hw|glass|vanilla", option-labels = "{{i18n.choice.tooltips.style.hw}}|{{i18n.choice.tooltips.style.glass}}|{{i18n.choice.tooltips.style.vanilla}}")
                    toggle "{{i18n.field.tooltips.showDurability}}" (id = "tooltips.showDurability", bind = "tooltips.showDurability", visual = "compact")
                    toggle "{{i18n.field.tooltips.showItemHeader}}" (id = "tooltips.showItemHeader", if = "tooltips.style=hw", bind = "tooltips.showItemHeader", visual = "compact")
                  }
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.tooltips.shulkerPeek}}" (id = "tooltips.shulkerPeek", bind = "tooltips.shulkerPeek", visual = "compact")
                    choice "{{i18n.field.tooltips.peekMode}}" (id = "tooltips.peekMode", if = "tooltips.shulkerPeek", bind = "tooltips.peekMode", options = "compact|full", option-labels = "{{i18n.choice.tooltips.peekMode.compact}}|{{i18n.choice.tooltips.peekMode.full}}")
                    choice "{{i18n.field.tooltips.peekTrigger}}" (id = "tooltips.peekTrigger", if = "tooltips.shulkerPeek", bind = "tooltips.peekTrigger", options = "always|shift", option-labels = "{{i18n.choice.tooltips.peekTrigger.always}}|{{i18n.choice.tooltips.peekTrigger.shift}}")
                    toggle "{{i18n.field.tooltips.colorByShulker}}" (id = "tooltips.colorByShulker", if = "tooltips.shulkerPeek", bind = "tooltips.colorByShulker", visual = "compact")
                    toggle "{{i18n.field.tooltips.hideVanillaList}}" (id = "tooltips.hideVanillaList", if = "tooltips.shulkerPeek", bind = "tooltips.hideVanillaList", visual = "compact")
                    text "{{i18n.tooltips.ctrl_peek_hint}}" (if = "tooltips.shulkerPeek", style = "nc-caption") { height = 16px; textOverflow = wrap }
                  }
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.tooltips.hwTooltips}}" (id = "tooltips.hwTooltips", bind = "tooltips.hwTooltips", visual = "compact")
                    toggle "{{i18n.field.tooltips.hwAuctionPrice}}" (id = "tooltips.hwAuctionPrice", if = "tooltips.hwTooltips", bind = "tooltips.hwAuctionPrice", visual = "compact")
                    toggle "{{i18n.field.tooltips.hwRarityBorder}}" (id = "tooltips.hwRarityBorder", if = "tooltips.hwTooltips", bind = "tooltips.hwRarityBorder", visual = "compact")
                  }
                }
                column (if = "module.id=minimap", style = "nc-controls") {
                  row (style = "nc-actions") {
                    button "{{i18n.worldMap.open_settings_button}}" (id = "minimap-settings-btn", style = "nc-btn", action = "map-settings-open") { grow = 1; height = 22px; fontSize = 6.8px }
                    button "{{i18n.worldMap.open_waypoints_button}}" (id = "minimap-waypoints-btn", style = "nc-btn", action = "waypoints-menu-open") { grow = 1; height = 22px; fontSize = 6.8px }
                  }
                  panel (style = "nc-sub") {
                    choice "{{i18n.field.minimap.shape}}" (id = "minimap.shape", bind = "minimap.shape", options = "square|sharp|circle|diamond|hexagon|octagon", option-labels = "{{i18n.choice.minimap.shape.square}}|{{i18n.choice.minimap.shape.sharp}}|{{i18n.choice.minimap.shape.circle}}|{{i18n.choice.minimap.shape.diamond}}|{{i18n.choice.minimap.shape.hexagon}}|{{i18n.choice.minimap.shape.octagon}}")
                    slider "{{i18n.field.minimap.size}}" (id = "minimap.size", bind = "minimap.size", min = "72", max = "240", step = "4")
                    slider "{{i18n.field.minimap.zoom}}" (id = "minimap.zoom", bind = "minimap.zoom", min = ".5", max = "4", step = ".1")
                    slider "{{i18n.field.minimap.frameOpacity}}" (id = "minimap.frameOpacity", bind = "minimap.frameOpacity", min = "0", max = "1", step = ".05")
                    toggle "{{i18n.field.minimap.lockNorth}}" (id = "minimap.lockNorth", bind = "minimap.lockNorth", visual = "compact")
                    choice "{{i18n.field.minimap.enlargeMode}}" (id = "minimap.enlargeMode", bind = "minimap.enlargeMode", options = "hold|toggle", option-labels = "{{i18n.choice.minimap.enlargeMode.hold}}|{{i18n.choice.minimap.enlargeMode.toggle}}")
                    slider "{{i18n.field.minimap.enlargeSize}}" (id = "minimap.enlargeSize", bind = "minimap.enlargeSize", min = "160", max = "420", step = "8")
                    slider "{{i18n.field.minimap.enlargeZoom}}" (id = "minimap.enlargeZoom", bind = "minimap.enlargeZoom", min = ".35", max = "1.5", step = ".05")
                  }
                  panel (style = "nc-sub") {
                    choice "{{i18n.field.minimap.arrowStyle}}" (id = "minimap.arrowStyle", bind = "minimap.arrowStyle", options = "classic|modern|pointer|head|dot", option-labels = "{{i18n.choice.minimap.arrowStyle.classic}}|{{i18n.choice.minimap.arrowStyle.modern}}|{{i18n.choice.minimap.arrowStyle.pointer}}|{{i18n.choice.minimap.arrowStyle.head}}|{{i18n.choice.minimap.arrowStyle.dot}}")
                    slider "{{i18n.field.minimap.arrowScale}}" (id = "minimap.arrowScale", bind = "minimap.arrowScale", min = ".6", max = "1.8", step = ".05")
                    color "{{i18n.field.minimap.arrowColor}}" (id = "minimap.arrowColor", bind = "minimap.arrowColor")
                  }
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.minimap.terrainShading}}" (id = "minimap.terrainShading", bind = "minimap.terrainShading", visual = "compact")
                    choice "{{i18n.field.minimap.reliefStyle}}" (id = "minimap.reliefStyle", if = "minimap.terrainShading", bind = "minimap.reliefStyle", options = "classic|vivid|contour|flat", option-labels = "{{i18n.choice.minimap.reliefStyle.classic}}|{{i18n.choice.minimap.reliefStyle.vivid}}|{{i18n.choice.minimap.reliefStyle.contour}}|{{i18n.choice.minimap.reliefStyle.flat}}")
                    slider "{{i18n.field.minimap.reliefStrength}}" (id = "minimap.reliefStrength", if = "minimap.terrainShading", bind = "minimap.reliefStrength", min = "0.5", max = "4", step = ".05")
                    toggle "{{i18n.field.minimap.biomeColors}}" (id = "minimap.biomeColors", bind = "minimap.biomeColors", visual = "compact")
                    toggle "{{i18n.field.minimap.blockLight}}" (id = "minimap.blockLight", bind = "minimap.blockLight", visual = "compact")
                    toggle "{{i18n.field.minimap.chunkGrid}}" (id = "minimap.chunkGrid", bind = "minimap.chunkGrid", visual = "compact")
                    toggle "{{i18n.field.minimap.showCompass}}" (id = "minimap.showCompass", bind = "minimap.showCompass", visual = "compact")
                    toggle "{{i18n.field.minimap.showCoords}}" (id = "minimap.showCoords", bind = "minimap.showCoords", visual = "compact")
                    toggle "{{i18n.field.minimap.showWaypoints}}" (id = "minimap.showWaypoints", bind = "minimap.showWaypoints", visual = "compact")
                    toggle "{{i18n.field.minimap.offscreenWaypoints}}" (id = "minimap.offscreenWaypoints", if = "minimap.showWaypoints", bind = "minimap.offscreenWaypoints", visual = "compact")
                  }
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.minimap.showPlayers}}" (id = "minimap.showPlayers", bind = "minimap.showPlayers", visual = "compact")
                    toggle "{{i18n.field.minimap.showPlayerHeads}}" (id = "minimap.showPlayerHeads", if = "minimap.showPlayers", bind = "minimap.showPlayerHeads", visual = "compact")
                    toggle "{{i18n.field.minimap.showPlayerNames}}" (id = "minimap.showPlayerNames", if = "minimap.showPlayers", bind = "minimap.showPlayerNames", visual = "compact")
                    toggle "{{i18n.field.minimap.showInvisiblePlayers}}" (id = "minimap.showInvisiblePlayers", if = "minimap.showPlayers", bind = "minimap.showInvisiblePlayers", visual = "compact")
                    slider "{{i18n.field.minimap.playerScale}}" (id = "minimap.playerScale", if = "minimap.showPlayers", bind = "minimap.playerScale", min = ".6", max = "1.8", step = ".05")
                  }
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.minimap.showItems}}" (id = "minimap.showItems", bind = "minimap.showItems", visual = "compact")
                    choice "{{i18n.field.minimap.itemStyle}}" (id = "minimap.itemStyle", if = "minimap.showItems", bind = "minimap.itemStyle", options = "icons|dots", option-labels = "{{i18n.choice.minimap.itemStyle.icons}}|{{i18n.choice.minimap.itemStyle.dots}}")
                    toggle "{{i18n.field.minimap.itemBackground}}" (id = "minimap.itemBackground", if = "minimap.showItems", bind = "minimap.itemBackground", visual = "compact")
                    toggle "{{i18n.field.minimap.onlyValuableItems}}" (id = "minimap.onlyValuableItems", if = "minimap.showItems", bind = "minimap.onlyValuableItems", visual = "compact")
                    slider "{{i18n.field.minimap.itemScale}}" (id = "minimap.itemScale", if = "minimap.showItems", bind = "minimap.itemScale", min = ".6", max = "1.6", step = ".05")
                    choice "{{i18n.field.minimap.mobIconStyle}}" (id = "minimap.mobIconStyle", bind = "minimap.mobIconStyle", options = "icons|dots", option-labels = "{{i18n.choice.minimap.mobIconStyle.icons}}|{{i18n.choice.minimap.mobIconStyle.dots}}")
                    slider "{{i18n.field.minimap.mobScale}}" (id = "minimap.mobScale", bind = "minimap.mobScale", min = ".6", max = "1.8", step = ".05")
                    toggle "{{i18n.field.minimap.showHostileMobs}}" (id = "minimap.showHostileMobs", bind = "minimap.showHostileMobs", visual = "compact")
                    toggle "{{i18n.field.minimap.showNeutralMobs}}" (id = "minimap.showNeutralMobs", bind = "minimap.showNeutralMobs", visual = "compact")
                    toggle "{{i18n.field.minimap.showPassiveMobs}}" (id = "minimap.showPassiveMobs", bind = "minimap.showPassiveMobs", visual = "compact")
                    toggle "{{i18n.field.minimap.showOtherEntities}}" (id = "minimap.showOtherEntities", bind = "minimap.showOtherEntities", visual = "compact")
                    toggle "{{i18n.field.minimap.showMobNames}}" (id = "minimap.showMobNames", bind = "minimap.showMobNames", visual = "compact")
                    toggle "{{i18n.field.minimap.showExplosives}}" (id = "minimap.showExplosives", bind = "minimap.showExplosives", visual = "compact")
                  }
                  row (style = "nc-actions") {
                    button "{{i18n.worldMap.clear_world_cache}}" (id = "minimap-clear-world-btn", style = "nc-btn", action = "map-clear-world-cache") { grow = 1; height = 22px; fontSize = 6.6px }
                    button "{{i18n.worldMap.clear_all_cache}}" (id = "minimap-clear-all-btn", style = "nc-btn", action = "map-clear-all-cache") { grow = 1; height = 22px; fontSize = 6.6px }
                  }
                }
                column (if = "module.id=worldMap", style = "nc-controls") {
                  row (style = "nc-actions") {
                    button "{{i18n.worldMap.open_button}}" (id = "worldMap-open-btn", style = "nc-btn", action = "world-map-open") { grow = 1; height = 24px; fontSize = 7px }
                    button "{{i18n.worldMap.open_settings_button}}" (id = "worldMap-settings-btn", style = "nc-btn", action = "map-settings-open") { grow = 1; height = 24px; fontSize = 7px }
                  }
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.worldMap.diskCache}}" (id = "worldMap.diskCache", bind = "worldMap.diskCache", visual = "compact")
                    toggle "{{i18n.field.worldMap.terrainShading}}" (id = "worldMap.terrainShading", bind = "worldMap.terrainShading", visual = "compact")
                    toggle "{{i18n.field.worldMap.biomeColors}}" (id = "worldMap.biomeColors", bind = "worldMap.biomeColors", visual = "compact")
                    toggle "{{i18n.field.worldMap.blockLight}}" (id = "worldMap.blockLight", bind = "worldMap.blockLight", visual = "compact")
                    toggle "{{i18n.field.worldMap.showGrid}}" (id = "worldMap.showGrid", bind = "worldMap.showGrid", visual = "compact")
                    toggle "{{i18n.field.worldMap.showCoordinatesBar}}" (id = "worldMap.showCoordinatesBar", bind = "worldMap.showCoordinatesBar", visual = "compact")
                  }
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.worldMap.showWaypoints}}" (id = "worldMap.showWaypoints", bind = "worldMap.showWaypoints", visual = "compact")
                    toggle "{{i18n.field.worldMap.showPlayers}}" (id = "worldMap.showPlayers", bind = "worldMap.showPlayers", visual = "compact")
                    toggle "{{i18n.field.worldMap.showInvisiblePlayers}}" (id = "worldMap.showInvisiblePlayers", if = "worldMap.showPlayers", bind = "worldMap.showInvisiblePlayers", visual = "compact")
                    toggle "{{i18n.field.worldMap.showItems}}" (id = "worldMap.showItems", bind = "worldMap.showItems", visual = "compact")
                    toggle "{{i18n.field.worldMap.showMobs}}" (id = "worldMap.showMobs", bind = "worldMap.showMobs", visual = "compact")
                    toggle "{{i18n.field.worldMap.showOtherEntities}}" (id = "worldMap.showOtherEntities", bind = "worldMap.showOtherEntities", visual = "compact")
                    toggle "{{i18n.field.worldMap.teleportCommand}}" (id = "worldMap.teleportCommand", bind = "worldMap.teleportCommand", visual = "compact")
                  }
                  row (style = "nc-actions") {
                    button "{{i18n.worldMap.clear_world_cache}}" (id = "worldMap-clear-world-btn", style = "nc-btn", action = "map-clear-world-cache") { grow = 1; height = 22px; fontSize = 6.6px }
                    button "{{i18n.worldMap.clear_all_cache}}" (id = "worldMap-clear-all-btn", style = "nc-btn", action = "map-clear-all-cache") { grow = 1; height = 22px; fontSize = 6.6px }
                  }
                }
                column (if = "module.id=accountSwitcher", style = "nc-controls") {
                  panel (style = "nc-sub") {
                    choice "{{i18n.field.accountSwitcher.layout}}" (id = "accountSwitcher.layout", bind = "accountSwitcher.layout", options = "modern|classic", option-labels = "{{i18n.choice.accountSwitcher.layout.modern}}|{{i18n.choice.accountSwitcher.layout.classic}}")
                    choice "{{i18n.field.accountSwitcher.titlePosition}}" (id = "accountSwitcher.titlePosition", bind = "accountSwitcher.titlePosition", options = "auto|besideButtons|topRight|topLeft|bottomLeft", option-labels = "{{i18n.choice.accountSwitcher.pos.auto}}|{{i18n.choice.accountSwitcher.pos.besideButtons}}|{{i18n.choice.accountSwitcher.overlayPosition.topRight}}|{{i18n.choice.accountSwitcher.overlayPosition.topLeft}}|{{i18n.choice.accountSwitcher.overlayPosition.bottomLeft}}")
                    choice "{{i18n.field.accountSwitcher.multiplayerPosition}}" (id = "accountSwitcher.multiplayerPosition", bind = "accountSwitcher.multiplayerPosition", options = "auto|headerRight|headerLeft|bottomLeft", option-labels = "{{i18n.choice.accountSwitcher.pos.auto}}|{{i18n.choice.accountSwitcher.pos.headerRight}}|{{i18n.choice.accountSwitcher.pos.headerLeft}}|{{i18n.choice.accountSwitcher.overlayPosition.bottomLeft}}")
                  }
                  panel (style = "nc-sub") {
                    toggle "{{i18n.field.accountSwitcher.titleScreen}}" (id = "accountSwitcher.titleScreen", bind = "accountSwitcher.titleScreen", visual = "compact")
                    toggle "{{i18n.field.accountSwitcher.multiplayerScreen}}" (id = "accountSwitcher.multiplayerScreen", bind = "accountSwitcher.multiplayerScreen", visual = "compact")
                    toggle "{{i18n.field.accountSwitcher.closeOnSwitch}}" (id = "accountSwitcher.closeOnSwitch", bind = "accountSwitcher.closeOnSwitch", visual = "compact")
                    toggle "{{i18n.field.accountSwitcher.hideAltsInProtect}}" (id = "accountSwitcher.hideAltsInProtect", bind = "accountSwitcher.hideAltsInProtect", visual = "compact")
                  }
                }
                column (if = "module.id=hwhelper", style = "nc-controls") {
                  panel (style = "nc-sub") {
                    text "{{i18n.hwhelper.group.economy}}" (style = "nc-section")
                    toggle "{{i18n.field.hwhelper.aucprice}}" (id = "hwhelper.aucprice.enabled", bind = "hwhelper.aucprice.enabled", visual = "compact")
                    toggle "{{i18n.field.hwhelper.answap}}" (id = "hwhelper.answap.enabled", bind = "hwhelper.answap.enabled", visual = "compact")
                    toggle "{{i18n.field.hwhelper.betterSell}}" (id = "hwhelper.betterSell.enabled", bind = "hwhelper.betterSell.enabled", visual = "compact")
                  }
                  panel (style = "nc-sub") {
                    text "{{i18n.hwhelper.group.events}}" (style = "nc-section")
                    toggle "{{i18n.field.hwhelper.events.enabled}}" (id = "hwhelper.events.enabled", bind = "hwhelper.events.enabled", visual = "compact")
                    button "{{i18n.hwhelper.open_events_tab}}" (id = "hwhelper-open-events-btn", if = "hwhelper.events.enabled", style = "nc-btn nc-btn-primary", action = "page:events") { height = 24px; fontSize = 7px }
                    toggle "{{i18n.field.hwhelper.events.showHud}}" (id = "hwhelper.events.showHud", if = "hwhelper.events.enabled", bind = "hwhelper.events.showHud", visual = "compact")
                    toggle "{{i18n.field.hwhelper.events.soundAlert}}" (id = "hwhelper.events.soundAlert", if = "hwhelper.events.enabled", bind = "hwhelper.events.soundAlert", visual = "compact")
                    slider "{{i18n.field.hwhelper.events.pollIntervalSeconds}}" (id = "hwhelper.events.pollIntervalSeconds", if = "hwhelper.events.enabled", bind = "hwhelper.events.pollIntervalSeconds", min = "10", max = "60", step = "5")
                  }
                  panel (style = "nc-sub") {
                    text "{{i18n.hwhelper.group.timers}}" (style = "nc-section")
                    toggle "{{i18n.field.hwhelper.timers.enabled}}" (id = "hwhelper.timers.enabled", bind = "hwhelper.timers.enabled", visual = "compact")
                    toggle "{{i18n.field.hwhelper.timers.traps}}" (id = "hwhelper.timers.traps", if = "hwhelper.timers.enabled", bind = "hwhelper.timers.traps", visual = "compact")
                    toggle "{{i18n.field.hwhelper.timers.explosiveTraps}}" (id = "hwhelper.timers.explosiveTraps", if = "hwhelper.timers.enabled", bind = "hwhelper.timers.explosiveTraps", visual = "compact")
                    toggle "{{i18n.field.hwhelper.timers.stuns}}" (id = "hwhelper.timers.stuns", if = "hwhelper.timers.enabled", bind = "hwhelper.timers.stuns", visual = "compact")
                    toggle "{{i18n.field.hwhelper.timers.snowball}}" (id = "hwhelper.timers.snowball", if = "hwhelper.timers.enabled", bind = "hwhelper.timers.snowball", visual = "compact")
                    toggle "{{i18n.field.hwhelper.timers.farewell}}" (id = "hwhelper.timers.farewell", if = "hwhelper.timers.enabled", bind = "hwhelper.timers.farewell", visual = "compact")
                    slider "{{i18n.field.hwhelper.timers.trapSeconds}}" (id = "hwhelper.timers.trapSeconds", if = "hwhelper.timers.enabled", bind = "hwhelper.timers.trapSeconds", min = "5", max = "30", step = ".5")
                    slider "{{i18n.field.hwhelper.timers.stunSeconds}}" (id = "hwhelper.timers.stunSeconds", if = "hwhelper.timers.enabled", bind = "hwhelper.timers.stunSeconds", min = "5", max = "30", step = ".5")
                    slider "{{i18n.field.hwhelper.timers.snowballSeconds}}" (id = "hwhelper.timers.snowballSeconds", if = "hwhelper.timers.enabled", bind = "hwhelper.timers.snowballSeconds", min = "3", max = "20", step = ".5")
                    slider "{{i18n.field.hwhelper.timers.scale}}" (id = "hwhelper.timers.scale", if = "hwhelper.timers.enabled", bind = "hwhelper.timers.scale", min = ".7", max = "1.6", step = ".05")
                  }
                }
                column (if = "module.id=soundControl", style = "nc-controls") {
                  panel (style = "nc-sub") {
                    text "{{i18n.soundControl.group.blocks}}" (style = "nc-section")
                    toggle "{{i18n.field.soundControl.muteBlocks}}" (id = "soundControl.muteBlocks", bind = "soundControl.muteBlocks", visual = "compact")
                    slider "{{i18n.field.soundControl.blockVolume}}" (id = "soundControl.blockVolume", if = "soundControl.muteBlocks", bind = "soundControl.blockVolume", min = "0", max = "1", step = ".05")
                    toggle "{{i18n.field.soundControl.filterStep}}" (id = "soundControl.filterStep", if = "soundControl.muteBlocks", bind = "soundControl.filterStep", visual = "compact")
                    toggle "{{i18n.field.soundControl.filterBreak}}" (id = "soundControl.filterBreak", if = "soundControl.muteBlocks", bind = "soundControl.filterBreak", visual = "compact")
                    toggle "{{i18n.field.soundControl.filterPlace}}" (id = "soundControl.filterPlace", if = "soundControl.muteBlocks", bind = "soundControl.filterPlace", visual = "compact")
                    toggle "{{i18n.field.soundControl.filterHit}}" (id = "soundControl.filterHit", if = "soundControl.muteBlocks", bind = "soundControl.filterHit", visual = "compact")
                    row (if = "soundControl.muteBlocks") { width = 100%; height = 24px; layout = row; gap = 4px
                      input (id = "sound-block-input", bind = "ui.soundBlockDraft", placeholder = "{{i18n.soundControl.block_placeholder}}", maxlength = "64") { grow = 1; height = 24px; fontSize = 6.8px }
                      button "{{i18n.soundControl.add_btn}}" (id = "sound-block-add-btn", style = "nc-btn nc-btn-primary", action = "sound-block-add-draft") { grow = 0; width = 62px; height = 24px; fontSize = 6.8px }
                    }
                    button "{{i18n.soundControl.add_crosshair}}" (id = "sound-block-crosshair-btn", if = "soundControl.muteBlocks", style = "nc-btn", action = "sound-block-add-crosshair") { height = 23px; fontSize = 6.8px }
                    text "{{i18n.soundControl.popular_hint}}" (if = "soundControl.muteBlocks", style = "nc-caption")
                    repeat (if = "soundControl.muteBlocks", source = "popularSoundBlocks", as = "chip", key = "{{chip.id}}", style = "nc-cape-grid") {
                      button "{{chip.label}}" (id = "sound-chip-{{chip.id}}", style = "nc-pill", action = "sound-block-toggle:{{chip.id}}") {
                        height = 20px; fontSize = 6.4px
                        borderColor = "{{= chip.muted ? vars.d_accent : vars.d_border}}"
                        background = "{{= chip.muted ? alphaColor(vars.d_accent,.16) : vars.d_raised}}"
                        color = "{{= chip.muted ? vars.d_text : vars.d_muted}}"
                      }
                    }
                    row (if = "{{= soundControl.muteBlocks && !soundMutedEmpty}}") { width = 100%; height = 18px; layout = row; align = center; gap = 4px
                      text "{{i18n.soundControl.active_hint}}" (style = "nc-caption") { grow = 1 }
                      button "{{i18n.soundControl.clear_blocks}}" (id = "sound-block-clear-btn", style = "nc-btn", action = "sound-block-clear") { grow = 0; width = 84px; height = 18px; fontSize = 6.1px; color = "#FF8A80" }
                    }
                    text "{{i18n.soundControl.empty_blocks}}" (if = "{{= soundControl.muteBlocks && soundMutedEmpty}}", style = "nc-caption") { height = 16px; textOverflow = wrap }
                    repeat (if = "soundControl.muteBlocks", source = "mutedBlocks", as = "blk", key = "{{blk.id}}") { gap = 4px
                      row { width = 100%; height = 23px; layout = row; align = center; gap = 6px; padding = "0px 7px"; background = "{{= vars.d_raised}}"; borderRadius = "{{= vars.r_ctrl}}"; borderWidth = .5px; borderColor = "{{= vars.d_border}}"
                        text "{{blk.label}}" { grow = 1; height = 100%; fontSize = 6.9px }
                        button (id = "sound-remove-{{blk.id}}", style = "nc-wp-icon", action = "sound-block-remove:{{blk.id}}", tooltip = "{{i18n.ui.delete}}") { width = 18px; height = 18px; icon (src = "trash") { width = 6px; height = 6px; color = "#FF6B5F" } }
                      }
                    }
                  }
                  panel (style = "nc-sub") {
                    text "{{i18n.soundControl.group.tnt}}" (style = "nc-section")
                    toggle "{{i18n.field.soundControl.customTnt}}" (id = "soundControl.customTnt", bind = "soundControl.customTnt", visual = "compact")
                    choice "{{i18n.field.soundControl.tntFusePreset}}" (id = "soundControl.tntFusePreset", if = "soundControl.customTnt", bind = "soundControl.tntFusePreset", options = "vanilla|note_pling|amethyst|anvil|bell|warden_sonic|mp3|custom", option-labels = "{{i18n.choice.soundPreset.vanilla}}|{{i18n.choice.soundPreset.note_pling}}|{{i18n.choice.soundPreset.amethyst}}|{{i18n.choice.soundPreset.anvil}}|{{i18n.choice.soundPreset.bell}}|{{i18n.choice.soundPreset.warden_sonic}}|{{i18n.choice.soundPreset.mp3}}|{{i18n.choice.soundPreset.custom}}")
                    choice "{{i18n.field.soundControl.tntExplodePreset}}" (id = "soundControl.tntExplodePreset", if = "soundControl.customTnt", bind = "soundControl.tntExplodePreset", options = "vanilla|warden_sonic|thunder|trident|amethyst|mute|mp3|custom", option-labels = "{{i18n.choice.soundPreset.vanilla}}|{{i18n.choice.soundPreset.warden_sonic}}|{{i18n.choice.soundPreset.thunder}}|{{i18n.choice.soundPreset.trident}}|{{i18n.choice.soundPreset.amethyst}}|{{i18n.choice.soundPreset.mute}}|{{i18n.choice.soundPreset.mp3}}|{{i18n.choice.soundPreset.custom}}")
                    column (if = "soundControl.customTnt") { width = 100%; gap = 4px
                      row { width = 100%; height = 24px; layout = row; align = center; gap = 5px; padding = "0px 7px"; background = "{{= vars.d_raised}}"; borderRadius = "{{= vars.r_ctrl}}"; borderWidth = .5px; borderColor = "{{= vars.d_border}}"
                        column { grow = 1; gap = 1px
                          text "{{i18n.soundControl.mp3_fuse_label}}" { fontSize = 6.5px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
                          text "{{tntFuseMp3Label}}" { fontSize = 5.9px; color = "{{= tntFuseMp3Loaded ? vars.d_accent : vars.d_muted}}" }
                        }
                        button "{{i18n.soundControl.mp3_browse}}" (id = "sound-tnt-fuse-mp3-btn", style = "nc-btn nc-btn-primary", action = "sound-tnt-fuse-mp3-browse") { grow = 0; height = 17px; padding = "0px 7px"; fontSize = 6.2px; borderRadius = "{{= vars.r_sm}}" }
                        button (id = "sound-tnt-fuse-mp3-clear", if = "tntFuseMp3Loaded", style = "nc-wp-icon", action = "sound-tnt-fuse-mp3-clear", tooltip = "{{i18n.ui.delete}}") { width = 17px; height = 17px; icon (src = "trash") { width = 6px; height = 6px; color = "#FF6B5F" } }
                      }
                      row { width = 100%; height = 24px; layout = row; align = center; gap = 5px; padding = "0px 7px"; background = "{{= vars.d_raised}}"; borderRadius = "{{= vars.r_ctrl}}"; borderWidth = .5px; borderColor = "{{= vars.d_border}}"
                        column { grow = 1; gap = 1px
                          text "{{i18n.soundControl.mp3_explode_label}}" { fontSize = 6.5px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
                          text "{{tntExplodeMp3Label}}" { fontSize = 5.9px; color = "{{= tntExplodeMp3Loaded ? vars.d_accent : vars.d_muted}}" }
                        }
                        button "{{i18n.soundControl.mp3_browse}}" (id = "sound-tnt-explode-mp3-btn", style = "nc-btn nc-btn-primary", action = "sound-tnt-explode-mp3-browse") { grow = 0; height = 17px; padding = "0px 7px"; fontSize = 6.2px; borderRadius = "{{= vars.r_sm}}" }
                        button (id = "sound-tnt-explode-mp3-clear", if = "tntExplodeMp3Loaded", style = "nc-wp-icon", action = "sound-tnt-explode-mp3-clear", tooltip = "{{i18n.ui.delete}}") { width = 17px; height = 17px; icon (src = "trash") { width = 6px; height = 6px; color = "#FF6B5F" } }
                      }
                    }
                    column (if = "{{= soundControl.customTnt && (soundControl.tntFusePreset=='custom' || soundControl.tntExplodePreset=='custom')}}") { width = 100%; gap = 3px
                      text "{{i18n.field.soundControl.customTntSoundId}}" (style = "nc-caption")
                      input (id = "soundControl.customTntSoundId", bind = "soundControl.customTntSoundId", maxlength = "96")
                    }
                    slider "{{i18n.field.soundControl.tntVolume}}" (id = "soundControl.tntVolume", if = "soundControl.customTnt", bind = "soundControl.tntVolume", min = "0", max = "2", step = ".05")
                    slider "{{i18n.field.soundControl.tntPitch}}" (id = "soundControl.tntPitch", if = "soundControl.customTnt", bind = "soundControl.tntPitch", min = ".5", max = "2", step = ".05")
                    toggle "{{i18n.field.soundControl.tntCountdownTicks}}" (id = "soundControl.tntCountdownTicks", if = "soundControl.customTnt", bind = "soundControl.tntCountdownTicks", visual = "compact")
                    button "{{i18n.soundControl.preview_tnt}}" (id = "sound-test-tnt-btn", if = "soundControl.customTnt", style = "nc-btn", action = "sound-test-tnt") { height = 22px; fontSize = 6.8px }
                  }
                  panel (style = "nc-sub") {
                    text "{{i18n.soundControl.group.mention}}" (style = "nc-section")
                    toggle "{{i18n.field.soundControl.chatMention}}" (id = "soundControl.chatMention", bind = "soundControl.chatMention", visual = "compact")
                    choice "{{i18n.field.soundControl.mentionPreset}}" (id = "soundControl.mentionPreset", if = "soundControl.chatMention", bind = "soundControl.mentionPreset", options = "note_pling|experience|amethyst|bell|chime|levelup", option-labels = "{{i18n.choice.soundPreset.note_pling}}|{{i18n.choice.soundPreset.experience}}|{{i18n.choice.soundPreset.amethyst}}|{{i18n.choice.soundPreset.bell}}|{{i18n.choice.soundPreset.chime}}|{{i18n.choice.soundPreset.levelup}}")
                    slider "{{i18n.field.soundControl.mentionVolume}}" (id = "soundControl.mentionVolume", if = "soundControl.chatMention", bind = "soundControl.mentionVolume", min = "0", max = "2", step = ".05")
                    slider "{{i18n.field.soundControl.mentionPitch}}" (id = "soundControl.mentionPitch", if = "soundControl.chatMention", bind = "soundControl.mentionPitch", min = ".5", max = "2", step = ".05")
                    toggle "{{i18n.field.soundControl.mentionHighlight}}" (id = "soundControl.mentionHighlight", if = "soundControl.chatMention", bind = "soundControl.mentionHighlight", visual = "compact")
                    column (if = "soundControl.chatMention") { width = 100%; gap = 3px
                      text "{{i18n.field.soundControl.mentionKeywords}}" (style = "nc-caption")
                      input (id = "soundControl.mentionKeywords", bind = "soundControl.mentionKeywords", maxlength = "96")
                    }
                    button "{{i18n.soundControl.preview_mention}}" (id = "sound-test-mention-btn", if = "soundControl.chatMention", style = "nc-btn", action = "sound-test-mention") { height = 22px; fontSize = 6.8px }
                  }
                  panel (style = "nc-sub") {
                    text "{{i18n.soundControl.group.global}}" (style = "nc-section")
                    slider "{{i18n.field.soundControl.explosionVolume}}" (id = "soundControl.explosionVolume", bind = "soundControl.explosionVolume", min = "0", max = "1.5", step = ".05")
                    slider "{{i18n.field.soundControl.totemVolume}}" (id = "soundControl.totemVolume", bind = "soundControl.totemVolume", min = "0", max = "1.5", step = ".05")
                    slider "{{i18n.field.soundControl.weatherVolume}}" (id = "soundControl.weatherVolume", bind = "soundControl.weatherVolume", min = "0", max = "1.5", step = ".05")
                    slider "{{i18n.field.soundControl.portalBeaconVolume}}" (id = "soundControl.portalBeaconVolume", bind = "soundControl.portalBeaconVolume", min = "0", max = "1.5", step = ".05")
                  }
                }
                column (if = "module.id=effectAlert", style = "nc-controls") {
                  panel (style = "nc-sub") {
                    text "{{i18n.effectAlert.group.mode}}" (style = "nc-section")
                    choice "{{i18n.field.effectAlert.mode}}" (id = "effectAlert.mode", bind = "effectAlert.mode", options = "both|sound|hud", option-labels = "{{i18n.choice.effectAlert.mode.both}}|{{i18n.choice.effectAlert.mode.sound}}|{{i18n.choice.effectAlert.mode.hud}}")
                    slider "{{i18n.field.effectAlert.warnSeconds}}" (id = "effectAlert.warnSeconds", bind = "effectAlert.warnSeconds", min = "1", max = "30", step = "1")
                    toggle "{{i18n.field.effectAlert.notifyOnExpire}}" (id = "effectAlert.notifyOnExpire", bind = "effectAlert.notifyOnExpire", visual = "compact")
                  }
                  panel (style = "nc-sub") {
                    text "{{i18n.effectAlert.group.sound}}" (style = "nc-section")
                    choice "{{i18n.field.effectAlert.soundPreset}}" (id = "effectAlert.soundPreset", if = "{{= effectAlert.mode!='hud'}}", bind = "effectAlert.soundPreset", options = "note_pling|amethyst|bell|experience|anvil|bass", option-labels = "{{i18n.choice.soundPreset.note_pling}}|{{i18n.choice.soundPreset.amethyst}}|{{i18n.choice.soundPreset.bell}}|{{i18n.choice.soundPreset.experience}}|{{i18n.choice.soundPreset.anvil}}|{{i18n.choice.soundPreset.bass}}")
                    slider "{{i18n.field.effectAlert.soundVolume}}" (id = "effectAlert.soundVolume", if = "{{= effectAlert.mode!='hud'}}", bind = "effectAlert.soundVolume", min = "0", max = "2", step = ".05")
                    slider "{{i18n.field.effectAlert.soundPitch}}" (id = "effectAlert.soundPitch", if = "{{= effectAlert.mode!='hud'}}", bind = "effectAlert.soundPitch", min = ".5", max = "2", step = ".05")
                    slider "{{i18n.field.effectAlert.scale}}" (id = "effectAlert.scale", if = "{{= effectAlert.mode!='sound'}}", bind = "effectAlert.scale", min = ".7", max = "1.6", step = ".05")
                    button "{{i18n.effectAlert.preview_btn}}" (id = "sound-test-effect-btn", if = "{{= effectAlert.mode!='hud'}}", style = "nc-btn", action = "sound-test-effect") { height = 22px; fontSize = 6.8px }
                  }
                  panel (style = "nc-sub") {
                    text "{{i18n.effectAlert.group.effects}}" (style = "nc-section")
                    toggle "{{i18n.field.effectAlert.strength}}" (id = "effectAlert.strength", bind = "effectAlert.strength", visual = "compact")
                    toggle "{{i18n.field.effectAlert.speed}}" (id = "effectAlert.speed", bind = "effectAlert.speed", visual = "compact")
                    toggle "{{i18n.field.effectAlert.fireResistance}}" (id = "effectAlert.fireResistance", bind = "effectAlert.fireResistance", visual = "compact")
                    toggle "{{i18n.field.effectAlert.invisibility}}" (id = "effectAlert.invisibility", bind = "effectAlert.invisibility", visual = "compact")
                    toggle "{{i18n.field.effectAlert.regeneration}}" (id = "effectAlert.regeneration", bind = "effectAlert.regeneration", visual = "compact")
                    toggle "{{i18n.field.effectAlert.resistance}}" (id = "effectAlert.resistance", bind = "effectAlert.resistance", visual = "compact")
                    toggle "{{i18n.field.effectAlert.absorption}}" (id = "effectAlert.absorption", bind = "effectAlert.absorption", visual = "compact")
                    toggle "{{i18n.field.effectAlert.haste}}" (id = "effectAlert.haste", bind = "effectAlert.haste", visual = "compact")
                    toggle "{{i18n.field.effectAlert.slowFalling}}" (id = "effectAlert.slowFalling", bind = "effectAlert.slowFalling", visual = "compact")
                    toggle "{{i18n.field.effectAlert.waterBreathing}}" (id = "effectAlert.waterBreathing", bind = "effectAlert.waterBreathing", visual = "compact")
                    toggle "{{i18n.field.effectAlert.nightVision}}" (id = "effectAlert.nightVision", bind = "effectAlert.nightVision", visual = "compact")
                  }
                }
                column (if = "module.id=tapeMouse", style = "nc-controls") {
                  panel (style = "nc-sub") {
                    text "{{i18n.tapeMouse.group.action}}" (style = "nc-section")
                    choice "{{i18n.field.tapeMouse.action}}" (id = "tapeMouse.action", bind = "tapeMouse.action", options = "attack|use|forward|jump|sneak|sprint|drop|custom", option-labels = "{{i18n.choice.tapeMouse.action.attack}}|{{i18n.choice.tapeMouse.action.use}}|{{i18n.choice.tapeMouse.action.forward}}|{{i18n.choice.tapeMouse.action.jump}}|{{i18n.choice.tapeMouse.action.sneak}}|{{i18n.choice.tapeMouse.action.sprint}}|{{i18n.choice.tapeMouse.action.drop}}|{{i18n.choice.tapeMouse.action.custom}}")
                    button (id = "binding-tapeMouseKey", if = "tapeMouse.action=custom", action = "bind-key:tapeMouseKey", style = "nc-bind-row") {
                      icon (src = "studio-bind") { width = 9px; height = 9px; color = "{{= vars.d_muted}}" }
                      text "{{i18n.field.tapeMouse.customKey}}" { height = 100%; grow = 1; padding = 0px; fontSize = 7.1px; textOverflow = ellipsis }
                      text "{{tapeMouseKeyBindingLabel}}" (style = "nc-keycap")
                    }
                    text "{{i18n.ui.escape_cancels_delete_unbinds}}" (if = "{{= bindingCapture=='tapeMouseKey'}}", style = "nc-caption")
                    slider "{{i18n.field.tapeMouse.delayTicks}}" (id = "tapeMouse.delayTicks", bind = "tapeMouse.delayTicks", min = "0", max = "200", step = "1")
                    slider "{{i18n.field.tapeMouse.jitterTicks}}" (id = "tapeMouse.jitterTicks", if = "{{= tapeMouse.delayTicks>0}}", bind = "tapeMouse.jitterTicks", min = "0", max = "10", step = "1")
                    row (style = "nc-actions") {
                      button "{{i18n.tapeMouse.preset.hold}}" (style = "nc-pill", action = "set:tapeMouse.delayTicks:0") { borderColor = "{{= tapeMouse.delayTicks==0 ? vars.d_accent : vars.d_border}}" }
                      button "{{i18n.tapeMouse.preset.sword}}" (style = "nc-pill", action = "set:tapeMouse.delayTicks:12") { borderColor = "{{= tapeMouse.delayTicks==12 ? vars.d_accent : vars.d_border}}" }
                      button "{{i18n.tapeMouse.preset.axe}}" (style = "nc-pill", action = "set:tapeMouse.delayTicks:16") { borderColor = "{{= tapeMouse.delayTicks==16 ? vars.d_accent : vars.d_border}}" }
                      button "{{i18n.tapeMouse.preset.sec}}" (style = "nc-pill", action = "set:tapeMouse.delayTicks:20") { borderColor = "{{= tapeMouse.delayTicks==20 ? vars.d_accent : vars.d_border}}" }
                    }
                  }
                  panel (style = "nc-sub") {
                    text "{{i18n.tapeMouse.group.behavior}}" (style = "nc-section")
                    toggle "{{i18n.field.tapeMouse.workInBackground}}" (id = "tapeMouse.workInBackground", bind = "tapeMouse.workInBackground", visual = "compact")
                    toggle "{{i18n.field.tapeMouse.pauseInScreens}}" (id = "tapeMouse.pauseInScreens", bind = "tapeMouse.pauseInScreens", visual = "compact")
                    toggle "{{i18n.field.tapeMouse.stopOnDamage}}" (id = "tapeMouse.stopOnDamage", bind = "tapeMouse.stopOnDamage", visual = "compact")
                    toggle "{{i18n.field.tapeMouse.showHudIndicator}}" (id = "tapeMouse.showHudIndicator", bind = "tapeMouse.showHudIndicator", visual = "compact")
                    text "{{i18n.tapeMouse.cmd_hint}}" (style = "nc-caption") { height = 16px; textOverflow = wrap }
                  }
                }
                repeat (if = "{{= module.id!='waypoints' && module.id!='tweaksy' && module.id!='atmosphere' && module.id!='optimization' && module.id!='noRender' && module.id!='customSky' && module.id!='safeCtLeave' && module.id!='betterChat' && module.id!='blockOverlay' && module.id!='enchantGlint' && module.id!='wavyCapes' && module.id!='explosionRing' && module.id!='tooltips' && module.id!='minimap' && module.id!='worldMap' && module.id!='accountSwitcher' && module.id!='hwhelper' && module.id!='soundControl' && module.id!='effectAlert' && module.id!='tapeMouse'}}", source = "controls", as = "control", key = "{{control.bind}}", style = "nc-controls") {
            toggle "{{control.label}}" (id = "{{control.bind}}", if = "control.kind=toggle", bind = "{{control.bind}}", visual = "compact")
            slider "{{control.label}}" (id = "{{control.bind}}", if = "control.kind=slider", bind = "{{control.bind}}", min = "{{control.min}}", max = "{{control.max}}", step = "{{control.step}}")
            column (if = "control.kind=input") { width = 100%; gap = 3px; text "{{control.label}}" (style = "nc-caption"); input (id = "{{control.bind}}", bind = "{{control.bind}}", maxlength = "128") }
            color "{{control.label}}" (id = "{{control.bind}}", if = "control.kind=color", bind = "{{control.bind}}")
            choice "{{control.label}}" (id = "{{control.bind}}", if = "control.kind=choice", bind = "{{control.bind}}", options = "{{control.options}}", option-labels = "{{control.optionLabels}}")
                }
          }
        }
      }
    }

    panel (id = "cosmetics-page", if = "page=cosmetics") {
      let cos-stage-w = "boxWidth >= 310 ? min(158, round(boxWidth * .36)) : max(0, boxWidth - 24)"
      let cos-right-x = "boxWidth >= 310 ? vars.cos_stage_w + 22 : 12"
      let cos-right-w = "boxWidth >= 310 ? max(0, boxWidth - vars.cos_stage_w - 34) : max(0, boxWidth - 24)"
      position = absolute; x = 0px; y = 44px; width = 100%; height = "{{= max(0,boxHeight-44)}}"; layout = absolute
      animateEnter = true; enterOpacity = 0%; enterOffsetY = 4px; enterDuration = 190ms; easing = "cubic-bezier(0.22,1,0.36,1)"

      panel (id = "cosmetics-stage") {
        position = absolute; x = 10px; y = 8px; width = "{{= vars.cos_stage_w}}"; height = "{{= boxWidth >= 310 ? max(0, boxHeight - 16) : 132}}"
        layout = absolute; background = "{{= vars.d_raised}}"; borderRadius = "{{= vars.r_card}}"; borderWidth = .6px; borderColor = "{{= vars.d_border}}"
        rect { position = absolute; x = 1px; y = 1px; width = "{{= max(0,boxWidth-2)}}"; height = "{{= max(0,boxHeight-2)}}"; radius = "{{= max(0,vars.r_card-1)}}"; fill = "{{= alphaColor(vars.d_text,.015)}}" }
        text "{{= ui.cosmeticsTab=='weapons' ? cosmeticsWeaponSummary : cosmeticsCapeSummary}}" (id = "cosmetics-stage-status") { position = absolute; x = 9px; y = 9px; width = "{{= max(0, boxWidth - 60)}}"; height = 11px; fontSize = 6.2px; fontFamily = studio-sans-medium; color = "{{= vars.d_muted}}" }
        button (id = "cosmetics-auto-btn", action = "cosmetics-auto", style = "nc-mini-icon", tooltip = "{{i18n.cosmetics.auto}}") { position = absolute; right = 29px; y = 6px; borderColor = "{{= previewAuto ? vars.d_accent : vars.d_border}}"; icon (src = "motion") { width = 9px; height = 9px; color = "{{= previewAuto ? vars.d_text : vars.d_muted}}" } }
        button (id = "cosmetics-reset-btn", action = "cosmetics-reset", style = "nc-mini-icon", tooltip = "{{i18n.cosmetics.reset}}") { position = absolute; right = 7px; y = 6px; icon (src = "reset") { width = 9px; height = 9px; color = "{{= vars.d_muted}}" } }
        player-preview (id = "player-showcase") { position = absolute; x = 4px; y = 24px; width = "{{= max(0, boxWidth - 8)}}"; height = "{{= max(0, boxHeight - 58)}}" }
        row (id = "cosmetics-stage-model") { position = absolute; x = 8px; bottom = 7px; width = "{{= max(0, boxWidth - 16)}}"; height = 20px; layout = row; gap = 2px; padding = 2px; background = "{{= vars.d_well}}"; borderRadius = "{{= vars.r_ctrl}}"; borderWidth = .5px; borderColor = "{{= vars.d_border}}"
          button "{{i18n.cosmetics.model.auto}}" (style = "nc-filter", action = "cosmetics-skin-model:auto") { height = 16px; background = "{{= cosmetics.skinModel=='auto' ? vars.d_accent : '#00000000'}}"; color = "{{= cosmetics.skinModel=='auto' ? vars.d_on_accent : vars.d_muted}}" }
          button "{{i18n.cosmetics.model.classic}}" (style = "nc-filter", action = "cosmetics-skin-model:classic") { height = 16px; background = "{{= cosmetics.skinModel=='classic' ? vars.d_accent : '#00000000'}}"; color = "{{= cosmetics.skinModel=='classic' ? vars.d_on_accent : vars.d_muted}}" }
          button "{{i18n.cosmetics.model.slim}}" (style = "nc-filter", action = "cosmetics-skin-model:slim") { height = 16px; background = "{{= cosmetics.skinModel=='slim' ? vars.d_accent : '#00000000'}}"; color = "{{= cosmetics.skinModel=='slim' ? vars.d_on_accent : vars.d_muted}}" }
        }
      }

      column (id = "cosmetics-studio") { position = absolute; x = "{{= vars.cos_right_x}}"; y = "{{= boxWidth >= 310 ? 8 : 148}}"; width = "{{= vars.cos_right_w}}"; height = "{{= boxWidth >= 310 ? max(0, boxHeight - 16) : max(0, boxHeight - 156)}}"; layout = column; gap = 5px; padding = 0px
        row (id = "cosmetics-tabs") { width = 100%; height = 22px; layout = row; gap = 2px; padding = 2px; background = "{{= vars.d_well}}"; borderRadius = "{{= vars.r_ctrl}}"; borderWidth = .55px; borderColor = "{{= vars.d_border}}"
          button "{{i18n.cosmetics.tab.capes}}" (id = "cosmetics-tab-capes", style = "nc-seg-tab", action = "ui-set:cosmeticsTab:capes") { fontSize = 6.7px; background = "{{= ui.cosmeticsTab=='capes' ? vars.d_accent : '#00000000'}}"; color = "{{= ui.cosmeticsTab=='capes' ? vars.d_on_accent : vars.d_muted}}" }
          button "{{i18n.cosmetics.tab.skins}}" (id = "cosmetics-tab-skins", style = "nc-seg-tab", action = "ui-set:cosmeticsTab:skins") { fontSize = 6.7px; background = "{{= ui.cosmeticsTab=='skins' ? vars.d_accent : '#00000000'}}"; color = "{{= ui.cosmeticsTab=='skins' ? vars.d_on_accent : vars.d_muted}}" }
          button "{{i18n.cosmetics.tab.weapons}}" (id = "cosmetics-tab-weapons", style = "nc-seg-tab", action = "ui-set:cosmeticsTab:weapons") { fontSize = 6.7px; background = "{{= ui.cosmeticsTab=='weapons' ? vars.d_accent : '#00000000'}}"; color = "{{= ui.cosmeticsTab=='weapons' ? vars.d_on_accent : vars.d_muted}}" }
        }
        row (if = "{{= cosmeticsError!='' || cosmeticsLoading}}") { width = 100%; height = 16px; padding = "0px 7px"; background = "{{= cosmeticsError!='' ? '#E5565624' : alphaColor(vars.d_accent,.12)}}"; borderRadius = "{{= vars.r_sm}}"; align = center
          text "{{= cosmeticsError!='' ? cosmeticsError : i18n.cosmetics.status.loading}}" { width = 100%; height = 10px; fontSize = 6.4px; color = "{{= cosmeticsError!='' ? (theme.mode=='light' ? '#DC2626' : '#E55656') : vars.d_text}}" }
        }

        column (id = "cosmetics-capes-pane", if = "ui.cosmeticsTab=='capes'") { width = 100%; height = 0px; grow = 1; layout = column; gap = 4px; padding = 0px; animateEnter = true; enterOpacity = 0%; enterOffsetY = 3px; enterDuration = 160ms
          row { width = 100%; height = 20px; layout = row; gap = 5px
            button "{{i18n.cosmetics.cape_default}}" (id = "cosmetics-cape-default", style = "nc-pill", action = "cosmetics-cape-reset") { height = 20px; borderColor = "{{= cosmetics.capeMode=='default' ? vars.d_accent : vars.d_border}}"; background = "{{= cosmetics.capeMode=='default' ? alphaColor(vars.d_accent,.12) : (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E')}}"; color = "{{= cosmetics.capeMode=='default' ? vars.d_text : vars.d_muted}}" }
            button "{{i18n.cosmetics.cape_disable}}" (id = "cosmetics-cape-disable", style = "nc-pill", action = "cosmetics-cape-none") { height = 20px; borderColor = "{{= cosmetics.capeMode=='none' ? vars.d_accent : vars.d_border}}"; background = "{{= cosmetics.capeMode=='none' ? alphaColor(vars.d_accent,.12) : (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E')}}"; color = "{{= cosmetics.capeMode=='none' ? vars.d_text : vars.d_muted}}" }
          }
          panel (if = "officialCapesEnabled") { width = 100%; height = 20px; layout = absolute
            input (id = "cosmetics-cape-search", bind = "ui.cosmeticsCapeQuery", placeholder = "{{i18n.cosmetics.search_capes}}", maxlength = "64") { position = absolute; x = 0px; y = 0px; width = 100%; height = 20px; padding = "0px 8px 0px 22px"; fontSize = 6.7px; borderRadius = "{{= vars.r_ctrl}}" }
            icon (src = "search") { position = absolute; x = 7px; y = 5.5px; width = 8.5px; height = 8.5px; color = "{{= vars.d_muted}}" }
          }
          row (if = "officialCapesEnabled") { width = 100%; height = 19px; layout = row; gap = 2px; padding = 1.5px; background = "{{= vars.d_well}}"; borderRadius = "{{= vars.r_ctrl}}"; borderWidth = .5px; borderColor = "{{= vars.d_border}}"
            button "{{i18n.cosmetics.filter.all}}" (style = "nc-filter", action = "ui-set:cosmeticsCapeFilter:all") { height = 16px; background = "{{= ui.cosmeticsCapeFilter=='all' ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E') : '#00000000'}}"; color = "{{= ui.cosmeticsCapeFilter=='all' ? vars.d_text : vars.d_muted}}" }
            button "{{i18n.cosmetics.filter.events}}" (style = "nc-filter", action = "ui-set:cosmeticsCapeFilter:events") { height = 16px; background = "{{= ui.cosmeticsCapeFilter=='events' ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E') : '#00000000'}}"; color = "{{= ui.cosmeticsCapeFilter=='events' ? vars.d_text : vars.d_muted}}" }
            button "{{i18n.cosmetics.filter.milestones}}" (style = "nc-filter", action = "ui-set:cosmeticsCapeFilter:milestones") { height = 16px; background = "{{= ui.cosmeticsCapeFilter=='milestones' ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E') : '#00000000'}}"; color = "{{= ui.cosmeticsCapeFilter=='milestones' ? vars.d_text : vars.d_muted}}" }
            button "{{i18n.cosmetics.filter.mojang}}" (style = "nc-filter", action = "ui-set:cosmeticsCapeFilter:mojang") { height = 16px; background = "{{= ui.cosmeticsCapeFilter=='mojang' ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E') : '#00000000'}}"; color = "{{= ui.cosmeticsCapeFilter=='mojang' ? vars.d_text : vars.d_muted}}" }
            button "{{i18n.cosmetics.filter.special}}" (style = "nc-filter", action = "ui-set:cosmeticsCapeFilter:special") { height = 16px; background = "{{= ui.cosmeticsCapeFilter=='special' ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E') : '#00000000'}}"; color = "{{= ui.cosmeticsCapeFilter=='special' ? vars.d_text : vars.d_muted}}" }
          }
          column (id = "cosmetics-cape-scroll", if = "officialCapesEnabled") { width = 100%; height = 0px; grow = 1; layout = column; gap = 5px; overflow = scroll; scrollbarWidth = 2px; scrollStep = 34px; scrollDuration = 180ms
            text "{{i18n.cosmetics.cape_empty}}" (if = "cosmeticsCapesEmpty", style = "nc-caption") { width = 100%; height = 24px; textAlign = center }
            repeat (source = "officialCapes", as = "cape", key = "{{cape.id}}", style = "nc-cape-grid") {
              button (id = "cape-card-{{cape.id}}", style = "nc-cape-card", action = "cosmetics-cape-select:{{cape.id}}") {
                borderColor = "{{= cape.selected ? vars.d_accent : vars.d_border}}"
                background = "{{= cape.selected ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.045) : theme.mode=='light' ? '#FFFFFF' : '#242429') : vars.d_raised}}"
                rect { position = absolute; x = 4px; y = 4px; width = 28px; height = 38px; radius = "{{= vars.r_sm}}"; fill = "{{= cape.selected ? alphaColor(vars.d_accent,.12) : vars.d_well}}" }
                cape-preview (source = "{{cape.id}}") { position = absolute; x = 5px; y = 4.5px; width = 26px; height = 37px }
                text "{{cape.name}}" { position = absolute; x = 36px; y = 10px; width = "{{= max(0, boxWidth - 42)}}"; height = 12px; fontSize = 7px; fontFamily = studio-sans-medium }
                text "{{cape.year}}" { position = absolute; x = 36px; y = 25px; width = "{{= max(0, boxWidth - 42)}}"; height = 9px; fontSize = 6px; color = "{{= cape.selected ? vars.d_text : vars.d_muted}}" }
              }
            }
          }
          panel (id = "cosmetics-custom-cape-bar", style = "nc-card") { padding = "5px 7px"; gap = 3px; borderRadius = "{{= vars.r_card}}"
            row { width = 100%; height = 15px; layout = row; align = center; gap = 4px
              text "{{i18n.cosmetics.custom_cape_title}}" { grow = 1; height = 11px; fontSize = 6.7px; fontFamily = studio-sans-medium }
              button "{{i18n.cosmetics.browse_file}}" (id = "cosmetics-cape-browse-btn", style = "nc-btn nc-btn-primary", action = "cosmetics-cape-browse") { grow = 0; height = 15px; padding = "0px 7px"; fontSize = 6px; borderRadius = "{{= vars.r_sm}}" }
            }
            row { width = 100%; height = 20px; layout = row; gap = 4px
              input (id = "cosmetics-cape-path-input", bind = "ui.cosmeticsCapePathDraft", placeholder = "{{i18n.cosmetics.path_placeholder}}", maxlength = "512") { grow = 1; height = 20px; fontSize = 6.4px }
              button "{{i18n.ui.apply}}" (id = "cosmetics-cape-path-btn", style = "nc-btn", action = "cosmetics-cape-path") { grow = 0; height = 20px; padding = "0px 8px"; fontSize = 6.4px }
            }
          }
        }

        column (id = "cosmetics-skins-pane", if = "ui.cosmeticsTab=='skins'") { width = 100%; height = 0px; grow = 1; layout = column; gap = 7px; padding = 0px; overflow = scroll; scrollbarWidth = 2px; animateEnter = true; enterOpacity = 0%; enterOffsetY = 3px; enterDuration = 160ms
          panel (id = "cosmetics-nick-card", style = "nc-card") { padding = "9px 10px"; gap = 6px
            text "{{i18n.cosmetics.skin_nick_title}}" { height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium }
            text "{{i18n.cosmetics.skin_nick_hint}}" (style = "nc-caption")
            row { width = 100%; height = 24px; layout = row; gap = 5px
              input (id = "cosmetics-nick-input", bind = "ui.cosmeticsNickDraft", placeholder = "{{i18n.cosmetics.nick_placeholder}}", maxlength = "16") { grow = 1; height = 24px; fontSize = 7px }
              button "{{i18n.ui.apply}}" (id = "cosmetics-nick-apply", style = "nc-btn nc-btn-primary", action = "cosmetics-skin-nick") { grow = 0; height = 24px; padding = "0px 10px"; fontSize = 6.9px }
            }
          }
          panel (id = "cosmetics-custom-skin-card", style = "nc-card") { padding = "9px 10px"; gap = 6px
            text "{{i18n.cosmetics.custom_skin_title}}" { height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium }
            text "{{i18n.cosmetics.custom_skin_hint}}" (style = "nc-caption")
            row { width = 100%; height = 23px; layout = row; gap = 5px
              button "{{i18n.cosmetics.browse_file}}" (id = "cosmetics-skin-browse-btn", style = "nc-btn nc-btn-primary", action = "cosmetics-skin-browse") { grow = 1; height = 23px; fontSize = 6.9px }
              button "{{i18n.cosmetics.skin_reset}}" (id = "cosmetics-skin-reset-btn", style = "nc-btn", action = "cosmetics-skin-reset") { grow = 1; height = 23px; fontSize = 6.9px; borderColor = "{{= cosmetics.skinMode=='default' ? vars.d_accent : (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}" }
            }
            row { width = 100%; height = 23px; layout = row; gap = 5px
              input (id = "cosmetics-skin-path-input", bind = "ui.cosmeticsSkinPathDraft", placeholder = "{{i18n.cosmetics.path_placeholder}}", maxlength = "512") { grow = 1; height = 23px; fontSize = 6.6px }
              button "{{i18n.ui.apply}}" (id = "cosmetics-skin-path-btn", style = "nc-btn", action = "cosmetics-skin-path") { grow = 0; height = 23px; padding = "0px 9px"; fontSize = 6.8px }
            }
          }
          panel (id = "cosmetics-model-card", style = "nc-card") { padding = "9px 10px"; gap = 6px
            text "{{i18n.cosmetics.model_title}}" { height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium }
            row { width = 100%; height = 23px; layout = row; gap = 4px
              button "{{i18n.cosmetics.model.auto}}" (style = "nc-pill", action = "cosmetics-skin-model:auto") { height = 23px; borderColor = "{{= cosmetics.skinModel=='auto' ? vars.d_accent : vars.d_border}}"; background = "{{= cosmetics.skinModel=='auto' ? alphaColor(vars.d_accent,.14) : (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E')}}"; color = "{{= cosmetics.skinModel=='auto' ? vars.d_text : vars.d_muted}}" }
              button "{{i18n.cosmetics.model.classic}}" (style = "nc-pill", action = "cosmetics-skin-model:classic") { height = 23px; borderColor = "{{= cosmetics.skinModel=='classic' ? vars.d_accent : vars.d_border}}"; background = "{{= cosmetics.skinModel=='classic' ? alphaColor(vars.d_accent,.14) : (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E')}}"; color = "{{= cosmetics.skinModel=='classic' ? vars.d_text : vars.d_muted}}" }
              button "{{i18n.cosmetics.model.slim}}" (style = "nc-pill", action = "cosmetics-skin-model:slim") { height = 23px; borderColor = "{{= cosmetics.skinModel=='slim' ? vars.d_accent : vars.d_border}}"; background = "{{= cosmetics.skinModel=='slim' ? alphaColor(vars.d_accent,.14) : (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E')}}"; color = "{{= cosmetics.skinModel=='slim' ? vars.d_text : vars.d_muted}}" }
            }
          }
        }

        column (id = "cosmetics-weapons-pane", if = "ui.cosmeticsTab=='weapons'") { width = 100%; height = 0px; grow = 1; layout = column; gap = 6px; padding = 0px; overflow = scroll; scrollbarWidth = 2px; scrollStep = 34px; scrollDuration = 180ms; animateEnter = true; enterOpacity = 0%; enterOffsetY = 3px; enterDuration = 160ms
          row { width = 100%; height = 22px; layout = row; gap = 5px
            button "{{i18n.cosmetics.weapon_none}}" (id = "cosmetics-weapon-reset", style = "nc-pill", action = "cosmetics-weapon-reset") { borderColor = "{{= cosmetics.weaponMode=='none' ? vars.d_accent : vars.d_border}}"; background = "{{= cosmetics.weaponMode=='none' ? alphaColor(vars.d_accent,.12) : (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E')}}"; color = "{{= cosmetics.weaponMode=='none' ? vars.d_text : vars.d_muted}}" }
          }
          text "{{i18n.cosmetics.weapon_materials_title}}" { height = 10px; fontSize = 6.7px; fontFamily = studio-sans-medium; color = "{{= vars.d_muted}}" }
          repeat (source = "cosmeticWeaponMaterials", as = "mat", key = "{{mat.id}}", style = "nc-cape-grid") {
            button (id = "weapon-mat-{{mat.id}}", style = "nc-cape-card", action = "cosmetics-weapon-material:{{mat.id}}") {
              height = 24px
              borderColor = "{{= mat.selected ? vars.d_accent : vars.d_border}}"
              background = "{{= mat.selected ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.045) : theme.mode=='light' ? '#FFFFFF' : '#242429') : vars.d_raised}}"
              rect { position = absolute; x = 5px; y = 5px; width = 14px; height = 14px; radius = "{{= vars.r_sm}}"; fill = "{{= mat.primary}}" }
              rect { position = absolute; x = 12px; y = 12px; width = 7px; height = 7px; radius = 2px; fill = "{{= mat.secondary}}" }
              text "{{mat.name}}" { position = absolute; x = 24px; y = 7px; width = "{{= max(0, boxWidth - 30)}}"; height = 10px; fontSize = 6.6px; fontFamily = studio-sans-medium; color = "{{= mat.selected ? vars.d_text : vars.d_muted}}" }
            }
          }
          text "{{i18n.cosmetics.weapon_shapes_title}}" { height = 10px; fontSize = 6.7px; fontFamily = studio-sans-medium; color = "{{= vars.d_muted}}" }
          repeat (source = "cosmeticWeapons", as = "model", key = "{{model.id}}", style = "nc-cape-grid") {
            button (id = "weapon-card-{{model.id}}", style = "nc-cape-card", action = "cosmetics-weapon-select:{{model.id}}") {
              height = 48px
              borderColor = "{{= model.selected ? vars.d_accent : vars.d_border}}"
              background = "{{= model.selected ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.045) : theme.mode=='light' ? '#FFFFFF' : '#242429') : vars.d_raised}}"
              rect { position = absolute; x = 4px; y = 4px; width = 34px; height = 40px; radius = "{{= vars.r_sm}}"; fill = "{{= model.selected ? alphaColor(vars.d_accent,.12) : vars.d_well}}" }
              cape-preview (source = "{{model.previewSource}}") { position = absolute; x = 5px; y = 4.5px; width = 32px; height = 39px }
              text "{{model.name}}" { position = absolute; x = 42px; y = 11px; width = "{{= max(0, boxWidth - 48)}}"; height = 11px; fontSize = 6.9px; fontFamily = studio-sans-medium }
              text "{{model.subtitle}}" { position = absolute; x = 42px; y = 25px; width = "{{= max(0, boxWidth - 48)}}"; height = 10px; fontSize = 5.6px; color = "{{= vars.d_muted}}" }
            }
          }
          panel (id = "cosmetics-weapon-options", style = "nc-card") { padding = "8px 9px"; gap = 6px
            text "{{i18n.cosmetics.weapon_target_title}}" { height = 10px; fontSize = 6.8px; fontFamily = studio-sans-medium }
            row { width = 100%; height = 21px; layout = row; gap = 2px; padding = 1.5px; background = "{{= vars.d_well}}"; borderRadius = "{{= vars.r_ctrl}}"; borderWidth = .5px; borderColor = "{{= vars.d_border}}"
              button "{{i18n.cosmetics.weapon_target.swords}}" (style = "nc-filter", action = "cosmetics-weapon-target:swords") { background = "{{= cosmetics.weaponTarget=='swords' ? vars.d_accent : '#00000000'}}"; color = "{{= cosmetics.weaponTarget=='swords' ? vars.d_on_accent : vars.d_muted}}" }
              button "{{i18n.cosmetics.weapon_target.weapons}}" (style = "nc-filter", action = "cosmetics-weapon-target:weapons") { background = "{{= cosmetics.weaponTarget=='weapons' ? vars.d_accent : '#00000000'}}"; color = "{{= cosmetics.weaponTarget=='weapons' ? vars.d_on_accent : vars.d_muted}}" }
              button "{{i18n.cosmetics.weapon_target.tools}}" (style = "nc-filter", action = "cosmetics-weapon-target:tools") { background = "{{= cosmetics.weaponTarget=='tools' ? vars.d_accent : '#00000000'}}"; color = "{{= cosmetics.weaponTarget=='tools' ? vars.d_on_accent : vars.d_muted}}" }
              button "{{i18n.cosmetics.weapon_target.all}}" (style = "nc-filter", action = "cosmetics-weapon-target:all") { background = "{{= cosmetics.weaponTarget=='all' ? vars.d_accent : '#00000000'}}"; color = "{{= cosmetics.weaponTarget=='all' ? vars.d_on_accent : vars.d_muted}}" }
            }
            slider "{{i18n.cosmetics.weapon_scale}}" (id = "cosmetics.weaponScale", bind = "cosmetics.weaponScale", min = "0.4", max = "2.0", step = "0.05")
            toggle "{{i18n.cosmetics.weapon_first_person}}" (id = "cosmetics.weaponFirstPerson", bind = "cosmetics.weaponFirstPerson", visual = "compact")
            toggle "{{i18n.cosmetics.weapon_third_person}}" (id = "cosmetics.weaponThirdPerson", bind = "cosmetics.weaponThirdPerson", visual = "compact")
            column (if = "cosmetics.weaponMode=='custom'") { width = 100%; layout = column; gap = 5px
              row { width = 100%; height = 20px; layout = row; align = center; gap = 5px
                button (style = "nc-swatch", action = "cosmetics-weapon-color:#38BDF8") { background = #38BDF8FF }
                button (style = "nc-swatch", action = "cosmetics-weapon-color:#EF4444") { background = #EF4444FF }
                button (style = "nc-swatch", action = "cosmetics-weapon-color:#22C55E") { background = #22C55EFF }
                button (style = "nc-swatch", action = "cosmetics-weapon-color:#A855F7") { background = #A855F7FF }
                button (style = "nc-swatch", action = "cosmetics-weapon-color:#FBBF24") { background = #FBBF24FF }
                button (style = "nc-swatch", action = "cosmetics-weapon-color:#EC4899") { background = #EC4899FF }
                button (style = "nc-swatch", action = "cosmetics-weapon-color:#F8FAFC") { background = #F8FAFCFF }
              }
              color "{{i18n.cosmetics.weapon_color}}" (id = "cosmetics.weaponColor", bind = "cosmetics.weaponColor")
            }
          }
          panel (id = "cosmetics-custom-weapon-card", style = "nc-card") { padding = "8px 9px"; gap = 5px
            row { width = 100%; height = 14px; layout = row; align = center; gap = 4px
              text "{{i18n.cosmetics.custom_weapon_title}}" { grow = 1; height = 11px; fontSize = 6.9px; fontFamily = studio-sans-medium }
              button "{{i18n.cosmetics.browse_model}}" (id = "cosmetics-weapon-browse-btn", style = "nc-btn nc-btn-primary", action = "cosmetics-weapon-browse") { grow = 0; height = 17px; padding = "0px 7px"; fontSize = 6.1px; borderRadius = "{{= vars.r_sm}}" }
            }
            text "{{i18n.cosmetics.custom_weapon_hint}}" (style = "nc-caption")
            row { width = 100%; height = 22px; layout = row; gap = 4px
              input (id = "cosmetics-weapon-path-input", bind = "ui.cosmeticsWeaponPathDraft", placeholder = "{{i18n.cosmetics.model_path_placeholder}}", maxlength = "512") { grow = 1; height = 22px; fontSize = 6.5px }
              button "{{i18n.ui.apply}}" (id = "cosmetics-weapon-path-btn", style = "nc-btn", action = "cosmetics-weapon-path") { grow = 0; height = 22px; padding = "0px 8px"; fontSize = 6.5px }
            }
          }
        }
      }
    }

    column (id = "waypoints-page", if = "page=waypoints", style = "nc-scroll") {
      column (if = "{{= ui.waypointManager!='create' && ui.waypointManager!='edit'}}") { width = 100%; gap = 7px
        panel (style = "nc-card") {
          toggle "{{i18n.module.waypoints.label}}" (bind = "waypoints.enabled")
          toggle "{{i18n.field.waypoints.hideNearby}}" (bind = "waypoints.hideNearby", visual = "compact")
          slider "{{i18n.field.waypoints.hideNearbyRadius}}" (if = "waypoints.hideNearby", bind = "waypoints.hideNearbyRadius", min = "1", max = "100", step = "1") { height = 32px }
          row (if = "waypointInWorld", style = "nc-wp-world-bar") {
            icon (src = "world") { width = 9px; height = 9px; color = "{{= vars.d_muted}}" }
            text "{{currentWorldLabel}}" { grow = 1; height = 100%; fontSize = 6.8px; color = "{{= vars.d_muted}}" }
            row (style = "nc-wp-scope-tabs") {
              button "{{i18n.waypoint.scope_world}}" (id = "waypoints-scope-world", style = "nc-wp-scope-tab", action = "sequence:ui-set:waypointScope:world;ui-set:waypointDeleteConfirm:false") { background = "{{= ui.waypointScope!='all' ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E') : '#00000000'}}"; color = "{{= ui.waypointScope!='all' ? vars.d_text : vars.d_muted}}" }
              button "{{i18n.waypoint.scope_all}}" (id = "waypoints-scope-all", style = "nc-wp-scope-tab", action = "sequence:ui-set:waypointScope:all;ui-set:waypointDeleteConfirm:false") { background = "{{= ui.waypointScope=='all' ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E') : '#00000000'}}"; color = "{{= ui.waypointScope=='all' ? vars.d_text : vars.d_muted}}" }
            }
          }
          row (style = "nc-actions") {
            button "{{i18n.ui.waypoint_add_here}}" (id = "waypoints-tab-here", style = "nc-btn", action = "waypoint-here")
            button "{{i18n.waypoint.create}}" (id = "waypoints-tab-create", style = "nc-btn nc-btn-primary", action = "waypoint-create-open")
            button "{{= ui.waypointDeleteConfirm ? i18n.waypoint.delete_all_confirm : i18n.waypoint.delete_all}}" (if = "!markersEmpty", id = "waypoints-tab-delete-all", style = "nc-btn", action = "waypoint-delete-all") { grow = 0; width = 96px; color = "{{= (theme.mode=='light' ? '#DC2626' : '#E55656')}}" }
          }
        }
        text "{{i18n.waypoint.empty}}" (if = "markersEmpty", style = "nc-caption") { height = 26px; textAlign = center }
        repeat (source = "savedWaypoints", as = "marker", key = "{{marker.id}}") { gap = 6px
          row (style = "nc-wp-row") {
            rect (style = "nc-wp-swatch") { fill = "{{= marker.color}}" }
            column (style = "nc-wp-copy") {
              text "{{marker.name}}" (style = "nc-wp-name")
              text "{{marker.meta}}" (style = "nc-wp-meta")
            }
            button (if = "marker.otherWorld", id = "waypoint-tab-bind-{{marker.id}}", style = "nc-wp-icon", action = "waypoint-bind-world:{{marker.id}}", tooltip = "{{i18n.waypoint.bind_current_world}}") { icon (src = "link") { width = 6.5px; height = 6.5px; color = "{{= vars.d_accent}}" } }
            button (style = "nc-wp-icon", action = "waypoint-visibility:{{marker.id}}", tooltip = "{{i18n.ui.visibility}}") { icon (src = "{{= marker.visible ? 'eye' : 'minus'}}") { width = 6.5px; height = 6.5px; color = "{{= marker.visible ? vars.d_text : vars.d_muted}}" } }
            button (id = "waypoint-tab-edit-{{marker.id}}", style = "nc-wp-icon", action = "waypoint-edit:{{marker.id}}", tooltip = "{{i18n.ui.rename}}") { icon (src = "edit") { width = 6.5px; height = 6.5px; color = "{{= vars.d_muted}}" } }
            button (style = "nc-wp-icon", action = "waypoint-remove:{{marker.id}}", tooltip = "{{i18n.ui.delete}}") { icon (src = "trash") { width = 6.5px; height = 6.5px; color = "{{= (theme.mode=='light' ? '#DC2626' : '#E55656')}}" } }
          }
        }
      }
      panel (id = "waypoints-tab-form", if = "{{= ui.waypointManager=='create' || ui.waypointManager=='edit'}}", style = "nc-card") { padding = 12px; gap = 9px
        row (style = "nc-modal-head") {
          icon (src = "anchor") { width = 9px; height = 9px; color = "{{= vars.d_accent}}" }
          text "{{= ui.waypointManager=='edit' ? i18n.waypoint.edit : i18n.waypoint.create}}" (style = "nc-modal-title")
        }
        column (style = "nc-wp-field") {
          text "{{i18n.waypoint.name_optional}}" (style = "nc-wp-label")
          input (id = "waypoints-tab-draft-name", bind = "ui.waypointDraftName", maxlength = "32", placeholder = "{{i18n.ui.waypoint_name_hint}}") { height = 22px }
        }
        row { height = 34px; width = 100%; gap = 4px
          column (style = "nc-wp-field") { grow = 1; text "X" (style = "nc-wp-label"); input (id = "waypoints-tab-draft-x", bind = "ui.waypointDraftX", maxlength = "20") { height = 22px } }
          column (style = "nc-wp-field") { grow = 1; text "Y" (style = "nc-wp-label"); input (id = "waypoints-tab-draft-y", bind = "ui.waypointDraftY", maxlength = "20") { height = 22px } }
          column (style = "nc-wp-field") { grow = 1; text "Z" (style = "nc-wp-label"); input (id = "waypoints-tab-draft-z", bind = "ui.waypointDraftZ", maxlength = "20") { height = 22px } }
        }
        column (style = "nc-wp-field", if = "ui.waypointManager=='edit'") {
          text "{{i18n.waypoint.dimension}}" (style = "nc-wp-label")
          text "{{ui.waypointDraftDimension}}" (style = "nc-caption") { height = 12px; fontSize = 7px }
        }
        row (style = "nc-wp-color-row") {
          text "{{i18n.waypoint.color}}" (style = "nc-wp-label") { grow = 1; height = 100% }
          rect (style = "nc-wp-color") { fill = "{{= ui.waypointDraftColor}}" }
          button "{{i18n.waypoint.random_color}}" (id = "waypoints-tab-shuffle", style = "nc-btn", action = "waypoint-shuffle-color") { grow = 0; width = 96px; height = 22px }
        }
        row (style = "nc-wp-footer") {
          button "{{i18n.ui.cancel}}" (id = "waypoints-tab-cancel", style = "nc-btn", action = "waypoint-cancel")
          button "{{i18n.ui.apply}}" (id = "waypoints-tab-apply", style = "nc-btn nc-btn-primary", action = "waypoint-apply")
        }
      }
    }

    column (id = "events-page", if = "page=events", style = "nc-scroll") {
      panel (style = "nc-card") { gap = 6px
        row { width = 100%; height = 22px; layout = row; align = center; gap = 6px
          icon (src = "events") { width = 10px; height = 10px; color = "{{= vars.d_accent}}" }
          text "{{hwEventsSummary}}" { grow = 1; height = 100%; fontSize = 7.2px; fontFamily = studio-sans-medium }
          button "{{i18n.hwEvents.hud_toggle}}" (id = "hw-events-hud-pill", style = "nc-pill", action = "toggle:hwhelper.events.showHud") { grow = 0; width = 46px; height = 20px; fontSize = 6.3px; borderColor = "{{= hwhelper.events.showHud ? vars.d_accent : vars.d_border}}"; background = "{{= hwhelper.events.showHud ? alphaColor(vars.d_accent,.14) : (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E')}}"; color = "{{= hwhelper.events.showHud ? vars.d_text : vars.d_muted}}" }
          button "{{i18n.hwEvents.refresh}}" (id = "hw-events-refresh-btn", style = "nc-btn nc-btn-primary", action = "hw-events-refresh") { grow = 0; width = 66px; height = 20px; fontSize = 6.5px }
        }
        row { width = 100%; height = 21px; layout = row; gap = 2px; padding = 1.5px; background = "{{= vars.d_well}}"; borderRadius = "{{= vars.r_ctrl}}"; borderWidth = .5px; borderColor = "{{= vars.d_border}}"
          button "{{i18n.hwEvents.mode.all}}" (style = "nc-filter", action = "set:hwhelper.events.modeFilter:all") { background = "{{= hwhelper.events.modeFilter=='all' ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E') : '#00000000'}}"; color = "{{= hwhelper.events.modeFilter=='all' ? vars.d_text : vars.d_muted}}" }
          button "{{i18n.hwEvents.mode.solo}}" (style = "nc-filter", action = "set:hwhelper.events.modeFilter:solo") { background = "{{= hwhelper.events.modeFilter=='solo' ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E') : '#00000000'}}"; color = "{{= hwhelper.events.modeFilter=='solo' ? vars.d_text : vars.d_muted}}" }
          button "{{i18n.hwEvents.mode.duo}}" (style = "nc-filter", action = "set:hwhelper.events.modeFilter:duo") { background = "{{= hwhelper.events.modeFilter=='duo' ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E') : '#00000000'}}"; color = "{{= hwhelper.events.modeFilter=='duo' ? vars.d_text : vars.d_muted}}" }
          button "{{i18n.hwEvents.mode.trio}}" (style = "nc-filter", action = "set:hwhelper.events.modeFilter:trio") { background = "{{= hwhelper.events.modeFilter=='trio' ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E') : '#00000000'}}"; color = "{{= hwhelper.events.modeFilter=='trio' ? vars.d_text : vars.d_muted}}" }
          button "{{i18n.hwEvents.mode.clan}}" (style = "nc-filter", action = "set:hwhelper.events.modeFilter:clan") { background = "{{= hwhelper.events.modeFilter=='clan' ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E') : '#00000000'}}"; color = "{{= hwhelper.events.modeFilter=='clan' ? vars.d_text : vars.d_muted}}" }
          button "{{i18n.hwEvents.mode.lite121}}" (style = "nc-filter", action = "set:hwhelper.events.modeFilter:lite121") { background = "{{= hwhelper.events.modeFilter=='lite121' ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E') : '#00000000'}}"; color = "{{= hwhelper.events.modeFilter=='lite121' ? vars.d_text : vars.d_muted}}" }
        }
        row { width = 100%; height = 20px; layout = row; gap = 4px
          button "{{i18n.hwEvents.rarity.all}}" (style = "nc-pill", action = "set:hwhelper.events.rarityFilter:all") { height = 19px; fontSize = 6.2px; borderColor = "{{= hwhelper.events.rarityFilter=='all' ? vars.d_accent : vars.d_border}}" }
          button "{{i18n.hwEvents.rarity.legendary}}" (style = "nc-pill", action = "set:hwhelper.events.rarityFilter:legendary") { height = 19px; fontSize = 6.2px; borderColor = "{{= hwhelper.events.rarityFilter=='legendary' ? '#FBBF24' : vars.d_border}}"; color = "{{= hwhelper.events.rarityFilter=='legendary' ? '#FBBF24' : vars.d_muted}}" }
          button "{{i18n.hwEvents.rarity.mythic}}" (style = "nc-pill", action = "set:hwhelper.events.rarityFilter:mythic") { height = 19px; fontSize = 6.2px; borderColor = "{{= hwhelper.events.rarityFilter=='mythic' ? '#E879F9' : vars.d_border}}"; color = "{{= hwhelper.events.rarityFilter=='mythic' ? '#E879F9' : vars.d_muted}}" }
          button "{{i18n.hwEvents.rarity.rare}}" (style = "nc-pill", action = "set:hwhelper.events.rarityFilter:rare") { height = 19px; fontSize = 6.2px; borderColor = "{{= hwhelper.events.rarityFilter=='rare' ? '#38BDF8' : vars.d_border}}"; color = "{{= hwhelper.events.rarityFilter=='rare' ? '#38BDF8' : vars.d_muted}}" }
        }
      }
      text "{{= hwEventsLoading ? i18n.hwEvents.loading : i18n.hwEvents.empty}}" (if = "hwEventsEmpty", style = "nc-caption") { height = 28px; textAlign = center }
      repeat (source = "hwEvents", as = "ev", key = "{{ev.id}}") { gap = 6px
        row (style = "nc-wp-row") { height = 42px
          rect (style = "nc-wp-swatch") { height = 24px; fill = "{{= ev.rarityColor}}" }
          column (style = "nc-wp-copy") { gap = 4px
            row { width = 100%; height = 11px; layout = row; align = center; gap = 5px
              text "{{ev.title}}" { height = 11px; fontSize = 7.6px; fontFamily = studio-sans-medium }
              text "{{ev.rarityBadge}}" { height = 11px; fontSize = 6px; fontFamily = studio-sans-medium; color = "{{= ev.rarityColor}}" }
            }
            text "{{ev.meta}}" (style = "nc-wp-meta") { height = 8px; fontSize = 6.3px }
          }
          button "{{= ev.canJoin ? i18n.hwEvents.join : '1.21'}}" (id = "hw-join-{{ev.id}}", style = "nc-btn", action = "hw-event-join:{{ev.id}}") { grow = 0; width = 62px; height = 22px; fontSize = 6.6px; borderWidth = "{{= ev.canJoin ? 0 : .55}}"; background = "{{= ev.canJoin ? vars.d_accent : (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E')}}"; color = "{{= ev.canJoin ? vars.d_on_accent : vars.d_muted}}" }
        }
      }
    }

    column (id = "settings-list", if = "{{= page=='settings' && ui.settingsSection=='appearance'}}", style = "nc-scroll") { gap = 10px
      panel (style = "nc-card") {
        text "{{i18n.ui.theme}}" { height = 11px; fontFamily = studio-sans-medium; fontSize = 7.6px; color = "{{= vars.d_text}}" }
        choice "" (id = "theme-mode", bind = "theme.mode", options = "dark|light|custom", option-labels = "{{i18n.theme.dark}}|{{i18n.theme.light}}|{{i18n.theme.custom}}", visual = "segmented") { height = 26px }
        column (if = "theme.mode=custom") { width = 100%; gap = 8px; padding = "4px 0px 0px 0px"
          color "{{i18n.field.customBackground}}" (id = "custom-background", bind = "theme.customBackground")
          color "{{i18n.field.customSurface}}" (id = "custom-surface", bind = "theme.customSurface")
          color "{{i18n.field.customRaised}}" (id = "custom-raised", bind = "theme.customRaised")
          color "{{i18n.field.customText}}" (id = "custom-text", bind = "theme.customText")
          color "{{i18n.field.accent}}" (id = "custom-accent", bind = "theme.customAccent")
        }
      }
      panel (style = "nc-card") { gap = 9px
        slider "{{i18n.field.radius}}" (id = "style-radius", bind = "theme.radius", min = "0", max = "16", step = "1")
        slider "{{i18n.ui.transparency}}" (id = "style-transparency", bind = "theme.backgroundOpacity", min = "0", max = "80", step = "1", value-scale = "-100", value-offset = "100")
        toggle "{{i18n.field.grain}}" (id = "style-grain", bind = "theme.effects.grain")
        slider "{{i18n.field.grainAmount}}" (id = "style-grain-amount", if = "theme.effects.grain", bind = "theme.effects.grainAmount", min = "0", max = ".15", step = ".005")
        slider "{{i18n.field.grainScale}}" (id = "style-grain-scale", if = "theme.effects.grain", bind = "theme.effects.grainScale", min = ".5", max = "4", step = ".1")
        toggle "{{i18n.field.backgroundBlur}}" (id = "glass-blur", bind = "theme.effects.backgroundBlur")
        slider "{{i18n.field.blurRadius}}" (id = "glass-radius", if = "theme.effects.backgroundBlur", bind = "theme.blurRadius", min = "0", max = "32", step = "1")
        column { width = 100%; gap = 4px
          text "{{i18n.field.numericMotion}}" { height = 10px; fontSize = 6.9px; color = "{{= (theme.mode=='custom' ? lerpColor(theme.text,theme.surface,.3) : theme.mode=='light' ? '#52525B' : '#A3A3AA')}}" }
          choice "" (id = "style-numeric-motion", bind = "theme.motion.numericMotion", options = "off|roll|fade|slide", option-labels = "{{i18n.numericMotion.off}}|{{i18n.numericMotion.roll}}|{{i18n.numericMotion.fade}}|{{i18n.numericMotion.slide}}", visual = "segmented") { height = 24px }
        }
      }
      row { width = 100%; height = 24px; gap = 6px; justify = flex-end
        button "{{i18n.ui.reset_appearance}}" (id = "style-reset", action = "theme-reset", style = "nc-btn") { grow = 0; width = 140px; height = 24px }
      }
    }

    column (id = "interface-list", if = "{{= page=='settings' && ui.settingsSection=='interface'}}", style = "nc-scroll") { gap = 10px
      panel (style = "nc-card") {
        choice "" (id = "language", bind = "language", options = "en|ru", option-labels = "English|Русский", visual = "segmented") { height = 26px }
        button (id = "binding-menu", action = "bind-key:menu", style = "nc-binding") {
          text "{{i18n.ui.menu}}" { height = 100%; grow = 1; padding = 0px; fontSize = 7.6px; fontFamily = studio-sans-medium }
          text "{{menuBindingLabel}}" (style = "nc-menu-key")
        }
        text "{{i18n.ui.escape_cancels_delete_unbinds}}" (if = "bindingCapture", style = "nc-caption")
      }
      panel (style = "nc-card") {
        repeat (source = "menuControls", as = "control", key = "{{control.bind}}", style = "nc-controls") {
          toggle "{{control.label}}" (id = "{{control.bind}}", if = "control.kind=toggle", bind = "{{control.bind}}")
          slider "{{control.label}}" (id = "{{control.bind}}", if = "control.kind=slider", bind = "{{control.bind}}", min = "{{control.min}}", max = "{{control.max}}", step = "{{control.step}}")
          column (if = "control.kind=input") { width = 100%; gap = 3px; text "{{control.label}}" (style = "nc-caption"); input (id = "{{control.bind}}", bind = "{{control.bind}}", maxlength = "128") }
          color "{{control.label}}" (id = "{{control.bind}}", if = "control.kind=color", bind = "{{control.bind}}")
          choice "{{control.label}}" (id = "{{control.bind}}", if = "control.kind=choice", bind = "{{control.bind}}", options = "{{control.options}}", option-labels = "{{control.optionLabels}}")
        }
      }
      panel (style = "nc-card") {
        button "{{i18n.ui.interface_scripts}}" (id = "theme-file-settings", action = "ui-toggle:scriptsOpen", style = "nc-btn") { grow = 0; width = 100% }
        column (if = "ui.scriptsOpen") { width = 100%; gap = 8px; padding = "4px 0px 0px 0px"
          toggle "{{i18n.ui.custom_nc_file}}" (bind = "skin.enabled")
          input (bind = "skin.file", maxlength = "68")
          row (style = "nc-actions") {
            button "{{i18n.ui.reload_nc}}" (style = "nc-btn", action = "reload-skin")
            button "{{i18n.ui.export_nc}}" (style = "nc-btn", action = "create-examples")
          }
          text "{{skinStatus}}" (style = "nc-caption") { height = 20px; textOverflow = wrap }
        }
      }
    }

    column (id = "profiles-list", if = "{{= page=='settings' && ui.settingsSection=='profiles'}}", style = "nc-scroll") { gap = 8px
      row (style = "nc-actions") {
        button "{{i18n.ui.save}}" (id = "create-profile", action = "ui-toggle:profileCreateOpen", style = "nc-btn nc-btn-primary")
        button (id = "copy-profile", action = "profile-export", style = "nc-icon-btn", tooltip = "{{i18n.ui.copy_profile}}") { width = 32px; icon (src = "copy") { width = 10px; height = 10px; color = "{{= vars.d_text}}" } }
        button (id = "paste-profile", action = "profile-import", style = "nc-icon-btn", tooltip = "{{i18n.ui.paste_profile}}") { width = 32px; icon (src = "paste") { width = 10px; height = 10px; color = "{{= vars.d_text}}" } }
      }
      text "{{i18n.ui.no_profiles}}" (if = "profilesEmpty", style = "nc-caption") { height = 30px; textAlign = center }
      repeat (source = "profiles", as = "profile", key = "{{profile.name}}") { gap = 7px
        panel {
          layout = absolute; width = 100%; height = "{{= vars.m_sheet_w<230 ? 62 : 38}}"; padding = 0px
          background = "{{= vars.d_raised}}"; borderRadius = "{{= vars.r_card}}"; borderWidth = .55px; borderColor = "{{= vars.d_border}}"
          icon (src = "layers") { position = absolute; x = 10px; y = 13px; width = 12px; height = 12px; color = "{{= vars.d_muted}}" }
          text "{{profile.name}}" { position = absolute; x = 30px; y = 12px; width = "{{= max(0,boxWidth-(vars.m_sheet_w<230 ? 42 : 140))}}"; height = 13px; fontSize = 7.8px; fontFamily = studio-sans-medium }
          button "{{i18n.ui.load}}" (id = "load-profile.{{profile.name}}", action = "profile-load:{{profile.name}}", style = "nc-btn") { position = absolute; right = 40px; y = "{{= vars.m_sheet_w<230 ? 34 : 7}}"; width = "{{= vars.m_sheet_w<230 ? max(0,boxWidth-50) : 68}}"; grow = 0; height = 24px }
          button (action = "profile-delete:{{profile.name}}", style = "nc-icon-btn", tooltip = "{{i18n.ui.delete}}") { position = absolute; right = 10px; y = "{{= vars.m_sheet_w<230 ? 34 : 7}}"; width = 24px; height = 24px; background = #00000000; borderWidth = 0px; icon (src = "trash") { width = 9px; height = 9px; color = "{{= (theme.mode=='light' ? '#DC2626' : '#E55656')}}" } }
        }
      }
      row (if = "profilePending", style = "nc-actions") {
        text "{{ui.deleteProfile}}" (style = "nc-caption") { grow = 1 }
        button "{{i18n.ui.delete}}" (action = "profile-delete-confirm", style = "nc-btn") { color = "{{= (theme.mode=='light' ? '#DC2626' : '#E55656')}}" }
        button "{{i18n.ui.cancel}}" (action = "profile-delete-cancel", style = "nc-btn")
      }
    }
  }

  panel (id = "create-profile-modal", if = "ui.profileCreateOpen", dismiss-action = "ui-set:profileCreateOpen:false", style = "nc-modal") {
    row (style = "nc-modal-head") {
      text "{{i18n.ui.save}}" (style = "nc-modal-title")
      button (action = "ui-set:profileCreateOpen:false", style = "nc-wp-close") { icon (src = "close") { width = 8px; height = 8px; color = "{{= vars.d_muted}}" } }
    }
    input (id = "profile-name", bind = "ui.profileName", placeholder = "{{i18n.ui.enter_a_profile_name}}", maxlength = "32")
    button "{{i18n.ui.save}}" (id = "save-profile", style = "nc-btn nc-btn-primary", action = "sequence:profile-save;ui-set:profileCreateOpen:false") { width = 100%; grow = 0 }
  }

  button (id = "waypoint-scrim", if = "{{= ui.waypointManager!='' && (page!='waypoints' || waypointStandalone)}}", action = "waypoint-manager-close", style = "nc-scrim")
  panel (id = "waypoint-manager", if = "{{= ui.waypointManager!='' && (page!='waypoints' || waypointStandalone)}}", style = "nc-wp-modal") {
    width = "{{= min(344,vars.m_w-16)}}"; height = "{{= min(ui.waypointManager=='list' ? 290 : 246,vars.m_h-16)}}"
    button (style = "nc-scrim", action = "waypoint-manager-noop") { position = absolute; x = 0px; y = 0px; width = 100%; height = 100%; zIndex = 0; background = #00000000 }
    column { position = relative; zIndex = 1; layout = column; width = 100%; height = 100%; gap = 6px
      row (style = "nc-modal-head") {
        icon (src = "nightclient-mark") { width = 8px; height = 8px; color = "{{= vars.d_text}}" }
        text "{{= ui.waypointManager=='list' ? i18n.waypoint.manager : ui.waypointManager=='edit' ? i18n.waypoint.edit : i18n.waypoint.create}}" (style = "nc-modal-title")
        button (id = "waypoint-manager-close", style = "nc-wp-close", action = "waypoint-manager-close", tooltip = "{{i18n.ui.close}}") { icon (src = "close") { width = 6.5px; height = 6.5px; color = "{{= vars.d_muted}}" } }
      }
      row (if = "{{= ui.waypointManager=='list' && waypointInWorld}}", style = "nc-wp-world-bar") {
        icon (src = "world") { width = 8px; height = 8px; color = "{{= vars.d_muted}}" }
        text "{{currentWorldLabel}}" { grow = 1; height = 100%; fontSize = 6.4px; color = "{{= vars.d_muted}}" }
        row (style = "nc-wp-scope-tabs") {
          button "{{i18n.waypoint.scope_world}}" (id = "waypoint-manager-scope-world", style = "nc-wp-scope-tab", action = "sequence:ui-set:waypointScope:world;ui-set:waypointDeleteConfirm:false") { background = "{{= ui.waypointScope!='all' ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E') : '#00000000'}}"; color = "{{= ui.waypointScope!='all' ? vars.d_text : vars.d_muted}}" }
          button "{{i18n.waypoint.scope_all}}" (id = "waypoint-manager-scope-all", style = "nc-wp-scope-tab", action = "sequence:ui-set:waypointScope:all;ui-set:waypointDeleteConfirm:false") { background = "{{= ui.waypointScope=='all' ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E') : '#00000000'}}"; color = "{{= ui.waypointScope=='all' ? vars.d_text : vars.d_muted}}" }
        }
      }
      column (if = "ui.waypointManager=='list'", style = "nc-wp-scroll") {
        text "{{i18n.waypoint.empty}}" (if = "markersEmpty", style = "nc-caption")
        repeat (source = "savedWaypoints", as = "marker", key = "{{marker.id}}") {
          row (style = "nc-wp-row") {
            rect (style = "nc-wp-swatch") { fill = "{{= marker.color}}" }
            column (style = "nc-wp-copy") {
              text "{{marker.name}}" (style = "nc-wp-name")
              text "{{marker.meta}}" (style = "nc-wp-meta")
            }
            button (if = "marker.otherWorld", id = "waypoint-manager-bind-{{marker.id}}", style = "nc-wp-icon", action = "waypoint-bind-world:{{marker.id}}", tooltip = "{{i18n.waypoint.bind_current_world}}") { icon (src = "link") { width = 6px; height = 6px; color = "{{= vars.d_accent}}" } }
            button (style = "nc-wp-icon", action = "waypoint-visibility:{{marker.id}}", tooltip = "{{i18n.ui.visibility}}") { icon (src = "{{= marker.visible ? 'eye' : 'minus'}}") { width = 6px; height = 6px; color = "{{= marker.visible ? vars.d_text : vars.d_muted}}" } }
            button (id = "waypoint-manager-edit-{{marker.id}}", style = "nc-wp-icon", action = "waypoint-edit:{{marker.id}}", tooltip = "{{i18n.ui.rename}}") { icon (src = "edit") { width = 6px; height = 6px; color = "{{= vars.d_muted}}" } }
            button (style = "nc-wp-icon", action = "waypoint-remove:{{marker.id}}", tooltip = "{{i18n.ui.delete}}") { icon (src = "trash") { width = 6px; height = 6px; color = "{{= (theme.mode=='light' ? '#DC2626' : '#E55656')}}" } }
          }
        }
      }
      column (if = "{{= ui.waypointManager=='create' || ui.waypointManager=='edit'}}", style = "nc-wp-form") {
        column (style = "nc-wp-field") {
          text "{{i18n.waypoint.name_optional}}" (style = "nc-wp-label")
          input (id = "waypoint-draft-name", bind = "ui.waypointDraftName", maxlength = "32", placeholder = "{{i18n.ui.waypoint_name_hint}}") { height = 22px }
        }
        row { height = 34px; width = 100%; gap = 4px
          column (style = "nc-wp-field") { grow = 1; text "X" (style = "nc-wp-label"); input (id = "waypoint-draft-x", bind = "ui.waypointDraftX", maxlength = "20") { height = 22px } }
          column (style = "nc-wp-field") { grow = 1; text "Y" (style = "nc-wp-label"); input (id = "waypoint-draft-y", bind = "ui.waypointDraftY", maxlength = "20") { height = 22px } }
          column (style = "nc-wp-field") { grow = 1; text "Z" (style = "nc-wp-label"); input (id = "waypoint-draft-z", bind = "ui.waypointDraftZ", maxlength = "20") { height = 22px } }
        }
        column (style = "nc-wp-field", if = "ui.waypointManager=='edit'") {
          text "{{i18n.waypoint.dimension}}" (style = "nc-wp-label")
          text "{{ui.waypointDraftDimension}}" (style = "nc-caption") { height = 12px; fontSize = 7px }
        }
        row (style = "nc-wp-color-row") {
          text "{{i18n.waypoint.color}}" (style = "nc-wp-label") { grow = 1; height = 100% }
          rect (style = "nc-wp-color") { fill = "{{= ui.waypointDraftColor}}" }
          button "{{i18n.waypoint.random_color}}" (id = "waypoint-shuffle", style = "nc-btn", action = "waypoint-shuffle-color") { grow = 0; width = 96px; height = 22px }
        }
      }
      row (style = "nc-wp-footer") {
        button "{{i18n.waypoint.create}}" (if = "ui.waypointManager=='list'", id = "waypoint-create", style = "nc-btn nc-btn-primary", action = "waypoint-create-open")
        button "{{= ui.waypointDeleteConfirm ? i18n.waypoint.delete_all_confirm : i18n.waypoint.delete_all}}" (if = "ui.waypointManager=='list'", id = "waypoint-delete-all", style = "nc-btn", action = "waypoint-delete-all") { color = "{{= (theme.mode=='light' ? '#DC2626' : '#E55656')}}" }
        button "{{i18n.ui.cancel}}" (if = "{{= ui.waypointManager=='create' || ui.waypointManager=='edit'}}", id = "waypoint-cancel", style = "nc-btn", action = "waypoint-cancel")
        button "{{i18n.ui.apply}}" (if = "{{= ui.waypointManager=='create' || ui.waypointManager=='edit'}}", id = "waypoint-apply", style = "nc-btn nc-btn-primary", action = "waypoint-apply")
      }
    }
  }

  button (id = "account-scrim", if = "{{= ui.accountManager!=''}}", action = "account-manager-close", style = "nc-acc-scrim")
  panel (id = "account-manager", if = "{{= ui.accountManager!=''}}", style = "nc-acc-modal") {
    width = "{{= min(ui.accountManager!='list' ? 324 : accountSwitcher.layout=='classic' ? 364 : 468, viewportWidth - 20)}}"
    height = "{{= min(ui.accountManager!='list' ? 242 : accountSwitcher.layout=='classic' ? 298 : 310, viewportHeight - 20)}}"
    button (style = "nc-acc-scrim", action = "account-manager-noop") { position = absolute; x = 0px; y = 0px; width = 100%; height = 100%; zIndex = 0; background = #00000000 }
    column { position = relative; zIndex = 1; layout = column; width = 100%; height = 100%; gap = 7px
      row (style = "nc-modal-head") {
        icon (src = "user") { width = 9px; height = 9px; color = "{{= vars.d_accent}}" }
        text "{{= ui.accountManager=='create' ? i18n.accounts.create_title : ui.accountManager=='edit' ? i18n.accounts.edit_title : i18n.accounts.title}}" (style = "nc-modal-title")
        row (if = "ui.accountManager=='list'", style = "nc-acc-mode-tabs") {
          button "{{i18n.accounts.mode_modern}}" (id = "account-mode-modern", style = "nc-acc-mode-tab", action = "account-layout-modern", tooltip = "{{i18n.accounts.mode_tooltip}}") { background = "{{= accountSwitcher.layout!='classic' ? vars.d_accent : '#00000000'}}"; color = "{{= accountSwitcher.layout!='classic' ? vars.d_on_accent : vars.d_muted}}" }
          button "{{i18n.accounts.mode_classic}}" (id = "account-mode-classic", style = "nc-acc-mode-tab", action = "account-layout-classic", tooltip = "{{i18n.accounts.mode_tooltip}}") { background = "{{= accountSwitcher.layout=='classic' ? vars.d_accent : '#00000000'}}"; color = "{{= accountSwitcher.layout=='classic' ? vars.d_on_accent : vars.d_muted}}" }
        }
        button (id = "account-manager-close", style = "nc-wp-close", action = "account-manager-close", tooltip = "{{i18n.ui.close}}") { icon (src = "close") { width = 6.5px; height = 6.5px; color = "{{= vars.d_muted}}" } }
      }
      row (if = "{{= accountError!=''}}") { width = 100%; height = 17px; padding = "0px 8px"; background = "#E5565624"; borderRadius = "{{= vars.r_sm}}"; align = center; text "{{accountError}}" { width = 100%; height = 10px; fontSize = 6.3px; color = "{{= (theme.mode=='light' ? '#DC2626' : '#E55656')}}" } }
      row (id = "account-modern-body", if = "{{= ui.accountManager=='list' && accountSwitcher.layout!='classic'}}") { width = 100%; height = 0px; grow = 1; gap = 9px
        column (id = "account-modern-stage", style = "nc-acc-stage") {
          row { width = 100%; height = 15px; align = center; gap = 5px
            circle { width = 6px; height = 6px; fill = "{{= accountSelectedIsLauncher ? (theme.mode=='light' ? '#16A34A' : '#4CC38A') : (theme.mode=='light' ? '#D97706' : '#E5B63C')}}" }
            text "{{= accountSelectedIsLauncher ? i18n.accounts.launcher_badge : i18n.accounts.offline_badge}}" { grow = 1; height = 10px; fontSize = 6.3px; fontFamily = studio-sans-medium; color = "{{= accountSelectedIsLauncher ? (theme.mode=='light' ? '#16A34A' : '#4CC38A') : (theme.mode=='light' ? '#D97706' : '#E5B63C')}}" }
            text "{{i18n.accounts.active_badge}}" (if = "accountSelectedActive", style = "nc-acc-pill") { background = "{{= alphaColor(vars.d_accent,.14)}}"; color = "{{= vars.d_text}}" }
          }
          player-preview (id = "account-3d-preview", source = "{{accountSelectedSkin}}") { width = 100%; height = 0px; grow = 1 }
          text "{{accountSelectedName}}" (id = "account-stage-name") { width = 100%; height = 12px; fontSize = 8.4px; fontFamily = studio-sans-medium; textAlign = center }
          text "{{accountSelectedMeta}}" { width = 100%; height = 10px; fontSize = 5.9px; color = "{{= vars.d_muted}}"; textAlign = center }
          button "{{= accountSelectedIsLauncher ? i18n.accounts.restore_btn : i18n.accounts.switch_btn}}" (id = "account-modern-switch", style = "nc-btn nc-btn-primary", action = "account-switch-selected") { width = 100%; height = 22px; grow = 0; fontSize = 6.8px }
          button "{{i18n.accounts.reconnect_btn}}" (if = "accountCanReconnect", id = "account-modern-reconnect", style = "nc-btn", action = "account-reconnect", tooltip = "{{accountServerLabel}}") { width = 100%; height = 20px; grow = 0; fontSize = 6.5px }
        }
        column (id = "account-modern-list-col") { grow = 1; height = 100%; gap = 6px
          row { width = 100%; height = 24px; gap = 4px; align = center
            input (id = "account-search", bind = "ui.accountSearch", placeholder = "{{i18n.accounts.search_placeholder}}", maxlength = "16") { grow = 1; height = 24px; fontSize = 7px }
            button "{{i18n.accounts.quick_switch}}" (id = "account-quick-btn", style = "nc-btn", action = "account-quick-switch") { grow = 0; width = 44px; height = 24px; fontSize = 6.5px }
            button (id = "account-random-btn", style = "nc-icon-btn", action = "account-random-nick", tooltip = "{{i18n.accounts.random_btn}}") { icon (src = "reset") { width = 9px; height = 9px; color = "{{= vars.d_muted}}" } }
            button (id = "account-add-btn", style = "nc-icon-btn", action = "account-create-open", tooltip = "{{i18n.accounts.add_btn}}") { background = "{{= alphaColor(vars.d_accent,.12)}}"; borderColor = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}"; icon (src = "plus") { width = 9px; height = 9px; color = "{{= vars.d_text}}" } }
          }
          button (id = "account-launcher-card", style = "nc-acc-row", action = "account-launcher-select", secondary-action = "account-launcher-restore") {
            borderColor = "{{= accountSelectedIsLauncher ? vars.d_accent : accountIsLauncher ? (theme.mode=='light' ? '#16A34A' : '#4CC38A') : vars.d_border}}"
            background = "{{= accountSelectedIsLauncher ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.045) : theme.mode=='light' ? '#FFFFFF' : '#242429') : vars.d_raised}}"
            panel (style = "nc-acc-face-frame") { account-face (source = "{{accountLauncherSkin}}", style = "nc-acc-face") }
            column (style = "nc-wp-copy") { text "{{accountLauncherName}}" (style = "nc-wp-name"); text "{{i18n.accounts.launcher_title}}" (style = "nc-wp-meta") }
            text "{{= accountIsLauncher ? i18n.accounts.active_badge : i18n.accounts.launcher_badge}}" (style = "nc-acc-pill") { background = "{{= alphaColor((theme.mode=='light' ? '#16A34A' : '#4CC38A'),.16)}}"; color = "{{= (theme.mode=='light' ? '#16A34A' : '#4CC38A')}}" }
            button (if = "!accountIsLauncher", id = "account-launcher-restore-btn", style = "nc-acc-icon", action = "account-launcher-restore", tooltip = "{{i18n.accounts.restore_btn}}") { icon (src = "check") { width = 7px; height = 7px; color = "{{= (theme.mode=='light' ? '#16A34A' : '#4CC38A')}}" } }
          }
          column (id = "account-scroll-modern", style = "nc-wp-scroll") {
            text "{{i18n.accounts.empty}}" (if = "accountsEmpty", style = "nc-caption") { width = 100%; height = 26px; textAlign = center }
            text "{{i18n.accounts.empty_search}}" (if = "accountsSearchEmpty", style = "nc-caption") { width = 100%; height = 22px; textAlign = center }
            repeat (source = "savedAccounts", as = "account", key = "{{account.id}}") {
              button (id = "account-row-{{account.id}}", style = "nc-acc-row", action = "account-select:{{account.id}}", secondary-action = "account-switch:{{account.id}}") {
                borderColor = "{{= account.selected ? vars.d_accent : account.active ? (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24') : vars.d_border}}"
                background = "{{= account.selected ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.045) : theme.mode=='light' ? '#FFFFFF' : '#242429') : vars.d_raised}}"
                panel (style = "nc-acc-face-frame") { account-face (source = "{{account.skinSource}}", style = "nc-acc-face") }
                column (style = "nc-wp-copy") { text "{{account.displayName}}" (style = "nc-wp-name") { color = "{{= account.active ? vars.d_text : (theme.mode=='custom' ? lerpColor(theme.text,theme.surface,.3) : theme.mode=='light' ? '#52525B' : '#A3A3AA')}}" }; text "{{account.meta}}" (style = "nc-wp-meta") }
                text "{{i18n.accounts.active_badge}}" (if = "account.active", style = "nc-acc-pill") { background = "{{= alphaColor(vars.d_accent,.14)}}"; color = "{{= vars.d_text}}" }
                button (if = "!account.active", id = "account-use-{{account.id}}", style = "nc-acc-icon", action = "account-switch:{{account.id}}", tooltip = "{{i18n.accounts.switch_btn}}") { icon (src = "check") { width = 7px; height = 7px; color = "{{= vars.d_text}}" } }
                button (id = "account-pin-{{account.id}}", style = "nc-acc-icon", action = "account-pin:{{account.id}}", tooltip = "{{= account.pinned ? i18n.accounts.unpin_btn : i18n.accounts.pin_btn}}") { icon (src = "anchor") { width = 7px; height = 7px; color = "{{= account.pinned ? vars.d_text : vars.d_muted}}" } }
                button (id = "account-edit-{{account.id}}", style = "nc-acc-icon", action = "account-edit:{{account.id}}", tooltip = "{{i18n.accounts.edit_btn}}") { icon (src = "edit") { width = 7px; height = 7px; color = "{{= vars.d_muted}}" } }
                button (id = "account-del-{{account.id}}", style = "nc-acc-icon", action = "account-remove:{{account.id}}", tooltip = "{{i18n.accounts.delete_btn}}") { icon (src = "trash") { width = 7px; height = 7px; color = "{{= (theme.mode=='light' ? '#DC2626' : '#E55656')}}" } }
              }
            }
          }
        }
      }
      column (id = "account-classic-body", if = "{{= ui.accountManager=='list' && accountSwitcher.layout=='classic'}}") { width = 100%; height = 0px; grow = 1; gap = 6px
        row { width = 100%; height = 24px; gap = 4px; align = center
          input (id = "account-search-classic", bind = "ui.accountSearch", placeholder = "{{i18n.accounts.search_placeholder}}", maxlength = "16") { grow = 1; height = 24px; fontSize = 7px }
          button "{{i18n.accounts.quick_switch}}" (id = "account-quick-classic", style = "nc-btn", action = "account-quick-switch") { grow = 0; width = 44px; height = 24px; fontSize = 6.5px }
        }
        column (id = "account-scroll-classic", style = "nc-wp-scroll") {
          button (id = "account-launcher-classic", style = "nc-acc-row", action = "account-launcher-select", secondary-action = "account-launcher-restore") {
            borderColor = "{{= accountSelectedIsLauncher ? vars.d_accent : vars.d_border}}"
            background = "{{= accountSelectedIsLauncher ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.045) : theme.mode=='light' ? '#FFFFFF' : '#242429') : vars.d_raised}}"
            panel (style = "nc-acc-face-frame") { account-face (source = "{{accountLauncherSkin}}", style = "nc-acc-face") }
            column (style = "nc-wp-copy") { text "{{accountLauncherName}}" (style = "nc-wp-name"); text "{{i18n.accounts.launcher_title}}" (style = "nc-wp-meta") }
            text "{{= accountIsLauncher ? i18n.accounts.active_badge : i18n.accounts.launcher_badge}}" (style = "nc-acc-pill") { background = "{{= alphaColor((theme.mode=='light' ? '#16A34A' : '#4CC38A'),.16)}}"; color = "{{= (theme.mode=='light' ? '#16A34A' : '#4CC38A')}}" }
          }
          repeat (source = "savedAccounts", as = "account", key = "{{account.id}}") {
            button (id = "account-classic-row-{{account.id}}", style = "nc-acc-row", action = "account-select:{{account.id}}", secondary-action = "account-switch:{{account.id}}") {
              borderColor = "{{= account.selected ? vars.d_accent : vars.d_border}}"
              background = "{{= account.selected ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.045) : theme.mode=='light' ? '#FFFFFF' : '#242429') : vars.d_raised}}"
              panel (style = "nc-acc-face-frame") { account-face (source = "{{account.skinSource}}", style = "nc-acc-face") }
              column (style = "nc-wp-copy") { text "{{account.displayName}}" (style = "nc-wp-name"); text "{{account.meta}}" (style = "nc-wp-meta") }
              text "{{i18n.accounts.active_badge}}" (if = "account.active", style = "nc-acc-pill") { background = "{{= alphaColor(vars.d_accent,.14)}}"; color = "{{= vars.d_text}}" }
            }
          }
        }
        row (style = "nc-wp-footer") { gap = 4px
          button "{{= accountSelectedIsLauncher ? i18n.accounts.restore_btn : i18n.accounts.switch_btn}}" (id = "account-classic-switch", style = "nc-btn nc-btn-primary", action = "account-switch-selected") { height = 23px; fontSize = 6.7px }
          button "{{i18n.accounts.add_btn}}" (id = "account-classic-add", style = "nc-btn", action = "account-create-open") { height = 23px; fontSize = 6.7px }
          button "{{i18n.accounts.edit_btn}}" (if = "!accountSelectedIsLauncher", id = "account-classic-edit", style = "nc-btn", action = "account-edit-selected") { height = 23px; fontSize = 6.7px }
          button "{{i18n.accounts.delete_btn}}" (if = "!accountSelectedIsLauncher", id = "account-classic-delete", style = "nc-btn", action = "account-delete-selected") { height = 23px; fontSize = 6.7px; color = "{{= (theme.mode=='light' ? '#DC2626' : '#E55656')}}" }
        }
      }
      column (id = "account-form-body", if = "{{= ui.accountManager=='create' || ui.accountManager=='edit'}}", style = "nc-wp-form") {
        column (style = "nc-wp-field") {
          text "{{i18n.accounts.name_label}}" (style = "nc-wp-label")
          row { width = 100%; height = 22px; gap = 4px; align = center
            input (id = "account-draft-name", bind = "ui.accountDraftName", maxlength = "16", placeholder = "{{i18n.accounts.name_placeholder}}") { grow = 1; height = 22px }
            button "{{i18n.accounts.random_btn}}" (id = "account-draft-random", style = "nc-btn", action = "account-random-nick") { grow = 0; width = 68px; height = 22px; fontSize = 6.4px }
          }
        }
        column (style = "nc-wp-field") { text "{{i18n.accounts.note_label}}" (style = "nc-wp-label"); input (id = "account-draft-note", bind = "ui.accountDraftNote", maxlength = "32", placeholder = "{{i18n.accounts.note_placeholder}}") { height = 22px } }
        column (style = "nc-wp-field") { text "{{i18n.accounts.skin_label}}" (style = "nc-wp-label"); input (id = "account-draft-skin", bind = "ui.accountDraftSkin", maxlength = "16", placeholder = "{{i18n.accounts.skin_placeholder}}") { height = 22px } }
      }
      row (if = "{{= ui.accountManager=='create' || ui.accountManager=='edit'}}", style = "nc-wp-footer") {
        button "{{i18n.accounts.cancel_btn}}" (id = "account-form-cancel", style = "nc-btn", action = "account-cancel")
        button "{{i18n.accounts.save_btn}}" (id = "account-form-apply", style = "nc-btn nc-btn-primary", action = "account-apply")
      }
    }
  }
}
