package fr.niware.nonbuild.testutil;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.banner.PatternType;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Installé par ServiceLoader (META-INF/services) pour les tests : depuis Paper 26.2,
 * PatternType se construit via RegistryAccess au chargement de la classe, ce qui
 * exige une implémentation même sans vrai serveur.
 *
 * Les registres sont créés paresseusement : instancier une implémentation de
 * Registry déclenche le clinit de l'interface Registry, qui rappelle
 * registryAccess() — il ne doit pas tourner pendant que le ServiceLoader
 * construit encore cette classe (le holder ne serait pas encore assigné).
 */
public final class TestRegistryAccess implements RegistryAccess {

    private Registry<PatternType> bannerPatterns;
    private Registry<Keyed> empty;

    @Override
    @SuppressWarnings({"unchecked", "removal"}) // getRegistry(Class) est déprécié mais abstrait : à implémenter
    public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
        return type == PatternType.class ? (Registry<T>) bannerRegistry() : (Registry<T>) emptyRegistry();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> registryKey) {
        return registryKey == RegistryKey.BANNER_PATTERN ? (Registry<T>) bannerRegistry() : (Registry<T>) emptyRegistry();
    }

    private Registry<PatternType> bannerRegistry() {
        if (bannerPatterns == null) {
            bannerPatterns = new BannerPatternRegistry();
        }
        return bannerPatterns;
    }

    private Registry<Keyed> emptyRegistry() {
        if (empty == null) {
            empty = new EmptyRegistry();
        }
        return empty;
    }

    /** Chaque getType(...) du clinit de PatternType crée son entrée à la volée. */
    private static final class BannerPatternRegistry extends Registry.NotARegistry<PatternType> {

        private final Map<NamespacedKey, PatternType> byKey = new LinkedHashMap<>();

        @Override
        public PatternType get(NamespacedKey key) {
            return byKey.computeIfAbsent(key, TestPatternType::new);
        }

        @Override
        public Iterator<PatternType> iterator() {
            return byKey.values().iterator();
        }
    }

    /** Registre vide pour tous les autres RegistryKey du clinit de l'interface Registry. */
    private static final class EmptyRegistry extends Registry.NotARegistry<Keyed> {

        @Override
        public Keyed get(NamespacedKey key) {
            return null;
        }

        @Override
        public Iterator<Keyed> iterator() {
            return Collections.emptyIterator();
        }
    }
}
