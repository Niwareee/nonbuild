package fr.niware.nonbuild.storage;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Écriture YAML atomique : le fichier final n'est jamais à demi écrit.
 * Essentiel pour deployments.yml, lu par le plugin practice sans verrou.
 */
final class YamlFiles {

    private YamlFiles() {
    }

    static void saveAtomic(YamlConfiguration yaml, File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Impossible de créer le dossier " + parent);
        }
        File tmp = new File(parent, file.getName() + ".tmp");
        yaml.save(tmp);
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
