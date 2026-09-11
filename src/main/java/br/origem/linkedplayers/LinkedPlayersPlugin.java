package br.origem.linkedplayers;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.UUID;

public final class LinkedPlayersPlugin extends JavaPlugin {

    private GroupManager groups;
    private SyncEngine engine;
    private LinkService links;
    private SkinService skins;
    private PromptTracker prompts;
    private PlayerLinkCommand playerCmd;
    private BedrockUi bedrockUi;   // null quando o Floodgate nao esta instalado
    private int sweepTask = -1;

    public BedrockUi bedrockUi() { return bedrockUi; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        groups = new GroupManager(new File(getDataFolder(), "groups.yml"), getLogger());
        groups.load();
        prompts = new PromptTracker(new File(getDataFolder(), "prompted.yml"), getLogger());
        prompts.load();

        engine = new SyncEngine(this, groups);
        applyConfig();
        skins = new SkinService(this);
        if (getServer().getPluginManager().getPlugin("floodgate") != null) {
            bedrockUi = new BedrockUi(this);
            getLogger().info("Floodgate detectado -- interface nativa do Bedrock ativa.");
        } else {
            getLogger().info("Floodgate ausente -- vinculo so por comando de texto.");
        }
        links = new LinkService(groups, engine, getConfig().getLong("link.code-timeout-seconds", 300),
                id -> bedrockUi != null && BedrockUi.isBedrock(id));

        getServer().getPluginManager().registerEvents(new SyncListener(this, engine, groups), this);
        getServer().getPluginManager().registerEvents(new JoinPrompt(this, groups, prompts), this);

        LinkCommand admin = new LinkCommand(this, groups, engine);
        var pc = getCommand("plink");
        if (pc != null) { pc.setExecutor(admin); pc.setTabCompleter(admin); }

        playerCmd = new PlayerLinkCommand(this, groups, links);
        var lc = getCommand("link");
        if (lc != null) lc.setExecutor(playerCmd);

        startTasks();

        for (Player p : getServer().getOnlinePlayers()) {
            if (groups.of(p.getUniqueId()) != null) engine.onJoin(p);
        }
    }

    @Override
    public void onDisable() {
        // Mesmo caminho do quit: grava o estado e esvazia as secundarias, pra
        // nao sobrar uma copia dos itens na playerdata delas.
        for (Player p : getServer().getOnlinePlayers()) {
            if (groups.of(p.getUniqueId()) != null) engine.onQuit(p);
        }
        if (groups != null) groups.save();
        if (prompts != null) prompts.save();
    }

    /** Convite do Bedrock: pergunta o nick Java e emite o codigo. */
    public void startBedrockFlow(Player p) {
        if (bedrockUi == null) return;
        bedrockUi.askJavaName(p,
                name -> playerCmd.handle(p, links.request(p, name)),
                () -> PlayerLinkCommand.msg(p, "Vinculo cancelado. Use /link quando quiser.",
                        NamedTextColor.GRAY));
    }

    /**
     * Depois de vincular, aplica a skin da conta Java na conta Bedrock.
     * Se nao houver exatamente uma de cada, nao ha o que copiar.
     */
    public void syncSkinForGroup(String groupName) {
        if (!getConfig().getBoolean("link.copy-java-skin", true)) return;
        LinkGroup g = groups.byName(groupName);
        if (g == null) return;

        UUID javaId = null;
        Player bedrock = null;
        for (Map.Entry<UUID, String> e : g.members().entrySet()) {
            boolean isBedrock = bedrockUi != null && BedrockUi.isBedrock(e.getKey());
            if (isBedrock) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) bedrock = p;
            } else {
                javaId = e.getKey();
            }
        }
        if (javaId == null || bedrock == null) return;

        Player target = bedrock;
        skins.copySkin(javaId, target,
                () -> PlayerLinkCommand.msg(target, "Skin da conta Java aplicada.", NamedTextColor.GREEN),
                err -> PlayerLinkCommand.msg(target, "Vinculo feito, mas a skin falhou: " + err,
                        NamedTextColor.YELLOW));
    }

    private void applyConfig() {
        engine.syncInventory = getConfig().getBoolean("sync.inventory", true);
        engine.syncEnderChest = getConfig().getBoolean("sync.ender-chest", true);
        engine.syncXp = getConfig().getBoolean("sync.xp", true);
        engine.syncHealth = getConfig().getBoolean("sync.health", false);
        engine.syncFood = getConfig().getBoolean("sync.food", false);
        engine.syncPets = getConfig().getBoolean("sync.pets", true);
        engine.clearSecondaryOnQuit = getConfig().getBoolean("safety.clear-secondary-on-quit", true);
        engine.backupsToKeep = getConfig().getInt("safety.backups-to-keep", 10);
    }

    private void startTasks() {
        if (sweepTask != -1) getServer().getScheduler().cancelTask(sweepTask);
        long ticks = Math.max(5, getConfig().getLong("sweep-interval-ticks", 20));
        sweepTask = getServer().getScheduler().runTaskTimer(this, engine::sweep, ticks, ticks).getTaskId();

        long saveTicks = Math.max(600, getConfig().getLong("save-interval-ticks", 6000));
        getServer().getScheduler().runTaskTimer(this, () -> {
            groups.save();
            prompts.save();
            links.purge();
        }, saveTicks, saveTicks);
    }

    public void reloadAll() {
        reloadConfig();
        applyConfig();
        groups.load();
        prompts.load();
        startTasks();
    }
}
