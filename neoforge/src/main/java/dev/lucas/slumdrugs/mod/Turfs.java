package dev.lucas.slumdrugs.mod;

import dev.lucas.slumdrugs.sim.economy.Coin;
import dev.lucas.slumdrugs.sim.npc.Turf;
import dev.lucas.slumdrugs.sim.player.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Corners in the world. A corner is a chunk. Claiming is done at the prompt for now
 * ({@code /slum turf claim}, standing on it with a stamped sovereign in hand), which is the
 * stopgap the design's deeds will replace. What holding a corner is worth is {@link Turf}'s.
 */
public final class Turfs {

    private Turfs() {}

    public static TurfMap of(ServerLevel level) { return level.getData(ModAttachments.TURF.get()); }

    public static void save(ServerLevel level, TurfMap map) { level.setData(ModAttachments.TURF.get(), map); }

    /** A corner's key: its chunk coordinates, packed the way the game packs them. */
    public static long key(BlockPos pos) { return pack(pos.getX() >> 4, pos.getZ() >> 4); }

    public static long pack(int chunkX, int chunkZ) { return (chunkX & 0xffffffffL) | ((long) chunkZ << 32); }

    public static int chunkX(long key) { return (int) key; }

    public static int chunkZ(long key) { return (int) (key >> 32); }

    public static String id(Player player) { return Turf.PLAYER_PREFIX + player.getUUID(); }

    /** Whether the player holds the corner this position is on. */
    public static boolean own(ServerPlayer player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        return of(level).holderAt(key(pos)).equals(id(player));
    }

    /** The standing a crew member behaves on here: their crew's standing, less the resentment on a corner lost to this player. */
    public static double standingHere(ServerLevel level, Player player, NpcData data, BlockPos pos) {
        double standing = Crews.standing(player, data);
        boolean resents = of(level).tookFrom(key(pos), id(player), data.crew());
        return Turf.standingOn(standing, resents);
    }

    /** How deep in their own crew's turf a member stands. */
    public static double depth(ServerLevel level, NpcData data, BlockPos pos) {
        return Turf.depth(of(level).holderAt(key(pos)), data.crew());
    }

    /** What the corner under the player is, for the action bar or a command. */
    public static Component describe(ServerLevel level, ServerPlayer player, BlockPos pos) {
        TurfMap.Cell cell = of(level).at(key(pos));
        String where = (pos.getX() >> 4) + ", " + (pos.getZ() >> 4);
        if (cell == null) return Component.translatable("message.slumdrugs.turf_here_free", where);
        if (cell.holder().equals(id(player))) return Component.translatable("message.slumdrugs.turf_here_yours", where);
        if (Turf.isPlayer(cell.holder())) return Component.translatable("message.slumdrugs.turf_here_player", where);
        return Component.translatable("message.slumdrugs.turf_here_crew", where, Crews.label(cell.holder()));
    }

    /**
     * Claims the corner the player stands on. The verdict is the rule's; the coin is a stamped
     * sovereign in the main hand, spent only on a claim that goes through.
     */
    public static Turf.Verdict claim(ServerLevel level, ServerPlayer player) {
        TurfMap map = of(level);
        long key = key(player.blockPosition());
        String holder = map.holderAt(key);
        String you = id(player);
        double standing = Turf.isCrew(holder) ? Crews.of(player).with(holder) : 0;
        Progression.Tier tier = player.getData(ModAttachments.PROGRESSION.get()).tier(Tuning.progression());
        Turf.Verdict verdict = Turf.claim(holder, you, standing, map.heldBy(you), tier);
        if (verdict != Turf.Verdict.OK) return verdict;

        ItemStack held = player.getMainHandItem();
        if (!Purse.isCoin(held) || !Purse.isStamped(held) || Purse.valueOf(held) < Turf.CLAIM_PENCE) return null;
        // A sovereign exactly: the stack's coins are one denomination, so take as many as make it.
        long each = Purse.valueOf(held) / held.getCount();
        int coins = (int) Math.max(1, (Turf.CLAIM_PENCE + each - 1) / each);
        if (coins > held.getCount()) return null;
        held.consume(coins, player);

        map.set(key, new TurfMap.Cell(you, Turf.isCrew(holder) ? holder : "", level.getGameTime()));
        save(level, map);
        if (Turf.isCrew(holder)) Crews.moved(player, holder, -Turf.CLAIM_COST);
        return Turf.Verdict.OK;
    }

    /** Gives the corner up. Whoever it was taken from does not get it back; a corner let go is free. Returns false if it was not theirs. */
    public static boolean release(ServerLevel level, ServerPlayer player) {
        TurfMap map = of(level);
        long key = key(player.blockPosition());
        if (!map.holderAt(key).equals(id(player))) return false;
        map.clear(key);
        save(level, map);
        return true;
    }

    /** Hands the corner to a crew, or clears it. For the prompt and, later, for settlements. */
    public static void assign(ServerLevel level, BlockPos pos, String crew) {
        TurfMap map = of(level);
        long key = key(pos);
        if (crew == null || crew.isBlank()) map.clear(key);
        else map.set(key, new TurfMap.Cell(crew, "", level.getGameTime()));
        save(level, map);
    }

    static String pence(long pence) { return Coin.format(pence); }
}
