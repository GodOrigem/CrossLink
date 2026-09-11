package br.origem.crosslink;

import org.bukkit.World;
import br.origem.crosslink.compat.Compat;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;
import br.origem.crosslink.compat.Schedulers;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;
import java.util.logging.Level;

/**
 * Espelha o estado da conta primaria nas secundarias.
 *
 * REGRA CENTRAL, e a licao de um bug que apagou o inventario de um jogador:
 * uma conta so vira "fonte da verdade" quando ELA MESMA mudou -- comparando
 * com o proprio retrato anterior, nunca com o estado do grupo. Comparar com o
 * estado do grupo faz qualquer conta recem-vinculada parecer "divergente", e
 * um inventario vazio entrando no grupo sobrescrevia o inventario cheio.
 */
public final class SyncEngine {

    private final Plugin plugin;
    private final GroupManager groups;
    private final Set<UUID> applying = new HashSet<>();
    private final Set<String> scheduled = new HashSet<>();
    /** Ultimo retrato conhecido de cada jogador, para detectar quem mexeu. */
    private final Map<UUID, Integer> lastSeen = new HashMap<>();
    /** Grupo -> conta que deu sinal de vida por ultimo; dona dos pets. */
    private final Map<String, UUID> active = new HashMap<>();
    private static final ItemStack[] EMPTY = new ItemStack[0];

    public boolean syncInventory = true;
    public boolean syncArmor = true;
    public boolean syncOffhand = true;
    public boolean syncEnderChest = true;
    public boolean syncXp = true;
    public boolean syncHealth = false;
    public boolean syncFood = false;
    public boolean syncPets = true;
    public boolean clearSecondaryOnQuit = true;
    public int backupsToKeep = 10;

    public SyncEngine(Plugin plugin, GroupManager groups) {
        this.plugin = plugin;
        this.groups = groups;
    }

    public boolean isApplying(Player p) { return applying.contains(p.getUniqueId()); }

    public void forget(UUID id) { lastSeen.remove(id); }

    public void markDirty(Player p) {
        if (p == null || isApplying(p)) return;
        LinkGroup g = groups.of(p.getUniqueId());
        if (g == null) return;
        if (!scheduled.add(g.name())) return;
        Schedulers.run(plugin, p, () -> {
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
        lastSeen.put(source.getUniqueId(), st.fingerprint());
        for (UUID id : g.members().keySet()) {
            if (id.equals(source.getUniqueId())) continue;
            Player other = plugin.getServer().getPlayer(id);
            if (other != null && other.isOnline()) apply(other, st);
        }
    }

    /**
     * Usado ao vincular: a primaria dita o estado e todo mundo adota.
     * Nunca o contrario -- e isso que impede a conta nova de zerar a antiga.
     */
    public void adoptFromPrimary(LinkGroup g) {
        UUID prim = g.primary();
        if (prim == null) return;
        Player primary = plugin.getServer().getPlayer(prim);
        if (primary != null && primary.isOnline()) {
            SharedState st = capture(primary);
            g.state(st);
            lastSeen.put(prim, st.fingerprint());
        }
        SharedState st = g.state();
        for (UUID id : g.members().keySet()) {
            if (id.equals(prim)) continue;
            Player other = plugin.getServer().getPlayer(id);
            if (other != null && other.isOnline()) apply(other, st);
        }
    }

    /** Jogador entrou. Secundaria SEMPRE adota; so a primaria pode definir a base. */
    public void onJoin(Player p) {
        LinkGroup g = groups.of(p.getUniqueId());
        if (g == null) return;
        SharedState st = g.state();
        boolean vazio = st.inventory.length == 0 && st.foodLevel < 0 && st.health < 0;

        if (vazio && g.isPrimary(p.getUniqueId())) {
            SharedState mine = capture(p);
            g.state(mine);
            lastSeen.put(p.getUniqueId(), mine.fingerprint());
        } else if (!vazio) {
            apply(p, st);
        } else {
            // Grupo sem estado e quem entrou nao e a primaria: nao toca em nada.
            lastSeen.put(p.getUniqueId(), fingerprintOf(p));
        }
        if (syncPets) {
            // Marca ja, para o ChunkLoadEvent saber de quem sao os pets; a
            // varredura completa espera os chunks do jogador carregarem.
            setActive(g, p);
            Schedulers.globalLater(plugin, () -> {
                if (p.isOnline()) retargetPets(g, p);
            }, 60);
        }
    }

    /**
     * Varredura de seguranca. So considera "autor" quem mudou em relacao ao
     * PROPRIO retrato anterior.
     */
    public void sweep() {
        for (LinkGroup g : groups.all()) {
            List<Player> online = new ArrayList<>();
            for (UUID id : g.members().keySet()) {
                Player p = plugin.getServer().getPlayer(id);
                if (p != null && p.isOnline()) online.add(p);
            }
            if (online.isEmpty()) continue;

            Player actor = null;
            for (Player p : online) {
                int fp = fingerprintOf(p);
                Integer last = lastSeen.get(p.getUniqueId());
                if (last == null) { lastSeen.put(p.getUniqueId(), fp); continue; }
                if (fp != last) { actor = p; break; }
            }
            if (actor != null) syncFrom(actor);
        }
    }

    /**
     * Ao sair, a conta secundaria fica com a playerdata vazia.
     *
     * Sem isso, os itens existiriam em DOIS arquivos de jogador ao mesmo tempo:
     * bastava remover o plugin para cada conta acordar com uma copia, duplicando
     * tudo. Mantendo os itens so no uid da conta primaria, remover o plugin
     * deixa exatamente um dono.
     */
    public void onQuit(Player p) {
        LinkGroup g = groups.of(p.getUniqueId());
        if (g == null) return;
        syncFrom(p);
        if (clearSecondaryOnQuit && !g.isPrimary(p.getUniqueId())) {
            applying.add(p.getUniqueId());
            try {
                if (anyInventorySync()) {
                    ItemStack[] cur = p.getInventory().getContents();
                    for (int i = 0; i < cur.length; i++) {
                        if (slotSynced(i, cur.length)) cur[i] = null;
                    }
                    p.getInventory().setContents(cur);
                }
                if (syncEnderChest) p.getEnderChest().clear();
                if (syncXp) { p.setLevel(0); p.setExp(0f); p.setTotalExperience(0); }
            } finally {
                applying.remove(p.getUniqueId());
            }
        }
        lastSeen.remove(p.getUniqueId());
    }

    /**
     * Pets reconhecem a conta que esta jogando agora.
     *
     * Um pet guarda um unico dono, entao nao da pra obedecer as duas contas ao
     * mesmo tempo. A regra e: a dona passa a ser a ultima conta do grupo que deu
     * sinal de vida -- entrou no servidor ou interagiu com o bicho. Como e a
     * mesma pessoa nas duas pontas, seguir a conta ativa e o que corresponde a
     * expectativa.
     *
     * Uma versao anterior desistia quando a outra conta tambem estava online, o
     * que fazia o pet nunca trocar de dono justamente para quem joga nas duas.
     */
    public void setActive(LinkGroup g, Player p) {
        if (g != null && p != null) active.put(g.name(), p.getUniqueId());
    }

    public Player activeMember(LinkGroup g) {
        UUID id = active.get(g.name());
        if (id == null) return null;
        Player p = plugin.getServer().getPlayer(id);
        return (p != null && p.isOnline()) ? p : null;
    }

    /** Varre todos os mundos. Custa caro, entao so na entrada do jogador. */
    public void retargetPets(LinkGroup g, Player to) {
        if (!syncPets || g == null || to == null) return;
        setActive(g, to);
        int changed = 0;
        for (World w : plugin.getServer().getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e instanceof Tameable t && retargetOne(t, g, to)) changed++;
            }
        }
        if (changed > 0) {
            plugin.getLogger().info("pets transferred to " + to.getName() + ": " + changed);
        }
    }

