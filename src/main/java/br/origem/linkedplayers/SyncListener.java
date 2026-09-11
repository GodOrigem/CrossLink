package br.origem.linkedplayers;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;

/**
 * Caminho rapido: qualquer acao que possa mexer no estado marca o jogador,
 * e o SyncEngine propaga no tick seguinte (ja com o efeito da acao aplicado).
 */
public final class SyncListener implements Listener {

    private final Plugin plugin;
    private final SyncEngine engine;
    private final GroupManager groups;

    public SyncListener(Plugin plugin, SyncEngine engine, GroupManager groups) {
        this.plugin = plugin;
        this.engine = engine;
        this.groups = groups;
    }

    private void touch(Player p) { engine.markDirty(p); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) touch(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p) touch(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) touch(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) { touch(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) touch(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { touch(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) { touch(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreakItem(PlayerItemBreakEvent e) { touch(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageItem(PlayerItemDamageEvent e) { touch(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent e) { touch(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExp(PlayerExpChangeEvent e) { touch(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLevel(PlayerLevelChangeEvent e) { touch(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p) touch(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) touch(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player p) touch(p);
    }

    /**
     * Morte precisa de tratamento proprio: o inventario do morto e esvaziado
     * DEPOIS do evento. Sem sincronizar aqui, os itens cairiam no chao e ainda
     * continuariam no inventario espelhado do outro -- duplicacao.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (groups.of(p.getUniqueId()) == null) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) engine.syncFrom(p);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) engine.syncFrom(p);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        LinkGroup g = groups.of(p.getUniqueId());
        if (g != null) {
            g.add(p.getUniqueId(), p.getName());   // mantem o nome exibido atualizado
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) engine.onJoin(p);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (groups.of(p.getUniqueId()) != null) engine.onQuit(p);
    }
}
