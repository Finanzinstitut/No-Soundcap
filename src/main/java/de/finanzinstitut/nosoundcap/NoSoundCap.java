package de.finanzinstitut.nosoundcap;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoSoundCap implements ClientModInitializer {

    public static final String MOD_ID = "nosoundcap";
    public static final Logger LOGGER = LoggerFactory.getLogger("No Soundcap");

    /** Werden vom Mixin gesetzt, damit wir sehen ob die Patches gegriffen haben. */
    public static volatile boolean contextPatched = false;
    public static volatile boolean streamingPatched = false;
    public static volatile boolean staticPatched = false;

    private static volatile boolean reported = false;

    @Override
    public void onInitializeClient() {
        NoSoundCapConfig cfg = NoSoundCapConfig.get();
        LOGGER.info("No Soundcap geladen - Ziel: {} Static / {} Streaming Kanaele, {} Mono-Sources",
                cfg.staticChannels, cfg.streamingChannels, cfg.monoSources);
    }

    /** Wird einmalig nach Library#init aufgerufen. */
    public static void reportOnce() {
        if (reported) return;
        reported = true;

        if (staticPatched && contextPatched) {
            LOGGER.info("Sound-Cap erfolgreich angehoben (Context + Kanal-Pools gepatcht).");
        } else {
            LOGGER.warn("Sound-Cap NICHT vollstaendig gepatcht! context={} streaming={} static={}",
                    contextPatched, streamingPatched, staticPatched);
            LOGGER.warn("Die Mixin-Targets in com.mojang.blaze3d.audio.Library haben sich "
                    + "vermutlich geaendert. Bitte Library#init mit javap gegenpruefen.");
        }
    }
}
