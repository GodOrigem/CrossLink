package br.origem.linkedplayers;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Formularios nativos do Bedrock, via Floodgate/Cumulus.
 *
 * Esta classe so e carregada quando o Floodgate esta instalado -- quem chama
 * verifica antes. Assim o plugin continua funcionando num servidor so-Java,
 * sem NoClassDefFoundError.
 */
public final class BedrockUi {

    private final Plugin plugin;

    public BedrockUi(Plugin plugin) { this.plugin = plugin; }

    public static boolean isBedrock(UUID id) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(id);
        } catch (Throwable t) {
            return false;   // Floodgate ausente ou ainda nao inicializado
        }
    }

    /** Convite automatico do primeiro login: modal sim/nao. */
    public void offerLink(Player p, Runnable onAccept, Runnable onDecline) {
        ModalForm form = ModalForm.builder()
                .title("Vincular conta")
                .content("Voce pode vincular esta conta Bedrock a uma conta Java.\n\n"
                        + "As duas passam a dividir inventario, ender chest e XP, "
                        + "e sua skin Java e aplicada aqui.\n\n"
                        + "Quer vincular agora?")
                .button1("Vincular")
                .button2("Agora nao")
                .validResultHandler(res -> main(() -> {
                    if (res.clickedFirst()) onAccept.run(); else onDecline.run();
                }))
                .closedOrInvalidResultHandler(() -> main(onDecline))
                .build();
        send(p, form);
    }

    /** Pede o nick da conta Java. */
    public void askJavaName(Player p, Consumer<String> onSubmit, Runnable onCancel) {
        CustomForm form = CustomForm.builder()
                .title("Vincular conta Java")
                .label("Digite o nick EXATO da sua conta Java.\n"
                        + "Depois entre nela e use o codigo que vou te dar.")
                .input("Nick Java", "Origem_")
                .validResultHandler(res -> {
                    String name = res.asInput(0);
                    main(() -> {
                        if (name == null || name.isBlank()) onCancel.run();
                        else onSubmit.accept(name.trim());
                    });
                })
                .closedOrInvalidResultHandler(() -> main(onCancel))
                .build();
        send(p, form);
    }

    /** Mostra o codigo gerado, com instrucao. */
    public void showCode(Player p, String code, String javaName, long seconds) {
        ModalForm form = ModalForm.builder()
                .title("Codigo de vinculo")
                .content("Seu codigo:\n\n§l" + code + "§r\n\n"
                        + "Entre na conta Java §l" + javaName + "§r e rode:\n"
                        + "§l/link " + code + "§r\n\n"
                        + "Expira em " + (seconds / 60) + " minuto(s).")
                .button1("Entendi")
                .button2("Fechar")
                .build();
        send(p, form);
    }

    public void info(Player p, String title, String message) {
        ModalForm form = ModalForm.builder()
                .title(title)
                .content(message)
                .button1("Ok")
                .button2("Fechar")
                .build();
        send(p, form);
    }

    private void send(Player p, org.geysermc.cumulus.form.Form form) {
        try {
            FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
        } catch (Throwable t) {
            plugin.getLogger().warning("falha ao enviar formulario para " + p.getName() + ": " + t);
        }
    }

    /** Handlers do Cumulus podem vir de outra thread; Bukkit exige a principal. */
    private void main(Runnable r) {
        plugin.getServer().getScheduler().runTask(plugin, r);
    }
}
