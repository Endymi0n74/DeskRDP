# DualDesk

Fork d'**[aRDP](https://github.com/iiordanov/remote-desktop-clients)** : un client **RDP** pour
Android (FreeRDP 2.11.7, RDPGFX). Paquet `com.dualdesk.rdp`, il cohabite avec aRDP.

- Dernière version : **1.13** — [page des versions](https://github.com/Endymi0n74/DualDesk/releases)
- Licence : **GPLv3**, comme l'amont (voir [Amont et licence](#amont-et-licence))
- Android 5.0 (API 21) ou plus récent

## Installer

| Fichier | Pour qui |
|---|---|
| `dualdesk-app-arm64-v8a-release.apk` | tout téléphone récent |
| `dualdesk-app-universal-release.apk` | ABI inconnue, au prix du poids |

```bash
adb install -r dualdesk-app-arm64-v8a-release.apk
```

- Sur **Xiaomi/MIUI**, `adb install` répond `INSTALL_FAILED_USER_RESTRICTED` tant que « Installer via
  USB » n'est pas activé dans les options pour développeurs.
- Les versions signées de la **même clé** s'installent par-dessus les précédentes. Les deux marches
  cassées : **1.0.0 → 1.10** (la 1.0.0 était signée en débogage) et **1.12 → 1.13** si l'APK 1.12
  installée venait du dépôt `dualdesk` (autre clé) — désinstaller une fois dans ce cas.
- La 1.13 **retire RustDesk** : les accès RustDesk enregistrés sont supprimés de la liste au premier
  lancement. Une sauvegarde de la liste faite en 1.12 reste lisible, ces accès sont simplement ignorés.

## Ce que fait l'application

Tout ce que fait aRDP, sous le nom DualDesk :

- **Accès RDP** : RDPGFX négocié (AVC444 quand le H.264 est disponible), certificats, passerelle,
  redirection audio.
- **Clavier** : touches modificatrices et barre de touches supplémentaires, Ctrl+Alt+Suppr.
- **Presse-papiers** : texte, dans les deux sens.
- **Gestes** : appui bref = clic, glissement = glisser avec le bouton gauche tenu, appui long = clic
  droit, pincement = zoom. Hors de la bande du bureau distant, l'appui est ignoré plutôt que ramené
  sur le bord.
- **Reconnexion automatique** : une session coupée (Wi-Fi qui saute, poste qui redémarre) est
  relancée toute seule — 2 s, puis 5, 10, 20 et 30 s, cinq essais en tout, avec un message qui dit
  quand la tentative suivante aura lieu. Une première connexion ratée garde son message immédiat :
  cinq essais sur un mot de passe faux n'aideraient personne. Mesuré sur un vrai téléphone en
  coupant Wi-Fi et données mobiles : les cinq délais relevés au journal tombent à 2,0 / 5,0 / 10,0 /
  20,0 / 30,0 s, puis l'application revient à la grille. Le budget couvre donc une coupure d'une
  minute environ ; au-delà, il faut rouvrir l'accès à la main.

### Limite connue : plusieurs écrans côté poste

RDP ouvre une session sur **un** bureau. Choisir lequel des écrans du poste est affiché, ou passer de
l'un à l'autre, demanderait la capacité multi-moniteurs de RDP (plusieurs surfaces, une par écran) :
la bibliothèque FreeRDP précompilée de ce dépôt n'en expose qu'une, et son client Android n'énumère
pas les moniteurs du poste. Le réglage de taille (`/size`) ne fait qu'agrandir ou réduire ce bureau
unique — il ne montre pas le second écran. Ce n'est donc pas une option de l'application aujourd'hui.

## Construire

JDK 21, SDK Android (`compileSdk 36`) déclaré dans `local.properties`, et les dépendances
précompilées (171 Mo, une fois) :

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

Test unitaire, sans appareil ni émulateur :

```bash
./gradlew :bVNC:testDebugUnitTest --offline
```

## Publier

```bash
./publish-release.sh                # construire, vérifier la signature, taguer, publier
./publish-release.sh --dry-run      # tout sauf le tag et la release
```

La version est lue dans `versionName` du manifeste : ce qui est publié est ce qui a été compilé. Clé,
sauvegarde et contrôle de signature : [docs/PUBLICATION.md](docs/PUBLICATION.md).

## Amont et licence

Fork de [iiordanov/remote-desktop-clients](https://github.com/iiordanov/remote-desktop-clients)
(GPLv3) : bVNC, aRDP, aSPICE et Opaque. DualDesk n'ajoute aucun composant d'une autre licence — le
cœur RustDesk des versions 1.11 et 1.12 a été retiré en 1.13 — donc l'application est distribuée sous
**GPLv3**, comme l'amont. Les notes de version et les mesures sont dans [CHANGELOG.md](CHANGELOG.md)
et [memory.md](memory.md).

---

# DualDesk (English)

A fork of **[aRDP](https://github.com/iiordanov/remote-desktop-clients)**: an **RDP** client for
Android (FreeRDP 2.11.7, RDPGFX). Package `com.dualdesk.rdp`, it sits alongside aRDP.

- Latest version: **1.13** — [releases](https://github.com/Endymi0n74/DualDesk/releases)
- Licence: **GPLv3**, like upstream (see [Upstream and licence](#upstream-and-licence))
- Android 5.0 (API 21) or newer

## Install

| File | For whom |
|---|---|
| `dualdesk-app-arm64-v8a-release.apk` | any recent phone |
| `dualdesk-app-universal-release.apk` | unknown ABI, at the cost of size |

```bash
adb install -r dualdesk-app-arm64-v8a-release.apk
```

- On **Xiaomi/MIUI**, `adb install` answers `INSTALL_FAILED_USER_RESTRICTED` until "Install via USB"
  is enabled in the developer options.
- Builds signed with the **same key** install over the previous ones. The two broken steps:
  **1.0.0 → 1.10** (1.0.0 was debug-signed) and **1.12 → 1.13** when the installed 1.12 came from the
  `dualdesk` repository (different key) — uninstall once in that case.
- 1.13 **removes RustDesk**: recorded RustDesk accesses are dropped from the list on first launch. A
  list exported by 1.12 still reads; those accesses are simply ignored.

## What it does

Everything aRDP does, under the DualDesk name:

- **RDP accesses**: RDPGFX negotiated (AVC444 when H.264 is available), certificates, gateway, audio
  redirection.
- **Keyboard**: modifier keys and the extra-keys bar, Ctrl+Alt+Del.
- **Clipboard**: text, both ways.
- **Gestures**: brief touch = click, swipe = drag with the left button held, long press = right click,
  pinch = zoom. Outside the remote desktop band, the touch is ignored rather than clamped to the edge.
- **Automatic reconnection**: a dropped session (Wi-Fi hiccup, host reboot) is restarted on its own —
  2 s, then 5, 10, 20 and 30 s, five attempts in all, with a message saying when the next one will
  happen. A first connection that fails keeps its immediate message: five attempts against a wrong
  password would help nobody. Measured on a real phone with Wi-Fi and mobile data switched off: the
  five journal delays land at 2.0 / 5.0 / 10.0 / 20.0 / 30.0 s, then the app returns to the grid. The
  budget therefore covers an outage of about a minute; past that, the access has to be reopened by hand.

### Known limit: several screens on the host

RDP opens a session on **one** desktop. Choosing which host screen is shown, or switching between
them, would need RDP's multi-monitor capability (one surface per screen): the precompiled FreeRDP in
this repository exposes a single surface, and its Android client does not enumerate the host's
monitors. The size setting (`/size`) only enlarges or shrinks that single desktop — it does not show
the second screen. It is therefore not an option of the application today.

## Build

JDK 21, the Android SDK (`compileSdk 36`) in `local.properties`, and the prebuilt dependencies
(171 MB, once):

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

Unit test, no device and no emulator:

```bash
./gradlew :bVNC:testDebugUnitTest --offline
```

## Publish

```bash
./publish-release.sh                # build, check the signature, tag, publish
./publish-release.sh --dry-run      # everything but the tag and the release
```

The version is read from `versionName` in the manifest, so what gets published is what was compiled.
Key, backup and signature check: [docs/PUBLICATION.md](docs/PUBLICATION.md).

## Upstream and licence

A fork of [iiordanov/remote-desktop-clients](https://github.com/iiordanov/remote-desktop-clients)
(GPLv3): bVNC, aRDP, aSPICE and Opaque. DualDesk adds no component under another licence — the
RustDesk core of 1.11 and 1.12 was removed in 1.13 — so the application is distributed under
**GPLv3**, like upstream. Release notes and measurements: [CHANGELOG.md](CHANGELOG.md) and
[memory.md](memory.md).
