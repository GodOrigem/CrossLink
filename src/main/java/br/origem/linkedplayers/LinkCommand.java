package br.origem.linkedplayers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public final class LinkCommand implements CommandExecutor, TabCompleter {

    private final LinkedPlayersPlugin plugin;
    private final GroupManager groups;
    private final SyncEngine engine;

    public LinkCommand(LinkedPlayersPlugin plugin, GroupManager groups, SyncEngine engine) {
        this.plugin = plugin;
        this.groups = groups;
        this.engine = engine;
    }

    private void ok(CommandSender s, String msg) {
        s.sendMessage(Component.text("[LinkedPlayers] ", NamedTextColor.AQUA)
                .append(Component.text(msg, NamedTextColor.GREEN)));
    }

    private void err(CommandSender s, String msg) {
        s.sendMessage(Component.text("[LinkedPlayers] ", NamedTextColor.AQUA)
                .append(Component.text(msg, NamedTextColor.RED)));
    }

    private void info(CommandSender s, String msg) {
        s.sendMessage(Component.text("[LinkedPlayers] ", NamedTextColor.AQUA)
                .append(Component.text(msg, NamedTextColor.WHITE)));
    }

    /**
     * Resolve um argumento em UUID.
     *
     * De proposito NAO usa Bukkit.getOfflinePlayer(nome) como atalho: com
     * online-mode=true isso consulta a Mojang, e uma conta Bedrock do Floodgate
     * (".Origembr") nao existe la -- voltaria uma UUID errada, e o vinculo
     * ficaria apontando pro nada sem dar erro nenhum.
     */
    private UUID resolve(CommandSender s, String arg) {
        Player online = Bukkit.getPlayerExact(arg);
        if (online != null) return online.getUniqueId();
        try {
            return UUID.fromString(arg);
        } catch (IllegalArgumentException ignored) {
            err(s, "'" + arg + "' nao esta online e nao e uma UUID.");
            info(s, "Peca pro jogador entrar e rode de novo, ou passe a UUID direto.");
            info(s, "Conta Bedrock nao pode ser resolvida por nome offline.");
            return null;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c,
                             @NotNull String label, String[] a) {
        if (a.length == 0) { usage(s, label); return true; }

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (a.length < 2) { err(s, "uso: /" + label + " create <grupo>"); return true; }
                if (groups.create(a[1]) == null) { err(s, "grupo '" + a[1] + "' ja existe."); return true; }
                groups.save();
                ok(s, "grupo '" + a[1].toLowerCase(Locale.ROOT) + "' criado.");
            }
            case "delete" -> {
                if (a.length < 2) { err(s, "uso: /" + label + " delete <grupo>"); return true; }
                if (!groups.delete(a[1])) { err(s, "grupo '" + a[1] + "' nao existe."); return true; }
                groups.save();
                ok(s, "grupo removido. Os jogadores ficam com o estado atual.");
            }
            case "add" -> {
                if (a.length < 3) { err(s, "uso: /" + label + " add <grupo> <jogador|uuid>"); return true; }
                LinkGroup g = groups.byName(a[1]);
                if (g == null) { err(s, "grupo '" + a[1] + "' nao existe."); return true; }
                UUID id = resolve(s, a[2]);
                if (id == null) return true;
                LinkGroup atual = groups.of(id);
                if (atual != null) {
                    err(s, a[2] + " ja esta no grupo '" + atual.name() + "'.");
                    return true;
                }
                Player p = Bukkit.getPlayer(id);
                groups.addMember(g, id, p != null ? p.getName() : id.toString());
                groups.save();
                ok(s, a[2] + " entrou no grupo '" + g.name() + "'.");
                // Adota a partir da primaria: quem entra recebe, nunca sobrescreve.
                engine.adoptFromPrimary(g);
                if (p != null) info(s, "estado da conta primaria aplicado a " + p.getName() + ".");
            }
            case "remove" -> {
                if (a.length < 3) { err(s, "uso: /" + label + " remove <grupo> <jogador|uuid>"); return true; }
                LinkGroup g = groups.byName(a[1]);
                if (g == null) { err(s, "grupo '" + a[1] + "' nao existe."); return true; }
                UUID id = resolve(s, a[2]);
                if (id == null) return true;
                if (!g.has(id)) { err(s, "esse jogador nao esta no grupo."); return true; }
                groups.removeMember(g, id);
                groups.save();
                ok(s, "removido do grupo '" + g.name() + "'. Fica com o inventario atual.");
            }
            case "sync" -> {
                if (a.length < 2) { err(s, "uso: /" + label + " sync <jogador>"); return true; }
                Player src = Bukkit.getPlayerExact(a[1]);
                if (src == null) { err(s, a[1] + " nao esta online."); return true; }
                if (groups.of(src.getUniqueId()) == null) { err(s, a[1] + " nao esta em grupo nenhum."); return true; }
                engine.syncFrom(src);
                groups.save();
                ok(s, "estado de " + src.getName() + " virou a verdade do grupo.");
            }
            case "list" -> {
                if (groups.all().isEmpty()) { info(s, "nenhum grupo definido."); return true; }
                for (LinkGroup g : groups.all()) {
                    info(s, "grupo '" + g.name() + "' (" + g.members().size() + "):");
                    for (Map.Entry<UUID, String> e : g.members().entrySet()) {
                        Player p = Bukkit.getPlayer(e.getKey());
                        String status = (p != null && p.isOnline()) ? " [online]" : "";
                        String prim = g.isPrimary(e.getKey()) ? " [PRIMARIA]" : "";
                        info(s, "   - " + e.getValue() + prim + status + "  " + e.getKey());
                    }
                }
            }
            case "primary" -> {
                if (a.length < 3) { err(s, "uso: /" + label + " primary <grupo> <jogador|uuid>"); return true; }
                LinkGroup g = groups.byName(a[1]);
                if (g == null) { err(s, "grupo '" + a[1] + "' nao existe."); return true; }
                UUID id = resolve(s, a[2]);
                if (id == null) return true;
                if (!g.has(id)) { err(s, "esse jogador nao esta no grupo."); return true; }
                g.primary(id);
                groups.save();
                ok(s, "conta primaria do grupo '" + g.name() + "' agora e " + a[2] + ".");
                info(s, "a playerdata de verdade fica nessa conta; as outras espelham.");
            }
            case "backups" -> {
                if (a.length < 2) { err(s, "uso: /" + label + " backups <jogador|uuid>"); return true; }
                UUID id = resolve(s, a[1]);
                if (id == null) return true;
                File[] list = backupFiles(id);
                if (list.length == 0) { info(s, "nenhum backup para esse jogador."); return true; }
                info(s, list.length + " backup(s), do mais novo pro mais antigo:");
                for (int i = list.length - 1; i >= 0; i--) {
                    YamlConfiguration y = YamlConfiguration.loadConfiguration(list[i]);
                    info(s, "   " + list[i].getName() + "  (" + y.getString("when", "?") + ")");
                }
                info(s, "restaure com: /" + label + " restore <jogador> [arquivo]");
            }
            case "restore" -> {
                if (a.length < 2) { err(s, "uso: /" + label + " restore <jogador> [arquivo]"); return true; }
                Player target = Bukkit.getPlayerExact(a[1]);
                if (target == null) { err(s, a[1] + " precisa estar online para restaurar."); return true; }
                File[] list = backupFiles(target.getUniqueId());
                if (list.length == 0) { err(s, "nenhum backup para " + a[1] + "."); return true; }
                File pick = list[list.length - 1];
                if (a.length >= 3) {
                    pick = null;
                    for (File f : list) if (f.getName().equals(a[2])) pick = f;
                    if (pick == null) { err(s, "backup '" + a[2] + "' nao encontrado."); return true; }
                }
                YamlConfiguration y = YamlConfiguration.loadConfiguration(pick);
                SharedState st = SharedState.load(y.getConfigurationSection("state"));
                engine.apply(target, st);
                ok(s, "restaurado " + pick.getName() + " em " + target.getName() + ".");
                info(s, "o estado anterior virou um backup novo, entao da pra desfazer.");
            }
            case "reload" -> {
                plugin.reloadAll();
                ok(s, "config e grupos recarregados.");
            }
            default -> usage(s, label);
        }
        return true;
    }

    private File[] backupFiles(UUID id) {
        File dir = new File(plugin.getDataFolder(), "backups/" + id);
        File[] list = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (list == null) return new File[0];
        Arrays.sort(list, Comparator.comparing(File::getName));
        return list;
    }

    private void usage(CommandSender s, String label) {
        info(s, "/" + label + " create <grupo>");
        info(s, "/" + label + " add <grupo> <jogador|uuid>");
        info(s, "/" + label + " remove <grupo> <jogador|uuid>");
        info(s, "/" + label + " sync <jogador>   - forca este como fonte da verdade");
        info(s, "/" + label + " primary <grupo> <jogador> - define a conta dona dos dados");
        info(s, "/" + label + " backups <jogador>");
        info(s, "/" + label + " restore <jogador> [arquivo]");
        info(s, "/" + label + " list");
        info(s, "/" + label + " delete <grupo>");
        info(s, "/" + label + " reload");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c,
                                      @NotNull String label, String[] a) {
        if (a.length == 1) {
            return filter(List.of("create", "add", "remove", "sync", "primary", "backups",
                    "restore", "list", "delete", "reload"), a[0]);
        }
        if (a.length == 2) {
            if (a[0].equalsIgnoreCase("sync") || a[0].equalsIgnoreCase("restore")
                    || a[0].equalsIgnoreCase("backups")) return filter(onlineNames(), a[1]);
            List<String> names = new ArrayList<>();
            groups.all().forEach(g -> names.add(g.name()));
            return filter(names, a[1]);
        }
        if (a.length == 3 && (a[0].equalsIgnoreCase("add") || a[0].equalsIgnoreCase("remove")
                || a[0].equalsIgnoreCase("primary"))) {
            return filter(onlineNames(), a[2]);
        }
        return List.of();
    }

    private List<String> onlineNames() {
        List<String> out = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        return out;
    }

    private static List<String> filter(List<String> src, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String x : src) if (x.toLowerCase(Locale.ROOT).startsWith(p)) out.add(x);
        return out;
    }
}
