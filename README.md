# Digger Remastered — Java port

A faithful Java/Swing port of **Digger Remastered** (Copyright (c) Andrew Jenner
1998-2004, GPL v2; original game Copyright (c) 1983 Windmill Software). The C
sources live in `../digger-src`; this is a line-by-line translation of the
game logic that loads the **original binary sprite/font assets** (`vga.spr`,
`vgatext.spr`) at runtime.

![Digger level 1](docs/screenshot.png)

## Build & run

Requires a JDK 17+ to compile/run. Gradle 9 itself needs JDK 17+; this repo's
`gradle.properties` points Gradle at a local JDK 22 — edit that path if your JDK
is elsewhere.

```sh
./gradlew run            # build and launch the game
./gradlew build          # produce build/libs/digger-java.jar
java -jar build/libs/digger-java.jar
```

## Distributions

Two packaging options:

**Without a bundled JDK** (small; the user must have Java 17+ installed) — the
standard `application`-plugin distribution:

```sh
./gradlew distZip        # build/distributions/digger-java.zip  (~90 KB)
./gradlew installDist    # build/install/digger-java/  (run bin/digger-java[.bat])
```

**With a bundled JDK** (self-contained; no Java needed to run) — a `jpackage`
app image. Requires building on JDK 14+ (this repo already runs Gradle on
JDK 22); the app-image type needs no extra tooling (unlike `.msi`/`.exe`
installers, which need WiX):

```sh
./gradlew jpackageImage  # build/jpackage/Digger/Digger.exe  (~150 MB, bundled runtime)
./gradlew jpackageZip    # build/distributions/Digger-app-image.zip
```

On Windows this yields `Digger.exe`; on macOS/Linux jpackage produces the
platform's native app image instead.

## Android

`android/` is an Android app that reuses the shared game logic in
`src/main/java` (platform-neutral) and the original sprite files; the Swing
launcher lives in `src/desktop/java`. The module is only included when an
Android SDK is found (`local.properties` `sdk.dir`, or `ANDROID_HOME`).

```sh
./gradlew :android:assembleRelease   # android/build/outputs/apk/release/android-release.apk
./gradlew :android:installDebug      # install a debug build on a connected device
```

Release signing reads `android/keystore.properties` (`storeFile`,
`storePassword`, `keyAlias`, `keyPassword`); keep it and the `.jks` out of
version control. Without it the release APK is built unsigned.

Touch controls: D-pad (bottom-left), FIRE (bottom-right), buttons for pause,
1P/2P, music and sound (top-right); tap the playfield to start / continue.
Back = F10 (end game / quit). Keyboards and gamepads (D-pad/stick, A/X = fire,
Start = Enter) work too; the on-screen keyboard opens for high-score initials.

## Run

Command-line options are passed through, matching the original where
implemented: `./gradlew run --args="/I:3 /2"` (start on level 3, two-player
simultaneous). Supported: `/I:level`, `/S:speed` (or a bare number),
`/G[:time]` gauntlet, `/2` two-player, `/Q` quiet, `/M` no music, `/U` unlimited
lives, `/V` vsync timing, `/L:file` level file, `/R:file` record, `/P:file`
playback.

## Controls

| Action            | Key                         |
|-------------------|-----------------------------|
| Move (Player 1)   | Arrow keys                  |
| Fire (Player 1)   | F1                          |
| Move (Player 2)   | W / A / S / D               |
| Fire (Player 2)   | Ctrl                        |
| Start game        | Any key (e.g. Enter)        |
| Toggle 1/2 players| Esc or N (at the title)     |
| Pause             | Space                       |
| Cheat (void DRF)  | T                           |
| Faster / slower   | + / -                       |
| Music / sound     | F7 / F9                     |
| Save recording    | F8                          |
| Quit              | F10 or close the window     |

## Architecture

Each C module maps to one Java class (all logic is `static`, mirroring the C
globals/functions so the translation stays 1:1):

| C source        | Java class   | Responsibility |
|-----------------|--------------|----------------|
| def.h           | `Def`        | constants, sprite indices, directions |
| main.c          | `Main`       | program shell, game loop, level data, RNG |
| digger.c        | `Dig`        | diggers, fireballs, emeralds, bonus mode |
| monster.c       | `Mon`        | Nobbins / Hobbins AI |
| bags.c          | `Bag`        | gold and money bags |
| sprite.c        | `Spr`        | sprite engine + bounding-box collisions |
| drawing.c       | `Draw`       | field, text, high-level draw routines |
| scores.c        | `Score`      | scoring + high-score table (DIGGER.SCO) |
| input.c         | `Inp`        | keyboard handling |
| sound.c         | `Snd`        | note state machines + square-wave synth |
| record.c        | `Rec`        | DRF record / playback |
| ini.c           | `IniFile`    | DIGGER.INI settings |
| mkg.c / vga.spr | `Assets`     | runtime sprite/font/palette decoding |
| win_vid.c       | `Vid`        | 640x400 indexed framebuffer + blitter |
| dospc.asm/win_sys.c | `Hw`     | timing + keyboard platform layer |
| (Swing window)  | `DiggerApp`  | JFrame, panel, key events, entry point |

