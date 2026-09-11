package br.origem.crosslink;

import br.origem.crosslink.compat.Msg;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.List;

import java.io.File;
import java.util.*;

public final class LinkCommand implements CommandExecutor, TabCompleter {

    private final CrossLinkPlugin plugin;
    private final GroupManager groups;
    private final SyncEngine engine;

    public LinkCommand(CrossLinkPlugin plugin, GroupManager groups, SyncEngine engine) {
        this.plugin = plugin;
        this.groups = groups;
        this.engine = engine;
    }

    private void ok(CommandSender s, String m)   { Msg.ok(s, m); }
    private void err(CommandSender s, String m)  { Msg.err(s, m); }
    private void info(CommandSender s, String m) { Msg.info(s, m); }

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
            err(s, "'" + arg + "' is not online and is not a UUID.");
            info(s, "Ask them to join and run it again, or pass the UUID directly.");
            info(s, "A Bedrock account cannot be resolved by name while offline.");
            return null;
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command c,
                             String label, String[] a) {
        if (a.length == 0) { usage(s, label); return true; }

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (a.length < 2) { err(s, "usage: /" + label + " create <group>"); return true; }
                if (groups.create(a[1]) == null) { err(s, "group '" + a[1] + "' already exists."); return true; }
                groups.save();
                ok(s, "group '" + a[1].toLowerCase(Locale.ROOT) + "' created.");
            }
            case "delete" -> {
                if (a.length < 2) { err(s, "usage: /" + label + " delete <group>"); return true; }
                if (!groups.delete(a[1])) { err(s, "group '" + a[1] + "' does not exist."); return true; }
                groups.save();
                ok(s, "group deleted. Players keep their current state.");
            }
            case "add" -> {
                if (a.length < 3) { err(s, "usage: /" + label + " add <group> <player|uuid>"); return true; }
                LinkGroup g = groups.byName(a[1]);
                if (g == null) { err(s, "group '" + a[1] + "' does not exist."); return true; }
                UUID id = resolve(s, a[2]);
                if (id == null) return true;
                LinkGroup atual = groups.of(id);
                if (atual != null) {
                    err(s, a[2] + " is already in group '" + atual.name() + "'.");
                    return true;
                }
                Player p = Bukkit.getPlayer(id);
                groups.addMember(g, id, p != null ? p.getName() : id.toString());
                groups.save();
                ok(s, a[2] + " joined group '" + g.name() + "'.");
                // Adota a partir da primaria: quem entra recebe, nunca sobrescreve.
                engine.adoptFromPrimary(g);
                if (p != null) info(s, "primary account state applied to " + p.getName() + ".");
            }
            case "remove" -> {
                if (a.length < 3) { err(s, "usage: /" + label + " remove <group> <player|uuid>"); return true; }
                LinkGroup g = groups.byName(a[1]);
                if (g == null) { err(s, "group '" + a[1] + "' does not exist."); return true; }
                UUID id = resolve(s, a[2]);
                if (id == null) return true;
                if (!g.has(id)) { err(s, "that player is not in the group."); return true; }
                groups.removeMember(g, id);
                groups.save();
                ok(s, "removed from group '" + g.name() + "'. Keeps the current inventory.");
            }
            case "sync" -> {
                if (a.length < 2) { err(s, "usage: /" + label + " sync <jogador>"); return true; }
                Player src = Bukkit.getPlayerExact(a[1]);
                if (src == null) { err(s, a[1] + " is not online."); return true; }
                if (groups.of(src.getUniqueId()) == null) { err(s, a[1] + " is not in any group."); return true; }
                engine.syncFrom(src);
                groups.save();
                ok(s, "" + src.getName() + " is now the group source of truth.");
            }
            case "list" -> {
                if (groups.all().isEmpty()) { info(s, "no groups defined."); return true; }
                for (LinkGroup g : groups.all()) {
                    info(s, "group '" + g.name() + "' (" + g.members().size() + "):");
                    for (Map.Entry<UUID, String> e : g.members().entrySet()) {
                        Player p = Bukkit.getPlayer(e.getKey());
                        String status = (p != null && p.isOnline()) ? " [online]" : "";
                        String prim = g.isPrimary(e.getKey()) ? " [PRIMARY]" : "";
                        info(s, "   - " + e.getValue() + prim + status + "  " + e.getKey());
                    }
                }
            }
            case "primary" -> {
                if (a.length < 3) { err(s, "usage: /" + label + " primary <group> <player|uuid>"); return true; }
                LinkGroup g = groups.byName(a[1]);
                if (g == null) { err(s, "group '" + a[1] + "' does not exist."); return true; }
                UUID id = resolve(s, a[2]);
                if (id == null) return true;
                if (!g.has(id)) { err(s, "that player is not in the group."); return true; }
                g.primary(id);
                groups.save();
                ok(s, "primary account of group '" + g.name() + "' is now " + a[2] + ".");
                info(s, "the real playerdata lives on that account; the others mirror it.");
            }
            case "backups" -> {
                if (a.length < 2) { err(s, "usage: /" + label + " backups <player|uuid>"); return true; }
                UUID id = resolve(s, a[1]);
                if (id == null) return true;
                File[] list = backupFiles(id);
                if (list.length == 0) { info(s, "no backups for that player."); return true; }
                info(s, list.length + " backup(s), newest first:");
                for (int i = list.length - 1; i >= 0; i--) {
                    YamlConfiguration y = YamlConfiguration.loadConfiguration(list[i]);
                    info(s, "   " + list[i].getName() + "  (" + y.getString("when", "?") + ")");
                }
                info(s, "restore with: /" + label + " restore <player> [file]");
            }
            case "restore" -> {
                if (a.length < 2) { err(s, "usage: /" + label + " restore <player> [file]"); return true; }
                Player target = Bukkit.getPlayerExact(a[1]);
                if (target == null) { err(s, a[1] + " must be online to restore."); return true; }
                File[] list = backupFiles(target.getUniqueId());
                if (list.length == 0) { err(s, "no backups for " + a[1] + "."); return true; }
                File pick = list[list.length - 1];
                if (a.length >= 3) {
                    pick = null;
                    for (File f : list) if (f.getName().equals(a[2])) pick = f;
                    if (pick == null) { err(s, "backup '" + a[2] + "' not found."); return true; }
                }
                YamlConfiguration y = YamlConfiguration.loadConfiguration(pick);
                SharedState st = SharedState.load(y.getConfigurationSection("state"));
                engine.apply(target, st);
                ok(s, "restored " + pick.getName() + " onto " + target.getName() + ".");
                info(s, "the previous state became a new backup, so this is undoable.");
            }
            case "resetprompt" -> {
                if (a.length < 2) { err(s, "usage: /" + label + " resetprompt <player|uuid>"); return true; }
                UUID id = resolve(s, a[1]);
                if (id == null) return true;
                if (plugin.prompts().reset(id)) {
                    plugin.prompts().save();
                    ok(s, "prompt reset. Reconnect the account for the form to show up.");
                } else {
                    info(s, "that account had not been prompted yet -- the form would already show.");
                }
            }
            case "pets" -> {
                if (!(s instanceof Player p)) { err(s, "run this in game, next to the animal."); return true; }
                int r = a.length >= 2 ? parseInt(a[1], 10) : 10;
                List<String> found = engine.describePetsNear(p, r);
                if (found.isEmpty()) { info(s, "no tameable animals within " + r + " blocks."); return true; }
                info(s, found.size() + " animal(s) within " + r + " blocks:");
                for (String line : found) info(s, "   " + line);
                info(s, "'OUTSIDE your group' means the animal was tamed by an account");
                info(s, "that is not linked -- use /" + label + " claimpets to adopt it.");
            }
            case "claimpets" -> {
                if (!(s instanceof Player p)) { err(s, "run this in game, next to the animal."); return true; }
                if (groups.of(p.getUniqueId()) == null) { err(s, "you are not in a linked group."); return true; }
                int r = a.length >= 2 ? parseInt(a[1], 10) : 10;
                int n = engine.claimPetsNear(p, r);
                if (n == 0) info(s, "no animal to adopt within " + r + " blocks.");
                else ok(s, n + " animal(s) now belong to you and your linked account.");
            }
            case "reload" -> {
                plugin.reloadAll();
                ok(s, "config and groups reloaded.");
            }
            default -> usage(s, label);
        }
        return true;
    }

    private static int parseInt(String v, int fallback) {
        try { return Math.max(1, Math.min(200, Integer.parseInt(v))); }
        catch (NumberFormatException e) { return fallback; }
    }

    private File[] backupFiles(UUID id) {
        File dir = new File(plugin.getDataFolder(), "backups/" + id);
        File[] list = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (list == null) return new File[0];
        Arrays.sort(list, Comparator.comparing(File::getName));
        return list;
    }

    private void usage(CommandSender s, String label) {
        info(s, "/" + label + " create <group>");
        info(s, "/" + label + " add <group> <player|uuid>");
        info(s, "/" + label + " remove <group> <player|uuid>");
        info(s, "/" + label + " sync <player>   - force this one as source of truth");
        info(s, "/" + label + " primary <group> <player> - set the account that owns the data");
        info(s, "/" + label + " resetprompt <player> - make the Bedrock prompt show again");
        info(s, "/" + label + " pets [radius]      - who owns the animals around you");
        info(s, "/" + label + " claimpets [radius] - adopt them into your group");
        info(s, "/" + label + " backups <player>");
        info(s, "/" + label + " restore <player> [file]");
        info(s, "/" + label + " list");
        info(s, "/" + label + " delete <group>");
        info(s, "/" + label + " reload");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c,
                                      String label, String[] a) {
        if (a.length == 1) {
            return filter(List.of("create", "add", "remove", "sync", "primary", "backups",
                    "restore", "resetprompt", "pets", "claimpets", "list", "delete", "reload"), a[0]);
        }
        if (a.length == 2) {
            if (a[0].equalsIgnoreCase("sync") || a[0].equalsIgnoreCase("restore")
                    || a[0].equalsIgnoreCase("backups") || a[0].equalsIgnoreCase("resetprompt")) {
                return filter(onlineNames(), a[1]);
            }
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
