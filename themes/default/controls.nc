style class nc-ctrl-btn {
  background = "{{= vars.d_raised}}"
  color = "{{= vars.d_text}}"
  borderWidth = .6px
  borderColor = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}"
  borderRadius = "{{= vars.r_ctrl}}"
  padding = "0px 8px"
  textAlign = center
  fontFamily = studio-sans-medium
  fontSize = 6.9px
  height = 22px
  hoverScale = 1
  pressScale = .98
  transitionDuration = 150ms
  easing = "cubic-bezier(0.22,1,0.36,1)"
  hover { background = "{{= (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E')}}" }
  active { background = "{{= vars.d_accent}}"; color = "{{= vars.d_on_accent}}" }
}

style class nc-ctrl-primary {
  background = "{{= vars.d_accent}}"
  color = "{{= vars.d_on_accent}}"
  borderWidth = 0px
  borderRadius = "{{= vars.r_ctrl}}"
  padding = "0px 10px"
  textAlign = center
  height = 22px
  fontFamily = studio-sans-medium
  fontSize = 6.9px
  hoverScale = 1
  pressScale = .975
  transitionDuration = 150ms
  easing = "cubic-bezier(0.22,1,0.36,1)"
  hover { background = "{{= lerpColor(vars.d_accent,vars.d_surface,.12)}}" }
}

style class nc-sheet {
  background = "{{= vars.d_surface}}"
  color = "{{= vars.d_text}}"
  borderWidth = .65px
  borderColor = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}"
  borderRadius = "{{= vars.r_outer}}"
  layout = column
  padding = 10px
  gap = 6px
  enterOpacity = 0%
  enterOffsetY = 6px
  enterDuration = 190ms
  exitDuration = 140ms
  transitionDuration = 170ms
  easing = "cubic-bezier(0.22,1,0.36,1)"
  surfaceOpacity = 100%
  backgroundOpacity = 100%
}

style class nc-editor-glass {
  blur = "{{= theme.effects.backgroundBlur}}"
  backgroundOpacity = "{{= theme.backgroundOpacity}}"
  tintOpacity = -1
}

style class nc-label {
  height = 14px; fontFamily = studio-sans-medium; fontSize = 7.8px; color = "{{= vars.d_text}}"; padding = 0px
}

style class nc-note {
  height = 26px; fontSize = 6.5px; lineHeight = 1.38; textOverflow = wrap; color = "{{= vars.d_muted}}"; padding = 0px
}

style class nc-editor-control { height = 22px; fontSize = 7px }
style class nc-editor-slider { height = 28px; fontSize = 7px }

component "element.toggle" {
  layout = absolute
  toggle (id = "{{elementId}}", bind = "{{bind}}", visual = "none", text = "{{label}}", tooltip = "{{tooltipText}}") {
    width = 100%; height = 100%; padding = 0px; background = #00000000; borderWidth = 0px; borderRadius = 6px; hoverScale = 1; pressScale = 1
    text "{{label}}" { position = absolute; x = 0px; y = 0px; width = "{{= max(0,boxWidth-34)}}"; height = 100%; fontSize = 7.1px; color = "{{= alphaColor(vars.d_text,moduleEnabled ? 1 : .5)}}" }
    rect {
      position = absolute; right = 0px; y = 4px; width = 26px; height = 14px; radius = 7px
      fillTopLeft = "{{= active ? (theme.mode=='custom' ? (theme.accent==theme.text ? '#070709' : lerpColor(theme.accent,'#000000',.25)) : theme.mode=='light' ? '#0F0F12' : '#070709') : (theme.mode=='custom' ? lerpColor(theme.background,'#000000',.22) : theme.mode=='light' ? '#D4D4CF' : '#0B0B0D')}}"
      fillTopRight = "{{= active ? (theme.mode=='custom' ? (theme.accent==theme.text ? '#070709' : lerpColor(theme.accent,'#000000',.25)) : theme.mode=='light' ? '#0F0F12' : '#070709') : (theme.mode=='custom' ? lerpColor(theme.background,'#000000',.22) : theme.mode=='light' ? '#D4D4CF' : '#0B0B0D')}}"
      fillBottomLeft = "{{= active ? (theme.mode=='custom' ? (theme.accent==theme.text ? '#18181D' : theme.accent) : theme.mode=='light' ? '#26262B' : '#18181D') : (theme.mode=='custom' ? lerpColor(theme.surface,theme.raised,.35) : theme.mode=='light' ? '#EFEFEB' : '#1E1E23')}}"
      fillBottomRight = "{{= active ? (theme.mode=='custom' ? (theme.accent==theme.text ? '#18181D' : theme.accent) : theme.mode=='light' ? '#26262B' : '#18181D') : (theme.mode=='custom' ? lerpColor(theme.surface,theme.raised,.35) : theme.mode=='light' ? '#EFEFEB' : '#1E1E23')}}"
      stroke = "{{= active ? (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24') : vars.d_border}}"; strokeWidth = .55px
      animateValue = true; transitionDuration = 160ms
    }
    rect {
      position = absolute; right = "{{= active ? 2 : 14}}"; y = 7px; width = 10px; height = 10px; radius = 5px
      fill = "{{= (theme.mode=='light' ? '#00000028' : '#00000058')}}"
      animateLayout = true; transitionDuration = 170ms; easing = "cubic-bezier(0.22,1,0.36,1)"
    }
    rect {
      position = absolute; right = "{{= active ? 2 : 14}}"; y = 6px; width = 10px; height = 10px; radius = 5px
      fillTopLeft = "{{= active ? (theme.mode=='custom' ? (theme.accent==theme.text ? theme.text : '#FFFFFF') : theme.mode=='light' ? '#3E3E45' : '#F8F8F5') : (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.28) : theme.mode=='light' ? '#FFFFFF' : '#62626B')}}"
      fillTopRight = "{{= active ? (theme.mode=='custom' ? (theme.accent==theme.text ? theme.text : '#FFFFFF') : theme.mode=='light' ? '#3E3E45' : '#F8F8F5') : (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.28) : theme.mode=='light' ? '#FFFFFF' : '#62626B')}}"
      fillBottomLeft = "{{= active ? (theme.mode=='custom' ? (theme.accent==theme.text ? lerpColor(theme.text,theme.surface,.14) : lerpColor('#FFFFFF',theme.accent,.12)) : theme.mode=='light' ? '#28282E' : '#DDDDD8') : (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.14) : theme.mode=='light' ? '#F2F2EF' : '#474750')}}"
      fillBottomRight = "{{= active ? (theme.mode=='custom' ? (theme.accent==theme.text ? lerpColor(theme.text,theme.surface,.14) : lerpColor('#FFFFFF',theme.accent,.12)) : theme.mode=='light' ? '#28282E' : '#DDDDD8') : (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.14) : theme.mode=='light' ? '#F2F2EF' : '#474750')}}"
      stroke = "{{= (theme.mode=='light' ? '#FFFFFF42' : '#FFFFFF34')}}"; strokeWidth = .45px
      animateLayout = true; animateValue = true; transitionDuration = 170ms; easing = "cubic-bezier(0.22,1,0.36,1)"
    }
  }
}

