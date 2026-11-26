package dev.nightclient.platform.mixin;

import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BufferUploader.class)
public interface BufferUploaderCacheAccessor {
    @Accessor("lastVertexArrayObject") static void nightclient$lastVertexArrayObject(int value){throw new AssertionError();}
    @Accessor("lastVertexBufferObject") static void nightclient$lastVertexBufferObject(int value){throw new AssertionError();}
    @Accessor("lastIndexBufferObject") static void nightclient$lastIndexBufferObject(int value){throw new AssertionError();}
    @Accessor("lastFormat") static void nightclient$lastFormat(VertexFormat value){throw new AssertionError();}
}
