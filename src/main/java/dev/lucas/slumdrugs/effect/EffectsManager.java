package dev.lucas.slumdrugs.effect;

import dev.lucas.slumdrugs.Keys;
import dev.lucas.slumdrugs.SlumDrugsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Personal sensory effects. Everything here is visible or audible to one player only:
 * hallucination mobs hidden from everyone else, footsteps behind you, whispers and particles.
 * No forced camera movement is used.
 */
public final class EffectsManager {

    private static final int PHANTOM_LIFETIME_TICKS = 140;

    private final SlumDrugsPlugin plugin;
    private final Keys keys;
    private final Map<UUID, Long> lastPhantom = new HashMap<>();
    private final Map<UUID, List<UUID>> phantoms = new HashMap<>();

    public EffectsManager(SlumDrugsPlugin plugin, Keys keys) {
        this.plugin = plugin;
        this.keys = keys;
    }

    /** Called about once a second while a hallucinogenic substance is active. */
    public void tick(Player p, double intensity) {
        ThreadLocalRandom r = ThreadLocalRandom.current();

        // Private particles drifting around the player.
        if (r.nextDouble() < 0.6) {
            Location base = p.getLocation().add(0, 1, 0);
            for (int i = 0; i < 3 + (int) (intensity * 6); i++) {
                Location at = base.clone().add(
                        (r.nextDouble() - 0.5) * 6,
                        (r.nextDouble() - 0.5) * 3,
                        (r.nextDouble() - 0.5) * 6);
                Particle particle = switch (r.nextInt(4)) {
                    case 0 -> Particle.SPORE_BLOSSOM_AIR;
                    case 1 -> Particle.END_ROD;
                    case 2 -> Particle.GLOW;
                    default -> Particle.WITCH;
                };
                p.spawnParticle(particle, at, 1);
            }
        }

        // Footsteps behind you, whispers, a door that did not open.
        if (r.nextDouble() < 0.12 + intensity * 0.15) {
            Location behind = p.getLocation().clone().add(p.getLocation().getDirection().multiply(-3.5));
            Sound s = switch (r.nextInt(6)) {
                case 0 -> Sound.BLOCK_GRAVEL_STEP;
                case 1 -> Sound.BLOCK_WOOD_STEP;
                case 2 -> Sound.AMBIENT_CAVE;
                case 3 -> Sound.ENTITY_ENDERMAN_STARE;
                case 4 -> Sound.BLOCK_CHEST_OPEN;
                default -> Sound.ENTITY_PLAYER_BREATH;
            };
            p.playSound(behind, s, 0.35f, 0.7f + r.nextFloat() * 0.5f);
        }

        // A silhouette at the edge of vision that vanishes when looked at.
        int interval = plugin.getConfig().getInt("effects.hallucination-interval-seconds", 12);
        long now = System.currentTimeMillis();
        if (intensity > 0.3 && now - lastPhantom.getOrDefault(p.getUniqueId(), 0L) > interval * 1000L
                && r.nextDouble() < intensity) {
            lastPhantom.put(p.getUniqueId(), now);
            spawnPhantom(p);
        }
    }

    /** Spawns a mob only this player can see. It never moves toward the player and cannot hurt anyone. */
    private void spawnPhantom(Player p) {
        List<String> names = plugin.getConfig().getStringList("effects.hallucination-mobs");
        if (names.isEmpty()) return;
        String pick = names.get(ThreadLocalRandom.current().nextInt(names.size()));
        EntityType type;
        try {
            type = EntityType.valueOf(pick.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return;
        }
        if (type.getEntityClass() == null || !LivingEntity.class.isAssignableFrom(type.getEntityClass())) return;

        ThreadLocalRandom r = ThreadLocalRandom.current();
        double angle = r.nextDouble() * Math.PI * 2;
        double dist = 7 + r.nextDouble() * 6;
        Location at = p.getLocation().clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
        at.setY(at.getWorld().getHighestBlockYAt(at) + 1);
        if (at.distanceSquared(p.getLocation()) > 400) return;

        Entity e = at.getWorld().spawn(at, type.getEntityClass(), spawned -> {
            spawned.setVisibleByDefault(false);
            spawned.setPersistent(false);
        });
        if (!(e instanceof LivingEntity le)) {
            e.remove();
            return;
        }
        le.setVisibleByDefault(false);
        p.showEntity(plugin, le);
        le.setSilent(true);
        le.setInvulnerable(true);
        le.setCollidable(false);
        le.setPersistent(false);
        le.setRemoveWhenFarAway(true);
        le.setAI(false);
        le.getPersistentDataContainer().set(keys.npcType, PersistentDataType.STRING, "hallucination");
        le.getPersistentDataContainer().set(keys.owner, PersistentDataType.STRING, p.getUniqueId().toString());
        if (le instanceof Mob mob) {
            mob.setAware(false);
            mob.lookAt(p);
        }
        phantoms.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).add(le.getUniqueId());

        Bukkit.getScheduler().runTaskLater(plugin, () -> despawn(p.getUniqueId(), le), PHANTOM_LIFETIME_TICKS);
    }

    private void despawn(UUID owner, Entity e) {
        if (e.isValid()) {
            Player viewer = Bukkit.getPlayer(owner);
            if (viewer != null && viewer.getWorld().equals(e.getWorld()))
                viewer.spawnParticle(Particle.SMOKE, e.getLocation().add(0, 1, 0), 6, 0.2, 0.4, 0.2, 0.01);
            e.remove();
        }
        List<UUID> list = phantoms.get(owner);
        if (list != null) list.remove(e.getUniqueId());
    }

    /** True if this entity is somebody's hallucination, so damage and targeting can be ignored. */
    public boolean isHallucination(Entity e) {
        return "hallucination".equals(e.getPersistentDataContainer().get(keys.npcType, PersistentDataType.STRING));
    }

    /** Removes every phantom belonging to a player, e.g. on logout or when the high ends. */
    public void clear(UUID owner) {
        lastPhantom.remove(owner);
        List<UUID> list = phantoms.remove(owner);
        if (list == null) return;
        for (UUID id : list) {
            for (org.bukkit.World w : Bukkit.getWorlds()) {
                Entity e = w.getEntity(id);
                if (e != null) e.remove();
            }
        }
    }

    public void clearAll() {
        for (UUID owner : new ArrayList<>(phantoms.keySet())) clear(owner);
    }

    /** A brief shove of misleading ambience: used by informants and rival scares. */
    public void unease(Player p) {
        Location l = p.getLocation();
        p.playSound(l, Sound.ENTITY_ENDERMAN_STARE, 0.5f, 0.6f);
        p.spawnParticle(Particle.LARGE_SMOKE, l.clone().add(new Vector(0, 1, 0)), 12, 0.6, 0.6, 0.6, 0.01);
    }
}
