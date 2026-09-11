package br.origem.crosslink;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Guarda os grupos e o estado de cada um em groups.yml. */
public final class GroupManager {

    private final File file;
    private final Logger log;
    private final Map<String, LinkGroup> byName = new LinkedHashMap<>();
    private final Map<UUID, LinkGroup> byMember = new HashMap<>();

    public GroupManager(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public Collection<LinkGroup> all() { return byName.values(); }
    public LinkGroup byName(String n) { return byName.get(n.toLowerCase(Locale.ROOT)); }
    public LinkGroup of(UUID player) { return byMember.get(player); }

    public LinkGroup create(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (byName.containsKey(key)) return null;
        LinkGroup g = new LinkGroup(key);
        byName.put(key, g);
        return g;
    }

    public boolean delete(String name) {
        LinkGroup g = byName.remove(name.toLowerCase(Locale.ROOT));
        if (g == null) return false;
        g.members().keySet().forEach(byMember::remove);
        return true;
    }

    public void addMember(LinkGroup g, UUID id, String displayName) {
        g.add(id, displayName);
        byMember.put(id, g);
        if (g.primary() == null) g.primary(id);
    }

    public void removeMember(LinkGroup g, UUID id) {
        g.remove(id);
        byMember.remove(id);
    }

    public void load() {
        byName.clear();
        byMember.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection groups = yml.getConfigurationSection("groups");
        if (groups == null) return;
        for (String key : groups.getKeys(false)) {
            ConfigurationSection gs = groups.getConfigurationSection(key);
            if (gs == null) continue;
            LinkGroup g = new LinkGroup(key);
            ConfigurationSection ms = gs.getConfigurationSection("members");
            if (ms != null) {
                for (String raw : ms.getKeys(false)) {
                    try {
                        UUID id = UUID.fromString(raw);
                        g.add(id, ms.getString(raw, raw));
                        byMember.put(id, g);
                    } catch (IllegalArgumentException ex) {
                        log.warning("invalid UUID in groups.yml (group " + key + "): " + raw);
                    }
                }
            }
            String prim = gs.getString("primary");
            if (prim != null) {
                try { g.primary(UUID.fromString(prim)); }
                catch (IllegalArgumentException ex) { log.warning("invalid primary in group " + key); }
            }
            if (g.primary() == null && !g.members().isEmpty()) {
                g.primary(g.members().keySet().iterator().next());
            }
            g.skin(gs.getString("skin.value"), gs.getString("skin.signature"));
            g.state(SharedState.load(gs.getConfigurationSection("state")));
            byName.put(key, g);
        }
        log.info("loaded " + byName.size() + " group(s)");
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (LinkGroup g : byName.values()) {
            String base = "groups." + g.name();
            if (g.primary() != null) yml.set(base + ".primary", g.primary().toString());
            for (Map.Entry<UUID, String> e : g.members().entrySet()) {
                yml.set(base + ".members." + e.getKey(), e.getValue());
            }
            if (g.hasSkin()) {
                yml.set(base + ".skin.value", g.skinValue());
                yml.set(base + ".skin.signature", g.skinSignature());
            }
            g.state().save(yml.createSection(base + ".state"));
        }
        try {
            yml.save(file);
        } catch (IOException ex) {
            log.log(Level.SEVERE, "failed to save groups.yml", ex);
        }
    }
}
