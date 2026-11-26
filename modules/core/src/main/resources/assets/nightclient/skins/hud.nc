style class ph-frame {
  blur = "{{= theme.effects.backgroundBlur}}"
  let ph-fit = "max(.25,min(1,(viewportWidth-24)/936,(viewportHeight-20)/520))"
  let ph-top = "hudEditing ? 42 : 10"
  let ph-cw = "hud.width==148 ? max(126,maxRowTextWidth('cooldownRows',hud.names ? 'name' : '',hud.time ? 'remainingSeconds|cooldown-duration' : '',6.8*hud.fontScale,'studio-sans-medium')+(hud.icons ? min(11,9.5*hud.iconScale)+4 : 0)+30) : max(116,hud.width)"
  let ph-tn = "min(retainedCount('tntRows'),max(1,floor((tnt.height-28)/18)))"
  let ph-th = "retainedCount('tntRows')==0 ? 28 : 28+vars.ph_tn*18+4"
  let ph-ts = "max(.01,min(tnt.scale*menu.hudScale*((tnt.layoutOrder==0 && tnt.scale==1 && tnt.anchorX==0 && tnt.anchorY==0 && tnt.offsetX==20 && tnt.offsetY==90) ? vars.ph_fit : 1),(width-16)/max(132,tnt.width),(height-16)/vars.ph_th))"
  let ph-ping-w = "max(52,textWidth(pingData.pingText,7,'studio-sans-medium')+26)"
  let ph-fps-w = "max(56,textWidth(fpsData.text,7,'studio-sans-medium')+26)"
  let ph-cd-rows = "min(retainedCount('cooldownRows'),hud.maxVisible)"
  let ph-cd-h = "vars.ph_cd_rows==0 ? (hud.showHeader ? 28 : 10) : (hud.showHeader ? 28 : 6)+(hud.horizontal ? 1 : vars.ph_cd_rows)*max(14,15*hud.rowHeight/38)+(hud.horizontal ? 0 : max(0,vars.ph_cd_rows-1)*3)+5"
  let ph-cd-s = "max(.01,min(hud.scale*menu.hudScale*((hud.layoutOrder==0 && hud.scale==1 && hud.anchorX==1 && hud.anchorY==0 && hud.offsetX==-20 && hud.offsetY==90) ? vars.ph_fit : 1),(width-16)/vars.ph_cw,(height-16)/vars.ph_cd_h))"
  position = absolute; layout = absolute; padding = 0px; overflow = hidden
  background = "{{= vars.h_surface}}"
  tintOpacity = -1; backgroundOpacity = "{{= vars.o}}"; surfaceOpacity = 100%; raisedOpacity = 100%
  borderWidth = "{{= .65*vars.s}}"; borderColor = "{{= (theme.mode=='light' ? '#18181B22' : '#FFFFFF1A')}}"
  fontFamily = studio-sans; fontTracking = 0px; color = "{{= vars.d_text}}"
  animateEnter = true; enterOpacity = 0%; enterOffsetY = 3px; enterDuration = 180ms; exitDuration = 140ms
  transitionDuration = 170ms; easing = "cubic-bezier(0.22,1,0.36,1)"; animateLayout = true; animateValue = true; hoverScale = 1; pressScale = 1
}

style class ph-title { position = absolute; padding = 0px; fontFamily = studio-sans-medium; fontSize = "{{= 7.3*vars.s}}"; color = "{{= vars.d_text}}"; textOverflow = ellipsis; enterOpacity = 100%; enterOffsetY = 0px }
style class ph-label { position = absolute; padding = 0px; fontFamily = studio-sans; fontSize = "{{= 6.8*vars.s}}"; color = "{{= (theme.mode=='custom' ? lerpColor(theme.text,theme.surface,.16) : theme.mode=='light' ? '#3F3F46' : '#D2D2DA')}}"; textOverflow = marquee; fadeWidth = "{{= 3*vars.s}}"; scrollSpeed = 12; scrollPause = 2400ms; enterOpacity = 100%; enterOffsetY = 0px }
style class ph-pill-val { textMotion = numeric; position = absolute; padding = 0px; fontFamily = studio-sans-medium; fontSize = "{{= 6.2*vars.s}}"; color = "{{= vars.d_text}}"; textOverflow = ellipsis; textAlign = center; enterOpacity = 100%; enterOffsetY = 0px; animateValue = true; transitionDuration = 160ms }
style class ph-val { textMotion = numeric; position = absolute; padding = 0px; fontFamily = studio-sans-medium; fontSize = "{{= 7*vars.s}}"; color = "{{= vars.d_text}}"; textOverflow = ellipsis; enterOpacity = 100%; enterOffsetY = 0px; animateValue = true; transitionDuration = 160ms }
style class ph-small { position = absolute; padding = 0px; fontFamily = studio-sans-medium; fontSize = "{{= 5.8*vars.s}}"; color = "{{= vars.d_muted}}"; textOverflow = ellipsis; enterOpacity = 100%; enterOffsetY = 0px }
style class ph-row { position = absolute; layout = absolute; padding = 0px; animateEnter = true; enterOpacity = 0%; enterOffsetY = 2px; enterDuration = 160ms; exitDuration = 130ms; animationStagger = 12ms; transitionDuration = 160ms; easing = "cubic-bezier(0.22,1,0.36,1)"; animateLayout = true; animateValue = true }

hud (auto-layout = "true") {
  layout = absolute; width = 100%; height = 100%; enterOpacity = 100%; enterOffsetY = 0px
  cooldowns (id = "cooldowns", source = "game")
  minimap (id = "minimap", source = "game")
  inventory (id = "inventory", source = "game")
  tnt (id = "tnt", source = "game")
  armor (id = "armor", source = "game")
  potions (id = "potions", source = "game")
  target (id = "target", source = "game")
  crosshair (id = "crosshair", source = "game")
  scoreboard (id = "scoreboard", source = "game")
  keystrokes (id = "keystrokes", source = "game")
  coordinates (id = "coordinates", source = "game")
  ping (id = "ping", source = "game")
  fps (id = "fps", source = "game")
  betterChat (id = "betterChat", source = "game")
}

component "hud.cooldowns" {
  panel (id = "cooldowns-frame", style = "ph-frame", if = "{{= hudEditing || (hud.enabled && (!hud.hideWhenEmpty || retainedCount('cooldownRows')>0))}}") {
    let w = "hud.horizontal ? max(1,min(retainedCount('cooldownRows'),hud.maxVisible))*vars.ph_cw+max(0,min(retainedCount('cooldownRows'),hud.maxVisible)-1)*max(0,hud.spacing)*.4 : vars.ph_cw"
    let rh = "max(14,15*hud.rowHeight/38)"
    let rows = "min(retainedCount('cooldownRows'),hud.maxVisible)"
    let h = "vars.rows==0 ? (hud.showHeader ? 28 : 10) : (hud.showHeader ? 28 : 6)+(hud.horizontal ? 1 : vars.rows)*vars.rh+(hud.horizontal ? 0 : max(0,vars.rows-1)*3)+5"
    let o = "hud.opacity*theme.backgroundOpacity"
    let edge = "hud.layoutOrder>0 ? 0 : 8"
    let s = "max(.01,min(hud.scale*menu.hudScale*(vars.automatic ? vars.ph_fit : 1),(width-vars.edge*2)/vars.w,(height-vars.edge*2)/vars.h))"
    let automatic = "hud.layoutOrder==0 && hud.scale==1 && hud.anchorX==1 && hud.anchorY==0 && hud.offsetX==-20 && hud.offsetY==90"
    hudScale = "{{= vars.s}}"
    x = "{{= clamp(vars.automatic ? width-vars.w*vars.s-10 : hud.anchorX*(width-vars.w*vars.s)+hud.offsetX,vars.edge,max(vars.edge,width-vars.w*vars.s-vars.edge))}}"
    y = "{{= clamp(vars.automatic ? 38 : hud.anchorY*(height-vars.h*vars.s)+hud.offsetY,vars.edge,max(vars.edge,height-vars.h*vars.s-vars.edge))}}"
    width = "{{= vars.w*vars.s}}"; height = "{{= vars.h*vars.s}}"; borderRadius = "{{= vars.r_card*vars.s}}"
    background = "{{= hud.background ? vars.h_surface : '#00000000'}}"; borderWidth = "{{= hud.background ? .65*vars.s : 0}}"

    icon (src = "cooldowns", if = "hud.showHeader") { position = absolute; x = "{{= 8.5*vars.s}}"; y = "{{= 6*vars.s}}"; width = "{{= 10*vars.s}}"; height = "{{= 10*vars.s}}"; color = "{{= vars.d_accent}}" }
    text "{{i18n.hud.cooldowns.title}}" (style = "ph-title", if = "hud.showHeader") { x = "{{= 22*vars.s}}"; y = "{{= 7.35*vars.s}}"; width = "{{= (vars.w-30)*vars.s}}"; height = "{{= 7.3*vars.s}}" }
    rect (if = "{{= hud.showHeader && vars.rows>0}}") { position = absolute; x = 0px; y = "{{= 22*vars.s}}"; width = 100%; height = "{{= .75*vars.s}}"; fill = "{{= (theme.mode=='light' ? '#18181B14' : '#FFFFFF14')}}" }

    repeat (source = "cooldownRows", as = "r", key = "{{r.key}}") {
      retainExit = true; exitDuration = 140ms; position = absolute; layout = absolute; width = 100%; height = 100%
      div (style = "ph-row", if = "{{= index<hud.maxVisible}}") {
        let fs = "min(vars.rh-5,6.8*hud.fontScale)"
        let is = "min(vars.rh-3,9.5*hud.iconScale)"
        let tx = "hud.icons ? 8.5+vars.is+3.5 : 8.5"
        let pillH = "vars.fs+4.6"
        let timew = "hud.time ? min(max(24,textWidth(format(r.remainingSeconds,'cooldown-duration'),vars.fs,'studio-sans-medium')+11),max(0,vars.ph_cw-vars.tx-12)) : 0"
        x = "{{= (hud.horizontal ? index*(vars.ph_cw+max(0,hud.spacing)*.4) : 0)*vars.s}}"
        y = "{{= ((hud.showHeader ? 28 : 6)+(hud.horizontal ? 0 : index*(vars.rh+3)))*vars.s}}"
        width = "{{= vars.ph_cw*vars.s}}"; height = "{{= vars.rh*vars.s}}"
        item (src = "{{r.itemId}}", if = "hud.icons") { position = absolute; x = "{{= 8.5*vars.s}}"; y = "{{= (vars.rh-vars.is)/2*vars.s}}"; width = "{{= vars.is*vars.s}}"; height = "{{= vars.is*vars.s}}" }
        text "{{r.name}}" (style = "ph-label dfh-label", if = "hud.names") { x = "{{= vars.tx*vars.s}}"; y = "{{= (vars.rh-vars.fs)/2*vars.s}}"; width = "{{= max(0,vars.ph_cw-vars.tx-vars.timew-14)*vars.s}}"; height = "{{= vars.fs*vars.s}}"; fontSize = "{{= vars.fs*vars.s}}" }
        panel (if = "hud.time") {
          position = absolute; right = "{{= 8.5*vars.s}}"; y = "{{= (vars.rh-vars.pillH)/2*vars.s}}"; width = "{{= vars.timew*vars.s}}"; height = "{{= vars.pillH*vars.s}}"
          background = "{{= (theme.mode=='custom' ? alphaColor(theme.raised,.94) : theme.mode=='light' ? '#E7E7E2F2' : '#1E1E24F0')}}"; borderRadius = "{{= min(vars.pillH/2,vars.r_ctrl)*vars.s}}"; borderWidth = "{{= .6*vars.s}}"; borderColor = "{{= (theme.mode=='light' ? '#18181B1E' : '#FFFFFF20')}}"
          text "{{r.remainingSeconds|cooldown-duration}}" (style = "ph-pill-val") { x = 0px; y = "{{= 2.3*vars.s}}"; width = 100%; height = "{{= vars.fs*vars.s}}"; fontSize = "{{= vars.fs*vars.s}}" }
        }
        div (if = "hud.progress") { position = absolute; x = "{{= vars.tx*vars.s}}"; bottom = 0px; width = "{{= max(0,vars.ph_cw-vars.tx-vars.timew-14)*vars.s}}"; height = "{{= max(.8,min(1.8,hud.progressHeight*.45))*vars.s}}"
          rect { position = absolute; width = 100%; height = 100%; radius = "{{= .6*vars.s}}"; fill = "{{= (theme.mode=='custom' ? alphaColor(theme.background,.91) : theme.mode=='light' ? '#DFDFDAE8' : '#0B0B0EE8')}}" }
          rect { position = absolute; width = "{{= clamp(r.fraction,0,1)*100}}%"; height = 100%; radius = "{{= .6*vars.s}}"; fill = "{{= vars.d_accent}}" }
        }
      }
    }
  }
}

