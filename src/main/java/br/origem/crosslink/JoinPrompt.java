package br.origem.crosslink;

import br.origem.crosslink.compat.Schedulers;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Oferece o vinculo no primeiro login de uma conta Bedrock.
 *
 * Aparece uma vez so por conta: aceitando ou recusando, fica marcado. Quem
 * recusar nao e mais incomodado -- so volta pelo /link.
 */
public final class JoinPrompt implements Listener {

    private final CrossLinkPlugin plugin;
    private final GroupManager groups;
    private final PromptTracker prompts;

    public JoinPrompt(CrossLinkPlugin plugin, GroupManager groups, PromptTracker prompts) {
        this.plugin = plugin;
        this.groups = groups;
        this.prompts = prompts;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (plugin.bedrockUi() == null) return;
        if (!plugin.getConfig().getBoolean("link.prompt-on-first-join", true)) return;
        if (groups.of(p.getUniqueId()) != null) return;     // ja vinculado
        if (prompts.alreadyPrompted(p.getUniqueId())) return;
        if (!BedrockUi.isBedrock(p.getUniqueId())) return;

        long delay = Math.max(20, plugin.getConfig().getLong("link.prompt-delay-ticks", 60));
        Schedulers.globalLater(plugin, () -> {
            if (!p.isOnline()) return;
            prompts.markPrompted(p.getUniqueId());
            prompts.save();
            plugin.bedrockUi().offerLink(p,
                    () -> plugin.startBedrockFlow(p),
                    () -> { /* recusou: nada a fazer, ja esta marcado */ });
        }, delay);
    }
}
