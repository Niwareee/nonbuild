package fr.niware.nonbuild.testutil;

import io.papermc.paper.ServerBuildInfo;
import net.kyori.adventure.key.Key;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Provider ServerBuildInfo factice, découvert via ServiceLoader, pour que
 * Bukkit.setServer puisse logger sa bannière de version pendant les tests.
 */
public class TestServerBuildInfo implements ServerBuildInfo {

    @Override
    public Key brandId() {
        return ServerBuildInfo.BRAND_PAPER_ID;
    }

    @Override
    public boolean isBrandCompatible(Key key) {
        return ServerBuildInfo.BRAND_PAPER_ID.equals(key);
    }

    @Override
    public String brandName() {
        return "Paper (test)";
    }

    @Override
    public String minecraftVersionId() {
        return "26.2";
    }

    @Override
    public String minecraftVersionName() {
        return "26.2";
    }

    @Override
    public OptionalInt buildNumber() {
        return OptionalInt.of(121);
    }

    @Override
    public Instant buildTime() {
        return Instant.EPOCH;
    }

    @Override
    public Optional<String> gitBranch() {
        return Optional.empty();
    }

    @Override
    public Optional<String> gitCommit() {
        return Optional.empty();
    }

    @Override
    public String asString(StringRepresentation representation) {
        return "Paper (test) 26.2";
    }
}
