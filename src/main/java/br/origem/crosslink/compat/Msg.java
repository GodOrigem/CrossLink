package br.origem.crosslink.compat;

import org.bukkit.command.CommandSender;

/**
 * Mensagens com codigos de cor legados.
 *
 * O Adventure (net.kyori) so existe no Paper, e so a partir da 1.16.5.
 * Usar sendMessage(String) com codigos § funciona em Spigot, Paper e Folia,
 * da 1.8 ate a 26.x -- e e o que permite um jar unico para todos.
 */
public final class Msg {

    public static final String PREFIX = "§b[CrossLink] §r";

    private Msg() {}

    public static void ok(CommandSender s, String m)   { s.sendMessage(PREFIX + "§a" + m); }
    public static void err(CommandSender s, String m)  { s.sendMessage(PREFIX + "§c" + m); }
    public static void info(CommandSender s, String m) { s.sendMessage(PREFIX + "§f" + m); }
    public static void gray(CommandSender s, String m) { s.sendMessage(PREFIX + "§7" + m); }
    public static void gold(CommandSender s, String m) { s.sendMessage(PREFIX + "§6" + m); }
    public static void warn(CommandSender s, String m) { s.sendMessage(PREFIX + "§e" + m); }
}
