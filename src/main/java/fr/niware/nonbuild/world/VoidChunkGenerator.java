package fr.niware.nonbuild.world;

import org.bukkit.generator.ChunkGenerator;

/**
 * Générateur de monde vide, utilisé par /deploy rebuild pour recréer le
 * monde de production. Les valeurs par défaut de ChunkGenerator suffisent :
 * generateNoise ne fait rien et tous les shouldGenerate* sont à false.
 */
public class VoidChunkGenerator extends ChunkGenerator {
}
