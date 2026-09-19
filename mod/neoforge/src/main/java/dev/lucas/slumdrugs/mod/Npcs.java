package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.npc.Npc;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.List;

/**
 * The quarter's people are ordinary villagers carrying our data, not a custom entity type.
 * That is a deliberate choice: they stay negotiable and poachable, they need no model, no
 * renderer and no client code at all, and a crew is visibly made of the same people who live
 * here. See docs/MOD-GDD.md §5.9.
 */
public final class Npcs {

    private static final List<String> FIRST_NAMES = List.of(
            "Marlo", "Dessa", "Kip", "Rusty", "Nen", "Odd Tam", "Vera", "Salt", "Pim", "Gret",
            "Hollow Jen", "Bramble", "Cass", "Wick", "Trudy", "Ovid");

    private Npcs() {}

    /** The trade a role wears, so a player can read the street at a glance. */
    private static ResourceKey<VillagerProfession> profession(Npc.Role role) {
        return switch (role) {
            case HEALER -> VillagerProfession.CLERIC;
            case BROKER -> VillagerProfession.WEAPONSMITH;
            case TRADER -> VillagerProfession.FARMER;
            case CONSTABLE -> VillagerProfession.ARMORER;
            case RESIDENT -> VillagerProfession.NITWIT;
            case BRUISER -> VillagerProfession.TOOLSMITH;
            case LIEUTENANT -> VillagerProfession.MASON;
            case CUSTOMER -> VillagerProfession.NONE;
        };
    }

    public static String randomName(ServerLevel level) {
        return FIRST_NAMES.get(level.getRandom().nextInt(FIRST_NAMES.size()));
    }

    /** Spawns one, or null if the game refused to create the entity. */
    public static Villager spawn(ServerLevel level, BlockPos pos, Npc.Role role, String crew, String name) {
        // 26.3 moved the entity type constants out of EntityType and into EntityTypes.
        Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
        if (villager == null) return null;

        villager.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        villager.setVillagerData(villager.getVillagerData()
                .withProfession(level.registryAccess(), profession(role)));
        villager.setCustomName(Component.literal(name));
        villager.setCustomNameVisible(true);
        // Ours are placed on purpose and must not wander off or despawn.
        villager.setPersistenceRequired();

        double resting = Npc.restingAggression(role, 0, 0);
        villager.setData(ModAttachments.NPC.get(), new NpcData(role, crew, resting));

        level.addFreshEntity(villager);
        return villager;
    }

    public static NpcData data(Villager villager) {
        return villager.getData(ModAttachments.NPC.get());
    }

    public static boolean isOurs(Villager villager) {
        return villager.hasData(ModAttachments.NPC.get());
    }
}
