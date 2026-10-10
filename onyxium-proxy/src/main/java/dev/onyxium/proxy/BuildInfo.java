package dev.onyxium.proxy;

import module java.base;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.IVersionProvider;

public record BuildInfo(String version, String commit, boolean dirty) implements IVersionProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildInfo.class);

    public BuildInfo() {
        this("unknown", "unknown", false);
    }

    static BuildInfo load() {
        var properties = new Properties();
        try (var stream = BuildInfo.class.getResourceAsStream("/git.properties")) {
            if (stream == null) {
                LOGGER.warn("Build information is missing from this build");
            }
            else {
                properties.load(stream);
            }
        }
        catch (IOException exception) {
            LOGGER.warn("Could not read build information", exception);
        }
        return new BuildInfo(properties.getProperty("git.build.version", "unknown"),
                properties.getProperty("git.commit.id.abbrev", "unknown"),
                Boolean.parseBoolean(properties.getProperty("git.dirty", "false")));
    }

    String display() {
        return "Onyxium v%s (commit: %s%s)".formatted(version, commit, dirty ? ", local changes" : "");
    }

    @Override
    public String[] getVersion() {
        return new String[] { load().display() };
    }
}
