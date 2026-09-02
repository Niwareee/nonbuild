package fr.niware.nonbuild.command;

import java.util.ArrayList;
import java.util.List;

public final class Args {

    private Args() {
    }

    /**
     * Reconstruit les arguments en gérant les guillemets :
     * ["\"nom", "de", "l'arène\"", "3"] devient ["nom de l'arène", "3"].
     */
    public static List<String> parse(String[] args) {
        String joined = String.join(" ", args);
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean hasToken = false;

        for (char c : joined.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
                hasToken = true;
            } else if (c == ' ' && !inQuotes) {
                if (hasToken) {
                    out.add(current.toString());
                    current.setLength(0);
                    hasToken = false;
                }
            } else {
                current.append(c);
                hasToken = true;
            }
        }
        if (hasToken) {
            out.add(current.toString());
        }
        return out;
    }
}
