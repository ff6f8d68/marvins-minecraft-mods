package advRocketry.Registry;

import advRocketry.Main;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, Main.MODID);

    public static final Supplier<SoundEvent> UI_OPEN = SOUNDS.register("ui.open",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Main.MODID, "ui.open")));
    public static final Supplier<SoundEvent> UI_CLOSE = SOUNDS.register("ui.close",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Main.MODID, "ui.close")));
    public static final Supplier<SoundEvent> UI_CLICK = SOUNDS.register("ui.click",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Main.MODID, "ui.click")));
    public static final Supplier<SoundEvent> UI_HOVER = SOUNDS.register("ui.hover",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Main.MODID, "ui.hover")));
}