### How rendering works

Like the original Windows build, the screen is a **640x400 8-bit indexed back
buffer** in which the logical 320x200 game coordinates are doubled. `Assets`
decodes the 32x32, 1-byte-per-pixel cells of `vga.spr` (a byte of `0xFF` is
transparent; the low nibble is the colour 0-15) and reproduces the `vgatable`
sprite-index mapping from `mkg.c`. The 16-colour palettes (`gpal`/`ginten`
select one of four) come straight from `win_vid.c`. Fireball-versus-dirt
collisions read back rendered pixels via `ggetpix`, exactly as the original did.

### Faithfulness notes

* **Game logic** (digger/monster/bag/score/field/collision) is a direct
  translation, preserving update order and the 32-bit RNG so behaviour matches.
* **Graphics** use the original sprite/font data, including the tunnel-edge
  "blob" sprites. Those overflow a 32x32 cell in the original's layout (stored
  with the "tall"/"wide" packing from `mkg.c`); `Vid.gputim` reconstructs that
  packing so the carved tunnels keep their original rounded edges.
* **Sound** runs the original note/jingle state machines from `sound.c` and
  feeds a single square-wave voice (effects take priority over music, as the one
  PC speaker did). Sound silently disables itself if no audio line is available.
* **Title screen**: the decorative title bitmap is not loaded; the menu (the
  `DIGGER` banner, high-score table and the walking-character demo) draws on a
  black background.
* **DRF** recording works (press F8 to save); playback is available via `/P`.
* CGA mode and the joystick are not ported (VGA + keyboard only).

The two `-Ddigger.*` system properties (`shot`, `shotdelay`, `autostart`) are
debug hooks used to capture screenshots headlessly; they have no effect in
normal play.

## Legal Notice & Game Assets

### Independent Engine Implementation

This project is an independent reimplementation of the *Digger* engine, designed to reproduce the original game's behavior on modern platforms.

The engine has been developed from the original source code of *Digger Remastered* (C, Copyright (c) Andrew Jenner 1998-2004, GPL v2), through technical analysis and compatibility testing. That source code is not included in this repository.

### Original Game Files

The port loads *Digger Remastered*'s sprite and font files (`vga.spr`, `vgatext.spr`), distributed with its sources under the GPL v2; no other original game file is needed.

Eloane Studio does not host, distribute, or provide unauthorized copies of copyrighted game assets.

### Copyright & Trademarks

All original game names, trademarks, graphics, music, and other proprietary assets of *Digger* remain the property of their respective copyright holders (Windmill Software, Andrew Jenner).

This project is an independent, unofficial implementation and is not affiliated with, endorsed by, or sponsored by the original game's developers or publishers.

### Preservation

The purpose of this project is to preserve the technical behavior and gameplay experience of classic video games while making them accessible on modern systems.

The engine is provided for compatibility, preservation, and educational purposes.

### Credits

Original game: Copyright (c) 1983 Windmill Software. *Digger Remastered*: Copyright (c) Andrew Jenner 1998-2004, GPL v2. Java port: **Guillaume Monet**.

<details>
<summary>Version française</summary>

### Mentions légales et données du jeu

#### Implémentation indépendante du moteur

Ce projet est une réimplémentation indépendante du moteur de *Digger*, conçue pour reproduire le comportement du jeu d'origine sur les plateformes modernes.

Le moteur a été développé à partir du code source d'origine de *Digger Remastered* (C, Copyright (c) Andrew Jenner 1998-2004, GPL v2), par analyse technique et tests de compatibilité. Ce code source n'est pas inclus dans ce dépôt.

#### Fichiers du jeu d'origine

Le portage charge les fichiers de sprites et de police de *Digger Remastered* (`vga.spr`, `vgatext.spr`), distribués avec ses sources sous GPL v2 ; aucun autre fichier du jeu d'origine n'est nécessaire.

Eloane Studio n'héberge, ne distribue ni ne fournit de copies non autorisées de données de jeu protégées par le droit d'auteur.

#### Droits d'auteur et marques

Les noms, marques, graphismes, musiques et autres éléments propriétaires de *Digger* restent la propriété de leurs titulaires respectifs (Windmill Software, Andrew Jenner).

Ce projet est une implémentation indépendante et non officielle ; il n'est ni affilié, ni approuvé, ni soutenu par les développeurs ou les éditeurs du jeu d'origine.

#### Préservation

Ce projet a pour but de préserver le comportement technique et l'expérience de jeu des jeux vidéo classiques, tout en les rendant accessibles sur les systèmes modernes.

Le moteur est fourni à des fins de compatibilité, de préservation et d'éducation.

#### Crédits

Jeu d'origine : Copyright (c) 1983 Windmill Software. *Digger Remastered* : Copyright (c) Andrew Jenner 1998-2004, GPL v2. Portage Java : **Guillaume Monet**.

</details>
