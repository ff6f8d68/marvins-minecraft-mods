package advRocketry.mixins;

import advRocketry.Client.SciFiColors;
import advRocketry.Utils.SciFiSounds;
import ARLib.gui.ModularScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModularScreen.class)
public class ModularScreenMixin {

    @Shadow(remap = false) int guiW;
    @Shadow(remap = false) int guiH;
    @Shadow(remap = false) int leftOffset;
    @Shadow(remap = false) int topOffset;

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        SciFiSounds.playOpen();
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        SciFiSounds.playClose();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        int x = leftOffset;
        int y = topOffset;
        int w = guiW;
        int h = guiH;
        int corner = SciFiColors.CORNER_SIZE;

        // glowing border
        guiGraphics.fill(x - 1, y - 1, x + w + 1, y, SciFiColors.BORDER_GLOW);
        guiGraphics.fill(x - 1, y + h, x + w + 1, y + h + 1, SciFiColors.BORDER_GLOW);
        guiGraphics.fill(x - 1, y, x, y + h, SciFiColors.BORDER_GLOW);
        guiGraphics.fill(x + w, y, x + w + 1, y + h, SciFiColors.BORDER_GLOW);

        // corner brackets (top-left)
        guiGraphics.fill(x, y, x + corner, y + 1, SciFiColors.PRIMARY_CYAN);
        guiGraphics.fill(x, y, x + 1, y + corner, SciFiColors.PRIMARY_CYAN);

        // top-right
        guiGraphics.fill(x + w - corner, y, x + w, y + 1, SciFiColors.PRIMARY_CYAN);
        guiGraphics.fill(x + w - 1, y, x + w, y + corner, SciFiColors.PRIMARY_CYAN);

        // bottom-left
        guiGraphics.fill(x, y + h - 1, x + corner, y + h, SciFiColors.PRIMARY_CYAN);
        guiGraphics.fill(x, y + h - corner, x + 1, y + h, SciFiColors.PRIMARY_CYAN);

        // bottom-right
        guiGraphics.fill(x + w - corner, y + h - 1, x + w, y + h, SciFiColors.PRIMARY_CYAN);
        guiGraphics.fill(x + w - 1, y + h - corner, x + w, y + h, SciFiColors.PRIMARY_CYAN);
    }
}
