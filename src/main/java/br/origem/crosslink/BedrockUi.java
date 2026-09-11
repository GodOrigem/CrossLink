package br.origem.crosslink;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Formularios nativos do Bedrock, via Floodgate/Cumulus.
 *
 * Esta classe so e carregada quando o Floodgate esta instalado -- quem chama
 * verifica antes. Assim o plugin continua funcionando num servidor so-Java,
 * sem NoClassDefFoundError.
 */
public final class BedrockUi {

    /**
     * O cliente Bedrock so mostra um formulario por vez. Mandar o proximo no
     * mesmo instante em que o anterior esta fechando faz o novo ser descartado,
     * entao os encadeados esperam alguns ticks.
     */
    private static final long CHAIN_DELAY_TICKS = 10;

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
                .title("Link account")
                .content("You can link this Bedrock account to a Java account.\n\n"
                        + "Both will share inventory, ender chest and XP, "
                        + "and your Java skin is applied here.\n\n"
                        + "Link now?")
                .button1("Link")
                .button2("Not now")
                .validResultHandler(res -> guarded(p, () -> {
                    if (res.clickedFirst()) chain(p, onAccept); else onDecline.run();
                }))
                .closedOrInvalidResultHandler(() -> guarded(p, onDecline))
                .build();
        send(p, form);
    }

    /**
     * Pede o nick da conta Java.
     *
     * O formulario tem UM unico componente de proposito. Labels tambem ocupam
     * indice na resposta do Cumulus, entao misturar label e input faz
     * asInput(0) estourar "Expected input on 0, got label" -- a excecao morre
     * dentro do handler e o jogador fica olhando pra um formulario que nao
     * responde. A instrucao vai no content, que nao vira componente.
     */
    public void askJavaName(Player p, Consumer<String> onSubmit, Runnable onCancel) {
        CustomForm form = CustomForm.builder()
                .title("Link Java account")
                .input("Type the EXACT name of your Java account", "Steve")
                .validResultHandler(res -> guarded(p, () -> {
                    String name = res.asInput(0);
                    if (name == null || name.isBlank()) onCancel.run();
                    else onSubmit.accept(name.trim());
                }))
                .closedOrInvalidResultHandler(() -> guarded(p, onCancel))
                .build();
        send(p, form);
    }

    /** Mostra o codigo gerado, com instrucao. */
    public void showCode(Player p, String code, String javaName, long seconds) {
        ModalForm form = ModalForm.builder()
                .title("Link code")
                .content("Your code:\n\n§l" + code + "§r\n\n"
                        + "Log in as §l" + javaName + "§r and run:\n"
                        + "§l/link " + code + "§r\n\n"
                        + "Expires in " + (seconds / 60) + " minute(s).\n"
                        + "The code is in your chat too.")
                .button1("Got it")
                .button2("Close")
                .validResultHandler(res -> { })
                .closedOrInvalidResultHandler(() -> { })
                .build();
        send(p, form);
    }

    public void info(Player p, String title, String message) {
        ModalForm form = ModalForm.builder()
                .title(title)
                .content(message)
                .button1("Ok")
                .button2("Close")
                .validResultHandler(res -> { })
                .closedOrInvalidResultHandler(() -> { })
                .build();
        send(p, form);
    }

    /** Agenda algo que vai abrir outro formulario, dando tempo do atual fechar. */
    public void chain(Player p, Runnable r) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) r.run();
        }, CHAIN_DELAY_TICKS);
    }

    private void send(Player p, org.geysermc.cumulus.form.Form form) {
        try {
            FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
        } catch (Throwable t) {
            plugin.getLogger().warning("failed to send form to " + p.getName() + ": " + t);
        }
    }

    /**
     * Handlers do Cumulus rodam fora da thread principal e engolem excecao:
     * se algo estourar la dentro, o jogador so ve o formulario parar de
     * responder. Aqui a gente volta pra thread principal e, se der errado,
     * pelo menos avisa em vez de deixar o jogador no escuro.
     */
    private void guarded(Player p, Runnable r) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                r.run();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "error handling form from " + p.getName(), t);
                if (p.isOnline()) {
                    p.sendMessage("§c[Vinculo] Algo deu errado. Tente /link novamente.");
                }
            }
        });
    }
}
