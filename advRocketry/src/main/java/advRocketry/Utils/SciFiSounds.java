package advRocketry.Utils;

import advRocketry.Registry.ModSounds;
import net.minecraft.client.Minecraft;

public class SciFiSounds {

    public static void playOpen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(ModSounds.UI_OPEN.get(), 0.5f, 1.0f);
        }
    }

    public static void playClose() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(ModSounds.UI_CLOSE.get(), 0.5f, 1.0f);
        }
    }

    public static void playClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(ModSounds.UI_CLICK.get(), 0.6f, 1.2f);
        }
    }

    public static void playHover() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(ModSounds.UI_HOVER.get(), 0.3f, 1.5f);
        }
    }
}
