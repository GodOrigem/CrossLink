package br.origem.linkedplayers;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lembra quem ja viu o convite automatico.
 *
 * O formulario aparece sozinho uma unica vez por conta. Quem recusar nao e
 * incomodado de novo -- so volta a ver se rodar /link por conta propria.
 */
public final class PromptTracker {

    private final File file;
    private final Logger log;
    private final Set<UUID> prompted = new HashSet<>();
    private boolean dirty;

    public PromptTracker(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public boolean alreadyPrompted(UUID id) { return prompted.contains(id); }

    public void markPrompted(UUID id) {
        if (prompted.add(id)) dirty = true;
    }

    public void load() {
        prompted.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String raw : yml.getStringList("prompted")) {
            try {
                prompted.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                log.warning("UUID invalida em prompted.yml: " + raw);
            }
        }
    }

    public void save() {
        if (!dirty) return;
        YamlConfiguration yml = new YamlConfiguration();
        List<String> out = new ArrayList<>(prompted.size());
        prompted.forEach(u -> out.add(u.toString()));
        yml.set("prompted", out);
        try {
            yml.save(file);
            dirty = false;
        } catch (IOException ex) {
            log.log(Level.SEVERE, "falha ao salvar prompted.yml", ex);
        }
    }
}
