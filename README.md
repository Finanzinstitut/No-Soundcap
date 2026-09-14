# No Soundcap

Hebt Minecrafts Sound-Kanal-Limit an. Fabric, Minecraft 26.2, Client-only.

## Warum ~247?

Das Limit ist nicht hart codiert, sondern ergibt sich aus `com.mojang.blaze3d.audio.Library#init`:

```java
int i = getChannelCount();                        // OpenAL-Mono-Sources, i.d.R. 256
int j = Mth.clamp((int) Mth.sqrt(i), 2, 8);       // Streaming-Kanaele -> 8
int k = Mth.clamp(i - j, 8, 255);                 // Static-Kanaele    -> ~247/248
```

Sind alle Static-Kanaele belegt, gibt `ChannelPool#acquire()` `null` zurueck und der
Sound wird stillschweigend verworfen. Genau das passiert bei grossen Farmen.

Deshalb patcht die Mod **zwei** Stellen:

1. **`alcCreateContext`** — es wird `ALC_MONO_SOURCES` an die Attributliste gehaengt,
   damit OpenAL ueberhaupt mehr als 256 Sources bereitstellt. Ohne das bringt Schritt 2 nichts.
2. **`Mth.clamp` in `Library#init`** — statt auf 8/255 wird auf die Config-Werte geklemmt.

## Config

`config/nosoundcap.json`, wird beim ersten Start erzeugt:

```json
{
  "staticChannels": 1024,
  "streamingChannels": 16,
  "monoSources": 2048
}
```

Grenzen: `staticChannels` 64–8192, `streamingChannels` 4–64, `monoSources` bis 16384.
Aenderungen greifen nach einem Neustart des Spiels (bzw. nach Wechsel des Audio-Geraets).

**Nicht ins Unendliche drehen.** Jeder aktive Kanal wird von OpenAL Soft real gemixt —
2000 gleichzeitige Sounds kosten spuerbar CPU. 1024 ist schon sehr grosszuegig;
512 reicht fuer die allermeisten Farmen.

## Falls OpenAL nicht mitspielt

OpenAL Soft begrenzt sich zusaetzlich ueber seine eigene Config. Wenn im Log trotz
Patch weiterhin nur ~256 Sources ankommen, in `alsoft.ini` setzen:

```ini
[general]
sources=2048
```

(Windows: `%APPDATA%\alsoft.ini`, Linux: `~/.alsoftrc`)

## Mixin-Targets gegenpruefen

Die Injections stehen alle auf `require = 0`, das Spiel startet also auch dann,
wenn Mojang `Library` umgebaut hat — die Mod loggt dann nur eine Warnung
(`Sound-Cap NICHT vollstaendig gepatcht!`). Zum Nachsehen:

```bash
# MC-Jar aus dem Gradle-Cache holen
find ~/.gradle -name "minecraft-26.2*.jar" | head -1

unzip -o -j <jar> "com/mojang/blaze3d/audio/Library.class" -d /tmp/lib
javap -p -c /tmp/lib/Library.class | sed -n '/init/,/^$/p'
```

Interessant sind die beiden `Mth.clamp(III)I`-Aufrufe (Reihenfolge = `ordinal 0` Streaming,
`ordinal 1` Static) und die Signatur von `alcCreateContext` (`(J[I)J` vs `(JLjava/nio/IntBuffer;)J`).
Beide Overloads sind bereits abgedeckt.

## Bauen

Laeuft ueber GitHub Actions (`.github/workflows/build.yml`), JDK 25 + Gradle 9.5.1,
Artefakt `no-soundcap` unter Actions → Run → Artifacts. Kein Wrapper noetig.

## Release

Zwei Wege, beide ueber denselben Workflow:

* **Tag pushen** — `git tag v1.0.0 && git push origin v1.0.0`
* **Manuell** — Actions → Build → *Run workflow*, bei `release_tag` z.B. `v1.0.0`
  eintragen. Der Tag wird dabei serverseitig auf dem gebauten Commit angelegt.

In beiden Faellen haengt der Workflow die gebauten JARs an ein GitHub-Release.
