package br.origem.linkedplayers;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * Copia o estado de quem mexeu para os outros membros online do grupo.
 *
 * Dois caminhos alimentam isso:
 *  - eventos (rapido, reage no tick seguinte a acao)
 *  - varredura periodica (rede de seguranca pra mudanca que nenhum evento viu)
 *
 * O conjunto 'applying' evita laco infinito: enquanto escrevemos no inventario
 * de alguem, os eventos que isso dispara sao ignorados.
 */
public final class SyncEngine {

    private final Plugin plugin;
    private final GroupManager groups;
    private final Set<UUID> applying = new HashSet<>();
    private final Set<String> scheduled = new HashSet<>();

    public boolean syncInventory = true;
    public boolean syncEnderChest = true;
    public boolean syncXp = true;
    public boolean syncHealth = false;
    public boolean syncFood = false;

    public SyncEngine(Plugin plugin, GroupManager groups) {
        this.plugin = plugin;
        this.groups = groups;
    }

    public boolean isApplying(Player p) { return applying.contains(p.getUniqueId()); }

    /** Agenda uma sincronizacao a partir deste jogador no proximo tick. */
    public void markDirty(Player p) {
        if (p == null || isApplying(p)) return;
        LinkGroup g = groups.of(p.getUniqueId());
        if (g == null) return;
        // Um agendamento por grupo por tick: varios eventos da mesma acao viram um sync so.
        if (!scheduled.add(g.name())) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            scheduled.remove(g.name());
            if (p.isOnline()) syncFrom(p);
        });
    }

    /** Torna o estado deste jogador a verdade do grupo e empurra pros demais. */
    public void syncFrom(Player source) {
        LinkGroup g = groups.of(source.getUniqueId());
        if (g == null) return;
        SharedState st = capture(source);
        g.state(st);
        for (UUID id : g.members().keySet()) {
            if (id.equals(source.getUniqueId())) continue;
            Player other = plugin.getServer().getPlayer(id);
            if (other != null && other.isOnline()) apply(other, st);
        }
    }

    /** Jogador entrou: recebe o estado do grupo (mudancas que ocorreram offline). */
    public void onJoin(Player p) {
        LinkGroup g = groups.of(p.getUniqueId());
        if (g == null) return;
        SharedState st = g.state();
        // Grupo ainda sem estado: o primeiro a entrar define a base.
        if (st.inventory.length == 0 && st.foodLevel < 0 && st.health < 0) {
            g.state(capture(p));
            return;
        }
        apply(p, st);
    }

    /** Varredura de seguranca: acha quem divergiu e propaga. */
    public void sweep() {
        for (LinkGroup g : groups.all()) {
            List<Player> online = new ArrayList<>();
            for (UUID id : g.members().keySet()) {
                Player p = plugin.getServer().getPlayer(id);
                if (p != null && p.isOnline()) online.add(p);
            }
            if (online.size() < 2) {
                // Sozinho: so mantem o estado do grupo atualizado pra quem entrar depois.
                if (online.size() == 1) g.state(capture(online.get(0)));
                continue;
            }
            int expected = g.state().fingerprint();
            Player diverged = null;
            for (Player p : online) {
                if (capture(p).fingerprint() != expected) { diverged = p; break; }
            }
            if (diverged != null) syncFrom(diverged);
        }
    }

    public SharedState capture(Player p) {
        SharedState st = new SharedState();
        if (syncInventory) st.inventory = cloneAll(p.getInventory().getContents());
        if (syncEnderChest) st.enderChest = cloneAll(p.getEnderChest().getContents());
        if (syncXp) {
            st.level = p.getLevel();
            st.exp = p.getExp();
            st.totalExperience = p.getTotalExperience();
        }
        if (syncHealth) st.health = p.getHealth();
        if (syncFood) {
            st.foodLevel = p.getFoodLevel();
            st.saturation = p.getSaturation();
        }
        return st;
    }

    public void apply(Player p, SharedState st) {
        applying.add(p.getUniqueId());
        try {
            if (syncInventory && st.inventory.length > 0) {
                p.getInventory().setContents(fit(st.inventory, p.getInventory().getSize()));
            }
            if (syncEnderChest && st.enderChest.length > 0) {
                p.getEnderChest().setContents(fit(st.enderChest, p.getEnderChest().getSize()));
            }
            if (syncXp) {
                p.setLevel(st.level);
                p.setExp(st.exp);
                p.setTotalExperience(st.totalExperience);
            }
            if (syncHealth && st.health >= 0) {
                double max = 20.0;
                var attr = p.getAttribute(Attribute.MAX_HEALTH);
                if (attr != null) max = attr.getValue();
                p.setHealth(Math.max(0.5, Math.min(st.health, max)));
            }
            if (syncFood && st.foodLevel >= 0) {
                p.setFoodLevel(st.foodLevel);
                p.setSaturation(st.saturation);
            }
            p.updateInventory();
        } finally {
            applying.remove(p.getUniqueId());
        }
    }

    private static ItemStack[] cloneAll(ItemStack[] src) {
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) out[i] = src[i] == null ? null : src[i].clone();
        return out;
    }

    /** Protege contra tamanho diferente de inventario entre versoes. */
    private static ItemStack[] fit(ItemStack[] src, int size) {
        if (src.length == size) return cloneAll(src);
        ItemStack[] out = new ItemStack[size];
        System.arraycopy(src, 0, out, 0, Math.min(size, src.length));
        return cloneAll(out);
    }
}
