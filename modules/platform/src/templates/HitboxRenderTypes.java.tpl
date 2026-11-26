package dev.nightclient.platform;
import net.minecraft.client.renderer.RenderType;
import java.util.OptionalDouble;
@@BOX_TYPE_IMPORTS@@

public abstract class HitboxRenderTypes extends RenderType {
    private static RenderType lines;
    private HitboxRenderTypes(){super(@@BOX_TYPE_SUPER@@);}
    public static RenderType lines(float width){
        if(lines!=null)return lines;
        @@BOX_TYPE_PREPARE@@
        lines=@@BOX_TYPE_CREATE@@;return lines;
    }
}
