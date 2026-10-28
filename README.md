# DualDesk — fork of aRDP

This repository is a fork of [iiordanov/remote-desktop-clients](https://github.com/iiordanov/remote-desktop-clients)
(GPLv3). It adds a `DualDesk` variant (package `com.dualdesk.rdp`) that keeps aRDP and gives it
RustDesk accesses as well. The state of each feature and the measurements behind it are in
[RAPPORT.md](RAPPORT.md) (French); known traps and tooling are in [memory.md](memory.md).

## Version and releases

The current version is **1.10** (`versionName` `1.10-dualdesk`, `versionCode` 2), published on the
[Releases](https://github.com/Endymi0n74/ardp/releases/tag/v1.10) page with two assets:
`dualdesk-app-arm64-v8a-release.apk` for any recent phone, and `dualdesk-app-universal-release.apk`
when the ABI is unknown. What changed since the previous milestone is in
[CHANGELOG.md](CHANGELOG.md).

1.10 is the first version signed with the **project's publication key** (see below), and every later
release will use the same one — so from 1.10 onwards, an update installs over the previous version.
The one step that still hurts is **1.0.0 → 1.10**: 1.0.0 was signed with a debug key, so 1.10 is
refused (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Uninstalling first **destroys the Keystore keys, hence
the sealed RustDesk passwords**: export with a passphrase before doing it.

## Version et publications

La version courante est la **1.10** (`versionName` `1.10-dualdesk`, `versionCode` 2), publiée sur la
page [Releases](https://github.com/Endymi0n74/ardp/releases/tag/v1.10) avec deux fichiers :
`dualdesk-app-arm64-v8a-release.apk` pour tout téléphone récent, et
`dualdesk-app-universal-release.apk` quand l'ABI est inconnue. Ce qui a changé depuis le jalon
précédent est dans [CHANGELOG.md](CHANGELOG.md).

La 1.10 est la première version signée par la **clé de publication du projet** (voir ci-dessous), et
toutes les suivantes s'appuieront sur la même — à partir de là, une mise à jour s'installe par-dessus
la précédente. La seule étape qui reste douloureuse est **1.0.0 → 1.10** : la 1.0.0 était signée avec
une clé de débogage, donc la 1.10 est refusée (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Désinstaller
d'abord **détruit les clés du Keystore, donc les mots de passe RustDesk scellés** : exportez avec une
phrase de passe avant de le faire.

## Signing the release

`assembleDebug` needs no key. `assembleRelease` signs with the project's publication key, which lives
**outside version control** in `_keystore/`: that directory is ignored by git, and it has to be — a
signing key in a public repository would let anyone publish an update as this application.

- `_keystore/dualdesk-release.jks` — RSA 4096, PKCS#12, alias `dualdesk`, valid 30 years (to 2056),
  certificate SHA-256 `72:FB:AB:59:83:38:FA:A7:C4:48:FD:65:AA:30:BA:91:39:ED:44:9C:30:E4:64:77:29:C1:B0:86:44:98:B7:85`;
- `_keystore/keystore.properties` — the path, alias and passphrases Gradle reads. It is in clear
  text: that directory must not be committed, copied into a build artifact, or pasted anywhere.

```bash
./gradlew :dualdesk-app:assembleRelease   # -> dualdesk-app/build/outputs/apk/release/
```

If `_keystore/` is missing (fresh clone, another machine), the build still succeeds but the files are
named `…-release-unsigned.apk`: no signing config is applied, nothing else changes, and
`assembleDebug` keeps working. To create a key for your own fork, follow the same shape:

```bash
keytool -genkeypair -keystore _keystore/dualdesk-release.jks -storetype PKCS12 \
  -alias dualdesk -keyalg RSA -keysize 4096 -validity 10950 \
  -dname "CN=DualDesk, O=DualDesk, C=FR"
```

**Keep a copy.** One encrypted archive, stored in two separate places, and the passphrase in a
password manager. Losing either the keystore or its passphrase means never publishing an update
again: Android refuses an APK signed by another key, so every installed user would have to uninstall
— losing the Keystore-sealed RustDesk passwords with it.

### Backup and restore

```bash
# archive (the passphrase is read from the environment, never typed on the command line)
export DUALDESK_KEY_PASS="$(sed -n 's/^storePassword=//p' _keystore/keystore.properties)"
tar czf - -C _keystore dualdesk-release.jks keystore.properties |
  openssl enc -aes-256-cbc -pbkdf2 -iter 600000 -md sha512 -salt \
    -pass env:DUALDESK_KEY_PASS -out dualdesk-keys.tar.enc

# restore, on this machine or another one
openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -md sha512 \
  -pass env:DUALDESK_KEY_PASS -in dualdesk-keys.tar.enc | tar xzf - -C _keystore
```

The archive is encrypted with the **keystore passphrase itself**, so it is only half the secret: put
that passphrase in a password manager (not in the same folder, not in the same backup). After a
restore, check the key is the right one before publishing anything:

```bash
keytool -list -v -keystore _keystore/dualdesk-release.jks | grep 'SHA 256'
# must print 72:FB:AB:59:83:38:FA:A7:C4:48:FD:65:AA:30:BA:91:39:ED:44:9C:30:E4:64:77:29:C1:B0:86:44:98:B7:85
```

1.10 is the first release signed with this key; the assets on the release page are the signed ones
(`…-release.apk`, not `…-debug.apk`), and their SHA-256 is listed in the release notes.

## Signer la publication

`assembleDebug` ne demande aucune clé. `assembleRelease` signe avec la clé de publication du projet,
qui vit **hors du dépôt**, dans `_keystore/` : ce dossier est ignoré par git, et il doit l'être — une
clé de signature dans un dépôt public laisserait n'importe qui publier une mise à jour sous le nom de
cette application.

- `_keystore/dualdesk-release.jks` — RSA 4096, PKCS#12, alias `dualdesk`, valide 30 ans (jusqu'en
  2056), empreinte SHA-256 du certificat
  `72:FB:AB:59:83:38:FA:A7:C4:48:FD:65:AA:30:BA:91:39:ED:44:9C:30:E4:64:77:29:C1:B0:86:44:98:B7:85` ;
- `_keystore/keystore.properties` — le chemin, l'alias et les phrases de passe que lit Gradle. Elles y
  sont en clair : ce dossier ne se commite pas, ne part pas dans un artefact de construction et ne se
  colle nulle part.

```bash
./gradlew :dualdesk-app:assembleRelease   # -> dualdesk-app/build/outputs/apk/release/
```

Si `_keystore/` manque (clone neuf, autre machine), la construction aboutit quand même mais les
fichiers s'appellent `…-release-unsigned.apk` : aucune configuration de signature n'est appliquée,
rien d'autre ne change, et `assembleDebug` continue de fonctionner. Pour créer une clé pour votre
propre fork, même forme :

```bash
keytool -genkeypair -keystore _keystore/dualdesk-release.jks -storetype PKCS12 \
  -alias dualdesk -keyalg RSA -keysize 4096 -validity 10950 \
  -dname "CN=DualDesk, O=DualDesk, C=FR"
```

**Gardez-en une copie.** Une archive chiffrée, stockée à deux endroits distincts, et la phrase de
passe dans un gestionnaire de mots de passe. Perdre le magasin ou sa phrase de passe, c'est ne plus
jamais pouvoir publier de mise à jour : Android refuse une APK signée d'une autre clé, donc chaque
utilisateur installé devrait désinstaller — en perdant les mots de passe RustDesk scellés sous le
Keystore.

### Sauvegarde et restauration

```bash
# archive (la phrase de passe est lue dans l'environnement, jamais tapée sur la ligne de commande)
export DUALDESK_KEY_PASS="$(sed -n 's/^storePassword=//p' _keystore/keystore.properties)"
tar czf - -C _keystore dualdesk-release.jks keystore.properties |
  openssl enc -aes-256-cbc -pbkdf2 -iter 600000 -md sha512 -salt \
    -pass env:DUALDESK_KEY_PASS -out dualdesk-keys.tar.enc

# restauration, sur cette machine ou une autre
openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -md sha512 \
  -pass env:DUALDESK_KEY_PASS -in dualdesk-keys.tar.enc | tar xzf - -C _keystore
```

L'archive est chiffrée avec la **phrase de passe du magasin elle-même** : elle ne porte donc que la
moitié du secret. Cette phrase va dans un gestionnaire de mots de passe, pas dans le même dossier ni
dans la même sauvegarde. Après une restauration, vérifiez que c'est la bonne clé avant de publier :

```bash
keytool -list -v -keystore _keystore/dualdesk-release.jks | grep 'SHA 256'
# doit afficher 72:FB:AB:59:83:38:FA:A7:C4:48:FD:65:AA:30:BA:91:39:ED:44:9C:30:E4:64:77:29:C1:B0:86:44:98:B7:85
```

La 1.10 est la première version signée par cette clé ; les fichiers de la page de release sont les
signés (`…-release.apk`, et non `…-debug.apk`), et leur SHA-256 est donné dans les notes de version.

## Exporting the access list: what happens to the RustDesk password

A RustDesk password is stored **sealed** under a key of the Android Keystore. That key never leaves
the device, and uninstalling the application destroys it — so the device seal cannot travel. The
export therefore offers **two modes**, chosen on screen at export time:

- **no passphrase** — the exported file contains no RustDesk password at all. The access, its device
  id and its nickname are kept, the secret stays on the device, and the number of accesses left
  behind is announced.
- **with a passphrase** — each password is reopened and re-sealed under that passphrase
  (`pw1:<iterations>:<salt>:<IV>:<ciphertext>`, PBKDF2 then AES-GCM). The file can then be restored
  on another device: import asks for the passphrase, reopens the values and seals them again with
  the arriving device's key.

The passphrase is stored nowhere. **If it is lost, the passwords in that file are unrecoverable** —
the file stays readable, and its accesses can still be restored, without their passwords.

Whatever is not reopened at import (import cancelled, passphrase refused) is dropped rather than
kept: such an access opens without a password, to be typed again, and the number of affected
accesses is announced. A value that could not be reopened can never be mistaken for a password: it
is neither shown in the edit form nor handed to RustDesk.

The rewriting is verified **before** the file is written: if a device seal (`ks1:`) survives, or if
the document does not have the expected shape, the export is refused instead of written as is. The
code lives in `bVNC/src/main/java/com/dualdesk/rustdesk/` (`RustDeskSecretCodec`,
`RustDeskSecretStore`, `RustDeskPortableSeal`, `RustDeskExportSanitizer`), and its JVM tests run
without a device:

```bash
./gradlew :bVNC:testDebugUnitTest --tests "com.dualdesk.rustdesk.*" --offline
```

## Export des accès : ce que devient le mot de passe RustDesk

Un mot de passe RustDesk est enregistré **scellé** sous une clé du Keystore Android. Cette clé ne
quitte jamais l'appareil, et une désinstallation détruit les clés de l'application : le scellement
local ne voyage donc pas. L'export de la liste d'accès propose en conséquence **deux modes**, choisis
à l'écran au moment de l'export :

- **sans phrase de passe** — le fichier exporté ne contient aucun mot de passe RustDesk. L'accès, son
  identifiant et son surnom sont conservés, le secret reste sur l'appareil, et le nombre d'accès qui
  en sont privés est annoncé.
- **avec une phrase de passe** — chaque mot de passe est rouvert puis rescellé sous cette phrase
  (`pw1:<itérations>:<sel>:<IV>:<chiffré>`, PBKDF2 puis AES-GCM). Le fichier devient restaurable sur
  un autre appareil : l'import redemande la phrase, rouvre les valeurs et les resselle avec la clé du
  téléphone d'arrivée.

La phrase n'est enregistrée nulle part. **Perdue, elle rend les mots de passe de ce fichier
définitivement irrécupérables** — le fichier reste lisible, et ses accès restaurables, sans leurs mots
de passe.

Ce qui n'est pas rouvert à l'import (import annulé, phrase refusée) est retiré plutôt que conservé :
l'accès s'ouvre alors sans mot de passe, à ressaisir, et le nombre d'accès concernés est annoncé. Une
valeur qui n'a pas pu être rouverte ne peut jamais être prise pour un mot de passe : elle n'est ni
affichée dans le formulaire, ni transmise à RustDesk.

Le nettoyage est vérifié **avant** l'écriture : si un scellement propre à l'appareil (`ks1:`) subsiste,
ou si le document n'a pas la forme attendue, l'export est refusé au lieu d'être écrit tel quel. Le
code est dans `bVNC/src/main/java/com/dualdesk/rustdesk/` (`RustDeskSecretCodec`,
`RustDeskSecretStore`, `RustDeskPortableSeal`, `RustDeskExportSanitizer`), et ses tests JVM se lancent
sans appareil :

```bash
./gradlew :bVNC:testDebugUnitTest --tests "com.dualdesk.rustdesk.*" --offline
```

## Building the fork from source

The wrapper pins Gradle 8.13 and the Android Gradle plugin 8.13.2. **No NDK and no C compilation is
needed**: FreeRDP and its Java glue ship precompiled for the four ABIs in the upstream dependencies
archive, which is not stored in this repository and has to be fetched once.

What the machine needs first:

- **JDK 21** — the sources target Java 21, and a JDK 8 on the `PATH` fails the build, so point
  `JAVA_HOME` at a 21 before calling Gradle;
- **the Android SDK** (compileSdk 36) with its path in `local.properties`; without that file Gradle
  stops after everything else with "SDK location not found".

Then, from the repository root:

```bash
./download-prebuilt-dependencies.sh        # 171 MB: the prebuilt .so and the FreeRDP Java glue
printf 'sdk.dir=/path/to/Android/sdk\n' > local.properties
export JAVA_HOME=/path/to/jdk-21
./gradlew :dualdesk-app:assembleDebug
```

On Windows with Git Bash, one step needs a hand: the archive holds a single symbolic link
(`freeRDPCore/src/main/libs`) that Windows cannot create, which stops the extraction. Run it yourself
with the link left out, then carry on:

```bash
tar xzf remote-desktop-clients-libs-17.tar.gz --exclude='*/src/main/libs'
```

Gradle does not read that link, so its absence changes nothing.

The fork's unit tests need no device and no emulator:

```bash
./gradlew :bVNC:testDebugUnitTest --tests "com.dualdesk.rustdesk.*" --offline
```

## Installing the APK

`assembleDebug` writes one APK per ABI plus a universal one, all in
`dualdesk-app/build/outputs/apk/debug/`:

- `dualdesk-app-arm64-v8a-debug.apk` (70 MB) — the right file for any phone sold in the last few
  years;
- `dualdesk-app-universal-debug.apk` (187 MB) — when the ABI is unknown, at the cost of size;
- `dualdesk-app-armeabi-v7a-debug.apk`, `dualdesk-app-x86-debug.apk`,
  `dualdesk-app-x86_64-debug.apk` (64 to 72 MB) — older 32-bit phones and emulator images.

Over USB:

```bash
adb install -r dualdesk-app/build/outputs/apk/debug/dualdesk-app-arm64-v8a-debug.apk
```

Or copy the APK to the device and open it, allowing installs from unknown sources.

Five things worth knowing before installing:

- **Android 5.0 (API 21) or newer** — the minimum the fork is built for.
- On **Xiaomi/MIUI**, `adb install` answers `INSTALL_FAILED_USER_RESTRICTED` until "Install via USB"
  is enabled in the developer options.
- DualDesk **does not replace aRDP**: its package is `com.dualdesk.rdp`, so both applications can be
  installed side by side and the RDP accesses stay where they are.
- The APK from `assembleDebug` is **signed with the debug key**: fine for sideloading, not for
  publishing. Installing a build signed with another key over an existing one fails with
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and the fix is `adb uninstall com.dualdesk.rdp` first.
- Uninstalling destroys the Keystore keys, hence the sealed RustDesk passwords. That is what the
  export section above is for: export with a passphrase **before** uninstalling if those accesses
  must be restored.

## Construire le fork depuis les sources

Le wrapper fige Gradle 8.13 et le plugin Android 8.13.2. **Aucun NDK, aucune compilation C** : FreeRDP
et sa glue Java arrivent précompilés pour les quatre ABI dans l'archive de dépendances de l'amont,
laquelle n'est pas stockée dans ce dépôt et doit être récupérée une fois.

Ce qu'il faut sur la machine avant de commencer :

- **un JDK 21** — les sources ciblent Java 21, et un JDK 8 sur le `PATH` fait échouer la
  construction : pointez `JAVA_HOME` vers un 21 avant d'appeler Gradle ;
- **le SDK Android** (compileSdk 36) avec son chemin dans `local.properties` ; sans ce fichier Gradle
  s'arrête après avoir tout téléchargé, sur « SDK location not found ».

Puis, depuis la racine du dépôt :

```bash
./download-prebuilt-dependencies.sh        # 171 Mo : les .so précompilés et la glue Java de FreeRDP
printf 'sdk.dir=/chemin/vers/Android/sdk\n' > local.properties
export JAVA_HOME=/chemin/vers/jdk-21
./gradlew :dualdesk-app:assembleDebug
```

Sous Windows en Git Bash, une étape demande la main : l'archive contient un lien symbolique
(`freeRDPCore/src/main/libs`) que Windows refuse de créer, ce qui interrompt l'extraction. Lancez-la
vous-même en l'écartant, puis reprenez :

```bash
tar xzf remote-desktop-clients-libs-17.tar.gz --exclude='*/src/main/libs'
```

Gradle ne lit pas ce lien : son absence ne change rien.

Les tests unitaires du fork ne demandent ni appareil ni émulateur :

```bash
./gradlew :bVNC:testDebugUnitTest --tests "com.dualdesk.rustdesk.*" --offline
```

## Installer l'APK

`assembleDebug` écrit un APK par ABI et un universel, tous dans
`dualdesk-app/build/outputs/apk/debug/` :

- `dualdesk-app-arm64-v8a-debug.apk` (70 Mo) — le bon fichier pour tout téléphone vendu ces
  dernières années ;
- `dualdesk-app-universal-debug.apk` (187 Mo) — quand l'ABI est inconnue, au prix du poids ;
- `dualdesk-app-armeabi-v7a-debug.apk`, `dualdesk-app-x86-debug.apk`,
  `dualdesk-app-x86_64-debug.apk` (64 à 72 Mo) — anciens téléphones 32 bits et images d'émulateur.

Par USB :

```bash
adb install -r dualdesk-app/build/outputs/apk/debug/dualdesk-app-arm64-v8a-debug.apk
```

Ou copiez l'APK sur l'appareil et ouvrez-le, en autorisant l'installation de sources inconnues.

Cinq choses à savoir avant d'installer :

- **Android 5.0 (API 21) ou plus récent** — le minimum pour lequel le fork est construit.
- Sur **Xiaomi/MIUI**, `adb install` répond `INSTALL_FAILED_USER_RESTRICTED` tant que « Installer via
  USB » n'est pas activé dans les options pour développeurs.
- DualDesk **ne remplace pas aRDP** : son paquet est `com.dualdesk.rdp`, les deux applications
  cohabitent donc, et les accès RDP restent où ils sont.
- L'APK produit par `assembleDebug` est **signé avec la clé de débogage** : parfait pour un
  chargement direct, pas pour une publication. Installer une construction signée d'une autre clé
  par-dessus une existante échoue en `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, et le remède est
  `adb uninstall com.dualdesk.rdp` d'abord.
- La désinstallation détruit les clés du Keystore, donc les mots de passe RustDesk scellés. C'est
  l'objet de la section d'export ci-dessus : exportez avec une phrase de passe **avant** de
  désinstaller si ces accès doivent être restaurés.

# Intro

The upstream project's own documentation follows, apart from one reversed link made clickable.
La documentation de l'amont suit, à un lien inversé près, rétabli en lien cliquable.

This is the source code for bVNC, aRDP, aSPICE and Opaque, four remote desktop
clients for Android.

Please see the LICENSE file for information on how the source is licensed.

# Android App Store Links

bVNC is a VNC client. It's released as a donation version at [bVNC Pro](https://play.google.com/store/apps/details?id=com.iiordanov.bVNC),
and as a free version at [bVNC](https://play.google.com/store/apps/details?id=com.iiordanov.freebVNC)
and [IzzyOnDroid](https://apt.izzysoft.de/packages/com.iiordanov.freebVNC)

aRDP is a RDP client. It's released as a donation version at, [aRDP Pro](https://play.google.com/store/apps/details?id=com.iiordanov.aRDP)
and as a free version at [aRDP](https://play.google.com/store/apps/details?id=com.iiordanov.freeaRDP)
and [IzzyOnDroid](https://apt.izzysoft.de/packages/com.iiordanov.freeaRDP)

aSPICE is a SPICE Protocol client. It's available as a donation version at [aSPICE Pro](https://play.google.com/store/apps/details?id=com.iiordanov.aSPICE),
and as a free version at [aSPICE](https://play.google.com/store/apps/details?id=com.iiordanov.freeaSPICE)

Opaque is an oVirt, RHEV, and Proxmox client available at [Opaque](https://play.google.com/store/apps/details?id=com.undatech.opaque)

The donation versions are also available on Amazon App Store at
[bVNC](https://www.amazon.ca/Unda-Tech-VNC-Pro-Viewer/dp/B00OWZYE1A), [aRDP](https://www.amazon.ca/Unda-Tech-RDP-Pro-Client/dp/B00OX1RBAO), [aSPICE](https://www.amazon.ca/aSPICE-Pro-Secure-SPICE-Client/dp/B07B6K9BDB), [Opaque](https://www.amazon.ca/Opaque-oVirt-RHEV-Proxmox-Client/dp/B07HMTYT1L).

## Apple App Store Links

bVNC Pro is available at [bVNC Pro](https://apps.apple.com/app/bvnc-pro/id1506461202).

aRDP is available at [aRDP Pro](https://apps.apple.com/app/ardp-pro/id1620745523)

aSPICE Pro is available at [aSPICE Pro](https://apps.apple.com/app/aspice-pro/id1560593107).

Find bVNC Pro, aRDP Pro, and aSPICE Pro for MacOS and iOS source code
[here](https://gitlab.com/iiordanov/remote-desktop-clients-ios)

# Links to Pro APKs

You can always get the latest Pro versions by supporting us as a Patreon member and keeping your membership active!

[bVNC Pro](https://www.patreon.com/posts/113067871)
[aRDP Pro](https://www.patreon.com/posts/113067997)
[aSPICE Pro](https://www.patreon.com/posts/113067946)
[Opaque](https://www.patreon.com/posts/113067670)

# Releases

You can find all free Android APK files [here](https://github.com/iiordanov/remote-desktop-clients/releases)

# Building

There are different ways to build the applications depending on OS and whether you are using
pre-built libraries, or building them from scratch.

## On Linux and WSL2

These instructions should work on Ubuntu 18.04, 20.04, and Windows Subsystem for Linux 2.
Builds are likely to also work on MacOS, feedback is welcome.

Pick one of I-a, I-b, or I-c below, then move onto II.

### I-a With Prebuilt Libraries

Building the projects with pre-built dependencies.

        ./download-prebuilt-dependencies.sh
        ./bVNC/prepare_project.sh --skip-build libs nopath

### I-b From Scratch with Docker

Make sure you're running the commands below from the root of the project.
Ensure ANDROID_SDK is set to the path to your SDK.

```bash
echo "USER_UID=$(id -u)" > docker/.env
echo "USER_GID=$(id -g)" >> docker/.env
echo "CURRENT_WORKING_DIR=$(pwd)" >> docker/.env
docker-compose -f docker/docker-compose.yml up
```

### I-c From Scratch

Building from scratch and working in Android Studio.

  - Install some packages. On Ubuntu:
        apt install gnome-common gobject-introspection nasm gtk-doc-tools python-is-python3

  - On Linux, install Android Studio
  - Install Android SDK from Tools -> SDK Tools
  - Install Android SDK command-line tools and CMake from the SDK Tools tab
  - Ensure that the path to ANDROID_SDK is ${HOME}/Android/Sdk/ and correct below if necessary

  - To build the projects

    - If building a non-custom client, set PROJECT to libs. For a custom VNC client, set PROJECT to a string
      that stars with Custom and contains Vnc, i.e. `CustomYourVncClient` (see III below for details).

    - Set the environment variables ANDROID_SDK to your SDK installation. The scripts will install the NDK automatically.

    - Example:

              export PROJECT=libs # or CustomSomethingOrOther
              export ANDROID_SDK=${HOME}/Android/Sdk
              export PATH=$PATH:${ANDROID_SDK}/platform-tools/
              export PATH=$PATH:${ANDROID_SDK}/tools

    - Accept all licenses (repeat if you see an error during build)

              ${ANDROID_SDK}/cmdline-tools/bin/sdkmanager --licenses

    - Then, run the build script which takes hours to run. E.g.:

              ./bVNC/prepare_project.sh $PROJECT $ANDROID_SDK

    - Switch to Android Studio, select the launch configuration you want to run, and run it on an emulator or device.

    - If using an emulator, choose x86_64 as the architecture to avoid "has text relocations" errors loading gstreamer on Android.

## On Windows with Git Bash

These instructions are for Windows without WSL2 installed. For now, only pre-build dependencies are
supported in this configuration.

- First [download and install git from here](https://git-scm.com/downloads)
- Then, start Git Bash, clone this project, cd into the project root directory and run:
```bash
./download-prebuilt-dependencies.sh
```
- Start Android Studio and open the project directory
- Click File->Sync Project with Gradle Files
- If Android Studio reports any missing android versions (such as `android-28`, for instance),
find and start SDK Manager and ensure any missing Android versions are installed. As of today,
the required versions are `android-28`, `android-29`, and `android-30`, but in future other versions
will need to be installed if Android Studio shows an error.


## II Importing projects into Android Studio

This should be as simple as selecting "Open an existing Android Studio project" on the
Welcome screen, browsing to the remote-desktop-clients directory and selecting it.

  - One final tweak is necessary to the (external) freeRDPCore project before
    the project can build. Double-click "Gradle Scripts" on the left, and
    open build.gradle (Module freeRDPCore). Change minSdkVersion to 11.

  - Build -> Make Project should now work.

## Custom Certificate Authority

You can add custom CAs for aSPICE and Opaque in remoteClientLib/certificate_authorities/. They will be merged with the
ca-bundle.crt provided to the app to validate your self-signed server certs if you have any.

## Generating Keyboard Layouts for aSPICE and Opaque

The directory `bVNC/layouts` contains a utiliy `convert.py` that can be used to generate new layouts for the desktop clients.

```bash
sudo apt install qemu-keymaps
cd bVNC/layouts
python3 ./convert.py
```

If you would like to add a layout, the best way to do so would be to create a file with the correct format in
`/usr/share/qemu/keymaps/`. Ideally, you should submit this file to the qemu project so everybody would be able to benefit
from the new layout.


## III Building "Custom" VNC clients

It is possible to programmatically build additional customized clients based on the VNC client contained in this project
without altering any of the source code of the project.

- Pick a unique identifier for your app. It will become part of the
  [application ID](https://developer.android.com/studio/build/application-id) of the app. For example,
  say you pick YourVncClient. The application ID will be com.iiordanov.YourVncClient.

- Place a configuration file in yaml format (note - with filename matching your resulting application ID) at
  `bVNC/src/main/assets/com.iiordanov.YourVncClient.yaml`

- Use the file `custom_vnc_client.yaml-EXAMPLE` as a starting point. The numbers after each field are one of
  View.INVISIBLE or View.GONE and it controls whether the field is invisible or gone in the customized interface.

- See [https://developer.android.com/reference/android/view/View.html#GONE] for the numeric value of View.GONE
  and [https://developer.android.com/reference/android/view/View.html#INVISIBLE] for the numberic value of View.INVISIBLE.

- Place an icon at `CustomVnc-app/src/main/res/drawable/icon_of_the_custom_app.png`.

- Edit `gradle.properties` and set CUSTOM_VNC_APP_NAME to `Name Of The Custom App` and CUSTOM_VNC_APP_ICON to `icon_of_the_custom_app`

- Follow the build procedure in I-a, I-b, or I-c above, but with the PROJECT environment variable set to anything that starts with
  `Custom` and has `Vnc` in its name. For instance, if you set PROJECT to `CustomYourVncClient`. The `bVNC/prepare_project.sh` script
  will strip "Custom" from that identifier and once the project is built will be com.iiordanov.YourVncClient.

## Bugs

Please post any bugs you find at the GitHub issue tracker:

https://github.com/iiordanov/remote-desktop-clients/issues

## Support Forum

Questions and general discussion should be posted at the following forum:

https://groups.google.com/forum/#!forum/bvnc-ardp-aspice-opaque-remote-desktop-clients