component "hud.potions" {
  panel (id = "potions-frame", style = "ph-frame", if = "{{= hudEditing || (potions.enabled && (!potions.hideWhenEmpty || retainedCount('potionRows')>0))}}") {
    let van = "potions.format=='vanilla'"
    let vanCount = "max(1,retainedCount('potionRows'))"
    let vanCellW = "potions.horizontal ? (potions.time ? 26 : 24) : (potions.time ? 54 : 24)"
    let vanCellH = "potions.horizontal ? (potions.time ? 34 : 24) : 24"
    let rh = "potions.format=='compact' ? 13 : potions.format=='pills' ? 16 : 14"
    let rows = "retainedCount('potionRows')"
    let w = "vars.van ? (potions.horizontal ? vars.vanCount*vars.vanCellW+(vars.vanCount-1)*3 : vars.vanCellW) : max(136,potions.width)"
    let h = "vars.van ? (potions.horizontal ? vars.vanCellH : vars.vanCount*vars.vanCellH+(vars.vanCount-1)*3) : (vars.rows==0 ? 28 : 28+vars.rows*vars.rh+max(0,vars.rows-1)*3+6)"
    let o = "potions.opacity*theme.backgroundOpacity"
    let edge = "potions.layoutOrder>0 ? 0 : 8"
    let s = "max(.01,min(potions.scale*menu.hudScale*(vars.automatic ? vars.ph_fit : 1),(width-vars.edge*2)/vars.w,(height-vars.edge*2)/vars.h))"
    let automatic = "potions.layoutOrder==0 && potions.scale==1 && potions.anchorX==0 && potions.anchorY==0 && potions.offsetX==20 && potions.offsetY==196"
    hudScale = "{{= vars.s}}"
    x = "{{= clamp(vars.automatic ? 10 : potions.anchorX*(width-vars.w*vars.s)+potions.offsetX,vars.edge,max(vars.edge,width-vars.w*vars.s-vars.edge))}}"
    y = "{{= clamp(vars.automatic ? vars.ph_top+((hudEditing || (tnt.enabled && vars.ph_tn>0)) ? vars.ph_th*vars.ph_ts+8 : 0) : potions.anchorY*(height-vars.h*vars.s)+potions.offsetY,vars.edge,max(vars.edge,height-vars.h*vars.s-vars.edge))}}"
    width = "{{= vars.w*vars.s}}"; height = "{{= vars.h*vars.s}}"
    borderRadius = "{{= (vars.van ? 0 : vars.r_card)*vars.s}}"
    blur = "{{= vars.van ? false : theme.effects.backgroundBlur}}"
    background = "{{= vars.van ? '#00000000' : vars.h_surface}}"
    borderWidth = "{{= vars.van ? 0 : .65*vars.s}}"

    icon (src = "potions", if = "{{= !vars.van}}") { position = absolute; x = "{{= 8.5*vars.s}}"; y = "{{= 6*vars.s}}"; width = "{{= 10*vars.s}}"; height = "{{= 10*vars.s}}"; color = "{{= vars.d_accent}}" }
    text "{{i18n.hud.potions.title}}" (style = "ph-title", if = "{{= !vars.van}}") { x = "{{= 22*vars.s}}"; y = "{{= 7.35*vars.s}}"; width = "{{= (vars.w-30)*vars.s}}"; height = "{{= 7.3*vars.s}}" }
    rect (if = "{{= !vars.van && vars.rows>0}}") { position = absolute; x = 0px; y = "{{= 22*vars.s}}"; width = 100%; height = "{{= .75*vars.s}}"; fill = "{{= (theme.mode=='light' ? '#18181B14' : '#FFFFFF14')}}" }

    repeat (source = "potionRows", as = "r", key = "{{r.id}}") {
      retainExit = true; exitDuration = 140ms; position = absolute; layout = absolute; width = 100%; height = 100%
      div (style = "ph-row", if = "{{= !vars.van}}") {
        let tx = "potions.icons ? 20.5 : 8.5"
        let tw = "potions.time ? min(max(25,textWidth(format(r.durationSeconds,'effect-duration'),6.2,'studio-sans-medium')+11),max(0,vars.w-vars.tx-12)) : 0"
        let nameW = "min(max(0,vars.w-vars.tx-vars.tw-16),textWidth(r.label,6.8,'studio-sans'))"
        let lineX = "vars.tx+vars.nameW+4"
        let lineW = "max(0,(vars.w-8.5-vars.tw-4)-vars.lineX)"
        x = 0px; y = "{{= (28+index*(vars.rh+3))*vars.s}}"; width = 100%; height = "{{= vars.rh*vars.s}}"
        image (src = "{{r.texture}}", if = "potions.icons") { position = absolute; x = "{{= 8.5*vars.s}}"; y = "{{= (vars.rh-9)/2*vars.s}}"; width = "{{= 9*vars.s}}"; height = "{{= 9*vars.s}}"; color = #FFFFFF }
        text "{{r.label}}" (style = "ph-label dfh-label") { x = "{{= vars.tx*vars.s}}"; y = "{{= (vars.rh-6.8)/2*vars.s}}"; width = "{{= max(0,vars.w-vars.tx-vars.tw-15)*vars.s}}"; height = "{{= 6.8*vars.s}}" }
        rect (if = "{{= potions.time && vars.lineW>5}}") {
          position = absolute; x = "{{= vars.lineX*vars.s}}"; y = "{{= (vars.rh/2-.4)*vars.s}}"; width = "{{= vars.lineW*vars.s}}"; height = "{{= .8*vars.s}}"
          fill = "{{= (theme.mode=='light' ? '#18181B14' : '#FFFFFF14')}}"
        }
        panel (if = "potions.time") {
          position = absolute; right = "{{= 8.5*vars.s}}"; y = "{{= (vars.rh-12)/2*vars.s}}"; width = "{{= vars.tw*vars.s}}"; height = "{{= 12*vars.s}}"
          background = "{{= (theme.mode=='custom' ? alphaColor(theme.raised,.94) : theme.mode=='light' ? '#E7E7E2F2' : '#1E1E24F0')}}"; borderRadius = "{{= vars.r_ctrl*vars.s}}"; borderWidth = "{{= .6*vars.s}}"; borderColor = "{{= (theme.mode=='light' ? '#18181B1E' : '#FFFFFF20')}}"
          text "{{r.durationSeconds|effect-duration}}" (style = "ph-pill-val") {
            x = 0px; y = "{{= 2.9*vars.s}}"; width = 100%; height = "{{= 6.2*vars.s}}"
            color = "{{= !r.infinite && r.durationSeconds<=10 ? (theme.mode=='light' ? '#DC2626' : '#E55656') : vars.d_text}}"
          }
        }
      }
      div (style = "ph-row", if = "potions.format=vanilla") {
        let rightAligned = "!potions.horizontal && potions.anchorX>.5"
        let boxX = "potions.horizontal ? (vars.vanCellW-24)/2 : (vars.rightAligned && potions.time ? 30 : 0)"
        let timeX = "potions.horizontal ? 0 : (vars.rightAligned ? 0 : 27)"
        let timeY = "potions.horizontal ? 25.5 : 7.5"
        let timeW = "potions.horizontal ? vars.vanCellW : 27"
        x = "{{= (potions.horizontal ? index*(vars.vanCellW+3) : 0)*vars.s}}"; y = "{{= (potions.horizontal ? 0 : index*(vars.vanCellH+3))*vars.s}}"; width = "{{= vars.vanCellW*vars.s}}"; height = "{{= vars.vanCellH*vars.s}}"
        sprite (src = "minecraft:hud/effect_background", u = "0", v = "0", region-width = "24", region-height = "24", source-width = "24", source-height = "24") { position = absolute; x = "{{= vars.boxX*vars.s}}"; y = 0px; width = "{{= 24*vars.s}}"; height = "{{= 24*vars.s}}"; color = "{{= alphaColor('#FFFFFF',max(.2,potions.opacity))}}" }
        image (src = "{{r.texture}}") { position = absolute; x = "{{= (vars.boxX+3)*vars.s}}"; y = "{{= 3*vars.s}}"; width = "{{= 18*vars.s}}"; height = "{{= 18*vars.s}}"; color = #FFFFFF }
        panel (if = "{{= potions.level && r.amplifier>0}}") { position = absolute; layout = absolute; padding = 0px; x = "{{= (vars.boxX+11)*vars.s}}"; y = "{{= 1.5*vars.s}}"; width = "{{= 11.5*vars.s}}"; height = "{{= 6.8*vars.s}}"; background = #A80B0E14; borderRadius = "{{= 2.2*vars.s}}"; borderWidth = 0px; enterOpacity = 100%; enterOffsetY = 0px; transitionDuration = 0ms
          text "{{r.levelText}}" (style = "ph-small") { x = 0px; y = "{{= .6*vars.s}}"; width = 100%; height = "{{= 5.6*vars.s}}"; fontSize = "{{= 5.2*vars.s}}"; textAlign = center; color = #FFFFFF }
        }
        panel (if = "potions.time") { position = absolute; layout = absolute; padding = 0px; x = "{{= vars.timeX*vars.s}}"; y = "{{= vars.timeY*vars.s}}"; width = "{{= vars.timeW*vars.s}}"; height = "{{= 8.5*vars.s}}"; background = #9E0B0E14; borderRadius = "{{= 3*vars.s}}"; borderWidth = 0px; enterOpacity = 100%; enterOffsetY = 0px; transitionDuration = 0ms
          text "{{r.durationSeconds|effect-duration}}" (style = "ph-pill-val") { x = 0px; y = "{{= 1*vars.s}}"; width = 100%; height = "{{= 6.5*vars.s}}"; fontSize = "{{= 6*vars.s}}"; color = "{{= !r.infinite && r.durationSeconds<=10 ? '#FCA5A5' : '#F4F7FC'}}" }
        }
      }
    }
  }
}

component "hud.tnt" {
  panel (id = "tnt-frame", style = "ph-frame", if = "{{= hudEditing || (tnt.enabled && retainedCount('tntRows')>0)}}") {
    let w = "max(132,tnt.width)"; let h = "vars.ph_th"; let o = "tnt.opacity*theme.backgroundOpacity"
    let edge = "tnt.layoutOrder>0 ? 0 : 8"
    let s = "max(.01,min(tnt.scale*menu.hudScale*(vars.automatic ? vars.ph_fit : 1),(width-vars.edge*2)/vars.w,(height-vars.edge*2)/vars.h))"
    let automatic = "tnt.layoutOrder==0 && tnt.scale==1 && tnt.anchorX==0 && tnt.anchorY==0 && tnt.offsetX==20 && tnt.offsetY==90"
    hudScale = "{{= vars.s}}"
    x = "{{= clamp(vars.automatic ? 10 : tnt.anchorX*(width-vars.w*vars.s)+tnt.offsetX,vars.edge,max(vars.edge,width-vars.w*vars.s-vars.edge))}}"
    y = "{{= clamp(vars.automatic ? vars.ph_top : tnt.anchorY*(height-vars.h*vars.s)+tnt.offsetY,vars.edge,max(vars.edge,height-vars.h*vars.s-vars.edge))}}"
    width = "{{= vars.w*vars.s}}"; height = "{{= vars.h*vars.s}}"; borderRadius = "{{= vars.r_card*vars.s}}"

    icon (src = "tnt") { position = absolute; x = "{{= 8.5*vars.s}}"; y = "{{= 6*vars.s}}"; width = "{{= 10*vars.s}}"; height = "{{= 10*vars.s}}"; color = "{{= (theme.mode=='light' ? '#DC2626' : '#E55656')}}" }
    text "{{i18n.hud.tnt.title}}" (style = "ph-title") { x = "{{= 22*vars.s}}"; y = "{{= 7.35*vars.s}}"; width = "{{= (vars.w-30)*vars.s}}"; height = "{{= 7.3*vars.s}}" }
    rect (if = "{{= retainedCount('tntRows')>0}}") { position = absolute; x = 0px; y = "{{= 22*vars.s}}"; width = 100%; height = "{{= .75*vars.s}}"; fill = "{{= (theme.mode=='light' ? '#18181B14' : '#FFFFFF14')}}" }

    repeat (source = "tntRows", as = "r", key = "{{r.id}}") {
      retainExit = true; exitDuration = 140ms; position = absolute; layout = absolute; width = 100%; height = 100%
      div (style = "ph-row", if = "{{= index<vars.ph_tn}}") {
        y = "{{= (28+index*18)*vars.s}}"; width = 100%; height = "{{= 16*vars.s}}"
        item (src = "minecraft:tnt") { position = absolute; x = "{{= 8.5*vars.s}}"; y = "{{= 3*vars.s}}"; width = "{{= 10*vars.s}}"; height = "{{= 10*vars.s}}" }
        text "{{i18n.hud.tnt.title}}" (style = "ph-label dfh-label", if = "!tnt.showDistance") { x = "{{= 22*vars.s}}"; y = "{{= 2.6*vars.s}}"; width = "{{= (vars.w-66)*vars.s}}"; height = "{{= 7*vars.s}}" }
        text "{{r.distance|fixed:0}}{{i18n.hud.unit.distanceSymbol}}" (style = "ph-label dfh-label", if = "tnt.showDistance") { x = "{{= 22*vars.s}}"; y = "{{= 2.6*vars.s}}"; width = "{{= (vars.w-66)*vars.s}}"; height = "{{= 7*vars.s}}" }
        panel {
          position = absolute; right = "{{= 8.5*vars.s}}"; y = "{{= 2*vars.s}}"; width = "{{= 32*vars.s}}"; height = "{{= 12*vars.s}}"
          background = "{{= r.seconds<1.2 ? alphaColor((theme.mode=='light' ? '#DC2626' : '#E55656'),.18) : (theme.mode=='custom' ? alphaColor(theme.raised,.94) : theme.mode=='light' ? '#E7E7E2F2' : '#1E1E24F0')}}"; borderRadius = "{{= vars.r_ctrl*vars.s}}"
          borderWidth = "{{= .6*vars.s}}"; borderColor = "{{= r.seconds<1.2 ? alphaColor((theme.mode=='light' ? '#DC2626' : '#E55656'),.5) : (theme.mode=='light' ? '#18181B1E' : '#FFFFFF20')}}"
          text "{{r.seconds|duration}}" (style = "ph-pill-val") { x = 0px; y = "{{= 2.9*vars.s}}"; width = 100%; height = "{{= 6.2*vars.s}}"; color = "{{= r.seconds<1.2 ? (theme.mode=='light' ? '#DC2626' : '#E55656') : vars.d_text}}" }
        }
        rect { position = absolute; x = "{{= 22*vars.s}}"; bottom = 0px; width = "{{= (vars.w-66)*vars.s}}"; height = "{{= 1.3*vars.s}}"; radius = "{{= .65*vars.s}}"; fill = "{{= (theme.mode=='custom' ? alphaColor(theme.background,.91) : theme.mode=='light' ? '#DFDFDAE8' : '#0B0B0EE8')}}" }
        rect { position = absolute; x = "{{= 22*vars.s}}"; bottom = 0px; width = "{{= (vars.w-66)*clamp(r.seconds/4,0,1)*vars.s}}"; height = "{{= 1.3*vars.s}}"; radius = "{{= .65*vars.s}}"; fill = "{{= r.seconds<1.2 ? (theme.mode=='light' ? '#DC2626' : '#E55656') : vars.d_accent}}" }
      }
    }
  }
}

component "hud.target" {
  panel (id = "target-frame", style = "ph-frame", if = "{{= target.style!='zakohp' && (hudEditing || (target.enabled && retainedCount('targetRows')>0))}}") {
    let w = "max(134,target.width)"; let h = "max(46,min(target.height,84))"; let o = "target.opacity*theme.backgroundOpacity"
    let edge = "target.layoutOrder>0 ? 0 : 8"
    let s = "max(.01,min(target.scale*menu.hudScale*(vars.automatic ? vars.ph_fit : 1),(width-vars.edge*2)/vars.w,(height-vars.edge*2)/vars.h))"
    let automatic = "target.layoutOrder==0 && target.scale==1 && target.style=='card' && target.anchorX==.5 && target.anchorY==.5 && target.offsetX==0 && target.offsetY==64"
    hudScale = "{{= vars.s}}"
    x = "{{= clamp(vars.automatic ? width/2-vars.w*vars.s/2 : target.anchorX*(width-vars.w*vars.s)+target.offsetX,vars.edge,max(vars.edge,width-vars.w*vars.s-vars.edge))}}"
    y = "{{= clamp(vars.automatic ? height/2+40*vars.ph_fit : target.anchorY*(height-vars.h*vars.s)+target.offsetY,vars.edge,max(vars.edge,height-vars.h*vars.s-vars.edge))}}"
    width = "{{= vars.w*vars.s}}"; height = "{{= vars.h*vars.s}}"; borderRadius = "{{= vars.r_card*vars.s}}"

    text "{{i18n.hud.target.empty}}" (style = "ph-label", if = "{{= retainedCount('targetRows')==0}}") { x = "{{= 9*vars.s}}"; y = "{{= (vars.h-6.8)/2*vars.s}}"; width = "{{= (vars.w-18)*vars.s}}"; height = "{{= 6.8*vars.s}}"; color = "{{= vars.d_muted}}" }
    repeat (source = "targetRows", as = "r", key = "{{r.entityId}}") {
      retainExit = true; exitDuration = 180ms; position = absolute; layout = absolute; width = 100%; height = 100%
      rect { position = absolute; x = "{{= 6.5*vars.s}}"; y = "{{= 5.5*vars.s}}"; width = "{{= 23*vars.s}}"; height = "{{= 23*vars.s}}"; radius = "{{= min(vars.r_sm+0.5,vars.r_card)*vars.s}}"; fill = "{{= (theme.mode=='custom' ? alphaColor(theme.background,.91) : theme.mode=='light' ? '#DFDFDAE8' : '#0B0B0EE8')}}"; stroke = "{{= (theme.mode=='light' ? '#18181B1E' : '#FFFFFF20')}}"; strokeWidth = "{{= .6*vars.s}}" }
      entity-head (source = "targetData") { position = absolute; x = "{{= 7*vars.s}}"; y = "{{= 6*vars.s}}"; width = "{{= 22*vars.s}}"; height = "{{= 22*vars.s}}"; radius = "{{= vars.r_sm*vars.s}}" }
      text "{{r.name}}" (style = "ph-title") { x = "{{= 34*vars.s}}"; y = "{{= 7*vars.s}}"; width = "{{= max(0,vars.w-74)*vars.s}}"; height = "{{= 8.5*vars.s}}"; fontSize = "{{= 7.6*vars.s}}" }
      text "HP / {{r.health|decimal:1}}" (style = "ph-label") { textMotion = numeric; x = "{{= 34*vars.s}}"; y = "{{= 19*vars.s}}"; width = "{{= 46*vars.s}}"; height = "{{= 7*vars.s}}"; fontSize = "{{= 6.3*vars.s}}" }
      panel (if = "target.distance") {
        position = absolute; right = "{{= 7*vars.s}}"; y = "{{= 6*vars.s}}"; width = "{{= 26*vars.s}}"; height = "{{= 11*vars.s}}"
        background = "{{= (theme.mode=='custom' ? alphaColor(theme.raised,.94) : theme.mode=='light' ? '#E7E7E2F2' : '#1E1E24F0')}}"; borderRadius = "{{= vars.r_ctrl*vars.s}}"; borderWidth = "{{= .55*vars.s}}"; borderColor = "{{= (theme.mode=='light' ? '#18181B1E' : '#FFFFFF20')}}"
        text "{{r.distance|fixed:1}}{{i18n.hud.unit.distanceSymbol}}" (style = "ph-pill-val") { x = 0px; y = "{{= 2.6*vars.s}}"; width = 100%; height = "{{= 5.8*vars.s}}"; fontSize = "{{= 5.7*vars.s}}" }
      }
      repeat (source = "targetEquipment", as = "gear", key = "{{gear.slot}}") { position = absolute; layout = absolute; width = 100%; height = 100%
        item (source = "gear", if = "{{= gear.count>0}}") { position = absolute; right = "{{= (7+index*9.5)*vars.s}}"; y = "{{= 18.5*vars.s}}"; width = "{{= 8.5*vars.s}}"; height = "{{= 8.5*vars.s}}" }
      }
      rect { position = absolute; x = "{{= 7*vars.s}}"; bottom = "{{= (target.absorption && r.absorption>0 ? 7.5 : 6.5)*vars.s}}"; width = "{{= (vars.w-14)*vars.s}}"; height = "{{= 3.6*vars.s}}"; radius = "{{= 1.8*vars.s}}"; fill = "{{= (theme.mode=='custom' ? alphaColor(theme.background,.91) : theme.mode=='light' ? '#DFDFDAE8' : '#0B0B0EE8')}}"; stroke = "{{= (theme.mode=='light' ? '#18181B22' : '#FFFFFF1A')}}"; strokeWidth = "{{= .45*vars.s}}" }
      rect { position = absolute; x = "{{= 7*vars.s}}"; bottom = "{{= (target.absorption && r.absorption>0 ? 7.5 : 6.5)*vars.s}}"; width = "{{= (vars.w-14)*clamp(r.fraction,0,1)*vars.s}}"; height = "{{= 3.6*vars.s}}"; radius = "{{= 1.8*vars.s}}"; fill = "{{= r.fraction<.25 ? (theme.mode=='light' ? '#DC2626' : '#E55656') : vars.d_accent}}"; transitionDuration = 220ms; animateLayout = true }
      rect (if = "{{= target.absorption && r.absorption>0}}") { position = absolute; x = "{{= 7*vars.s}}"; bottom = "{{= 4*vars.s}}"; width = "{{= (vars.w-14)*clamp(r.absorptionFraction,0,1)*vars.s}}"; height = "{{= 1.4*vars.s}}"; radius = "{{= .7*vars.s}}"; fill = "{{= (theme.mode=='light' ? '#D97706' : '#E5B63C')}}" }
    }
  }
  div (id = "target-native", if = "{{= target.style=='zakohp' && (hudEditing || (target.enabled && targetVisible && targetData.player))}}") {
    let hp = "(targetVisible && targetData.player) ? targetData.health : (hudEditing ? 20 : 0)"
    position = absolute; layout = absolute; hudScale = "{{target.scale}}"
    x = "{{= width/2+target.offsetX-max(6,len(format(vars.hp,'integer'))*6)*target.scale*(vars.hp<10 ? 2 : 1)/2}}"
    y = "{{= floor(height/(target.scale*(vars.hp<10 ? 2 : 1)*2))*target.scale*(vars.hp<10 ? 2 : 1)+4*target.scale*(vars.hp<10 ? 2 : 1)+target.offsetY-64}}"
    width = "{{= max(6,len(format(vars.hp,'integer'))*6)*target.scale*(vars.hp<10 ? 2 : 1)}}"; height = "{{= 9*target.scale*(vars.hp<10 ? 2 : 1)}}"
    game-text "{{= format(vars.hp,'integer')}}" { position = absolute; x = 50%; anchorX = .5; width = 100%; height = 100%; textAlign = pixel-center; pixelOriginX = "{{target.offsetX}}"; size = "{{= target.scale*(vars.hp<10 ? 2 : 1)}}"; shadow = 1; color = "{{= vars.hp<6 ? '#FCA5A5' : vars.hp<12 ? '#FDE68A' : '#55FF55'}}" }
  }
}

component "hud.inventory" {
  panel (id = "inventory-frame", style = "ph-frame", if = "{{= hudEditing || inventory.enabled}}") {
    let cell = "max(15,(max(152,inventory.width)-8-16)/9)"
    let gap = "2"
    let w = "8+9*vars.cell+8*vars.gap"
    let totalRows = "inventory.rows+(inventory.hotbar ? 1 : 0)"
    let h = "8+vars.totalRows*vars.cell+max(0,vars.totalRows-1)*vars.gap+(inventory.hotbar ? 4 : 0)"
    let o = "inventory.opacity*theme.backgroundOpacity"
    let edge = "inventory.layoutOrder>0 ? 0 : 8"
    let s = "max(.01,min(inventory.scale*menu.hudScale*(vars.automatic ? vars.ph_fit : 1),(width-vars.edge*2)/vars.w,(height-vars.edge*2)/vars.h))"
    let automatic = "inventory.layoutOrder==0 && inventory.scale==1 && inventory.anchorX==1 && inventory.anchorY==1 && inventory.offsetX==-20 && inventory.offsetY==-54"
    hudScale = "{{= vars.s}}"
    x = "{{= clamp(vars.automatic ? width-vars.w*vars.s-10 : inventory.anchorX*(width-vars.w*vars.s)+inventory.offsetX,vars.edge,max(vars.edge,width-vars.w*vars.s-vars.edge))}}"
    y = "{{= clamp(vars.automatic ? height-vars.h*vars.s-48 : inventory.anchorY*(height-vars.h*vars.s)+inventory.offsetY,vars.edge,max(vars.edge,height-vars.h*vars.s-vars.edge))}}"
    width = "{{= vars.w*vars.s}}"; height = "{{= vars.h*vars.s}}"; borderRadius = "{{= vars.r_ctrl*vars.s}}"

    rect (if = "inventory.hotbar") { position = absolute; x = "{{= 5*vars.s}}"; y = "{{= (4+inventory.rows*(vars.cell+vars.gap)+.7)*vars.s}}"; width = "{{= (vars.w-10)*vars.s}}"; height = "{{= .6*vars.s}}"; fill = "{{= (theme.mode=='light' ? '#18181B14' : '#FFFFFF14')}}" }
    repeat (source = "inventoryCells", as = "r", key = "{{r.slotId}}") {
      position = absolute; layout = absolute; width = 100%; height = 100%
      div {
        let is = "min(14,vars.cell-2.5)"
        position = absolute; layout = absolute
        x = "{{= (4+r.column*(vars.cell+vars.gap))*vars.s}}"
        y = "{{= (4+r.row*(vars.cell+vars.gap)+(r.hotbar ? 4 : 0))*vars.s}}"
        width = "{{= vars.cell*vars.s}}"; height = "{{= vars.cell*vars.s}}"
        rect { position = absolute; x = 0px; y = 0px; width = 100%; height = 100%; radius = "{{= min(vars.r_sm,3)*vars.s}}"; fill = "{{= (theme.mode=='custom' ? alphaColor(theme.background,.91) : theme.mode=='light' ? '#DFDFDAE8' : '#0B0B0EE8')}}"; stroke = "{{= (theme.mode=='light' ? '#18181B14' : '#FFFFFF14')}}"; strokeWidth = "{{= .4*vars.s}}" }
        item (source = "r.slot", if = "r.occupied") { position = absolute; x = "{{= (vars.cell-vars.is)/2*vars.s}}"; y = "{{= (vars.cell-vars.is)/2*vars.s}}"; width = "{{= vars.is*vars.s}}"; height = "{{= vars.is*vars.s}}" }
        text "{{r.count|integer}}" (style = "ph-val", if = "{{= inventory.counts && r.stacked}}") { right = "{{= 1.5*vars.s}}"; bottom = "{{= 1*vars.s}}"; width = "{{= (vars.cell-2)*vars.s}}"; height = "{{= 5.6*vars.s}}"; fontSize = "{{= 5.4*vars.s}}"; textAlign = right }
        rect (if = "{{= inventory.durability && r.damageable}}") { position = absolute; x = "{{= 2.5*vars.s}}"; bottom = "{{= 1.5*vars.s}}"; width = "{{= max(0,(vars.cell-5)*r.fraction)*vars.s}}"; height = "{{= 1.1*vars.s}}"; radius = "{{= .5*vars.s}}"; fill = "{{= r.fraction>.25 ? vars.d_accent : (theme.mode=='light' ? '#DC2626' : '#E55656')}}" }
      }
    }
  }
}

component "hud.armor" {
  panel (id = "armor-frame", style = "ph-frame", if = "{{= hudEditing || armor.enabled}}") {
    let durMode = "!armor.durability ? 'off' : (armor.durabilityStyle=='auto' ? (armor.style=='minecraft' ? 'vanilla' : 'bar') : armor.durabilityStyle)"
    let showVanillaBar = "vars.durMode=='vanilla'"
    let showBar = "vars.durMode=='bar'"
    let showNum = "vars.durMode=='bar' || vars.durMode=='text'"
    let w = "armor.horizontal ? (armor.style=='minecraft' ? 82 : (armor.names ? max(168,armor.width) : (armor.width==128 ? 96 : max(92,armor.width)))) : (armor.names ? max(112,armor.width) : vars.showNum ? (armor.width==128 ? 58 : max(54,armor.width*.52)) : 24)"
    let aboveH = "armor.horizontal && vars.showNum && armor.durabilityPosition=='above'"
    let itemTop = "vars.aboveH ? 9 : 0"
    let row = "armor.horizontal ? (armor.names && vars.showBar ? 28 : 22) + vars.itemTop : (armor.names && vars.showBar ? 26 : (vars.showBar && armor.style=='visual') ? 24 : 22)"
    let vPad = "(!armor.horizontal && armor.style=='visual') ? 3 : 0"
    let vGap = "(!armor.horizontal && armor.style=='minecraft') ? 2 : 0"
    let h = "armor.horizontal ? (armor.names ? (vars.showBar ? 40 : vars.showNum ? 37 : 30) : vars.showBar ? 31 : vars.showNum ? 29 : 22) : (vars.vPad*2 + 4*vars.row + 3*vars.vGap)"
    let o = "armor.opacity*theme.backgroundOpacity"
    let s = "max(.01,min(armor.scale,width/vars.w,height/vars.h))"
    let automatic = "armor.layoutOrder==0 && armor.scale==1 && armor.horizontal && armor.anchorX==0 && armor.anchorY==1 && armor.offsetX==20 && armor.offsetY==-56"
    hudScale = "{{= vars.s}}"
    x = "{{= clamp(vars.automatic ? floor((width-182)/2)+190+(leftMainArm ? 28 : 0) : armor.anchorX*(width-vars.w*vars.s)+armor.offsetX,0,max(0,width-vars.w*vars.s))}}"
    y = "{{= clamp(vars.automatic ? height-vars.h*vars.s-8 : armor.anchorY*(height-vars.h*vars.s)+armor.offsetY,0,max(0,height-vars.h*vars.s))}}"
    width = "{{= vars.w*vars.s}}"; height = "{{= vars.h*vars.s}}"; borderRadius = "{{= vars.r_card*vars.s}}"
    background = "{{= armor.style=='visual' ? vars.h_surface : '#00000000'}}"
    borderWidth = "{{= armor.style=='visual' ? .65*vars.s : 0}}"

    repeat (source = "armorSlots", as = "r", key = "{{r.slotId}}") {
      position = absolute; layout = absolute; width = 100%; height = 100%
      div {
        let mcH = "armor.horizontal && armor.style=='minecraft'"
        let slotLeft = "(!vars.mcH || index==0) ? 3 : 2"
        let slotRight = "(!vars.mcH || index==3) ? 3 : 2"
        let cw = "armor.horizontal ? (armor.style=='minecraft' ? (index==0 || index==3 ? 21 : 20) : (vars.w-8)/4) : vars.w"
        let cellX = "armor.horizontal ? (armor.style=='minecraft' ? (index==0 ? 0 : 1+index*20) : 4+index*vars.cw) : 0"
        let rh = "armor.horizontal ? vars.h : vars.row"
        let durCol = "r.fraction>=.5 ? lerpColor(theme.mode=='light' ? '#D97706' : '#F2C14E',theme.mode=='light' ? '#16A34A' : '#5CE08A',(r.fraction-.5)*2) : lerpColor(theme.mode=='light' ? '#DC2626' : '#F05A5A',theme.mode=='light' ? '#D97706' : '#F2C14E',r.fraction*2)"
        let vanillaBarCol = "r.fraction>=.5 ? lerpColor('#FFFF00','#00FF00',(r.fraction-.5)*2) : lerpColor('#FF0000','#FFFF00',r.fraction*2)"
        let slotY = "armor.horizontal ? vars.itemTop : (vars.rh-22)/2"
        let itemX = "armor.horizontal ? (armor.style=='minecraft' ? vars.slotLeft : (vars.cw-16)/2) : 4"
        let itemY = "armor.horizontal ? (vars.itemTop+3) : (vars.rh-16)/2"
        let vAbove = "!armor.horizontal && armor.durabilityPosition=='above'"
        let numY = "armor.horizontal ? (vars.aboveH ? 1.2 : 20) : (armor.names ? (vars.showBar ? (vars.vAbove ? 8.5 : 12) : (vars.vAbove ? 4.5 : 12.5)) : (vars.showBar ? (vars.vAbove ? (vars.rh-11)/2+4 : (vars.rh-11)/2) : (vars.rh-6)/2))"
        let nameY = "armor.horizontal ? (vars.aboveH ? 32 : (vars.showBar ? 31 : vars.showNum ? 29 : 21)) : (vars.showBar ? (vars.vAbove ? 16.5 : 4) : vars.showNum ? (vars.vAbove ? 12.5 : 4.5) : (vars.rh-6)/2)"
        let barY = "armor.horizontal ? (vars.aboveH ? 7.5 : 27.5) : (armor.names ? (vars.vAbove ? 4.5 : 20) : (vars.vAbove ? (vars.rh-11)/2 : (vars.rh-11)/2+8.5))"
        position = absolute; layout = absolute; x = "{{= vars.cellX*vars.s}}"; y = "{{= (armor.horizontal ? 0 : vars.vPad+index*(vars.rh+vars.vGap))*vars.s}}"; width = "{{= vars.cw*vars.s}}"; height = "{{= vars.rh*vars.s}}"
        rect (if = "{{= armor.style=='visual' && !armor.horizontal && index>0}}") { position = absolute; x = "{{= 4*vars.s}}"; y = 0px; width = "{{= max(0,vars.cw-8)*vars.s}}"; height = "{{= .5*vars.s}}"; fill = "{{= (theme.mode=='light' ? '#18181B12' : '#FFFFFF10')}}" }
        div (if = "armor.style=minecraft") { position = absolute; layout = absolute; x = "{{= (armor.horizontal ? 0 : 1)*vars.s}}"; y = "{{= vars.slotY*vars.s}}"; width = "{{= (vars.slotLeft+16+vars.slotRight)*vars.s}}"; height = "{{= 22*vars.s}}"
          sprite (if = "{{= !vars.mcH || index==0}}", src = "minecraft:hud/hotbar", u = "0", v = "0", region-width = "3", region-height = "22", source-width = "182", source-height = "22") { position = absolute; x = 0px; y = 0px; width = "{{= 3*vars.s}}"; height = 100%; color = #FFFFFF }
          sprite (if = "{{= vars.mcH && index>0}}", src = "minecraft:hud/hotbar", u = "21", v = "0", region-width = "2", region-height = "22", source-width = "182", source-height = "22") { position = absolute; x = 0px; y = 0px; width = "{{= 2*vars.s}}"; height = 100%; color = #FFFFFF }
          sprite (src = "minecraft:hud/hotbar", u = "3", v = "0", region-width = "16", region-height = "3", source-width = "182", source-height = "22") { position = absolute; x = "{{= vars.slotLeft*vars.s}}"; y = 0px; width = "{{= 16*vars.s}}"; height = "{{= 3*vars.s}}"; color = #FFFFFF }
          sprite (src = "minecraft:hud/hotbar", u = "3", v = "3", region-width = "16", region-height = "16", source-width = "182", source-height = "22") { position = absolute; x = "{{= vars.slotLeft*vars.s}}"; y = "{{= 3*vars.s}}"; width = "{{= 16*vars.s}}"; height = "{{= 16*vars.s}}"; color = "{{= alphaColor('#FFFFFF',vars.o)}}" }
          sprite (src = "minecraft:hud/hotbar", u = "3", v = "19", region-width = "16", region-height = "3", source-width = "182", source-height = "22") { position = absolute; x = "{{= vars.slotLeft*vars.s}}"; y = "{{= 19*vars.s}}"; width = "{{= 16*vars.s}}"; height = "{{= 3*vars.s}}"; color = #FFFFFF }
          sprite (if = "{{= vars.mcH && index<3}}", src = "minecraft:hud/hotbar", u = "19", v = "0", region-width = "2", region-height = "22", source-width = "182", source-height = "22") { position = absolute; x = "{{= (vars.slotLeft+16)*vars.s}}"; y = 0px; width = "{{= 2*vars.s}}"; height = 100%; color = #FFFFFF }
          sprite (if = "{{= !vars.mcH || index==3}}", src = "minecraft:hud/hotbar", u = "179", v = "0", region-width = "3", region-height = "22", source-width = "182", source-height = "22") { position = absolute; x = "{{= (vars.slotLeft+16)*vars.s}}"; y = 0px; width = "{{= 3*vars.s}}"; height = 100%; color = #FFFFFF }
        }
        item (source = "r.slot", if = "r.occupied") { position = absolute; x = "{{= vars.itemX*vars.s}}"; y = "{{= vars.itemY*vars.s}}"; width = "{{= 16*vars.s}}"; height = "{{= 16*vars.s}}" }
        rect (if = "{{= vars.showVanillaBar && r.occupied && r.damageable && (r.fraction<1 || hudEditing)}}") { position = absolute; x = "{{= (vars.itemX+2)*vars.s}}"; y = "{{= (vars.itemY+13)*vars.s}}"; width = "{{= 13*vars.s}}"; height = "{{= 2*vars.s}}"; radius = 0px; fill = #000000FF }
        rect (if = "{{= vars.showVanillaBar && r.occupied && r.damageable && (r.fraction<1 || hudEditing)}}") { position = absolute; x = "{{= (vars.itemX+2)*vars.s}}"; y = "{{= (vars.itemY+13)*vars.s}}"; width = "{{= clamp(floor(13*r.fraction+.5),0,13)*vars.s}}"; height = "{{= 1*vars.s}}"; radius = 0px; fill = "{{= vars.vanillaBarCol}}" }
        text "{{= armor.percent ? format(r.remainingPercent,'integer')+'%' : format(r.remainingDurability,'integer')}}" (style = "ph-small", if = "{{= vars.showNum && r.occupied && r.damageable}}") {
          textMotion = numeric; x = "{{= (armor.horizontal ? 1 : 25)*vars.s}}"; y = "{{= vars.numY*vars.s}}"
          width = "{{= (armor.horizontal ? vars.cw-2 : max(0,vars.w-29))*vars.s}}"; height = "{{= 6*vars.s}}"; fontSize = "{{= (armor.horizontal ? 5.3 : 5.6)*vars.s}}"
          fontFamily = studio-sans-medium
          textAlign = "{{= armor.horizontal ? 'center' : 'right'}}"; color = "{{= vars.durCol}}"
        }
        text "{{r.name}}" (style = "ph-small", if = "{{= armor.names && r.occupied}}") { x = "{{= (armor.horizontal ? 2 : 25)*vars.s}}"; y = "{{= vars.nameY*vars.s}}"; width = "{{= (armor.horizontal ? vars.cw-4 : max(0,vars.w-29))*vars.s}}"; height = "{{= 6*vars.s}}"; textAlign = "{{= armor.horizontal ? 'center' : 'left'}}" }
        rect (if = "{{= vars.showBar && r.occupied && r.damageable}}") { position = absolute; x = "{{= (armor.horizontal ? 3 : 25)*vars.s}}"; y = "{{= vars.barY*vars.s}}"; width = "{{= (armor.horizontal ? vars.cw-6 : max(0,vars.w-29))*vars.s}}"; height = "{{= 1*vars.s}}"; radius = "{{= .5*vars.s}}"; fill = "{{= (theme.mode=='custom' ? alphaColor(theme.background,.91) : theme.mode=='light' ? '#DFDFDAE8' : '#0B0B0EE8')}}" }
        rect (if = "{{= vars.showBar && r.occupied && r.damageable}}") { position = absolute; x = "{{= (armor.horizontal ? 3 : 25)*vars.s}}"; y = "{{= vars.barY*vars.s}}"; width = "{{= max(0,(armor.horizontal ? vars.cw-6 : max(0,vars.w-29))*r.fraction)*vars.s}}"; height = "{{= 1*vars.s}}"; radius = "{{= .5*vars.s}}"; fill = "{{= vars.durCol}}" }
      }
    }
  }
}

component "hud.scoreboard" {
  panel (id = "scoreboard-frame", style = "ph-frame", if = "{{= hudEditing || (scoreboard.enabled && scoreboardData.active)}}") {
    let titletext = "len(scoreboardData.title)>0 ? scoreboardData.title : (hudEditing ? i18n.module.scoreboard.label : '')"
    let visualfont = "scoreboard.font=='visual'"
    let sw = "!scoreboard.hideScores ? max(16,ceil(vars.visualfont ? maxRowTextWidth('scoreboardData.lines','score','',6.8,theme.fontFamily,false,theme.fontTracking) : maxRowGameTextWidth('scoreboardData.lines','','score',1))) : 0"
    let titlew = "vars.visualfont ? textWidth(format(vars.titletext,'plain'),7.5,theme.fontFamily,true,theme.fontTracking) : gameTextWidth(vars.titletext,1)"
    let rowsw = "vars.visualfont ? maxRowTextWidth('scoreboardData.lines','text|plain',!scoreboard.hideScores ? 'score' : '',7,theme.fontFamily,false,theme.fontTracking) : maxRowGameTextWidth('scoreboardData.lines','text',!scoreboard.hideScores ? 'score' : '',1)"
    let contentw = "max(vars.titlew,vars.rowsw+(!scoreboard.hideScores ? 12 : 0))+18"
    let w = "min(320,max(146,scoreboard.width,ceil(vars.contentw)))"
    let h = "27+max(1,retainedCount('scoreboardData.lines'))*12+6"
    let o = "scoreboard.opacity*theme.backgroundOpacity"
    let edge = "scoreboard.layoutOrder>0 ? 0 : 8"
    let s = "max(.01,min(scoreboard.scale*menu.hudScale*(vars.automatic ? vars.ph_fit : 1),(width-vars.edge*2)/vars.w,(height-vars.edge*2)/vars.h))"
    let automatic = "scoreboard.layoutOrder==0 && scoreboard.scale==1 && scoreboard.anchorX==1 && scoreboard.anchorY==.5 && scoreboard.offsetX==-10 && scoreboard.offsetY==0"
    hudScale = "{{= vars.s}}"
    x = "{{= clamp(vars.automatic ? width-vars.w*vars.s-10 : scoreboard.anchorX*(width-vars.w*vars.s)+scoreboard.offsetX,vars.edge,max(vars.edge,width-vars.w*vars.s-vars.edge))}}"
    y = "{{= clamp(vars.automatic ? 38+((hudEditing || (hud.enabled && (!hud.hideWhenEmpty || retainedCount('cooldownRows')>0))) ? vars.ph_cd_h*vars.ph_cd_s+8 : 0) : scoreboard.anchorY*(height-vars.h*vars.s)+scoreboard.offsetY,vars.edge,max(vars.edge,height-vars.h*vars.s-vars.edge))}}"
    width = "{{= vars.w*vars.s}}"; height = "{{= vars.h*vars.s}}"; borderRadius = "{{= vars.r_card*vars.s}}"
    background = "{{= scoreboard.background ? '#131317EB' : '#00000000'}}"
    borderWidth = "{{= scoreboard.background ? .65*vars.s : 0}}"
    borderColor = "{{= '#FFFFFF1A'}}"

    game-text "{{= vars.titletext}}" (if = "{{= !vars.visualfont}}") { overflow = hidden; position = absolute; padding = 0px; textAlign = center; x = "{{= 8*vars.s}}"; y = "{{= 6.5*vars.s}}"; width = "{{= (vars.w-16)*vars.s}}"; height = "{{= 10*vars.s}}"; size = "{{= vars.s}}"; shadow = 1; color = "{{= '#F2F2EF'}}" }
    text "{{= vars.titletext}}" (if = "{{= vars.visualfont}}", format = "minecraft") { position = absolute; padding = 0px; textAlign = center; fontFamily = studio-sans-medium; fontWeight = bold; fontSize = "{{= 7.5*vars.s}}"; textOverflow = ellipsis; x = "{{= 8*vars.s}}"; y = 0px; width = "{{= (vars.w-16)*vars.s}}"; height = "{{= 22*vars.s}}"; color = "{{= '#F2F2EF'}}" }
    rect (if = "scoreboard.background") { position = absolute; x = 0px; y = "{{= 22*vars.s}}"; width = 100%; height = "{{= .75*vars.s}}"; fill = "{{= '#FFFFFF14'}}" }

    repeat (source = "scoreboardData.lines", as = "r", key = "{{r.id}}") {
      position = absolute; layout = absolute; width = 100%; height = 100%
      game-text "{{r.text}}" (if = "{{= !vars.visualfont}}") { overflow = hidden; position = absolute; padding = 0px; x = "{{= 8*vars.s}}"; y = "{{= (27+index*12)*vars.s}}"; width = "{{= (vars.w-16-(!scoreboard.hideScores ? vars.sw+8 : 0))*vars.s}}"; height = "{{= 10*vars.s}}"; size = "{{= vars.s}}"; shadow = 1; color = "{{= '#F2F2EF'}}" }
      game-text "{{r.score}}" (if = "{{= !vars.visualfont && !scoreboard.hideScores}}") { overflow = hidden; position = absolute; padding = 0px; textAlign = right; right = "{{= 8*vars.s}}"; y = "{{= (27+index*12)*vars.s}}"; width = "{{= vars.sw*vars.s}}"; height = "{{= 10*vars.s}}"; size = "{{= vars.s}}"; shadow = 1; color = "#FF5555" }
      text "{{r.text}}" (if = "{{= vars.visualfont}}", format = "minecraft") { position = absolute; padding = 0px; fontSize = "{{= 7*vars.s}}"; textOverflow = ellipsis; x = "{{= 8*vars.s}}"; y = "{{= (27+index*12)*vars.s}}"; width = "{{= (vars.w-16-(!scoreboard.hideScores ? vars.sw+8 : 0))*vars.s}}"; height = "{{= 10*vars.s}}"; color = "{{= '#F2F2EF'}}" }
      text "{{r.score}}" (if = "{{= vars.visualfont && !scoreboard.hideScores}}") { position = absolute; padding = 0px; fontFamily = studio-sans-medium; fontSize = "{{= 6.8*vars.s}}"; textOverflow = ellipsis; textAlign = right; right = "{{= 8*vars.s}}"; y = "{{= (27+index*12)*vars.s}}"; width = "{{= vars.sw*vars.s}}"; height = "{{= 10*vars.s}}"; color = "#FF6B6B" }
    }
  }
}

component "hud.keystrokes" {
  panel (id = "keystrokes-frame", style = "ph-frame", if = "{{= hudEditing || keystrokes.enabled}}") {
    let w = "82"; let h = "keystrokes.space ? (keystrokes.mouseButtons ? 93 : 71) : (keystrokes.mouseButtons ? 80 : 56)"
    let o = "keystrokes.opacity*theme.backgroundOpacity"
    let edge = "keystrokes.layoutOrder>0 ? 0 : 8"
    let s = "max(.01,min(keystrokes.scale*menu.hudScale*(vars.automatic ? vars.ph_fit : 1),(width-vars.edge*2)/vars.w,(height-vars.edge*2)/vars.h))"
    let automatic = "keystrokes.layoutOrder==0 && keystrokes.scale==1 && keystrokes.anchorX==0 && keystrokes.anchorY==0 && keystrokes.offsetX==20 && keystrokes.offsetY==20"
    hudScale = "{{= vars.s}}"
    x = "{{= clamp(vars.automatic ? (hudEditing ? 258 : 10) : keystrokes.anchorX*(width-vars.w*vars.s)+keystrokes.offsetX,vars.edge,max(vars.edge,width-vars.w*vars.s-vars.edge))}}"
    y = "{{= clamp(vars.automatic ? (hudEditing ? height-vars.h*vars.s-10 : height-vars.h*vars.s-36) : keystrokes.anchorY*(height-vars.h*vars.s)+keystrokes.offsetY,vars.edge,max(vars.edge,height-vars.h*vars.s-vars.edge))}}"
    width = "{{= vars.w*vars.s}}"; height = "{{= vars.h*vars.s}}"; borderRadius = "{{= vars.r_card*vars.s}}"
    background = "{{= keystrokes.background ? vars.h_surface : '#00000000'}}"; borderWidth = "{{= keystrokes.background ? .65*vars.s : 0}}"

    panel { position = absolute; x = "{{= 30*vars.s}}"; y = "{{= 5.5*vars.s}}"; width = "{{= 22*vars.s}}"; height = "{{= 22*vars.s}}"; borderRadius = "{{= vars.r_sm*vars.s}}"; background = "{{= keystrokesData.w ? vars.d_accent : (theme.mode=='custom' ? alphaColor(theme.raised,.94) : theme.mode=='light' ? '#E7E7E2F2' : '#1E1E24F0')}}"; borderWidth = "{{= .55*vars.s}}"; borderColor = "{{= (theme.mode=='light' ? '#18181B1E' : '#FFFFFF20')}}"
      text "W" (style = "ph-val") { x = 0px; y = 0px; width = 100%; height = 100%; textAlign = center; color = "{{= keystrokesData.w ? vars.d_on_accent : vars.d_text}}" }
    }
    panel { position = absolute; x = "{{= 7*vars.s}}"; y = "{{= 28.5*vars.s}}"; width = "{{= 22*vars.s}}"; height = "{{= 22*vars.s}}"; borderRadius = "{{= vars.r_sm*vars.s}}"; background = "{{= keystrokesData.a ? vars.d_accent : (theme.mode=='custom' ? alphaColor(theme.raised,.94) : theme.mode=='light' ? '#E7E7E2F2' : '#1E1E24F0')}}"; borderWidth = "{{= .55*vars.s}}"; borderColor = "{{= (theme.mode=='light' ? '#18181B1E' : '#FFFFFF20')}}"
      text "A" (style = "ph-val") { x = 0px; y = 0px; width = 100%; height = 100%; textAlign = center; color = "{{= keystrokesData.a ? vars.d_on_accent : vars.d_text}}" }
    }
    panel { position = absolute; x = "{{= 30*vars.s}}"; y = "{{= 28.5*vars.s}}"; width = "{{= 22*vars.s}}"; height = "{{= 22*vars.s}}"; borderRadius = "{{= vars.r_sm*vars.s}}"; background = "{{= keystrokesData.s ? vars.d_accent : (theme.mode=='custom' ? alphaColor(theme.raised,.94) : theme.mode=='light' ? '#E7E7E2F2' : '#1E1E24F0')}}"; borderWidth = "{{= .55*vars.s}}"; borderColor = "{{= (theme.mode=='light' ? '#18181B1E' : '#FFFFFF20')}}"
      text "S" (style = "ph-val") { x = 0px; y = 0px; width = 100%; height = 100%; textAlign = center; color = "{{= keystrokesData.s ? vars.d_on_accent : vars.d_text}}" }
    }
    panel { position = absolute; x = "{{= 53*vars.s}}"; y = "{{= 28.5*vars.s}}"; width = "{{= 22*vars.s}}"; height = "{{= 22*vars.s}}"; borderRadius = "{{= vars.r_sm*vars.s}}"; background = "{{= keystrokesData.d ? vars.d_accent : (theme.mode=='custom' ? alphaColor(theme.raised,.94) : theme.mode=='light' ? '#E7E7E2F2' : '#1E1E24F0')}}"; borderWidth = "{{= .55*vars.s}}"; borderColor = "{{= (theme.mode=='light' ? '#18181B1E' : '#FFFFFF20')}}"
      text "D" (style = "ph-val") { x = 0px; y = 0px; width = 100%; height = 100%; textAlign = center; color = "{{= keystrokesData.d ? vars.d_on_accent : vars.d_text}}" }
    }
    panel (if = "keystrokes.mouseButtons") { position = absolute; x = "{{= 7*vars.s}}"; y = "{{= 55.5*vars.s}}"; width = "{{= 32*vars.s}}"; height = "{{= 19*vars.s}}"; borderRadius = "{{= vars.r_sm*vars.s}}"; background = "{{= keystrokesData.lmb ? vars.d_accent : (theme.mode=='custom' ? alphaColor(theme.raised,.94) : theme.mode=='light' ? '#E7E7E2F2' : '#1E1E24F0')}}"; borderWidth = "{{= .55*vars.s}}"; borderColor = "{{= (theme.mode=='light' ? '#18181B1E' : '#FFFFFF20')}}"
      text "{{i18n.hud.keystrokes.lmb}}" (style = "ph-small") { x = 0px; y = "{{= (keystrokes.cps ? 2.5 : 0)*vars.s}}"; width = 100%; height = "{{= (keystrokes.cps ? 7 : 19)*vars.s}}"; textAlign = center; color = "{{= keystrokesData.lmb ? vars.d_on_accent : vars.d_text}}" }
      text "{{keystrokesData.lmbText}}" (style = "ph-small", if = "keystrokes.cps") { textMotion = numeric; x = 0px; y = "{{= 10.5*vars.s}}"; width = 100%; height = "{{= 6*vars.s}}"; fontSize = "{{= 5.2*vars.s}}"; textAlign = center; color = "{{= keystrokesData.lmb ? vars.d_on_accent : vars.d_muted}}" }
    }
    panel (if = "keystrokes.mouseButtons") { position = absolute; x = "{{= 43*vars.s}}"; y = "{{= 55.5*vars.s}}"; width = "{{= 32*vars.s}}"; height = "{{= 19*vars.s}}"; borderRadius = "{{= vars.r_sm*vars.s}}"; background = "{{= keystrokesData.rmb ? vars.d_accent : (theme.mode=='custom' ? alphaColor(theme.raised,.94) : theme.mode=='light' ? '#E7E7E2F2' : '#1E1E24F0')}}"; borderWidth = "{{= .55*vars.s}}"; borderColor = "{{= (theme.mode=='light' ? '#18181B1E' : '#FFFFFF20')}}"
      text "{{i18n.hud.keystrokes.rmb}}" (style = "ph-small") { x = 0px; y = "{{= (keystrokes.cps ? 2.5 : 0)*vars.s}}"; width = 100%; height = "{{= (keystrokes.cps ? 7 : 19)*vars.s}}"; textAlign = center; color = "{{= keystrokesData.rmb ? vars.d_on_accent : vars.d_text}}" }
      text "{{keystrokesData.rmbText}}" (style = "ph-small", if = "keystrokes.cps") { textMotion = numeric; x = 0px; y = "{{= 10.5*vars.s}}"; width = 100%; height = "{{= 6*vars.s}}"; fontSize = "{{= 5.2*vars.s}}"; textAlign = center; color = "{{= keystrokesData.rmb ? vars.d_on_accent : vars.d_muted}}" }
    }
    panel (if = "keystrokes.space") { position = absolute; x = "{{= 7*vars.s}}"; y = "{{= (keystrokes.mouseButtons ? 77.5 : 55.5)*vars.s}}"; width = "{{= 68*vars.s}}"; height = "{{= 10*vars.s}}"; borderRadius = "{{= vars.r_sm*vars.s}}"; background = "{{= keystrokesData.space ? vars.d_accent : (theme.mode=='custom' ? alphaColor(theme.raised,.94) : theme.mode=='light' ? '#E7E7E2F2' : '#1E1E24F0')}}"; borderWidth = "{{= .55*vars.s}}"; borderColor = "{{= (theme.mode=='light' ? '#18181B1E' : '#FFFFFF20')}}"
      rect { position = absolute; x = "{{= 20*vars.s}}"; y = "{{= 4.5*vars.s}}"; width = "{{= 28*vars.s}}"; height = "{{= 1.2*vars.s}}"; radius = "{{= .6*vars.s}}"; fill = "{{= keystrokesData.space ? vars.d_on_accent : vars.d_muted}}" }
    }
  }
}

component "hud.coordinates" {
  panel (id = "coordinates-frame", style = "ph-frame", if = "{{= hudEditing || coordinates.enabled}}") {
    let w = "min(220,max(54,textWidth(coordinatesData.xyz,7,'studio-sans-medium')+28))"; let h = "20"; let o = "coordinates.opacity*theme.backgroundOpacity"
    let edge = "coordinates.layoutOrder>0 ? 0 : 8"
    let s = "max(.01,min(coordinates.scale*menu.hudScale*(vars.automatic ? vars.ph_fit : 1),(width-vars.edge*2)/vars.w,(height-vars.edge*2)/vars.h))"
    let automatic = "coordinates.layoutOrder==0 && coordinates.scale==1 && coordinates.anchorX==0 && coordinates.anchorY==1 && coordinates.offsetX==10 && coordinates.offsetY==-10"
    hudScale = "{{= vars.s}}"
    x = "{{= clamp(coordinates.anchorX*(width-vars.w*vars.s)+coordinates.offsetX,vars.edge,max(vars.edge,width-vars.w*vars.s-vars.edge))}}"
    y = "{{= clamp(coordinates.anchorY*(height-vars.h*vars.s)+coordinates.offsetY,vars.edge,max(vars.edge,height-vars.h*vars.s-vars.edge))}}"
    width = "{{= vars.w*vars.s}}"; height = "{{= vars.h*vars.s}}"; borderRadius = "{{= min(10,vars.r_card)*vars.s}}"
    background = "{{= coordinates.background ? vars.h_surface : '#00000000'}}"
    borderWidth = "{{= coordinates.background ? .75*vars.s : 0}}"
    borderColor = "{{= (theme.mode=='light' ? '#18181B1E' : '#FFFFFF20')}}"
    icon (src = "coordinates") { position = absolute; x = "{{= 7.5*vars.s}}"; y = "{{= 5.25*vars.s}}"; width = "{{= 9.5*vars.s}}"; height = "{{= 9.5*vars.s}}"; color = "{{= vars.d_accent}}" }
    text "{{coordinatesData.xyz}}" (style = "ph-val") { x = "{{= 20.5*vars.s}}"; y = 0px; width = "{{= (vars.w-27)*vars.s}}"; height = 100% }
  }
}

component "hud.ping" {
  panel (id = "ping-frame", style = "ph-frame", if = "{{= hudEditing || ping.enabled}}") {
    let w = "vars.ph_ping_w"; let h = "20"; let o = "ping.opacity*theme.backgroundOpacity"
    let edge = "ping.layoutOrder>0 ? 0 : 8"
    let s = "max(.01,min(ping.scale*menu.hudScale*(vars.automatic ? vars.ph_fit : 1),(width-vars.edge*2)/vars.w,(height-vars.edge*2)/vars.h))"
    let automatic = "ping.layoutOrder==0 && ping.scale==1 && ping.anchorX==1 && ping.anchorY==0 && ping.offsetX==-10 && ping.offsetY==10"
    hudScale = "{{= vars.s}}"
    x = "{{= clamp(ping.anchorX*(width-vars.w*vars.s)+ping.offsetX,vars.edge,max(vars.edge,width-vars.w*vars.s-vars.edge))}}"
    y = "{{= clamp(ping.anchorY*(height-vars.h*vars.s)+ping.offsetY,vars.edge,max(vars.edge,height-vars.h*vars.s-vars.edge))}}"
    width = "{{= vars.w*vars.s}}"; height = "{{= vars.h*vars.s}}"; borderRadius = "{{= min(10,vars.r_card)*vars.s}}"
    background = "{{= ping.background ? vars.h_surface : '#00000000'}}"
    borderWidth = "{{= ping.background ? .75*vars.s : 0}}"
    borderColor = "{{= (theme.mode=='light' ? '#18181B1E' : '#FFFFFF20')}}"
    icon (src = "network") { position = absolute; x = "{{= 7.5*vars.s}}"; y = "{{= 5.25*vars.s}}"; width = "{{= 9.5*vars.s}}"; height = "{{= 9.5*vars.s}}"; color = "{{= ping.colored ? pingData.pingColor : vars.d_accent}}" }
    text "{{pingData.pingText}}" (style = "ph-val") { x = "{{= 20.5*vars.s}}"; y = 0px; width = "{{= (vars.w-26)*vars.s}}"; height = 100% }
  }
}

component "hud.fps" {
  panel (id = "fps-frame", style = "ph-frame", if = "{{= hudEditing || fps.enabled}}") {
    let w = "vars.ph_fps_w"; let h = "20"; let o = "fps.opacity*theme.backgroundOpacity"
    let edge = "fps.layoutOrder>0 ? 0 : 8"
    let s = "max(.01,min(fps.scale*menu.hudScale*(vars.automatic ? vars.ph_fit : 1),(width-vars.edge*2)/vars.w,(height-vars.edge*2)/vars.h))"
    let automatic = "fps.layoutOrder==0 && fps.scale==1 && fps.anchorX==1 && fps.anchorY==0 && fps.offsetX==-10 && fps.offsetY==36"
    hudScale = "{{= vars.s}}"
    x = "{{= clamp(vars.automatic ? width-10-vars.ph_ping_w*vars.s-6-vars.w*vars.s : fps.anchorX*(width-vars.w*vars.s)+fps.offsetX,vars.edge,max(vars.edge,width-vars.w*vars.s-vars.edge))}}"
    y = "{{= clamp(vars.automatic ? 10 : fps.anchorY*(height-vars.h*vars.s)+fps.offsetY,vars.edge,max(vars.edge,height-vars.h*vars.s-vars.edge))}}"
    width = "{{= vars.w*vars.s}}"; height = "{{= vars.h*vars.s}}"; borderRadius = "{{= min(10,vars.r_card)*vars.s}}"
    background = "{{= fps.background ? vars.h_surface : '#00000000'}}"
    borderWidth = "{{= fps.background ? .75*vars.s : 0}}"
    borderColor = "{{= (theme.mode=='light' ? '#18181B1E' : '#FFFFFF20')}}"
    icon (src = "fps") { position = absolute; x = "{{= 7.5*vars.s}}"; y = "{{= 5.25*vars.s}}"; width = "{{= 9.5*vars.s}}"; height = "{{= 9.5*vars.s}}"; color = "{{= fps.colored ? fpsData.color : vars.d_accent}}" }
    text "{{fpsData.text}}" (style = "ph-val") { x = "{{= 20.5*vars.s}}"; y = 0px; width = "{{= (vars.w-26)*vars.s}}"; height = 100% }
  }
}

component "hud.betterChat" {
  panel (id = "betterChat-frame", style = "ph-frame", if = "hudEditing") {
    let w = "240"; let h = "96"; let o = "max(.25,betterChat.backgroundOpacity)*theme.backgroundOpacity"
    let s = "max(.01,min(betterChat.scale,width/vars.w,height/vars.h))"
    hudScale = "{{= vars.s}}"
    x = "{{= clamp(betterChat.anchorX*(width-vars.w*vars.s)+betterChat.offsetX,0,max(0,width-vars.w*vars.s))}}"
    y = "{{= clamp(betterChat.anchorY*(height-vars.h*vars.s)+betterChat.offsetY,0,max(0,height-vars.h*vars.s))}}"
    width = "{{= vars.w*vars.s}}"; height = "{{= vars.h*vars.s}}"; borderRadius = "{{= vars.r_card*vars.s}}"
    background = "{{= betterChat.backgroundStyle=='transparent' ? '#00000000' : vars.h_surface}}"
    borderWidth = "{{= .65*vars.s}}"
    icon (src = "better-chat") { position = absolute; x = "{{= 8.5*vars.s}}"; y = "{{= 6*vars.s}}"; width = "{{= 10*vars.s}}"; height = "{{= 10*vars.s}}"; color = "{{= vars.d_accent}}" }
    text "{{i18n.module.betterChat.label}}" (style = "ph-title") { x = "{{= 22*vars.s}}"; y = "{{= 7.35*vars.s}}"; width = "{{= (vars.w-30)*vars.s}}"; height = "{{= 7.3*vars.s}}" }
    rect { position = absolute; x = 0px; y = "{{= 22*vars.s}}"; width = 100%; height = "{{= .75*vars.s}}"; fill = "{{= (theme.mode=='light' ? '#18181B14' : '#FFFFFF14')}}" }
    text "{{= (betterChat.timestamps ? '[14:20] ' : '') + i18n.hud.preview.chat.line1 + (betterChat.stackDuplicates ? ' [x2]' : '')}}" (style = "ph-label") { x = "{{= 8.5*vars.s}}"; y = "{{= 29*vars.s}}"; width = "{{= (vars.w-17)*vars.s}}"; height = "{{= 8*vars.s}}" }
    text "{{= (betterChat.timestamps ? '[14:21] ' : '') + i18n.hud.preview.chat.line2}}" (style = "ph-label") { x = "{{= 8.5*vars.s}}"; y = "{{= 43*vars.s}}"; width = "{{= (vars.w-17)*vars.s}}"; height = "{{= 8*vars.s}}" }
    text "{{= (betterChat.timestamps ? '[14:21] ' : '') + i18n.hud.preview.chat.line3}}" (style = "ph-small") { x = "{{= 8.5*vars.s}}"; y = "{{= 58*vars.s}}"; width = "{{= (vars.w-17)*vars.s}}"; height = "{{= 7*vars.s}}" }
  }
}

component "hud.minimap" {
  panel (id = "minimap-frame", style = "ph-frame", if = "hudEditing") {
    let w = "max(72,minimap.size)"; let h = "vars.w+(minimap.showCoords ? 16 : 0)"
    let o = "minimap.opacity*theme.backgroundOpacity"
    let s = "max(.01,min(minimap.scale,width/vars.w,height/vars.h))"
    hudScale = "{{= vars.s}}"
    x = "{{= clamp(minimap.anchorX*(width-vars.w*vars.s)+minimap.offsetX,0,max(0,width-vars.w*vars.s))}}"
    y = "{{= clamp(minimap.anchorY*(height-vars.h*vars.s)+minimap.offsetY,0,max(0,height-vars.h*vars.s))}}"
    width = "{{= vars.w*vars.s}}"; height = "{{= vars.h*vars.s}}"
    overflow = visible; blur = false; background = #00000000; borderWidth = 0px
    animateEnter = false; animateLayout = false; transitionDuration = 0ms
    minimap (source = "native") { position = absolute; x = 0px; y = 0px; width = 100%; height = 100%; enterOpacity = 100%; enterOffsetY = 0px; transitionDuration = 0ms }
  }
}

component "hud.crosshair" {
  crosshair (id = "crosshair-native", source = "native", if = "{{= hudEditing || (crosshair.enabled && crosshairVisible)}}") {
    position = absolute; x = 0px; y = 0px; width = 100%; height = 100%; enterOpacity = 100%; enterOffsetY = 0px; transitionDuration = 0ms
  }
}
