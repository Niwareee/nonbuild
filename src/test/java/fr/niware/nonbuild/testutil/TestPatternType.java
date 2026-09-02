package fr.niware.nonbuild.testutil;

import org.bukkit.NamespacedKey;
import org.bukkit.block.banner.PatternType;

import java.util.Locale;

/** Faux PatternType pour les tests : sans serveur réel, l'interface exige un RegistryAccess. */
@SuppressWarnings("removal") // OldEnum.name/ordinal/compareTo et Keyed.getKey sont abstraits : à implémenter
public record TestPatternType(NamespacedKey key) implements PatternType {

    @Override
    public NamespacedKey getKey() {
        return key;
    }

    @Override
    public String getIdentifier() {
        return key.getKey();
    }

    @Override
    public String name() {
        return key.getKey().toUpperCase(Locale.ROOT);
    }

    @Override
    public int ordinal() {
        return 0;
    }

    @Override
    public int compareTo(PatternType other) {
        return name().compareTo(other.name());
    }
}
