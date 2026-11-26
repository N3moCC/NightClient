package dev.nightclient.platform.mixin;
import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(ChatComponent.class)
public interface ChatLinesAccessor {
    @Accessor("allMessages") List<?> nightclient$allMessages();
    @Accessor("trimmedMessages") List<?> nightclient$visibleLines();
    @Accessor("chatScrollbarPos") int nightclient$scrollPosition();
    @Invoker("getScale") double nightclient$chatScale();
    @Invoker("getLinesPerPage") int nightclient$linesPerPage();
    @Invoker("getWidth") int nightclient$chatWidth();
    @Invoker("isChatFocused") boolean nightclient$chatFocused();
    @Invoker("rescaleChat") void nightclient$rescaleChat();
    @@LINE_HEIGHT_ACCESSOR@@
}
