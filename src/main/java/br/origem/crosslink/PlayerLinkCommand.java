package br.origem.crosslink;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

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

    static void msg(CommandSender s, String m, NamedTextColor c) {
        s.sendMessage(Component.text("[Vinculo] ", NamedTextColor.AQUA)
                .append(Component.text(m, c)));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command c,
                             @NotNull String label, String[] a) {
        if (!(s instanceof Player p)) {
            msg(s, "This command is for players. Use /crosslink from console.", NamedTextColor.RED);
            return true;
        }

        if (a.length == 0) {
            LinkGroup g = groups.of(p.getUniqueId());
            if (g != null) { showStatus(p, g); return true; }
            // Bedrock ganha a interface; Java usa texto.
            if (plugin.bedrockUi() != null && BedrockUi.isBedrock(p.getUniqueId())) {
                plugin.startBedrockFlow(p);
            } else {
                msg(p, "To link: /link <other account name>", NamedTextColor.WHITE);
                msg(p, "To confirm: /link <6-digit code>", NamedTextColor.WHITE);
            }
            return true;
        }

        String arg = a[0].trim();

        if (arg.equalsIgnoreCase("status")) {
            LinkGroup g = groups.of(p.getUniqueId());
            if (g == null) msg(p, "Your account is not linked.", NamedTextColor.WHITE);
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
        msg(p, "Linked in group '" + g.name() + "':", NamedTextColor.GREEN);
        for (Map.Entry<UUID, String> e : g.members().entrySet()) {
            Player o = Bukkit.getPlayer(e.getKey());
            String tag = e.getKey().equals(p.getUniqueId()) ? " (you)"
                    : (o != null && o.isOnline() ? " [online]" : "");
            msg(p, "  - " + e.getValue() + tag, NamedTextColor.WHITE);
        }
        msg(p, "To unlink, ask an admin (/crosslink remove).", NamedTextColor.GRAY);
    }

    void handle(Player p, LinkService.Result r) {
        switch (r) {
            case LinkService.Result.CodeIssued ci -> {
                msg(p, "Code: " + ci.code(), NamedTextColor.GOLD);
                msg(p, "Log in as '" + ci.targetName() + "' and run: /link " + ci.code(),
                        NamedTextColor.WHITE);
                msg(p, "Expires in " + (ci.seconds() / 60) + " minute(s).", NamedTextColor.GRAY);
                if (plugin.bedrockUi() != null && BedrockUi.isBedrock(p.getUniqueId())) {
                    // Encadeado: o formulario do nick acabou de fechar agora.
                    plugin.bedrockUi().chain(p,
                            () -> plugin.bedrockUi().showCode(p, ci.code(), ci.targetName(), ci.seconds()));
                }
            }
            case LinkService.Result.Linked ln -> {
                msg(p, "Linked with " + ln.otherName() + "!", NamedTextColor.GREEN);
                msg(p, "Inventory, ender chest and XP are now shared.", NamedTextColor.WHITE);
                Player other = Bukkit.getPlayer(ln.otherId());
                if (other != null) msg(other, "Your account was linked with " + p.getName() + ".",
                        NamedTextColor.GREEN);
                plugin.syncSkinForGroup(ln.group());
            }
            case LinkService.Result.Error err -> msg(p, err.message(), NamedTextColor.RED);
        }
    }
}
