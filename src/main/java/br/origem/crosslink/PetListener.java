package br.origem.crosslink;

import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Mantem os pets reconhecendo a conta que esta jogando.
 *
 * Sao dois gatilhos, porque a varredura da entrada nao basta:
 *  - chunk carregado: pega o bicho que estava longe quando o jogador entrou
 *  - interacao direta: resposta imediata, sem esperar varredura
 */
public final class PetListener implements Listener {

    private final CrossLinkPlugin plugin;
    private final SyncEngine engine;
    private final GroupManager groups;

    public PetListener(CrossLinkPlugin plugin, SyncEngine engine, GroupManager groups) {
        this.plugin = plugin;
        this.engine = engine;
        this.groups = groups;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent e) {
        engine.retargetPetsIn(e.getChunk().getEntities());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Tameable t)) return;
        Player p = e.getPlayer();
        LinkGroup g = groups.of(p.getUniqueId());
        if (g == null) return;
        engine.setActive(g, p);
        if (engine.retargetOne(t, g, p)) {
            plugin.getLogger().fine("pet transferred to " + p.getName() + " on interaction");
        }
    }
}
