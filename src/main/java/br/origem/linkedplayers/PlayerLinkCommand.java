package br.origem.linkedplayers;

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

    private final LinkedPlayersPlugin plugin;
    private final GroupManager groups;
    private final LinkService links;

    public PlayerLinkCommand(LinkedPlayersPlugin plugin, GroupManager groups, LinkService links) {
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
            msg(s, "Esse comando e para jogadores. No console use /plink.", NamedTextColor.RED);
            return true;
        }

        if (a.length == 0) {
            LinkGroup g = groups.of(p.getUniqueId());
            if (g != null) { showStatus(p, g); return true; }
            // Bedrock ganha a interface; Java usa texto.
            if (plugin.bedrockUi() != null && BedrockUi.isBedrock(p.getUniqueId())) {
                plugin.startBedrockFlow(p);
            } else {
                msg(p, "Para vincular: /link <nick da outra conta>", NamedTextColor.WHITE);
                msg(p, "Para confirmar: /link <codigo de 6 digitos>", NamedTextColor.WHITE);
            }
            return true;
        }

        String arg = a[0].trim();

        if (arg.equalsIgnoreCase("status")) {
            LinkGroup g = groups.of(p.getUniqueId());
            if (g == null) msg(p, "Sua conta nao esta vinculada.", NamedTextColor.WHITE);
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
        msg(p, "Vinculada no grupo '" + g.name() + "':", NamedTextColor.GREEN);
        for (Map.Entry<UUID, String> e : g.members().entrySet()) {
            Player o = Bukkit.getPlayer(e.getKey());
            String tag = e.getKey().equals(p.getUniqueId()) ? " (voce)"
                    : (o != null && o.isOnline() ? " [online]" : "");
            msg(p, "  - " + e.getValue() + tag, NamedTextColor.WHITE);
        }
        msg(p, "Para desfazer, peca a um admin (/plink remove).", NamedTextColor.GRAY);
    }

    void handle(Player p, LinkService.Result r) {
        switch (r) {
            case LinkService.Result.CodeIssued ci -> {
                msg(p, "Codigo: " + ci.code(), NamedTextColor.GOLD);
                msg(p, "Entre na conta '" + ci.targetName() + "' e rode: /link " + ci.code(),
                        NamedTextColor.WHITE);
                msg(p, "Expira em " + (ci.seconds() / 60) + " minuto(s).", NamedTextColor.GRAY);
                if (plugin.bedrockUi() != null && BedrockUi.isBedrock(p.getUniqueId())) {
                    // Encadeado: o formulario do nick acabou de fechar agora.
                    plugin.bedrockUi().chain(p,
                            () -> plugin.bedrockUi().showCode(p, ci.code(), ci.targetName(), ci.seconds()));
                }
            }
            case LinkService.Result.Linked ln -> {
                msg(p, "Vinculado com " + ln.otherName() + "!", NamedTextColor.GREEN);
                msg(p, "Inventario, ender chest e XP agora sao compartilhados.", NamedTextColor.WHITE);
                Player other = Bukkit.getPlayer(ln.otherId());
                if (other != null) msg(other, "Sua conta foi vinculada com " + p.getName() + ".",
                        NamedTextColor.GREEN);
                plugin.syncSkinForGroup(ln.group());
            }
            case LinkService.Result.Error err -> msg(p, err.message(), NamedTextColor.RED);
        }
    }
}
