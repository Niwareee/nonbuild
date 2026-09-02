package fr.niware.nonbuild;

import org.bukkit.command.CommandSender;

public final class Msg {

    private Msg() {
    }

    public static void info(CommandSender to, String message) {
        to.sendRichMessage("<gray>" + message);
    }

    public static void ok(CommandSender to, String message) {
        to.sendRichMessage("<green>" + message);
    }

    public static void warn(CommandSender to, String message) {
        to.sendRichMessage("<yellow>" + message);
    }

    public static void error(CommandSender to, String message) {
        to.sendRichMessage("<red>" + message);
    }

    public static void raw(CommandSender to, String message) {
        to.sendRichMessage(message);
    }
}
