defaults {
  themeName = "NightClient"
  background = #101216
  surface = #171A20
  raised = #22262F
  color = #F4F6FB
  muted = #A0A7B3
  accent = #F4F6FB
  secondaryAccent = #F4F6FB
  borderColor = #FFFFFF20
  borderRadius = 8px
  borderWidth = .6px
  backgroundOpacity = 94%
  surfaceOpacity = 100%
  raisedOpacity = 100%
  tintOpacity = -1
  blur = true
  blurRadius = 14px
  blurQuality = 3
  blurMix = 100%
  glassMix = -1
  accentGradient = 0
  fontSize = 8px
  fontTracking = 0px
  fontWeight = regular
  fontFamily = studio-sans
  grain = false
  grainAmount = .012
  grainScale = 1
  transitionDuration = 170ms
  enterDuration = 210ms
  exitDuration = 140ms
  easing = "cubic-bezier(0.22,1,0.36,1)"
  enterScale = .985
  hoverScale = 1
  pressScale = .98
  enterOpacity = 0%
  enterOffsetX = 0px
  enterOffsetY = 6px
  animateLayout = true
  animateValue = true
  animationStagger = 14ms
}

theme {
  let tnt-header-height = 28
  let tnt-row-height = 18
  let d-bg = "theme.mode=='custom' ? theme.background : theme.mode=='light' ? '#EFEFED' : '#101012'"
  let d-surface = "(theme.mode=='custom' || (setAlphaColor(theme.surface,1)!=setAlphaColor('#171A20',1) && setAlphaColor(theme.surface,1)!=setAlphaColor('#FFFFFF',1))) ? theme.surface : theme.mode=='light' ? '#FFFFFF' : '#17171A'"
  let d-raised = "theme.mode=='custom' ? theme.raised : theme.mode=='light' ? '#F9F9F7' : '#1E1E22'"
  let d-well = "theme.mode=='custom' ? lerpColor(theme.background,theme.surface,.35) : theme.mode=='light' ? '#E8E8E4' : '#121214'"
  let d-text = "theme.mode=='custom' ? theme.text : theme.mode=='light' ? '#18181B' : '#F2F2EF'"
  let d-muted = "theme.mode=='custom' ? theme.muted : theme.mode=='light' ? '#71717A' : '#7C7C85'"
  let d-border = "theme.mode=='custom' ? alphaColor(theme.text,.1) : theme.mode=='light' ? '#18181B16' : '#FFFFFF14'"
  let d-accent = "theme.mode=='custom' ? theme.accent : theme.mode=='light' ? '#18181B' : '#F2F2EF'"
  let d-on-accent = "theme.mode=='custom' ? (theme.accent==theme.text ? theme.background : '#FFFFFF') : theme.mode=='light' ? '#FAFAFA' : '#111113'"
  let h-surface = "(theme.mode=='custom' || (setAlphaColor(theme.surface,1)!=setAlphaColor('#171A20',1) && setAlphaColor(theme.surface,1)!=setAlphaColor('#FFFFFF',1))) ? alphaColor(theme.surface,.92) : theme.mode=='light' ? '#F6F6F3F0' : '#131317EB'"
  let r-card = "clamp(theme.radius,0,16)"
  let r-outer = "clamp(theme.radius>0 ? theme.radius+2 : 0,0,18)"
  let r-ctrl = "clamp(theme.radius*.75,0,12)"
  let r-sm = "clamp(theme.radius*.5,0,8)"
}
