package de.finanzinstitut.nosoundcap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Config unter config/nosoundcap.json.
 *
 * Wird beim ersten Zugriff geladen (lazy), weil Library#init frueh laeuft und
 * wir uns nicht auf die Reihenfolge der Mod-Initialisierung verlassen wollen.
 */
public class NoSoundCapConfig {

    /** Gleichzeitig abspielbare "normale" Sounds. Vanilla: ~247. */
    public int staticChannels = 1024;

    /** Kanaele fuer Streaming-Sounds (Musik, Records). Vanilla: 8. */
    public int streamingChannels = 16;

    /** Wie viele Mono-Sources wir bei OpenAL anfordern. Muss >= staticChannels sein. */
    public int monoSources = 2048;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile NoSoundCapConfig instance;

    public static NoSoundCapConfig get() {
        NoSoundCapConfig local = instance;
        if (local == null) {
            synchronized (NoSoundCapConfig.class) {
                local = instance;
                if (local == null) {
                    local = load();
                    instance = local;
                }
            }
        }
        return local;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("nosoundcap.json");
    }

    private static NoSoundCapConfig load() {
        NoSoundCapConfig cfg = new NoSoundCapConfig();
        Path file = path();
        try {
            if (Files.exists(file)) {
                NoSoundCapConfig read = GSON.fromJson(Files.readString(file), NoSoundCapConfig.class);
                if (read != null) cfg = read;
            }
        } catch (Exception e) {
            NoSoundCap.LOGGER.warn("Config konnte nicht gelesen werden, nutze Defaults", e);
            cfg = new NoSoundCapConfig();
        }

        cfg.sanitise();
        cfg.save();
        return cfg;
    }

    private void sanitise() {
        // Obergrenzen bewusst konservativ: jeder Kanal kostet CPU beim Mixen.
        staticChannels = clamp(staticChannels, 64, 8192);
        streamingChannels = clamp(streamingChannels, 4, 64);
        monoSources = clamp(monoSources, staticChannels + streamingChannels, 16384);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void save() {
        try {
            Path file = path();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this));
        } catch (IOException e) {
            NoSoundCap.LOGGER.warn("Config konnte nicht geschrieben werden", e);
        }
    }
}