component "element.toggle.compact" {
  layout = absolute
  toggle (id = "{{elementId}}", bind = "{{bind}}", visual = "none", text = "{{label}}", tooltip = "{{tooltipText}}") {
    width = 100%; height = 100%; padding = 0px; background = #00000000; borderWidth = 0px; borderRadius = 5px; hoverScale = 1; pressScale = 1
    text "{{label}}" { position = absolute; x = 0px; y = 0px; width = "{{= max(0,boxWidth-20)}}"; height = 100%; fontSize = 6.9px; color = "{{= alphaColor(vars.d_text,moduleEnabled ? 1 : .55)}}" }
    rect {
      position = absolute; right = 0px; y = 4px; width = 12px; height = 12px; radius = "{{= min(vars.r_sm,4)}}"
      fill = "{{= active ? vars.d_accent : vars.d_well}}"
      stroke = "{{= active ? vars.d_accent : (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}"; strokeWidth = .65px
      animateValue = true; transitionDuration = 140ms
    }
    icon (src = "check", if = "active") {
      position = absolute; right = 2px; y = 6px; width = 8px; height = 8px
      strokeWidth = 2px; color = "{{= vars.d_on_accent}}"
    }
  }
}

component "element.slider" {
  layout = absolute
  text "{{label}}" { position = absolute; x = 0px; y = 0px; width = "{{= max(0,boxWidth-44)}}"; height = 11px; fontSize = 6.9px; color = "{{= alphaColor((theme.mode=='custom' ? lerpColor(theme.text,theme.surface,.3) : theme.mode=='light' ? '#52525B' : '#A3A3AA'),moduleEnabled ? 1 : .55)}}" }
  text "{{= format(clamp(value,min,max),'decimal:2')}}" {
    position = absolute; right = 0px; y = 0px; width = 40px; height = 11px
    fontFamily = studio-sans-medium; fontSize = 6.9px; textAlign = right; color = "{{= vars.d_text}}"
    textMotion = numeric; animateValue = true; transitionDuration = 150ms
  }
  slider (id = "{{elementId}}", bind = "{{bind}}", min = "{{min}}", max = "{{max}}", step = "{{step}}", value-scale = "{{valueScale}}", value-offset = "{{valueOffset}}", visual = "none", text = "{{label}}", tooltip = "{{tooltipText}}") {
    position = absolute; x = 0px; y = 13px; width = 100%; height = 14px; padding = 0px; borderWidth = 0px; background = #00000000; hoverScale = 1; pressScale = 1
    rect { position = absolute; x = 0px; y = 5px; width = 100%; height = 4px; radius = 2px; fill = "{{= vars.d_well}}"; stroke = "{{= vars.d_border}}"; strokeWidth = .45px }
    rect { position = absolute; x = 0px; y = 5px; width = "{{= clamp(round(5+clamp(ratio,0,1)*max(0,boxWidth-10)),2,max(2,boxWidth))}}"; height = 4px; radius = 2px; fill = "{{= vars.d_accent}}"; animateLayout = false }
    rect {
      position = absolute; x = "{{= round(5+clamp(ratio,0,1)*max(0,boxWidth-10))}}"; y = 1px; anchorX = .5
      width = 10px; height = 12px; radius = 4px
      fill = "{{= vars.d_raised}}"
      animateLayout = false
    }
    rect {
      position = absolute; x = "{{= round(5+clamp(ratio,0,1)*max(0,boxWidth-10))}}"; y = 2px; anchorX = .5
      width = 8px; height = 10px; radius = 3px
      fill = "{{= vars.d_accent}}"
      animateLayout = false
    }
  }
}

component "element.slider.compact" {
  layout = absolute
  text "{{label}}" { position = absolute; x = 0px; y = 0px; width = "{{= max(0,boxWidth-84)}}"; height = 100%; fontSize = 6.7px; color = "{{= (theme.mode=='custom' ? lerpColor(theme.text,theme.surface,.3) : theme.mode=='light' ? '#52525B' : '#A3A3AA')}}" }
  text "{{= format(clamp(value,min,max),'decimal:1')}}" { position = absolute; right = 48px; width = 30px; height = 100%; fontSize = 6.5px; fontFamily = studio-sans-medium; textAlign = right; color = "{{= vars.d_text}}" }
  slider (id = "{{elementId}}", bind = "{{bind}}", min = "{{min}}", max = "{{max}}", step = "{{step}}", value-scale = "{{valueScale}}", value-offset = "{{valueOffset}}", visual = "none", text = "{{label}}", tooltip = "{{tooltipText}}") {
    position = absolute; right = 0px; width = 44px; height = 100%; padding = 0px; background = #00000000; borderWidth = 0px; hoverScale = 1; pressScale = 1
    rect { position = absolute; x = 0px; y = 50%; anchorY = .5; width = 100%; height = 4px; radius = 2px; fill = "{{= vars.d_well}}" }
    rect { position = absolute; x = 0px; y = 50%; anchorY = .5; width = "{{= clamp(round(4+clamp(ratio,0,1)*max(0,boxWidth-8)),2,max(2,boxWidth))}}"; height = 4px; radius = 2px; fill = "{{= vars.d_accent}}"; animateLayout = false }
    circle { position = absolute; x = "{{= round(4+clamp(ratio,0,1)*max(0,boxWidth-8))}}"; y = 50%; anchorX = .5; anchorY = .5; width = 8px; height = 8px; fill = "{{= vars.d_accent}}"; animateLayout = false }
  }
}

