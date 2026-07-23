package advRocketry.mixins;

import advRocketry.Utils.SciFiSounds;
import ARLib.gui.modules.guiModuleButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(guiModuleButton.class)
public class GuiModuleButtonMixin {

    @Inject(method = "client_onMouseClick", at = @At(value = "INVOKE", target = "LARLib/gui/modules/guiModuleButton;onButtonClicked()V", shift = At.Shift.BEFORE))
    private void onButtonClick(double x, double y, int button, CallbackInfo ci) {
        SciFiSounds.playClick();
    }
}
