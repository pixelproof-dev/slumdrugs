package dev.lucas.slumdrugs.station;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A placed piece of processing furniture. */
public final class Station {

    /** One drying job on a rack. */
    public static final class Batch {
        public ItemStack input;
        public long finishAt;
        public long startedAt;

        public double progress(long now) {
            if (now >= finishAt) return 1.0;
            long total = Math.max(1, finishAt - startedAt);
            return Math.max(0, Math.min(1, (double) (now - startedAt) / total));
        }
    }

    public final Location location;
    public final StationType type;
    public final UUID owner;
    public final List<Batch> batches = new ArrayList<>();
    public ItemStack[] storage;
    public float yaw;
    public final GrowboxState growbox = new GrowboxState();

    public Station(Location location, StationType type, UUID owner) {
        this.location = location;
        this.type = type;
        this.owner = owner;
    }

    public String key() {
        return key(location);
    }

    public static String key(Location l) {
        return l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }
}