component "element.choice" (scope = "nc-choice.{{elementId}}") {
  intrinsicHeight = true; layout = column; width = 100%; height = auto; padding = 0px; gap = 3px
  text "{{label}}" { width = 100%; height = 10px; fontSize = 6.9px; color = "{{= (theme.mode=='custom' ? lerpColor(theme.text,theme.surface,.3) : theme.mode=='light' ? '#52525B' : '#A3A3AA')}}" }
  button (id = "{{elementId}}", action = "local-toggle:expanded", tooltip = "{{tooltipText}}") {
    width = 100%; height = 22px; layout = absolute; padding = 0px
    background = "{{= vars.d_raised}}"
    borderWidth = .6px; borderColor = "{{= local.expanded ? (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24') : vars.d_border}}"; borderRadius = "{{= vars.r_ctrl}}"
    hoverScale = 1; pressScale = .99; transitionDuration = 150ms
    hover { background = "{{= (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E')}}" }
    text "{{chosenLabel}}" { position = absolute; x = 8px; y = 0px; width = "{{= max(0,boxWidth-28)}}"; height = 100%; fontFamily = studio-sans-medium; fontSize = 6.9px; color = "{{= vars.d_text}}" }
    icon (src = "chevron-down") { position = absolute; right = 8px; y = 7.5px; width = 7px; height = 7px; color = "{{= vars.d_muted}}" }
  }
  panel (if = "local.expanded") {
    width = 100%; layout = column; padding = 3px; gap = 2px
    background = "{{= vars.d_surface}}"; borderWidth = .65px; borderColor = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}"; borderRadius = "{{= vars.r_card}}"
    overflow = hidden; animateEnter = true; enterOpacity = 0%; enterOffsetY = -3px; enterDuration = 160ms; exitDuration = 110ms; easing = "cubic-bezier(0.22,1,0.36,1)"
    repeat (source = "choiceOptions", as = "option", key = "{{option.value}}") { layout = column; width = 100%
      button (id = "option.{{elementId}}.{{option.value}}", action = "sequence:set:{{bind}}:{{option.value}};local-set:expanded:false") {
        width = 100%; height = 19px; layout = absolute; padding = 0px; borderRadius = "{{= vars.r_sm}}"; borderWidth = 0px; pressScale = .99
        background = "{{= option.selected ? (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E') : '#00000000'}}"
        hover { background = "{{= (theme.mode=='custom' ? lerpColor(theme.raised,theme.text,.075) : theme.mode=='light' ? '#EFEFEC' : '#28282E')}}" }
        text "{{option.label}}" { position = absolute; x = 6px; y = 0px; width = "{{= max(0,boxWidth-22)}}"; height = 100%; fontFamily = "{{= option.selected ? 'studio-sans-medium' : 'studio-sans'}}"; fontSize = 6.8px; color = "{{= option.selected ? vars.d_text : (theme.mode=='custom' ? lerpColor(theme.text,theme.surface,.3) : theme.mode=='light' ? '#52525B' : '#A3A3AA')}}" }
        icon (src = "check", if = "option.selected") { position = absolute; right = 6px; y = 5.5px; width = 8px; height = 8px; strokeWidth = 1.9px; color = "{{= vars.d_text}}" }
      }
    }
  }
}

component "element.choice.segmented" {
  layout = absolute
  panel {
    position = absolute; x = 0px; y = 0px; width = 100%; height = 100%; layout = row; padding = 2px; gap = 2px
    background = "{{= vars.d_well}}"; borderRadius = "{{= vars.r_ctrl}}"; borderWidth = .55px; borderColor = "{{= vars.d_border}}"
    repeat (source = "choiceOptions", as = "option", key = "{{option.value}}") { width = 100%; height = 100%; layout = row; gap = 2px
      button (id = "option.{{elementId}}.{{option.value}}", action = "set:{{bind}}:{{option.value}}") {
        grow = 1; height = 100%; padding = 0px; borderRadius = "{{= vars.r_sm}}"; borderWidth = "{{= option.selected ? .55 : 0}}"
        background = "{{= option.selected ? vars.d_accent : '#00000000'}}"
        borderColor = "{{= option.selected ? vars.d_accent : '#00000000'}}"
        hoverScale = 1; pressScale = .98; transitionDuration = 150ms
        hover { background = "{{= option.selected ? vars.d_accent : alphaColor(vars.d_text,.05)}}" }
        text "{{option.label}}" { width = 100%; height = 100%; textAlign = center; fontFamily = studio-sans-medium; fontSize = 6.8px; color = "{{= option.selected ? vars.d_on_accent : (theme.mode=='custom' ? lerpColor(theme.text,theme.surface,.3) : theme.mode=='light' ? '#52525B' : '#A3A3AA')}}" }
      }
    }
  }
}

component "element.color" {
  layout = absolute
  color (id = "{{elementId}}", bind = "{{bind}}", visual = "none", text = "{{label}}", tooltip = "{{tooltipText}}") {
    width = 100%; height = 100%; padding = 0px; background = #00000000; borderWidth = 0px; borderRadius = "{{= vars.r_ctrl}}"; pressScale = .99
    text "{{label}}" { position = absolute; x = 0px; y = 0px; width = "{{= max(0,boxWidth-44)}}"; height = 100%; fontSize = 7.1px; color = "{{= vars.d_text}}" }
    rect { position = absolute; right = 0px; y = 3px; width = 34px; height = 18px; radius = "{{= vars.r_ctrl}}"; fill = "{{= vars.d_raised}}"; stroke = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}"; strokeWidth = .6px }
    color-swatch (id = "{{elementId}}.preview", bind = "{{bind}}") { position = absolute; right = 19px; y = 6px; width = 12px; height = 12px; borderRadius = "{{= min(vars.r_sm,4)}}" }
    icon (src = "chevron-down") { position = absolute; right = 6px; y = 8.5px; width = 7px; height = 7px; color = "{{= vars.d_muted}}" }
  }
}

