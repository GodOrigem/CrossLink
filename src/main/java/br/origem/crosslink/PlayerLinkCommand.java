package br.origem.crosslink;

import br.origem.crosslink.compat.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/** /link -- self-service, qualquer jogador usa. */
public final class PlayerLinkCommand implements CommandExecutor {

    private final CrossLinkPlugin plugin;
    private final GroupManager groups;
    private final LinkService links;

    public PlayerLinkCommand(CrossLinkPlugin plugin, GroupManager groups, LinkService links) {
        this.plugin = plugin;
        this.groups = groups;
        this.links = links;
    }

    /** Cor mapeada para codigo legado; ver compat.Msg. */
    static void msg(CommandSender s, String m, String color) {
        s.sendMessage(Msg.PREFIX + color + m);
    }

    static final String GREEN = "§a", RED = "§c", WHITE = "§f",
                        GRAY = "§7", GOLD = "§6", YELLOW = "§e";

    @Override
    public boolean onCommand(CommandSender s, Command c,
                             String label, String[] a) {
        if (!(s instanceof Player p)) {
            msg(s, "This command is for players. Use /crosslink from console.", RED);
            return true;
        }

        if (a.length == 0) {
            LinkGroup g = groups.of(p.getUniqueId());
            if (g != null) { showStatus(p, g); return true; }
            // Bedrock ganha a interface; Java usa texto.
            if (plugin.bedrockUi() != null && BedrockUi.isBedrock(p.getUniqueId())) {
                plugin.startBedrockFlow(p);
            } else {
                msg(p, "To link: /link <other account name>", WHITE);
                msg(p, "To confirm: /link <6-digit code>", WHITE);
            }
            return true;
        }

        String arg = a[0].trim();

        if (arg.equalsIgnoreCase("status")) {
            LinkGroup g = groups.of(p.getUniqueId());
            if (g == null) msg(p, "Your account is not linked.", WHITE);
            else showStatus(p, g);
            return true;
        }

        // 6 digitos = confirmacao. Qualquer outra coisa = nick da outra conta.
        if (arg.matches("\\d{6}")) {
            handle(p, links.confirm(p, arg));
        } else {
            handle(p, links.request(p, arg));
        }
        return true;
    }

    private void showStatus(Player p, LinkGroup g) {
        msg(p, "Linked in group '" + g.name() + "':", GREEN);
        for (Map.Entry<UUID, String> e : g.members().entrySet()) {
            Player o = Bukkit.getPlayer(e.getKey());
            String tag = e.getKey().equals(p.getUniqueId()) ? " (you)"
                    : (o != null && o.isOnline() ? " [online]" : "");
            msg(p, "  - " + e.getValue() + tag, WHITE);
        }
        msg(p, "To unlink, ask an admin (/crosslink remove).", GRAY);
    }

    void handle(Player p, LinkService.Result r) {
        // if/else em vez de switch com pattern: pattern em switch e Java 21+,
        // e o alvo aqui e Java 17 para alcancar servidores 1.18.
        if (r instanceof LinkService.Result.CodeIssued ci) {
            msg(p, "Code: " + ci.code(), GOLD);
            msg(p, "Log in as '" + ci.targetName() + "' and run: /link " + ci.code(),
                    WHITE);
            msg(p, "Expires in " + (ci.seconds() / 60) + " minute(s).", GRAY);
            if (plugin.bedrockUi() != null && BedrockUi.isBedrock(p.getUniqueId())) {
                // Encadeado: o formulario do nick acabou de fechar agora.
                plugin.bedrockUi().chain(p,
                        () -> plugin.bedrockUi().showCode(p, ci.code(), ci.targetName(), ci.seconds()));
            }
        } else if (r instanceof LinkService.Result.Linked ln) {
            msg(p, "Linked with " + ln.otherName() + "!", GREEN);
            msg(p, "Inventory, ender chest and XP are now shared.", WHITE);
            Player other = Bukkit.getPlayer(ln.otherId());
            if (other != null) msg(other, "Your account was linked with " + p.getName() + ".",
                    GREEN);
            plugin.syncSkinForGroup(ln.group());
        } else if (r instanceof LinkService.Result.Error err) {
            msg(p, err.message(), RED);
        }
    }
}
