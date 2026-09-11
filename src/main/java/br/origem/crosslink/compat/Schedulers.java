package br.origem.crosslink.compat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Agendamento que funciona em Bukkit/Paper e em Folia.
 *
 * O Folia removeu a thread principal unica: BukkitScheduler.runTask lanca
 * UnsupportedOperationException la. Cada tarefa precisa ir para o scheduler da
 * regiao dona da entidade, ou para o scheduler global.
 *
 * Tudo por reflexao de proposito -- assim o plugin continua compilando contra
 * a API do Spigot e roda nas duas plataformas com um jar so.
 */
public final class Schedulers {

    private static final boolean FOLIA = detectFolia();

    private Schedulers() {}

    public static boolean isFolia() { return FOLIA; }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Roda agora, na thread certa para esta entidade. */
    public static void run(Plugin plugin, Entity entity, Runnable task) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }
        try {
            Object sched = entity.getClass().getMethod("getScheduler").invoke(entity);
            Method run = sched.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            run.invoke(sched, plugin, (Consumer<Object>) t -> task.run(), null);
        } catch (Exception ex) {
            // Entidade pode ter sido removida entre o agendamento e a execucao.
            global(plugin, task);
        }
    }

    /** Roda daqui a N ticks, na thread certa para esta entidade. */
    public static void runLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            return;
        }
        try {
            Object sched = entity.getClass().getMethod("getScheduler").invoke(entity);
            Method run = sched.getClass().getMethod("runDelayed",
                    Plugin.class, Consumer.class, Runnable.class, long.class);
            run.invoke(sched, plugin, (Consumer<Object>) t -> task.run(), null, Math.max(1, delayTicks));
        } catch (Exception ex) {
            globalLater(plugin, task, delayTicks);
        }
    }

    /** Tarefa que nao pertence a nenhuma entidade. */
    public static void global(Plugin plugin, Runnable task) {
        if (!FOLIA) { Bukkit.getScheduler().runTask(plugin, task); return; }
        try {
            Object sched = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            sched.getClass().getMethod("run", Plugin.class, Consumer.class)
                    .invoke(sched, plugin, (Consumer<Object>) t -> task.run());
        } catch (Exception ex) {
            task.run();   // ultimo recurso: executa na thread atual
        }
    }

    public static void globalLater(Plugin plugin, Runnable task, long delayTicks) {
        if (!FOLIA) { Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks); return; }
        try {
            Object sched = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            sched.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class)
                    .invoke(sched, plugin, (Consumer<Object>) t -> task.run(), Math.max(1, delayTicks));
        } catch (Exception ex) {
            task.run();
        }
    }

    /** Tarefa repetida global. */
    public static void repeating(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
            return;
        }
        try {
            Object sched = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            sched.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class)
                    .invoke(sched, plugin, (Consumer<Object>) t -> task.run(),
                            Math.max(1, delayTicks), Math.max(1, periodTicks));
        } catch (Exception ex) {
            plugin.getLogger().warning("nao consegui agendar tarefa repetida no Folia: " + ex);
        }
    }

    /** Fora da thread do jogo (HTTP, disco). Igual nas duas plataformas. */
    public static void async(Plugin plugin, Runnable task) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            return;
        }
        try {
            Object sched = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            sched.getClass().getMethod("runNow", Plugin.class, Consumer.class)
                    .invoke(sched, plugin, (Consumer<Object>) t -> task.run());
        } catch (Exception ex) {
            new Thread(task, "CrossLink-async").start();
        }
    }
}
