package dev.lucas.slumdrugs.mod;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;

/**
 * Our people are drawn as people (client.NpcRenderer) and should not grunt like villagers.
 *
 * <p>They are villagers underneath, and the villager plays its own sounds. Those arrive here as
 * sounds at a position, not at an entity, so a villager sound is matched to one of ours
 * standing where it was played. Hurt and death become a player's; the rest — the idle
 * muttering, the yes and no, the trade chime — goes quiet, because the barks in chat and the
 * sale sounds already say what they meant.
 */
@EventBusSubscriber(modid = SlumDrugsMod.ID)
public final class Voices {

    private Voices() {}

    @SubscribeEvent
    public static void onSound(PlayLevelSoundEvent.AtPosition event) {
        Holder<SoundEvent> sound = event.getSound();
        if (sound == null || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!sound.value().location().getPath().startsWith("entity.villager.")) return;

        // Entity sounds are played at the entity's feet; ours also play some at its block
        // centre. A block either way finds the speaker without catching the one next door.
        var at = event.getPosition();
        var box = new AABB(at.x - 0.6, at.y - 1.0, at.z - 0.6, at.x + 0.6, at.y + 1.0, at.z + 0.6);
        if (level.getEntitiesOfClass(Villager.class, box, Npcs::isOurs).isEmpty()) return;

        SoundEvent played = sound.value();
        if (played == SoundEvents.VILLAGER_HURT) event.setSound(holder(SoundEvents.PLAYER_HURT));
        else if (played == SoundEvents.VILLAGER_DEATH) event.setSound(holder(SoundEvents.PLAYER_DEATH));
        else event.setCanceled(true);
    }

    private static Holder<SoundEvent> holder(SoundEvent sound) {
        return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
    }
}
