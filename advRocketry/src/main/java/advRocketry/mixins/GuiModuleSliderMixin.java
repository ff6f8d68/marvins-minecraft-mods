package advRocketry.mixins;

import advRocketry.Utils.SciFiSounds;
import ARLib.gui.modules.guiModuleSlider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(guiModuleSlider.class)
public class GuiModuleSliderMixin {

    @Inject(method = "client_onMouseClick", at = @At("HEAD"))
    private void onSliderClick(double mouseX, double mouseY, int button, CallbackInfo ci) {
        SciFiSounds.playClick();
    }
}
