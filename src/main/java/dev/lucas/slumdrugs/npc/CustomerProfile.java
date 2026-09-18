package dev.lucas.slumdrugs.npc;

import org.bukkit.entity.Villager;

/** A named buyer with a taste, a budget and a routine. */
public final class CustomerProfile {

    public final String id;
    public final String name;
    public final String drug;
    public final int minQuality;
    public final double budget;
    /** Visiting window in Minecraft day ticks (0..24000). */
    public final long openTick;
    public final long closeTick;
    public final Villager.Profession profession;
    public final String haunt;

    public CustomerProfile(String id, String name, String drug, int minQuality, double budget,
                           long openTick, long closeTick, Villager.Profession profession, String haunt) {
        this.id = id;
        this.name = name;
        this.drug = drug;
        this.minQuality = minQuality;
        this.budget = budget;
        this.openTick = openTick;
        this.closeTick = closeTick;
        this.profession = profession;
        this.haunt = haunt;
    }

    /** True if the customer is around at this world time. Windows may wrap past midnight. */
    public boolean openAt(long worldTime) {
        long t = worldTime % 24000L;
        if (openTick <= closeTick) return t >= openTick && t <= closeTick;
        return t >= openTick || t <= closeTick;
    }

    public String hours() {
        return clock(openTick) + " to " + clock(closeTick);
    }

    private static String clock(long tick) {
        long hours = (tick / 1000L + 6L) % 24L;
        long minutes = (tick % 1000L) * 60L / 1000L;
        return String.format("%02d:%02d", hours, minutes);
    }
}