    /**
     * Varre so as entidades recem-carregadas.
     *
     * E o que resolve o pet distante: na entrada do jogador o chunk dele ainda
     * nem tinha carregado, entao a varredura global nao o encontrava.
     */
    public void retargetPetsIn(Entity[] entities) {
        if (!syncPets) return;
        for (Entity e : entities) {
            if (!(e instanceof Tameable t)) continue;
            UUID ownerId = Compat.petOwnerId(t);
            if (ownerId == null) continue;
            LinkGroup g = groups.of(ownerId);
            if (g == null) continue;
            Player to = activeMember(g);
            if (to != null) retargetOne(t, g, to);
        }
    }

    /** Transferencia pontual. Retorna true quando de fato mudou de dono. */
    public boolean retargetOne(Tameable t, LinkGroup g, Player to) {
        UUID oid = Compat.petOwnerId(t);
        if (oid == null) return false;
        if (oid.equals(to.getUniqueId()) || !g.has(oid)) return false;
        t.setOwner(to);
        return true;
    }

    /** Lista os pets em volta e de quem sao. Usado pelo diagnostico. */
    public List<String> describePetsNear(Player p, int radius) {
        List<String> out = new ArrayList<>();
        LinkGroup g = groups.of(p.getUniqueId());
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof Tameable t)) continue;
            UUID oid = Compat.petOwnerId(t);
            String owner;
            if (oid == null) {
                owner = t.isTamed() ? "tamed, owner unknown" : "not tamed";
            } else if (oid.equals(p.getUniqueId())) {
                owner = "YOU";
            } else if (g != null && g.has(oid)) {
                owner = "linked account (" + g.members().get(oid) + ")";
            } else {
                owner = "OUTSIDE your group: " + oid;
            }
            out.add(e.getType() + " -> " + owner);
        }
        return out;
    }

    /**
     * Adota para o grupo os pets em volta, seja qual for o dono atual.
     *
     * Existe porque um pet domado antes do vinculo -- ou num periodo em que o
     * servidor rodava offline-mode, quando a UUID do jogador era outra --
     * pertence a uma UUID que o grupo nao conhece, e a transferencia normal
     * ignora. Por ser destrutivo (rouba o pet de quem quer que seja o dono),
     * exige comando explicito de admin.
     */
    public int claimPetsNear(Player p, int radius) {
        LinkGroup g = groups.of(p.getUniqueId());
        if (g == null) return 0;
        setActive(g, p);
        int n = 0;
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof Tameable t) || !t.isTamed()) continue;
            UUID oid = Compat.petOwnerId(t);
            if (oid != null && oid.equals(p.getUniqueId())) continue;
            t.setOwner(p);
            n++;
        }
        return n;
    }

    /**
     * O inventario do jogador vem num array unico de 41 posicoes:
     * 0-35 mochila, 36-39 armadura, 40 offhand. Separar as tres faixas e o que
     * permite, por exemplo, compartilhar a mochila mas manter a armadura de
     * cada conta.
     */
    private boolean slotSynced(int i, int size) {
        if (size < 41) return syncInventory;      // inventario nao-jogador
        if (i <= 35) return syncInventory;
        if (i <= 39) return syncArmor;
        return syncOffhand;
    }

    private boolean anyInventorySync() {
        return syncInventory || syncArmor || syncOffhand;
    }

    /** Conteudo com as faixas desligadas zeradas, para captura e para hash. */
    private ItemStack[] masked(Player p) {
        ItemStack[] src = p.getInventory().getContents();
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            if (slotSynced(i, src.length)) out[i] = src[i];
        }
        return out;
    }

    /**
     * Hash do estado atual do jogador, sem alocar copia nenhuma.
     * E o que a varredura usa; capture() so entra quando algo de fato mudou.
     */
    public int fingerprintOf(Player p) {
        // Desligado precisa dar o MESMO hash que capture() produziria, e la o
        // campo fica como array vazio -- cujo deepHashCode e 1, nao 0. Divergir
        // aqui faria a varredura ver mudanca a cada passada e ressincronizar
        // para sempre.
        int inv = java.util.Arrays.deepHashCode(anyInventorySync() ? masked(p) : EMPTY);
        int ec = java.util.Arrays.deepHashCode(
                syncEnderChest ? p.getEnderChest().getContents() : EMPTY);
        int level = syncXp ? p.getLevel() : 0;
        float exp = syncXp ? p.getExp() : 0f;
        int total = syncXp ? p.getTotalExperience() : 0;
        double hp = syncHealth ? p.getHealth() : -1;
        int food = syncFood ? p.getFoodLevel() : -1;
        float sat = syncFood ? p.getSaturation() : 0f;
        return SharedState.mix(inv, ec, level, exp, total, hp, food, sat);
    }

    public SharedState capture(Player p) {
        SharedState st = new SharedState();
        if (anyInventorySync()) st.inventory = cloneAll(masked(p));
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
        backup(p);
        applying.add(p.getUniqueId());
        try {
            if (anyInventorySync() && st.inventory.length > 0) {
                // Escreve so as faixas ligadas; o resto do inventario de quem
                // recebe fica intacto.
                ItemStack[] cur = p.getInventory().getContents();
                ItemStack[] out = java.util.Arrays.copyOf(cur, cur.length);
                int n = Math.min(out.length, st.inventory.length);
                for (int i = 0; i < n; i++) {
                    if (slotSynced(i, out.length)) {
                        out[i] = st.inventory[i] == null ? null : st.inventory[i].clone();
                    }
                }
                p.getInventory().setContents(out);
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
                double max = Compat.maxHealth(p);
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
        lastSeen.put(p.getUniqueId(), st.fingerprint());
    }

    /** Retrato em disco antes de qualquer escrita destrutiva. */
    private void backup(Player p) {
        if (backupsToKeep <= 0) return;
        try {
            SharedState cur = capture(p);
            boolean vazio = true;
            for (ItemStack i : cur.inventory) if (i != null) { vazio = false; break; }
            if (vazio && cur.totalExperience == 0) return;   // nada que valha salvar

            File dir = new File(plugin.getDataFolder(), "backups/" + p.getUniqueId());
            if (!dir.exists() && !dir.mkdirs()) return;
            YamlConfiguration yml = new YamlConfiguration();
            yml.set("player", p.getName());
            yml.set("when", new Date().toString());
            cur.save(yml.createSection("state"));
            yml.save(new File(dir, System.currentTimeMillis() + ".yml"));

            File[] all = dir.listFiles((d, n) -> n.endsWith(".yml"));
            if (all != null && all.length > backupsToKeep) {
                Arrays.sort(all, Comparator.comparing(File::getName));
                for (int i = 0; i < all.length - backupsToKeep; i++) all[i].delete();
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "failed to back up " + p.getName(), ex);
        }
    }

    private static ItemStack[] cloneAll(ItemStack[] src) {
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) out[i] = src[i] == null ? null : src[i].clone();
        return out;
    }

    private static ItemStack[] fit(ItemStack[] src, int size) {
        if (src.length == size) return cloneAll(src);
        ItemStack[] out = new ItemStack[size];
        System.arraycopy(src, 0, out, 0, Math.min(size, src.length));
        return cloneAll(out);
    }
}
