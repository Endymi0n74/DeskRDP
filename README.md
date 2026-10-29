# DualDesk

Client de bureau à distance pour Android : **RDP** (aRDP, FreeRDP 2.11.7 avec RDPGFX) et **RustDesk**,
ce dernier rendu **dans l'application** par son propre cœur embarqué. Paquet `com.dualdesk.rdp`,
cohabite avec aRDP.

- Dernière version : **1.12** — [page des versions](https://github.com/Endymi0n74/DualDesk/releases)
- Licence : **AGPL-3.0** pour cette build (le cœur RustDesk embarqué l'impose) ; GPLv3 sans lui
- Android 5.0 (API 21) ou plus récent

Ce fichier va à l'essentiel. Le détail technique est dans [docs/](docs/) :
[cœur RustDesk](docs/RUSTDESK-NATIF.md) et [signature et publication](docs/PUBLICATION.md).

## Installer

Deux fichiers sur la page des versions :

| Fichier | Pour qui |
|---|---|
| `dualdesk-app-arm64-v8a-release.apk` | tout téléphone récent |
| `dualdesk-app-universal-release.apk` | ABI inconnue, au prix du poids |

```bash
adb install -r dualdesk-app-arm64-v8a-release.apk
```

- Sur **Xiaomi/MIUI**, `adb install` répond `INSTALL_FAILED_USER_RESTRICTED` tant que « Installer via
  USB » n'est pas activé dans les options pour développeurs.
- Les versions signées de la **même clé** s'installent par-dessus les précédentes. La seule marche
  cassée est **1.0.0 → 1.10** : la 1.0.0 était signée en débogage, donc la 1.10 est refusée
  (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
- **Désinstaller détruit les mots de passe RustDesk scellés**, qui vivent sous une clé du Keystore :
  exportez la liste avec une phrase de passe avant.

## Ce que fait l'application

- **Accès RDP** : client aRDP complet, RDPGFX négocié, clavier, presse-papiers, certificats.
- **Accès RustDesk** : ouverts dans l'application elle-même — état, taille du bureau et images par
  seconde sont observables. Sans le cœur dans l'APK, la session est remise à l'application RustDesk
  installée, par deep link puis par Intent, et le compte rendu dit laquelle des trois voies a servi.
- **Gestes** : appui bref = clic, glissement = glisser avec le bouton gauche tenu, appui long = clic
  droit. Hors de la bande du bureau distant, l'appui est ignoré plutôt que ramené sur le bord.
- **Mots de passe RustDesk scellés** sous une clé du Keystore, avec migration des accès enregistrés en
  clair.
- **Export avec phrase de passe facultative** : sans phrase, aucun mot de passe ne quitte l'appareil ;
  avec, le fichier est restaurable sur un autre appareil. La phrase perdue rend ces mots de passe
  irrécupérables.

Limites du cœur embarqué : **arm64-v8a seulement**, clavier physique seulement (pas de clavier
logiciel), pas de presse-papiers, pas de H.264 matériel. Détail et mesures :
[docs/RUSTDESK-NATIF.md](docs/RUSTDESK-NATIF.md).

## Construire

JDK 21, SDK Android (compileSdk 36) déclaré dans `local.properties`, et les dépendances précompilées
(171 Mo, une fois) :

```bash
./download-prebuilt-dependencies.sh
printf 'sdk.dir=/chemin/vers/Android/sdk\n' > local.properties
export JAVA_HOME=/chemin/vers/jdk-21
./gradlew :dualdesk-app:assembleDebug
```

Aucun NDK n'est nécessaire : FreeRDP arrive précompilé. Sous Windows en Git Bash, l'archive contient
un lien symbolique que Windows refuse de créer et qui interrompt l'extraction ; écartez-le :

```bash
tar xzf remote-desktop-clients-libs-17.tar.gz --exclude='*/src/main/libs'
```

Pour une build avec le cœur RustDesk (la bibliothèque n'est **pas** versionnée) :

```bash
./gradlew :dualdesk-app:assembleRelease \
    -Pdualdesk.rustdeskLib=/chemin/vers/librustdesk.so \
    -Pdualdesk.libcxxShared=/chemin/vers/libc++_shared.so
```

Tests unitaires, sans appareil ni émulateur :

```bash
./gradlew :bVNC:testDebugUnitTest --tests "com.dualdesk.rustdesk.*" --offline
```

## Publier

```bash
./publish-release.sh                # construire, vérifier la signature, taguer, publier
./publish-release.sh --dry-run      # tout sauf le tag et la release
```

La version est lue dans `versionName` du manifeste : ce qui est publié est ce qui a été compilé. Clé,
sauvegarde, contrôle de signature et pièges de `gh` : [docs/PUBLICATION.md](docs/PUBLICATION.md).

## Amont et licence

Fork de [iiordanov/remote-desktop-clients](https://github.com/iiordanov/remote-desktop-clients)
(GPLv3) : bVNC, aRDP, aSPICE et Opaque. Le cœur RustDesk embarqué
([LICENCE-RustDesk.txt](LICENCE-RustDesk.txt)) est en AGPL-3.0, donc **une build qui l'embarque est
distribuée sous AGPL-3.0 dans son ensemble** ; une build sans lui reste GPLv3. Le reste du dépôt —
documentation, recettes de compilation, mesures — est décrit dans [docs/](docs/), [RAPPORT.md](RAPPORT.md)
et [memory.md](memory.md). Documentation de l'amont : [son README](https://github.com/iiordanov/remote-desktop-clients#readme).

---

# DualDesk (English)

Remote desktop client for Android: **RDP** (aRDP, FreeRDP 2.11.7 with RDPGFX) and **RustDesk**, the
latter rendered **inside the application** by its own embedded core. Package `com.dualdesk.rdp`, it
sits alongside aRDP.

- Latest version: **1.12** — [releases](https://github.com/Endymi0n74/DualDesk/releases)
- Licence: **AGPL-3.0** for this build (the embedded RustDesk core requires it); GPLv3 without it
- Android 5.0 (API 21) or newer

This file stays short. Technical detail lives in [docs/](docs/):
[RustDesk core](docs/RUSTDESK-NATIF.md) and [signing and publishing](docs/PUBLICATION.md) (both in
French).

## Install

Two files on the releases page: `dualdesk-app-arm64-v8a-release.apk` for any recent phone, and
`dualdesk-app-universal-release.apk` when the ABI is unknown.

```bash
adb install -r dualdesk-app-arm64-v8a-release.apk
```

- On **Xiaomi/MIUI**, `adb install` answers `INSTALL_FAILED_USER_RESTRICTED` until "Install via USB"
  is enabled in the developer options.
- Versions signed with the **same key** install over the previous ones. The one broken step is
  **1.0.0 → 1.10**: 1.0.0 was debug-signed, so 1.10 is refused
  (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
- **Uninstalling destroys the sealed RustDesk passwords**, which live under an Android Keystore key:
  export the list with a passphrase beforehand.

## What it does

- **RDP accesses**: the full aRDP client, RDPGFX negotiated, keyboard, clipboard, certificates.
- **RustDesk accesses**: opened inside the application itself — state, desktop size and frame rate
  are observable. Without the core in the APK, the session is handed to the installed RustDesk
  application, by deep link then by Intent, and the report says which of the three paths was used.
- **Gestures**: brief touch = click, swipe = drag with the left button held, long press = right click.
  Outside the remote desktop band, the touch is ignored rather than clamped to the edge.
- **RustDesk passwords sealed** under an Android Keystore key, with migration of records stored in
  clear.
- **Export with an optional passphrase**: with no passphrase, no password leaves the device; with
  one, the file can be restored on another device. A lost passphrase makes those passwords
  unrecoverable.

Embedded core limits: **arm64-v8a only**, physical keyboard only (no soft keyboard), no clipboard, no
hardware H.264. Details and measurements: [docs/RUSTDESK-NATIF.md](docs/RUSTDESK-NATIF.md).

## Build

JDK 21, the Android SDK (compileSdk 36) in `local.properties`, and the prebuilt dependencies (171 MB,
once):

```bash
./download-prebuilt-dependencies.sh
printf 'sdk.dir=/path/to/Android/sdk\n' > local.properties
export JAVA_HOME=/path/to/jdk-21
./gradlew :dualdesk-app:assembleDebug
```

No NDK is needed: FreeRDP ships precompiled. On Windows with Git Bash, the archive holds a symbolic
link Windows cannot create, which stops the extraction; leave it out:

```bash
tar xzf remote-desktop-clients-libs-17.tar.gz --exclude='*/src/main/libs'
```

For a build with the RustDesk core (the library is **not** versioned):

```bash
./gradlew :dualdesk-app:assembleRelease \
    -Pdualdesk.rustdeskLib=/path/to/librustdesk.so \
    -Pdualdesk.libcxxShared=/path/to/libc++_shared.so
```

Unit tests need no device and no emulator:

```bash
./gradlew :bVNC:testDebugUnitTest --tests "com.dualdesk.rustdesk.*" --offline
```

## Publish

```bash
./publish-release.sh                # build, check the signature, tag, publish
./publish-release.sh --dry-run      # everything but the tag and the release
```

The version is read from `versionName` in the manifest, so what gets published is what was compiled.
Key, backup, signature check and `gh` traps: [docs/PUBLICATION.md](docs/PUBLICATION.md).

## Upstream and licence

A fork of [iiordanov/remote-desktop-clients](https://github.com/iiordanov/remote-desktop-clients)
(GPLv3): bVNC, aRDP, aSPICE and Opaque. The embedded RustDesk core
([LICENCE-RustDesk.txt](LICENCE-RustDesk.txt)) is AGPL-3.0, so **a build that embeds it is distributed
under AGPL-3.0 as a whole**; a build without it stays GPLv3. Upstream documentation: [its
README](https://github.com/iiordanov/remote-desktop-clients#readme).
