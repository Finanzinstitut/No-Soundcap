# No Soundcap

Raises Minecraft's sound channel limit. Fabric, Minecraft 26.2, client-only.

## Why ~247?

The limit is not hardcoded — it falls out of `com.mojang.blaze3d.audio.Library#init`:

```java
int i = getChannelCount();                        // OpenAL mono sources, usually 256
int j = Mth.clamp((int) Mth.sqrt(i), 2, 8);       // streaming channels -> 8
int k = Mth.clamp(i - j, 8, 255);                 // static channels    -> ~247/248
```

Once every static channel is taken, `ChannelPool#acquire()` returns `null` and the
sound is silently dropped. That is exactly what happens on large farms.

So the mod patches **two** places:

1. **`alcCreateContext`** — `ALC_MONO_SOURCES` is appended to the attribute list so
   that OpenAL hands out more than 256 sources in the first place. Without this,
   step 2 achieves nothing.
2. **`Mth.clamp` in `Library#init`** — instead of clamping to 8/255, the config
   values are used.

## Config

`config/nosoundcap.json`, created on first launch:

```json
{
  "staticChannels": 1024,
  "streamingChannels": 16,
  "monoSources": 2048
}
```

Bounds: `staticChannels` 64–8192, `streamingChannels` 4–64, `monoSources` up to 16384.
Changes take effect after restarting the game (or after switching audio devices).

**Don't crank it to infinity.** Every active channel is really mixed by OpenAL Soft —
2000 simultaneous sounds cost noticeable CPU. 1024 is already very generous;
512 is plenty for almost any farm.

## If OpenAL won't play along

OpenAL Soft additionally limits itself through its own config. If the log still shows
only ~256 sources despite the patch, set this in `alsoft.ini`:

```ini
[general]
sources=2048
```

(Windows: `%APPDATA%\alsoft.ini`, Linux: `~/.alsoftrc`)

## Verifying the mixin targets

All injections use `require = 0`, so the game still starts if Mojang reworks
`Library` — the mod then only logs a warning (`Sound-Cap NICHT vollstaendig gepatcht!`).
To check:

```bash
# grab the MC jar from the Gradle cache
find ~/.gradle -name "minecraft-26.2*.jar" | head -1

unzip -o -j <jar> "com/mojang/blaze3d/audio/Library.class" -d /tmp/lib
javap -p -c /tmp/lib/Library.class | sed -n '/init/,/^$/p'
```

The interesting parts are the two `Mth.clamp(III)I` calls (order = `ordinal 0`
streaming, `ordinal 1` static) and the signature of `alcCreateContext`
(`(J[I)J` vs `(JLjava/nio/IntBuffer;)J`). Both overloads are already covered.

## Building

Runs through GitHub Actions (`.github/workflows/build.yml`), JDK 25 + Gradle 9.5.1,
artifact `no-soundcap` under Actions → Run → Artifacts. No wrapper needed.

## Releasing

Two ways, both through the same workflow:

* **Push a tag** — `git tag v1.0.0 && git push origin v1.0.0`
* **Manually** — Actions → Build → *Run workflow*, entering e.g. `v1.0.0` for
  `release_tag`. The tag is then created server-side on the commit that was built.

Either way the workflow attaches the built JARs to a GitHub release.
