package br.origem.crosslink;

import org.bukkit.Bukkit;
import br.origem.crosslink.compat.Schedulers;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.UUID;

public final class CrossLinkPlugin extends JavaPlugin {

    private GroupManager groups;
    private SyncEngine engine;
    private LinkService links;
    private SkinService skins;
    private PromptTracker prompts;
    private PlayerLinkCommand playerCmd;
    private BedrockUi bedrockUi;   // null quando o Floodgate nao esta instalado
    private boolean skinsRestorer;
    private boolean tasksStarted;

    public BedrockUi bedrockUi() { return bedrockUi; }
    public PromptTracker prompts() { return prompts; }

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
            getLogger().info("Floodgate detected -- native Bedrock UI enabled.");
        } else {
            getLogger().info("Floodgate not found -- linking via text commands only.");
        }
        links = new LinkService(groups, engine, getConfig().getLong("link.code-timeout-seconds", 300),
                id -> bedrockUi != null && BedrockUi.isBedrock(id));

        // O SkinsRestorer tambem reescreve o perfil do jogador. Dois plugins
        // disputando a mesma skin dao resultado piscante e imprevisivel, entao
        // por padrao a gente sai da frente e diz isso no log.
        skinsRestorer = getServer().getPluginManager().getPlugin("SkinsRestorer") != null;
        if (skinsRestorer && skinHandledElsewhere()) {
            getLogger().info("SkinsRestorer detected -- leaving skins to it. "
                    + "Set link.skin-provider: native to override.");
        }

        getServer().getPluginManager().registerEvents(new SyncListener(this, engine, groups), this);
        getServer().getPluginManager().registerEvents(new JoinPrompt(this, groups, prompts), this);
        getServer().getPluginManager().registerEvents(new PetListener(this, engine, groups), this);

        LinkCommand admin = new LinkCommand(this, groups, engine);
        var pc = getCommand("crosslink");
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
                () -> PlayerLinkCommand.msg(p, "Link cancelled. Use /link whenever you want.",
                        PlayerLinkCommand.GRAY));
    }

    /** true quando outro plugin ja e dono das skins deste servidor. */
    public boolean skinHandledElsewhere() {
        return skinsRestorer && !"native".equalsIgnoreCase(
                getConfig().getString("link.skin-provider", "auto"));
    }

    /**
     * Aplica a skin da conta Java na conta Bedrock.
     *
     * Chamado ao vincular E a cada entrada da conta Bedrock: o Geyser aplica a
     * skin do Bedrock durante o login, entao sem reaplicar a nossa se perde no
     * primeiro relogin -- que foi exatamente o bug reportado.
     *
     * A textura fica guardada no grupo, entao a reaplicacao e local e imediata;
     * a Mojang so e consultada em segundo plano, para pegar troca de skin.
     */
    public void applyGroupSkin(Player bedrock, LinkGroup g, boolean announce) {
        if (g == null || bedrock == null) return;
        if (!getConfig().getBoolean("link.copy-java-skin", true)) return;
        if (skinHandledElsewhere()) {
            if (announce) PlayerLinkCommand.msg(bedrock,
                    "Linked. Skin left to SkinsRestorer.", PlayerLinkCommand.GRAY);
            return;
        }

        UUID javaId = null;
        for (UUID id : g.members().keySet()) {
            if (bedrockUi == null || !BedrockUi.isBedrock(id)) { javaId = id; break; }
        }
        if (javaId == null) return;

        // 1) imediato, do que ja conhecemos -- sem rede, sem espera
        if (g.hasSkin()) {
            skins.applyNow(bedrock, new SkinService.Texture(g.skinValue(), g.skinSignature()));
        }

        // 2) em segundo plano, confere se a skin da conta Java mudou
        final UUID jid = javaId;
        skins.fetchAsync(jid, tex -> {
            boolean novo = !tex.value().equals(g.skinValue());
            g.skin(tex.value(), tex.signature());
            if (novo) groups.save();
            if (bedrock.isOnline() && (novo || !g.hasSkin())) {
                skins.applyNow(bedrock, tex);
            }
            if (announce) PlayerLinkCommand.msg(bedrock,
                    "Java account skin applied.", PlayerLinkCommand.GREEN);
        }, err -> {
            if (announce) PlayerLinkCommand.msg(bedrock,
                    "Linked, but the skin failed: " + err, PlayerLinkCommand.YELLOW);
        });
    }

    /** Usado pelo fluxo de vinculo, que so conhece o nome do grupo. */
    public void syncSkinForGroup(String groupName) {
        LinkGroup g = groups.byName(groupName);
        if (g == null) return;
        for (UUID id : g.members().keySet()) {
            if (bedrockUi != null && BedrockUi.isBedrock(id)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) applyGroupSkin(p, g, true);
            }
        }
    }

    /** Chamado na entrada da conta Bedrock, com atraso para vencer o Geyser. */
    public void scheduleSkinOnJoin(Player p) {
        if (bedrockUi == null || !BedrockUi.isBedrock(p.getUniqueId())) return;
        if (!getConfig().getBoolean("link.reapply-skin-on-join", true)) return;
        LinkGroup g = groups.of(p.getUniqueId());
        if (g == null) return;
        long delay = Math.max(20, getConfig().getLong("link.skin-apply-delay-ticks", 40));
        Schedulers.globalLater(this, () -> {
            if (p.isOnline()) applyGroupSkin(p, g, false);
        }, delay);
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
        // Tarefas repetidas nao sao cancelaveis por id no Folia, entao em vez
        // de recriar no reload a gente inicia uma unica vez e le a config a
        // cada passada.
        if (tasksStarted) return;
        tasksStarted = true;

        long ticks = Math.max(5, getConfig().getLong("sweep-interval-ticks", 20));
        Schedulers.repeating(this, engine::sweep, ticks, ticks);

        long saveTicks = Math.max(600, getConfig().getLong("save-interval-ticks", 6000));
        Schedulers.repeating(this, () -> {
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
