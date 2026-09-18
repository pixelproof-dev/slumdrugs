package dev.lucas.slumdrugs.listener;

import dev.lucas.slumdrugs.Msg;
import dev.lucas.slumdrugs.SlumDrugsPlugin;
import dev.lucas.slumdrugs.player.PlayerData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Talking to NPCs, and the consequences of hurting them. */
public final class NpcListener implements Listener {

    private final SlumDrugsPlugin plugin;

    public NpcListener(SlumDrugsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getPlayer().isSneaking() && event.getRightClicked().getPersistentDataContainer()
                .has(plugin.keys().reusedVillager, org.bukkit.persistence.PersistentDataType.BYTE)) return;
        String type = plugin.npcs().typeOf(event.getRightClicked());
        if (type == null) return;
        event.setCancelled(true);
        plugin.npcs().interact(event.getPlayer(), event.getRightClicked());
    }

    /** Attacking a resident or customer costs reputation and draws attention. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) return;
        Entity victim = event.getEntity();
        String type = plugin.npcs().typeOf(victim);
        if (type == null || "hallucination".equals(type) || "thug".equals(type)) return;

        PlayerData pd = plugin.players().get(p);
        switch (type) {
            case "guard", "officer" -> {
                pd.addRep("guards", -10);
                plugin.heat().add(p, 15);
                Msg.send(p, "<red>Assaulting a guard. That will not be forgotten.</red>");
            }
            case "resident" -> {
                pd.addRep("residents", -8);
                plugin.heat().add(p, 6);
            }
            case "customer" -> {
                pd.addRep("traders", -6);
                plugin.heat().add(p, 4);
                Msg.send(p, "<red>Word travels. Buyers will be wary of you.</red>");
            }
            default -> pd.addRep("residents", -3);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        String type = plugin.npcs().typeOf(event.getEntity());
        if (type == null) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        PlayerData pd = plugin.players().get(killer);
        if ("thug".equals(type)) {
            pd.addRep("residents", 3);
            pd.addRep("gangs", -6);
            Msg.send(killer, "<gray>The neighbours saw that. The gang will have heard too.</gray>");
        } else {
            pd.addRep("residents", -15);
            plugin.heat().add(killer, 25);
            Msg.send(killer, "<red>You killed someone from the district.</red>");
        }
    }
}