component "element.color.inline" (scope = "nc-color.{{elementId}}") {
  intrinsicHeight = true; layout = column; width = 100%; height = auto; padding = 0px; gap = 4px
  button (id = "{{elementId}}", action = "local-toggle:expanded", tooltip = "{{tooltipText}}") {
    width = 100%; height = 22px; layout = absolute; padding = 0px; background = #00000000; borderWidth = 0px; pressScale = .99
    text "{{label}}" { position = absolute; x = 0px; y = 0px; width = "{{= max(0,boxWidth-44)}}"; height = 100%; fontSize = 6.9px; color = "{{= vars.d_text}}" }
    rect { position = absolute; right = 0px; y = 2px; width = 34px; height = 18px; radius = "{{= vars.r_ctrl}}"; fill = "{{= vars.d_raised}}"; stroke = "{{= local.expanded ? (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24') : vars.d_border}}"; strokeWidth = .6px }
    color-swatch (id = "{{elementId}}.preview", bind = "{{bind}}") { position = absolute; right = 19px; y = 5px; width = 12px; height = 12px; borderRadius = "{{= min(vars.r_sm,4)}}" }
    icon (src = "chevron-down") { position = absolute; right = 6px; y = 7.5px; width = 7px; height = 7px; color = "{{= vars.d_muted}}" }
  }
  panel (if = "local.expanded") {
    width = 100%; layout = column; padding = "6px 7px"; gap = 4px
    background = "{{= vars.d_surface}}"; borderWidth = .65px; borderColor = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}"; borderRadius = "{{= vars.r_card}}"
    animateEnter = true; enterOpacity = 0%; enterOffsetY = -3px; enterDuration = 170ms; exitDuration = 120ms; overflow = hidden
    color-plane (id = "{{elementId}}.plane", bind = "{{bind}}") { width = 100%; height = 42px; borderRadius = "{{= vars.r_sm}}"; hoverScale = 1; pressScale = 1 }
    hue-strip (id = "{{elementId}}.hue", bind = "{{bind}}") { width = 100%; height = 7px; borderRadius = 3.5px; hoverScale = 1; pressScale = 1 }
    alpha-strip (id = "{{elementId}}.alpha", bind = "{{bind}}") { width = 100%; height = 7px; borderRadius = 3.5px; hoverScale = 1; pressScale = 1 }
    row { width = 100%; height = 18px; gap = 4px; align = center
      input (id = "{{elementId}}.hex", bind = "{{bind}}", format = "hex", maxlength = "9") { grow = 1; height = 18px; fontSize = 6.7px; borderRadius = "{{= vars.r_sm}}"; background = "{{= vars.d_well}}"; color = "{{= vars.d_text}}" }
      color-swatch (bind = "{{bind}}") { width = 18px; height = 18px; borderRadius = "{{= vars.r_sm}}" }
    }
    row { width = 100%; height = 11px; gap = 4px; justify = space-between; align = center
      button (action = "set:{{bind}}:#3478F6") { width = 11px; height = 11px; padding = 0px; background = #3478F6; borderRadius = 5.5px; borderWidth = .5px; borderColor = "{{= vars.d_border}}" }
      button (action = "set:{{bind}}:#9270E8") { width = 11px; height = 11px; padding = 0px; background = #9270E8; borderRadius = 5.5px; borderWidth = .5px; borderColor = "{{= vars.d_border}}" }
      button (action = "set:{{bind}}:#E66AA4") { width = 11px; height = 11px; padding = 0px; background = #E66AA4; borderRadius = 5.5px; borderWidth = .5px; borderColor = "{{= vars.d_border}}" }
      button (action = "set:{{bind}}:#E55656") { width = 11px; height = 11px; padding = 0px; background = #E55656; borderRadius = 5.5px; borderWidth = .5px; borderColor = "{{= vars.d_border}}" }
      button (action = "set:{{bind}}:#ED9141") { width = 11px; height = 11px; padding = 0px; background = #ED9141; borderRadius = 5.5px; borderWidth = .5px; borderColor = "{{= vars.d_border}}" }
      button (action = "set:{{bind}}:#E5B63C") { width = 11px; height = 11px; padding = 0px; background = #E5B63C; borderRadius = 5.5px; borderWidth = .5px; borderColor = "{{= vars.d_border}}" }
      button (action = "set:{{bind}}:#65A65A") { width = 11px; height = 11px; padding = 0px; background = #65A65A; borderRadius = 5.5px; borderWidth = .5px; borderColor = "{{= vars.d_border}}" }
      button (action = "set:{{bind}}:#169D83") { width = 11px; height = 11px; padding = 0px; background = #169D83; borderRadius = 5.5px; borderWidth = .5px; borderColor = "{{= vars.d_border}}" }
    }
    row { width = 100%; height = 16px; gap = 4px
      button "{{i18n.ui.copy}}" (id = "{{elementId}}.copy", style = "nc-ctrl-btn", action = "clipboard-copy:{{bind}}") { grow = 1; height = 16px; fontSize = 6.3px; borderRadius = "{{= vars.r_sm}}" }
      button "{{i18n.ui.paste}}" (id = "{{elementId}}.paste", style = "nc-ctrl-btn", action = "clipboard-paste:{{bind}}") { grow = 1; height = 16px; fontSize = 6.3px; borderRadius = "{{= vars.r_sm}}" }
    }
  }
}

component "color-picker" {
  panel (id = "color-dialog", style = "nc-sheet") {
    background = "{{= setAlphaColor(vars.d_surface,1)}}"; backgroundOpacity = 100%; surfaceOpacity = 100%; tintOpacity = -1
    let cp-w = "min(186,width-16)"; let cp-h = "min(226,height-16)"
    position = absolute
    x = "{{= clamp(anchor.x+anchor.width+6+vars.cp_w<width-8 ? anchor.x+anchor.width+6 : anchor.x-vars.cp_w-6,8,max(8,width-vars.cp_w-8))}}"
    y = "{{= clamp(anchor.y,8,max(8,height-vars.cp_h-8))}}"
    width = "{{= vars.cp_w}}"; height = "{{= vars.cp_h}}"; padding = 8px; gap = 6px; borderRadius = "{{= vars.r_outer}}"
    row { width = 100%; height = 16px; gap = 4px; align = center
      text "{{colorLabel}}" { grow = 1; height = 100%; fontFamily = studio-sans-medium; fontSize = 7.4px; color = "{{= vars.d_text}}" }
      button (id = "color-done", action = "color-close", style = "nc-ctrl-btn") { width = 16px; height = 16px; padding = 0px; borderRadius = "{{= vars.r_sm}}"; layout = row; align = center; justify = center; icon (src = "close") { width = 7px; height = 7px; color = "{{= vars.d_muted}}" } }
    }
    color-plane (id = "color-plane", bind = "{{colorBind}}") { width = 100%; grow = 1; minHeight = 44px; borderRadius = "{{= vars.r_ctrl}}"; hoverScale = 1; pressScale = 1 }
    hue-strip (id = "color-hue", bind = "{{colorBind}}") { width = 100%; height = 8px; borderRadius = 4px; hoverScale = 1; pressScale = 1 }
    alpha-strip (id = "color-alpha", bind = "{{colorBind}}") { width = 100%; height = 8px; borderRadius = 4px; hoverScale = 1; pressScale = 1 }
    row { width = 100%; height = 20px; gap = 4px; align = center
      input (id = "color-hex", bind = "{{colorBind}}", format = "hex", maxlength = "9") { grow = 1; height = 20px; fontSize = 6.9px; borderRadius = "{{= vars.r_sm}}"; background = "{{= vars.d_well}}"; color = "{{= vars.d_text}}" }
      color-swatch (bind = "{{colorBind}}") { width = 20px; height = 20px; borderRadius = "{{= vars.r_sm}}" }
    }
    row { width = 100%; height = 13px; gap = 4px; justify = space-between; align = center
      button (action = "set:{{colorBind}}:#3478F6") { width = 13px; height = 13px; padding = 0px; background = #3478F6; borderRadius = 6.5px; borderWidth = .5px; borderColor = "{{= vars.d_border}}" }
      button (action = "set:{{colorBind}}:#9270E8") { width = 13px; height = 13px; padding = 0px; background = #9270E8; borderRadius = 6.5px; borderWidth = .5px; borderColor = "{{= vars.d_border}}" }
      button (action = "set:{{colorBind}}:#E66AA4") { width = 13px; height = 13px; padding = 0px; background = #E66AA4; borderRadius = 6.5px; borderWidth = .5px; borderColor = "{{= vars.d_border}}" }
      button (action = "set:{{colorBind}}:#E55656") { width = 13px; height = 13px; padding = 0px; background = #E55656; borderRadius = 6.5px; borderWidth = .5px; borderColor = "{{= vars.d_border}}" }
      button (action = "set:{{colorBind}}:#ED9141") { width = 13px; height = 13px; padding = 0px; background = #ED9141; borderRadius = 6.5px; borderWidth = .5px; borderColor = "{{= vars.d_border}}" }
      button (action = "set:{{colorBind}}:#E5B63C") { width = 13px; height = 13px; padding = 0px; background = #E5B63C; borderRadius = 6.5px; borderWidth = .5px; borderColor = "{{= vars.d_border}}" }
      button (action = "set:{{colorBind}}:#65A65A") { width = 13px; height = 13px; padding = 0px; background = #65A65A; borderRadius = 6.5px; borderWidth = .5px; borderColor = "{{= vars.d_border}}" }
      button (action = "set:{{colorBind}}:#169D83") { width = 13px; height = 13px; padding = 0px; background = #169D83; borderRadius = 6.5px; borderWidth = .5px; borderColor = "{{= vars.d_border}}" }
    }
    row { width = 100%; height = 19px; gap = 4px
      button "{{i18n.ui.copy}}" (style = "nc-ctrl-btn", action = "clipboard-copy:{{colorBind}}") { grow = 1; height = 19px; fontSize = 6.4px }
      button "{{i18n.ui.paste}}" (style = "nc-ctrl-btn", action = "clipboard-paste:{{colorBind}}") { grow = 1; height = 19px; fontSize = 6.4px }
    }
  }
}

component "menu.backdrop" {}

component "overlay.tooltip" {
  let max-width = 216px; let viewport-margin = 8px; let padding-x = 7px; let padding-y = 3px
  let arrow-width = 7px; let arrow-height = 3.5px; let side-offset = 3.5px; let delay = 140ms
  fontFamily = studio-sans-medium; fontSize = 6.6px; lineHeight = 1.28
  panel (id = "hover-tooltip") {
    let arrow-width = 7px; let arrow-height = 3.5px
    position = absolute; x = "{{tooltip.x}}"; y = "{{tooltip.y}}"
    width = "{{= tooltip.width+tooltip.paddingX*2}}"; height = "{{= tooltip.height+tooltip.paddingY*2}}"
    padding = "{{tooltip.paddingY}}px {{tooltip.paddingX}}px"
    background = "{{= (theme.mode=='custom' ? lerpColor(theme.background,'#000000',.32) : '#09090B')}}"
    borderWidth = .65px
    borderColor = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.24) : '#27272A')}}"
    borderRadius = "{{= vars.r_ctrl}}"
    surfaceOpacity = 100%; backgroundOpacity = 100%; tintOpacity = -1
    animateEnter = true; enterOpacity = 0%; enterScale = .96; enterOffsetY = "{{= tooltip.arrowSide=='top' ? -3 : 3}}"; enterDuration = 130ms; exitDuration = 95ms
    text "{{tooltip.text}}" (id = "hover-tooltip-text") {
      width = 100%; height = 100%; padding = 0px; fontFamily = studio-sans-medium; fontSize = 6.6px; lineHeight = 1.28
      textOverflow = wrap; color = "{{= (theme.mode=='custom' ? theme.text : '#FAFAFA')}}"
    }
  }
}

component "overlay.notification" {
  fontSize = 6.9px; lineHeight = 1.35
  panel (id = "notification") {
    position = absolute; x = 50%; anchorX = .5; y = "{{= viewportHeight-44}}"; width = "{{= min(viewportWidth-24,notification.width+30)}}"; height = 26px; layout = absolute; padding = 0px
    background = "{{= vars.d_surface}}"; borderWidth = .65px; borderColor = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}"; borderRadius = "{{= vars.r_outer}}"
    enterOpacity = 0%; enterOffsetY = 8px; enterDuration = 220ms; exitDuration = 160ms; backgroundOpacity = 100%; surfaceOpacity = 100%
    rect { position = absolute; x = 10px; y = 8px; width = 2.5px; height = 10px; radius = 1.25px; fill = "{{= vars.d_accent}}" }
    text "{{notification.text}}" { position = absolute; x = 20px; width = "{{= max(0,boxWidth-28)}}"; height = 100%; padding = 0px; fontSize = 6.9px; fontFamily = studio-sans-medium; color = "{{= vars.d_text}}" }
  }
}

component "editor.behavior" {
  let edge-inset = 0px; let snap-inset = 0px; let snap-distance = 7px; let keyboard-fast-step = 10px; let scale-wheel-step = .05
  let compact-width = 1100px; let compact-height = 530px
  let toolbar-compact-width = 380px; let toolbar-width = 860px; let toolbar-compact-height = 28px; let toolbar-height = 34px
  let toolbar-compact-y = 8px; let toolbar-y = 10px; let toolbar-compact-padding = 3px; let toolbar-padding = 4px; let toolbar-compact-gap = 2px; let toolbar-gap = 3px
  let inspector-inset = 12px; let inspector-top = 56px; let inspector-width = 194px; let viewmodel-inspector-width = 212px; let inspector-min-size = 24px
  let inspector-max-height = 286px; let inspector-base-height = 96px; let inspector-row-height = 36px; let viewmodel-inspector-height = 242px; let fire-inspector-height = 176px
}

component "editor.chrome" {
  panel (id = "editor-toolbar", style = "nc-editor-glass") {
    position = absolute; x = 50%; anchorX = .5; y = "{{editorToolbarY}}"; width = "{{editorToolbarWidth}}"; maxWidth = 96%; height = "{{editorToolbarHeight}}"; layout = row; padding = "{{editorToolbarPadding}}"; gap = "{{editorToolbarGap}}"
    background = "{{= vars.d_surface}}"; borderWidth = .7px; borderColor = "{{= (theme.mode=='light' ? '#18181B1E' : '#FFFFFF20')}}"; borderRadius = "{{= vars.r_outer}}"; surfaceOpacity = 100%
    enterOpacity = 0%; enterOffsetY = -6px; enterDuration = 200ms; exitDuration = 150ms
    repeat (source = "editorTargets", as = "target") { layout = row; grow = 1; height = 100%; gap = 2px
      button "{{= editorCompact ? '' : target.label}}" (id = "editor-target-{{target.id}}", action = "editor-target:{{target.id}}", tooltip = "{{target.label}}", style = "nc-ctrl-btn") {
        grow = 1; height = 100%; padding = 0px; borderWidth = 0px; borderRadius = "{{= vars.r_ctrl}}"; fontSize = 6.6px
        background = "{{= editorTarget==target.id ? vars.d_accent : '#00000000'}}"
        color = "{{= editorTarget==target.id ? vars.d_on_accent : vars.d_muted}}"
        icon (src = "{{target.icon}}", if = "{{= editorCompact}}") { position = absolute; x = 50%; y = 50%; anchorX = .5; anchorY = .5; width = 10px; height = 10px; color = "{{= editorTarget==target.id ? vars.d_on_accent : vars.d_muted}}" }
      }
    }
    rect { width = .6px; height = 64%; fill = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}" }
    button "{{i18n.ui.done}}" (id = "editor-done", action = "editor-done", style = "nc-ctrl-primary") { width = "{{= editorCompact ? 46 : 64}}"; height = 100%; grow = 0; padding = 0px; fontSize = 6.8px; borderRadius = "{{= vars.r_ctrl}}" }
  }
}

component "editor.inspector" {
  panel (id = "editor-inspector", style = "nc-sheet nc-editor-glass") {
    position = absolute; x = "{{editorInspectorX}}"; y = "{{editorInspectorY}}"; width = "{{editorInspectorWidth}}"; height = "{{editorInspectorHeight}}"; overflow = scroll; scrollbarWidth = 1.5px; scrollStep = 24px; scrollDuration = 160ms
    text "{{editorSelectedLabel}}" (style = "nc-label") { height = 16px; fontSize = 8.2px }
    rect { width = 100%; height = .6px; fill = "{{= vars.d_border}}" }
    slider "{{editorScaleLabel}}" (id = "editor-scale", style = "nc-editor-slider", bind = "{{editorScaleBind}}", min = "{{editorScaleMin}}", max = "{{editorScaleMax}}", step = "{{editorScaleStep}}") { height = 28px }
    toggle "{{editorSnapLabel}}" (style = "nc-editor-control", if = "!editorTarget=crosshair", bind = "hud.snap", visual = "compact")
    button "{{editorDrawLabel}}" (id = "editor-draw-crosshair", style = "nc-ctrl-btn", if = "editorTarget=crosshair", action = "editor-crosshair") { width = 100%; height = 22px }
    repeat (source = "editorControls", as = "control", key = "{{control.bind}}") {
      layout = column; width = 100%; gap = 6px
      toggle "{{control.label}}" (id = "{{control.bind}}", style = "nc-editor-control", if = "control.kind=toggle", bind = "{{control.bind}}", visual = "compact")
      slider "{{control.label}}" (id = "{{control.bind}}", style = "nc-editor-slider", if = "control.kind=slider", bind = "{{control.bind}}", min = "{{control.min}}", max = "{{control.max}}", step = "{{control.step}}") { height = 28px }
      column (if = "control.kind=input") { width = 100%; gap = 3px; text "{{control.label}}" { height = 9px; fontSize = 6.8px; color = "{{= vars.d_muted}}" }; input (id = "{{control.bind}}", bind = "{{control.bind}}", maxlength = "128") { width = 100%; height = 22px } }
      color "{{control.label}}" (id = "{{control.bind}}", style = "nc-editor-control", if = "control.kind=color", bind = "{{control.bind}}")
      choice "{{control.label}}" (id = "{{control.bind}}", style = "nc-editor-control", if = "control.kind=choice", bind = "{{control.bind}}", options = "{{control.options}}", option-labels = "{{control.optionLabels}}")
    }
  }
}

component "editor.selection" {
  layout = absolute
  rect (if = "selection.visible") { position = absolute; x = "{{= max(.5,selection.x-2)}}"; y = "{{= max(.5,selection.y-2)}}"; width = "{{= max(0,min(width-.5,selection.x+selection.width+2)-max(.5,selection.x-2))}}"; height = "{{= max(0,min(height-.5,selection.y+selection.height+2)-max(.5,selection.y-2))}}"; radius = "{{= vars.r_card}}"; fill = #00000000; stroke = "{{= alphaColor(vars.d_accent,.85)}}"; strokeWidth = .85px }
  rect (id = "editor-resize-handle", if = "selection.visible") { position = absolute; x = "{{= clamp(selection.x+selection.width-4,0,max(0,width-8))}}"; y = "{{= clamp(selection.y+selection.height-4,0,max(0,height-8))}}"; width = 8px; height = 8px; radius = "{{= min(vars.r_sm,3)}}"; fill = "{{= vars.d_accent}}"; stroke = "{{= vars.d_surface}}"; strokeWidth = .9px }
}

component "editor.guides" {
  layout = absolute
  rect (if = "guides.vertical") { position = absolute; x = "{{guides.x}}"; width = .65px; height = 100%; fill = "{{= alphaColor(vars.d_accent,.6)}}" }
  rect (if = "guides.horizontal") { position = absolute; y = "{{guides.y}}"; width = 100%; height = .65px; fill = "{{= alphaColor(vars.d_accent,.6)}}" }
}

component "editor.viewmodel" {
  panel (id = "viewmodel-inspector", style = "nc-sheet nc-editor-glass") {
    position = absolute; x = "{{editorInspectorX}}"; y = "{{editorInspectorY}}"; width = "{{editorInspectorWidth}}"; height = "{{editorInspectorHeight}}"; overflow = scroll; scrollbarWidth = 1.5px
    text "{{i18n.ui.transform}}" (style = "nc-label") { height = 16px; fontSize = 8.2px }
    text "{{i18n.ui.click_the_item_or_hand}}" (style = "nc-note", if = "!viewmodelSelected")
    row { width = 100%; height = 24px; layout = row; padding = 2px; gap = 2px; background = "{{= vars.d_well}}"; borderRadius = "{{= vars.r_ctrl}}"; borderWidth = .5px; borderColor = "{{= vars.d_border}}"
      button "{{i18n.editor.mainHand}}" (id = "viewmodel-hand-main", action = "viewmodel-hand:main", style = "nc-ctrl-btn") { grow = 1; height = 100%; padding = 0px; borderWidth = 0px; borderRadius = "{{= vars.r_sm}}"; fontSize = 6.6px; background = "{{= viewmodelActiveHand=='main' ? vars.d_accent : '#00000000'}}"; color = "{{= viewmodelActiveHand=='main' ? vars.d_on_accent : vars.d_muted}}" }
      button "{{i18n.editor.offHand}}" (id = "viewmodel-hand-off", action = "viewmodel-hand:off", style = "nc-ctrl-btn") { grow = 1; height = 100%; padding = 0px; borderWidth = 0px; borderRadius = "{{= vars.r_sm}}"; fontSize = 6.6px; background = "{{= viewmodelActiveHand=='off' ? vars.d_accent : '#00000000'}}"; color = "{{= viewmodelActiveHand=='off' ? vars.d_on_accent : vars.d_muted}}" }
    }
    row { width = 100%; height = 28px; layout = row; padding = 2px; gap = 2px; background = "{{= vars.d_well}}"; borderRadius = "{{= vars.r_ctrl}}"; borderWidth = .5px; borderColor = "{{= vars.d_border}}"
      button (id = "viewmodel-mode-G", action = "viewmodel-mode:G", tooltip = "{{i18n.ui.move}}", style = "nc-ctrl-btn") { grow = 1; height = 100%; layout = row; align = center; justify = center; padding = 0px; borderWidth = 0px; borderRadius = "{{= vars.r_sm}}"; background = "{{= viewmodelMode=='G' ? vars.d_accent : '#00000000'}}"; icon (src = "transform-move") { width = 10px; height = 10px; color = "{{= viewmodelMode=='G' ? vars.d_on_accent : vars.d_muted}}" } }
      button (id = "viewmodel-mode-R", action = "viewmodel-mode:R", tooltip = "{{i18n.ui.rotate}}", style = "nc-ctrl-btn") { grow = 1; height = 100%; layout = row; align = center; justify = center; padding = 0px; borderWidth = 0px; borderRadius = "{{= vars.r_sm}}"; background = "{{= viewmodelMode=='R' ? vars.d_accent : '#00000000'}}"; icon (src = "transform-rotate") { width = 10px; height = 10px; color = "{{= viewmodelMode=='R' ? vars.d_on_accent : vars.d_muted}}" } }
      button (id = "viewmodel-mode-S", action = "viewmodel-mode:S", tooltip = "{{i18n.ui.scale}}", style = "nc-ctrl-btn") { grow = 1; height = 100%; layout = row; align = center; justify = center; padding = 0px; borderWidth = 0px; borderRadius = "{{= vars.r_sm}}"; background = "{{= viewmodelMode=='S' ? vars.d_accent : '#00000000'}}"; icon (src = "transform-scale") { width = 10px; height = 10px; color = "{{= viewmodelMode=='S' ? vars.d_on_accent : vars.d_muted}}" } }
    }
    text "{{i18n.ui.transform_pointer_hint}}" (style = "nc-note") { height = 34px }
    button "{{i18n.ui.reset_hand}}" (id = "viewmodel-reset", action = "viewmodel-reset", style = "nc-ctrl-btn") { width = 100%; height = 22px }
    button "{{i18n.ui.reset_all_hands}}" (id = "viewmodel-reset-all", action = "viewmodel-reset-all", style = "nc-ctrl-btn") { width = 100%; height = 22px }
  }
}

component "editor.lowfire" {
  panel (id = "lowfire-inspector", style = "nc-sheet nc-editor-glass") {
    position = absolute; x = "{{editorInspectorX}}"; y = "{{editorInspectorY}}"; width = "{{editorInspectorWidth}}"; height = "{{editorInspectorHeight}}"; overflow = scroll; scrollbarWidth = 1.5px
    text "{{i18n.module.lowFire.label}}" (style = "nc-label") { height = 16px; fontSize = 8.2px }
    text "{{i18n.ui.drag_the_fire_down}}" (style = "nc-note", if = "fireVisible")
    text "{{i18n.ui.fire_appears_while_the_player_is_burning}}" (style = "nc-note", if = "!fireVisible") { height = 30px }
    toggle "{{i18n.ui.hide_fire}}" (id = "lowFire.hidden", bind = "lowFire.hidden", visual = "compact") { height = 20px }
    slider "{{i18n.ui.fire_position}}" (id = "lowFire.offsetY", bind = "lowFire.offsetY", min = "0", max = "1.5", step = ".01") { height = 28px }
  }
}

component "editor.fire-indicator" {
  layout = absolute
  rect (id = "editor-fire-drag-region", if = "fire.visible") { position = absolute; x = 20%; y = "{{= min(height-26,height*.5+fire.offsetY*height*.5)}}"; width = 60%; height = 22px; radius = "{{= vars.r_ctrl}}"; fill = "{{= alphaColor(vars.d_accent,.1)}}"; stroke = "{{= alphaColor(vars.d_accent,.75)}}"; strokeWidth = .8px }
}

component "editor.viewmodel-behavior" { let gizmo-radius = 54px; let ring-segments = 72; let axis-min-length = 3px; let hit-distance = 7px; let ring-label-angle = 45; let center-hit-radius = 6px }
component "editor.viewmodel-outline" { viewmodel-outline (name = "hover", if = "viewmodelGizmo.opened") { color = "{{= theme.accent}}" } }
component "editor.viewmodel-gizmo" {
  layout = absolute
  div (if = "viewmodelGizmo.visible") {
    position = absolute; layout = absolute; width = 100%; height = 100%
    viewmodel-gizmo (source = "viewmodelGizmo", thickness = "3.6", axis-x-color = "#E55656", axis-y-color = "#65A65A", axis-z-color = "#3478F6") { position = absolute; x = 0px; y = 0px; width = 100%; height = 100% }
    repeat (source = "viewmodelGizmo.labels", as = "axis") {
      position = absolute; layout = absolute; width = 100%; height = 100%
      text "{{axis.axis}}" { position = absolute; x = "{{= axis.x-3}}"; y = "{{= axis.y-5}}"; width = 13px; height = 10px; fontFamily = studio-sans-medium; fontSize = 8.5px; color = "{{= axis.axis=='X' ? '#E55656' : axis.axis=='Y' ? '#65A65A' : '#3478F6'}}" }
    }
    circle (if = "viewmodelGizmo.hoverCenter") { position = absolute; x = "{{viewmodelGizmo.pivot.x}}"; y = "{{viewmodelGizmo.pivot.y}}"; anchorX = .5; anchorY = .5; width = 18px; height = 18px; fill = #FFFFFF22 }
    circle { position = absolute; x = "{{viewmodelGizmo.pivot.x}}"; y = "{{viewmodelGizmo.pivot.y}}"; anchorX = .5; anchorY = .5; width = 10px; height = 10px; fill = #FFFFFF16; stroke = #FFFFFFD8; strokeWidth = 1px }
    panel (if = "{{= viewmodelGizmo.active && (len(viewmodelGizmo.axis)>0 || len(viewmodelGizmo.number)>0)}}") { position = absolute; x = "{{= viewmodelGizmo.pivot.x+12}}"; y = "{{= viewmodelGizmo.pivot.y+12}}"; width = 92px; height = 18px; padding = "0px 7px"; borderRadius = "{{= vars.r_ctrl}}"; borderWidth = .6px; borderColor = "{{= (theme.mode=='custom' ? alphaColor(theme.text,.18) : theme.mode=='light' ? '#18181B2C' : '#FFFFFF24')}}"; background = "{{= vars.d_surface}}"
      text "{{viewmodelGizmo.mode}} {{viewmodelGizmo.axis}}  {{viewmodelGizmo.number}}" { width = 100%; height = 100%; fontSize = 6.8px; color = "{{= vars.d_text}}" }
    }
  }
}

component "chat.hud" {
  button (id = "chat-hud-edit", action = "chat-hud-edit", tooltip = "{{= chatEditorActive ? i18n.ui.chat_hud_done : i18n.ui.chat_hud_edit}}") {
    position = absolute; right = 6px; bottom = 23px; width = "{{= min(24,max(0,width-12))}}"; height = "{{= min(24,max(0,height-29))}}"; layout = row; align = center; justify = center; padding = 0px
    background = "{{= chatEditorActive ? vars.d_accent : vars.h_surface}}"; borderRadius = "{{= vars.r_card}}"; borderWidth = .65px; borderColor = "{{= (theme.mode=='light' ? '#18181B22' : '#FFFFFF1A')}}"
    surfaceOpacity = 100%; backgroundOpacity = 100%; hoverScale = 1; pressScale = .96; transitionDuration = 160ms
    icon (src = "hud-layout-edit") { width = 11px; height = 11px; color = "{{= chatEditorActive ? vars.d_on_accent : vars.d_text}}" }
  }
}
