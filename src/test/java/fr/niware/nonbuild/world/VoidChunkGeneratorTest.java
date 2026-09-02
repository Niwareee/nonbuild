package fr.niware.nonbuild.world;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Verrouille le contrat du monde vide : les valeurs par défaut de
 * ChunkGenerator ne doivent produire aucun terrain ni contenu.
 */
class VoidChunkGeneratorTest {

    @Test
    void lesDefautsProduisentUnMondeVide() {
        VoidChunkGenerator generator = new VoidChunkGenerator();
        assertFalse(generator.shouldGenerateNoise());
        assertFalse(generator.shouldGenerateSurface());
        assertFalse(generator.shouldGenerateBedrock());
        assertFalse(generator.shouldGenerateCaves());
        assertFalse(generator.shouldGenerateDecorations());
        assertFalse(generator.shouldGenerateMobs());
        assertFalse(generator.shouldGenerateStructures());
        assertTrue(generator.getDefaultPopulators(mock(World.class)).isEmpty());
    }
}
