package de.finanzinstitut.nosoundcap.mixin;

import com.mojang.blaze3d.audio.Library;
import de.finanzinstitut.nosoundcap.NoSoundCap;
import de.finanzinstitut.nosoundcap.NoSoundCapConfig;
import net.minecraft.util.Mth;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;

import java.nio.IntBuffer;

/**
 * Hebt das Sound-Limit an zwei Stellen an:
 *
 * 1. alcCreateContext -> wir haengen ALC_MONO_SOURCES an die Attribut-Liste,
 *    damit OpenAL ueberhaupt mehr als die ueblichen 256 Sources bereitstellt.
 * 2. Mth.clamp in Library#init -> Vanilla klemmt die Pools auf max. 8 Streaming
 *    und max. 255 Static (= die beruehmten ~247).
 *
 * Alle Injections sind mit require = 0 markiert: wenn Mojang die Methode
 * umbaut, startet das Spiel trotzdem normal, die Mod loggt dann nur eine Warnung.
 */
@Mixin(Library.class)
public class LibraryMixin {

    // ---------- 1. mehr OpenAL-Sources anfordern ----------

    @Redirect(
            method = "init",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/ALC10;alcCreateContext(J[I)J"),
            require = 0
    )
    private long nosoundcap$createContextArray(long device, int[] attributes) {
        NoSoundCap.contextPatched = true;
        return ALC10.alcCreateContext(device, nosoundcap$patch(attributes));
    }

    @Redirect(
            method = "init",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/ALC10;alcCreateContext(JLjava/nio/IntBuffer;)J"),
            require = 0
    )
    private long nosoundcap$createContextBuffer(long device, IntBuffer attributes) {
        int[] existing = null;
        if (attributes != null) {
            existing = new int[attributes.remaining()];
            attributes.duplicate().get(existing);
        }
        NoSoundCap.contextPatched = true;
        return ALC10.alcCreateContext(device, nosoundcap$patch(existing));
    }

    /** Kopiert die vorhandenen Attribut-Paare und setzt ALC_MONO_SOURCES neu. */
    private static int[] nosoundcap$patch(int[] original) {
        NoSoundCapConfig cfg = NoSoundCapConfig.get();

        java.util.List<Integer> out = new java.util.ArrayList<>();
        if (original != null) {
            for (int i = 0; i + 1 < original.length; i += 2) {
                int key = original[i];
                if (key == 0) break;                      // 0 terminiert die Liste
                if (key == ALC11.ALC_MONO_SOURCES) continue;   // ersetzen wir gleich
                out.add(key);
                out.add(original[i + 1]);
            }
        }
        out.add(ALC11.ALC_MONO_SOURCES);
        out.add(cfg.monoSources);
        out.add(0);

        int[] result = new int[out.size()];
        for (int i = 0; i < result.length; i++) result[i] = out.get(i);
        return result;
    }

    // ---------- 2. MCs eigenen Clamp anheben ----------
    // Vanilla:
    //   int j = Mth.clamp((int) Mth.sqrt(i), 2, 8);   <- ordinal 0, Streaming
    //   int k = Mth.clamp(i - j, 8, 255);             <- ordinal 1, Static

    @Redirect(
            method = "init",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(III)I", ordinal = 0),
            require = 0
    )
    private int nosoundcap$streamingChannels(int value, int min, int max) {
        NoSoundCap.streamingPatched = true;
        int wanted = NoSoundCapConfig.get().streamingChannels;
        return Math.max(Mth.clamp(value, min, max), wanted);
    }

    @Redirect(
            method = "init",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(III)I", ordinal = 1),
            require = 0
    )
    private int nosoundcap$staticChannels(int value, int min, int max) {
        NoSoundCap.staticPatched = true;
        int wanted = NoSoundCapConfig.get().staticChannels;
        // statt auf 255 zu klemmen, klemmen wir auf den Config-Wert
        return Mth.clamp(value, min, Math.max(max, wanted));
    }

    @Inject(method = "init", at = @At("RETURN"), require = 0)
    private void nosoundcap$report(CallbackInfo ci) {
        NoSoundCap.reportOnce();
    }
}
