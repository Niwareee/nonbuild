package fr.niware.nonbuild.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArgsTest {

    @Test
    void nomEntreGuillemetsSuiviDuNombre() {
        List<String> parsed = Args.parse(new String[]{"\"nom", "de", "l'arène\"", "3"});
        assertEquals(List.of("nom de l'arène", "3"), parsed);
    }

    @Test
    void guillemetsDansUnSeulToken() {
        List<String> parsed = Args.parse(new String[]{"\"getdown\"", "2"});
        assertEquals(List.of("getdown", "2"), parsed);
    }

    @Test
    void argumentsSansGuillemets() {
        List<String> parsed = Args.parse(new String[]{"getdown", "4"});
        assertEquals(List.of("getdown", "4"), parsed);
    }

    @Test
    void plusieursGroupesEntreGuillemets() {
        List<String> parsed = Args.parse(new String[]{"\"a b\"", "\"c d\"", "5"});
        assertEquals(List.of("a b", "c d", "5"), parsed);
    }

    @Test
    void guillemetNonFerme() {
        List<String> parsed = Args.parse(new String[]{"\"abc", "def"});
        assertEquals(List.of("abc def"), parsed);
    }

    @Test
    void aucunArgument() {
        assertEquals(List.of(), Args.parse(new String[]{}));
    }

    @Test
    void nomAvecGuillemetsVides() {
        List<String> parsed = Args.parse(new String[]{"\"\"", "2"});
        assertEquals(List.of("", "2"), parsed);
    }
}
