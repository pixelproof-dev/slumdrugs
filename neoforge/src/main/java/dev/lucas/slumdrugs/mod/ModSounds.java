package dev.lucas.slumdrugs.mod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every sound a station makes, as the mod's own events. Each one is mapped in
 * {@code assets/slumdrugs/sounds.json} to a vanilla sound for now, with its own subtitle, so
 * the stations already sound like themselves and a recorded sound drops in later by editing
 * that one file. Code never names a vanilla sound for a station directly.
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, SlumDrugsMod.ID);

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () ->
                SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(SlumDrugsMod.ID, name)));
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> FRAME_PLANT = sound("station.frame.plant");
    public static final DeferredHolder<SoundEvent, SoundEvent> FRAME_HARVEST = sound("station.frame.harvest");
    public static final DeferredHolder<SoundEvent, SoundEvent> LOFT_HANG = sound("station.loft.hang");
    public static final DeferredHolder<SoundEvent, SoundEvent> LOFT_TAKE = sound("station.loft.take");
    public static final DeferredHolder<SoundEvent, SoundEvent> PRESS_LOAD = sound("station.press.load");
    public static final DeferredHolder<SoundEvent, SoundEvent> PRESS_TURN = sound("station.press.turn");
    public static final DeferredHolder<SoundEvent, SoundEvent> PRESS_DONE = sound("station.press.done");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEAL_LOAD = sound("station.seal.load");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEAL_WAX = sound("station.seal.wax");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEAL_STAMP = sound("station.seal.stamp");
    public static final DeferredHolder<SoundEvent, SoundEvent> CENTRIFUGE_SPIN = sound("station.centrifuge.spin");
    public static final DeferredHolder<SoundEvent, SoundEvent> STILL_BUBBLE = sound("station.still.bubble");
    public static final DeferredHolder<SoundEvent, SoundEvent> STILL_FIRE = sound("station.still.fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> CUTTING_LOAD = sound("station.cutting.load");
    public static final DeferredHolder<SoundEvent, SoundEvent> CUTTING_FILLER = sound("station.cutting.filler");
    public static final DeferredHolder<SoundEvent, SoundEvent> CUTTING_CHOP = sound("station.cutting.chop");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAFTING_POT = sound("station.grafting.pot");
    public static final DeferredHolder<SoundEvent, SoundEvent> GRAFTING_SNIP = sound("station.grafting.snip");
    public static final DeferredHolder<SoundEvent, SoundEvent> COUNTING_STAMP = sound("station.counting.stamp");

    private ModSounds() {}
}
