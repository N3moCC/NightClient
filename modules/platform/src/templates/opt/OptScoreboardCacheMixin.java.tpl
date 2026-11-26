package dev.nightclient.platform.mixin;

import dev.nightclient.platform.OptimizationBridge;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import @@SB_OWNER_IMPORT@@;
import @@SB_GFX_IMPORT@@;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(@@SB_OWNER@@.class)
public abstract class OptScoreboardCacheMixin {
  @Shadow @Final private Minecraft minecraft;
  @Shadow @Final private static Comparator<PlayerScoreEntry> SCORE_DISPLAY_ORDER;
  @Shadow public abstract Font getFont();

  @Unique private Objective ncLastObjective;
  @Unique private long ncLastTick = Long.MIN_VALUE;
  @Unique private FormattedCharSequence ncTitleSeq;
  @Unique private int ncTitleWidth;
  @Unique private int ncMaxWidth;
  @Unique private int ncEntryCount;
  @Unique private final FormattedCharSequence[] ncNames = new FormattedCharSequence[15];
  @Unique private final FormattedCharSequence[] ncScores = new FormattedCharSequence[15];
  @Unique private final int[] ncScoreWidths = new int[15];
  @Unique private PlayerScoreEntry[] ncSortBuf = new PlayerScoreEntry[32];

  @Inject(
      method = "displayScoreboardSidebar(@@SB_GFX_DESC@@Lnet/minecraft/world/scores/Objective;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void nightclient$cachedSidebar(@@SB_GFX@@ gfx, Objective objective, CallbackInfo ci) {
    if (!OptimizationBridge.opt062ScoreboardCache || objective == null) return;
    dev.nightclient.runtime.ClientRuntime ncRt = dev.nightclient.runtime.ClientRuntime.get();
    if (ncRt != null && ncRt.settings().scoreboard.enabled) return;
    Font font = this.getFont();
    long tick = this.minecraft.level != null ? this.minecraft.level.getGameTime() : Long.MIN_VALUE;
    if (objective != this.ncLastObjective || tick != this.ncLastTick || this.ncTitleSeq == null) {
      this.ncLastObjective = objective;
      this.ncLastTick = tick;
      Scoreboard scoreboard = objective.getScoreboard();
      NumberFormat numberFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
      Collection<PlayerScoreEntry> raw = scoreboard.listPlayerScores(objective);
      int n = 0;
      for (PlayerScoreEntry e : raw) {
        if (e.isHidden()) continue;
        if (n >= this.ncSortBuf.length) {
          this.ncSortBuf = Arrays.copyOf(this.ncSortBuf, this.ncSortBuf.length << 1);
        }
        this.ncSortBuf[n++] = e;
      }
      if (n > 1) {
        Arrays.sort(this.ncSortBuf, 0, n, SCORE_DISPLAY_ORDER);
      }
      int count = Math.min(15, n);
      this.ncEntryCount = count;

      Component titleComp = objective.getDisplayName();
      this.ncTitleSeq = titleComp.getVisualOrderText();
      this.ncTitleWidth = font.width(this.ncTitleSeq);
      int maxW = this.ncTitleWidth;
      int colonW = font.width(": ");
      for (int i = 0; i < count; i++) {
        PlayerScoreEntry entry = this.ncSortBuf[i];
        Component formattedName = PlayerTeam.formatNameForTeam(
            scoreboard.getPlayersTeam(entry.owner()), entry.ownerName());
        Component scoreComp = entry.formatValue(numberFormat);
        FormattedCharSequence nameSeq = formattedName.getVisualOrderText();
        FormattedCharSequence scoreSeq = scoreComp.getVisualOrderText();
        int sw = font.width(scoreSeq);
        int nw = font.width(nameSeq);
        this.ncNames[i] = nameSeq;
        this.ncScores[i] = scoreSeq;
        this.ncScoreWidths[i] = sw;
        int rowW = nw + (sw > 0 ? colonW + sw : 0);
        if (rowW > maxW) maxW = rowW;
      }
      this.ncMaxWidth = maxW;
    }

    final int count = this.ncEntryCount;
    final int maxW = this.ncMaxWidth;
    final int totalH = count * 9;
    final int bottomY = gfx.guiHeight() / 2 + totalH / 3;
    final int leftX = gfx.guiWidth() - maxW - 3;
    final int rightX = gfx.guiWidth() - 3 + 2;
    final int bodyBg = this.minecraft.options.getBackgroundColor(0.3f);
    final int headerBg = this.minecraft.options.getBackgroundColor(0.4f);
    final int topY = bottomY - count * 9;

    gfx.fill(leftX - 2, topY - 9 - 1, rightX, topY - 1, headerBg);
    gfx.fill(leftX - 2, topY - 1, rightX, bottomY, bodyBg);
    gfx.@@SB_TEXT_METHOD@@(font, this.ncTitleSeq, leftX + maxW / 2 - this.ncTitleWidth / 2, topY - 9, -1, false);
    for (int i = 0; i < count; i++) {
      int rowY = bottomY - (count - i) * 9;
      gfx.@@SB_TEXT_METHOD@@(font, this.ncNames[i], leftX, rowY, -1, false);
      int sw = this.ncScoreWidths[i];
      if (sw > 0) {
        gfx.@@SB_TEXT_METHOD@@(font, this.ncScores[i], rightX - sw, rowY, -1, false);
      }
    }
    ci.cancel();
  }
}
