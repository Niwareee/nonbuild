package fr.niware.nonbuild.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlugifyTest {

    @Test
    void minusculesEtEspaces() {
        assertEquals("getdown", ArenaStorage.slugify("Getdown"));
        assertEquals("nom-de-l-arene", ArenaStorage.slugify("Nom de l'Arène"));
    }

    @Test
    void lesAccentsSontRetires() {
        assertEquals("ile-brulee-2", ArenaStorage.slugify("Île Brûlée 2"));
        assertEquals("cafe-au-lait", ArenaStorage.slugify("Café au Lait!"));
        assertEquals("deja-vu", ArenaStorage.slugify("déjà vu"));
    }

    @Test
    void tiretsEtUnderscoresSontConserves() {
        assertEquals("deja_vu-1", ArenaStorage.slugify("déjà_vu-1"));
    }

    @Test
    void caracteresSpeciauxDeviennentDesTirets() {
        assertEquals("floor-is-lava", ArenaStorage.slugify("floor.is.lava"));
        assertEquals("hello-world", ArenaStorage.slugify("  --Hello  World--  "));
    }

    @Test
    void lesTiretsEnTropSontFusionnesEtRognes() {
        assertEquals("abc", ArenaStorage.slugify("--abc--"));
        assertEquals("a-b", ArenaStorage.slugify("a!!!b"));
    }

    @Test
    void nomTropLongEstTronqueA40() {
        String slug = ArenaStorage.slugify("a".repeat(60));
        assertEquals(40, slug.length());
        assertEquals("a".repeat(40), slug);
    }

    @Test
    void laTroncatureRetireUnTiretFinal() {
        String slug = ArenaStorage.slugify("a".repeat(39) + "-" + "b".repeat(20));
        assertEquals("a".repeat(39), slug);
    }

    @Test
    void entreesInvalidesDonnentUnSlugVide() {
        assertEquals("", ArenaStorage.slugify(""));
        assertEquals("", ArenaStorage.slugify("!!!"));
        assertEquals("", ArenaStorage.slugify("   "));
    }
}
