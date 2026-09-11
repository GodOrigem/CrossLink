package br.origem.crosslink;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Copia a skin da conta Java para a conta Bedrock vinculada.
 *
 * Busca a textura assinada no sessionserver da Mojang e aplica no perfil do
 * jogador. A assinatura precisa vir junto (unsigned=false): sem ela o cliente
 * rejeita a textura e o jogador aparece com a skin padrao.
 */
public final class SkinService {

    private static final String SESSION =
            "https://sessionserver.mojang.com/session/minecraft/profile/%s?unsigned=false";

    private final Plugin plugin;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public SkinService(Plugin plugin) { this.plugin = plugin; }

    public record Texture(String value, String signature) {}

    /**
     * Aplica a skin de {@code javaId} em {@code target}, de forma assincrona.
     * O HTTP roda fora da thread principal; so a aplicacao volta pra ela.
     */
    public void copySkin(UUID javaId, Player target, Runnable onSuccess, java.util.function.Consumer<String> onError) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Texture tex;
            try {
                tex = fetch(javaId);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "failed to fetch skin for " + javaId, ex);
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> onError.accept("could not reach Mojang servers"));
                return;
            }
            if (tex == null) {
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> onError.accept("the Java account has no public skin"));
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!target.isOnline()) return;
                try {
                    apply(target, tex);
                    onSuccess.run();
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "failed to apply skin", ex);
                    onError.accept("could not apply the skin");
                }
            });
        });
    }

    private Texture fetch(UUID id) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(String.format(SESSION, id.toString().replace("-", ""))))
                .timeout(Duration.ofSeconds(15))
                .GET().build();
        HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) return null;
        String body = new String(res.body().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        String value = extract(body, "\"value\"");
        String sig = extract(body, "\"signature\"");
        if (value == null) return null;
        return new Texture(value, sig);
    }

    /** Extracao pontual: e um JSON pequeno e conhecido, nao vale puxar uma lib so pra isso. */
    private static String extract(String json, String key) {
        int k = json.indexOf(key);
        if (k < 0) return null;
        int colon = json.indexOf(':', k + key.length());
        if (colon < 0) return null;
        int open = json.indexOf('"', colon);
        if (open < 0) return null;
        int close = json.indexOf('"', open + 1);
        if (close < 0) return null;
        return json.substring(open + 1, close);
    }

    private void apply(Player target, Texture tex) {
        PlayerProfile profile = target.getPlayerProfile();
        profile.removeProperty("textures");
        profile.setProperty(new ProfileProperty("textures", tex.value(), tex.signature()));
        target.setPlayerProfile(profile);
    }
}
