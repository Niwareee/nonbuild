package fr.niware.nonbuild;

import org.bukkit.command.CommandSender;

public final class Msg {

    private Msg() {
    }

    public static void info(CommandSender to, String message) {
        to.sendMessage("§7" + message);
    }

    public static void ok(CommandSender to, String message) {
        to.sendMessage("§a" + message);
    }

    public static void warn(CommandSender to, String message) {
        to.sendMessage("§e" + message);
    }

    public static void error(CommandSender to, String message) {
        to.sendMessage("§c" + message);
    }

    public static void raw(CommandSender to, String message) {
        to.sendMessage(message);
    }
}
