package dev.lucas.slumdrugs.mod;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.TicketType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Chunk tickets of our own.
 *
 * <p>A town being built needs its chunks held loaded between ticks, and the obvious tool,
 * {@code setChunkForced}, is wrong twice over. It loads the chunk on the spot, generating it on
 * the server thread if it is new -- one such chunk held the server 664 ms, measured. And a forced
 * chunk is saved with the world, so a server stopped half way through a town would keep its
 * sixty-odd chunks loaded for good.
 *
 * <p>This type only loads, never persists and never times out. The chunk system generates the
 * chunks it holds on its worker threads at its own pace; the town waits for them.
 */
public final class ModTickets {

    public static final DeferredRegister<TicketType> TYPES =
            DeferredRegister.create(Registries.TICKET_TYPE, SlumDrugsMod.ID);

    public static final DeferredHolder<TicketType, TicketType> TOWN =
            TYPES.register("town", () -> new TicketType(0L, TicketType.FLAG_LOADING));

    private ModTickets() {}
}
