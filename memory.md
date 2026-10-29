# ardp — mémoire du fork DualDesk

Fork de [iiordanov/remote-desktop-clients](https://github.com/iiordanov/remote-desktop-clients) (GPLv3),
cloné le 26/10/2026 (v6.4.9, versionCode 116490). But : garder le client RDP mature (FreeRDP 2.11.7,
RDPGFX + H.264 MediaCodec) et y greffer la partie DualDesk — les accès **RustDesk**.

> **1.13 (29/10/2026) — décision : retour au RDP seul.** RustDesk est retiré du fork. Les sections
> qui décrivent la partie RustDesk ci-dessous sont de l'**histoire** : elles restent pour expliquer
> ce qui a été essayé et pourquoi ça s'arrête, pas pour décrire le code actuel. Où en est le dépôt :
> voir « État de l'arbre en 1.13 » plus bas.

## État de l'arbre en 1.13 (29/10/2026)

- Supprimés : `com/dualdesk/rustdesk/` (7 fichiers), `com/dualdesk/rdp/` (3 fichiers dont l'activité
  de session embarquée), les 5 tests, `docs/RUSTDESK-NATIF.md`, `scripts/compile-rustdesk-android.sh`,
  `LICENCE-RustDesk.txt`, `_check/flow_rustdesk.py`, `_check/delete_rustdesk_access.py`, deux
  `preuve-poco/*rustdesk*.png`.
- `Constants.CONN_TYPE_RETIRED = 6` : la valeur reste pour que le chargement de la liste retrouve les
  accès de 1.12 et les supprime (`ConnectionGridActivity.dropRetiredAccesses`, appelée depuis
  `loadSavedConnections`). Le nombre d'entrées retirées va au journal, pas à un message.
- `dualdesk-app/build.gradle` n'a plus ni `rustdeskLib`, ni `libcxxShared`, ni `rustdeskJniDir`, ni la
  tâche `copieRustDeskLib`. `bVNC/build.gradle` n'a plus la dépendance Kotlin du JNI.
- `publish-release.sh` n'a plus `--rustdesk-lib` ni `--libcxx`.
- Licence : retour en **GPLv3**. `versionCode` 5, `versionName` 1.13-dualdesk.

### Publication et vérification de la 1.13 (29/10/2026)

- Release `v1.13` publiée sur `Endymi0n74/DualDesk` avec les deux APK signés par la clé de
  publication (contrôle `verifieSignatureRelease` passé avant le tag) : arm64 62 422 154 o
  (`9f78c305…`), universel 177 105 218 o (`013e5931…`). Le tag pointe sur le commit du retrait.
- **Vérifié sur le POCO** avec l'APK arm64 de la release : la grille ne contient plus que l'accès
  RDP existant, aucune entrée RustDesk résiduelle, et le menu ⋮ n'a plus ni « Nouvel accès RustDesk »
  ni « Serveur RustDesk ». Un appui sur l'accès RDP atteint bien le poste : dialogue de certificat
  (nom du poste en `CN`), puis demande d'identifiants RDP, barre de touches supplémentaires visible sous
  le clavier (ESC, SHIFT, PGUP, PGDN, HOME, END, CTRL, SUPER, ALT, flèches).
- **Non vérifié** : la session RDP complète jusqu'au bureau distant — l'authentification du poste
  demande un mot de passe que l'agent n'a pas à manipuler. Le rendu du bureau, les gestes et la
  reprise après perte de surface ne sont donc **pas** rejoués en 1.13 ; le code de session RDP est
  inchangé depuis la 1.12, mais cela reste une inférence, pas une mesure.
- Le téléphone doit être **déverrouillé** pour toute vérification `adb` : le 29/10, le POCO est resté
  sous son écran de verrouillage une partie de la session, `screencap` renvoyant alors du noir et
  `mCurrentFocus` restant sur `NotificationShade`. Le remède est côté utilisateur, pas côté agent.

### Pourquoi pas de multi-écran en RDP (mesuré, pas supposé)

La demande « passer d'un écran à l'autre » a été poussée jusqu'au bout côté RDP avant d'être
abandonnée, et la raison est dans les binaires livrés :

- `libfreerdp-client2.so` (précompilé, non recompilable sans refaire la chaîne FreeRDP) contient les
  symboles de réglage `UseMultimon`, `MonitorDefArray`, `MonitorCount`, `SupportMonitorLayoutPdu` —
  mais l'analyse des moniteurs passe par l'API Win32 (`List detected monitors`), qui n'existe pas
  sous Android.
- Côté Java, `LibFreeRDP.java` ne construit que `/size:WxH` : aucun chemin ne remplit
  `MonitorDefArray`, et il n'existe qu'**une** surface de rendu pour tout le client.
- Conclusion : `/multimon` sans définition de moniteurs retombe sur un seul bureau, et `/size` ne fait
  que changer la taille de ce bureau. RDP n'expose donc pas le second écran du poste dans ce fork.
  C'est écrit tel quel dans le README (FR et EN) pour ne pas promettre l'inverse.
- Vérifié dans le code amont (`D:/Codex/freerdp-src`) : `/multimon` ne fait que poser
  `FreeRDP_UseMultimon` et forcer le plein écran (`client/common/cmdline.c:4953`,
  `client/common/client.c:387`) ; `/monitors:` ne fait que **choisir des identifiants** dans une liste
  déjà détectée (`parse_monitors_options`, `cmdline.c:2860`) ; la géométrie ne vient que des clients
  qui énumèrent l'écran — RandR pour X11 (`client/X11/xf_monitor.c:677`), `EnumDisplayMonitors` pour
  Windows. `libfreerdp/core/settings.c:1760` : sans moniteur fourni, un seul moniteur de
  `DesktopWidth` x `DesktopHeight` est créé. Il n'existe donc pas d'argument permettant de décrire
  deux écrans à la main.

### Reconnexion automatique (1.14, en cours)

- `bVNC/.../input/ReconnectPolicy.java` : table pure `2 s, 5 s, 10 s, 20 s, 30 s`, `MAX_ATTEMPTS = 5`,
  testée par `ReconnectPolicyTest` (`./gradlew :bVNC:testDebugUnitTest --tests
  "com.iiordanov.bVNC.input.ReconnectPolicyTest"`).
- `RemoteCanvasHandler` : `RDP_CONNECT_FAILURE` ne quitte plus, il programme un message interne
  `MSG_RECONNECT` (0x5243) ; ce message rappelle `initializeConnection()` puis `setParameters(...)`,
  comme le fait déjà `REINIT_SESSION`. `RDP_UNABLE_TO_CONNECT` ne consomme une étape de plus que si
  une reconnexion est déjà en cours (`reconnectAttempts > 0`) : une première connexion ratée garde
  son message immédiat, sinon un mot de passe faux boucherait sur cinq essais. Le compteur est remis
  à zéro à `GRAPHICS_SETTINGS_RECEIVED`, seul signal qui prouve que la session est de nouveau vivante.
- **Garde-fou mesuré sur le POCO** (29/10, 23:16) : un accès temporaire vers `192.0.2.10:3389`
  (TEST-NET, injoignable) a été créé par `_check/flow_rdp_access.py --keep`, ouvert, puis supprimé.
  Journal : deux `OnPreConnect` (23:15:38 et 23:15:54 — la reprise sans identifiants existe déjà en
  amont), puis `RDP_UNABLE_TO_CONNECT` à 23:16:09, le dialogue « Impossible de se connecter au
  serveur RDP » à l'écran, et **zéro** ligne `Scheduling reconnect` / `RECONNECT attempt` : une
  première connexion ratée ne boucle pas. C'est exactement ce que la condition `reconnectAttempts > 0`
  doit produire.
- **Pas encore mesuré de bout en bout** : couper le réseau pendant une session RDP suppose une
  session ouverte, donc le mot de passe du poste — que l'agent n'a pas. Vérifié à la place : les
  tests de la politique, la compilation, l'installation, le chemin normal intact (appui sur l'accès →
  dialogue d'identifiants RDP, certificat déjà accepté) et le garde-fou ci-dessus. Pour finir la
  mesure : saisir le mot de passe une fois avec « Conserver » coché, puis couper le Wi-Fi ; le
  journal doit montrer `Scheduling reconnect n in Xs` à 2, 5, 10, 20, 30 s, puis le dialogue d'échec
  au 5e.
- `versionCode` 6, `versionName` 1.14-dualdesk : **non publiée** tant que la mesure manque.

## Construire (état 29/10/2026)

```bash
export JAVA_HOME=/d/Codex/tools/jdk-21
export ANDROID_SDK_ROOT=/d/android-sdk ANDROID_HOME=/d/android-sdk
./gradlew :dualdesk-app:assembleRelease --offline
```

- **Le JDK n'est pas sous `C:`** : `/d/Codex/tools/jdk-21` (l'ancienne note ci-dessous parle de Zulu
  sous `C:`, c'est périmé). Le SDK est `/d/android-sdk`.
- La clé de publication est dans `_keystore/` (`dualdesk-release.jks` + `keystore.properties`), donc
  `assembleRelease` signe ; `_check/verifie_signature.py` contrôle l'empreinte.
- `--offline` suffit : les dépendances sont déjà dans le cache Gradle.


## Ce qui a été vérifié

- `git clone --depth 1` puis `download-prebuilt-dependencies.sh` : l'archive
  `remote-desktop-clients-libs-17.tar.gz` (171 Mo) contient les **`.so` FreeRDP déjà compilés**
  pour les 4 ABI et les sources Java de la glue (`freeRDPCore`). **Aucune compilation C, aucun NDK
  n'est nécessaire** pour construire l'application.
- Un seul lien symbolique dans l'archive (`freeRDPCore/src/main/libs -> jniLibs`) : Windows refuse
  de le créer. Extraire avec `tar xzf … --exclude='*/src/main/libs'` ; Gradle n'en a pas besoin.
- `local.properties` (`sdk.dir=D:/android-sdk`) est **indispensable** : sans lui, Gradle échoue sur
  « SDK location not found » après avoir tout téléchargé.
- L'AAR SQLCipher n'est **pas** dans l'archive alors que `common/build.gradle` l'attend dans
  `common/aars` : le récupérer sur Maven Central
  (`net.zetetic:android-database-sqlcipher:4.5.4`), sinon `:common:generateDebugRFile` échoue.
- L'archive de dépendances (DEPVER 17, mars 2025) est **plus ancienne que le code applicatif** :
  `RdpCommunicator` appelle `PerformanceFlags.setGlyphCache(boolean)`, absent de la glue livrée.
  Corrigé dans `BookmarkBase.java` (champ + getter/setter + Parcel) et `LibFreeRDP.java`
  (`addFlag("glyph-cache", flags.getGlyphCache())`, qui était figé à `false`).

## Construire

```bash
export JAVA_HOME="/c/Program Files/Zulu/zulu-21"   # le java du PATH est un 1.8
export GRADLE_USER_HOME="D:/codex/android-tools/gradle-home"
./gradlew :dualdesk-app:assembleDebug              # Gradle 8.13, AGP 8.13.2
```

Sortie : `dualdesk-app/build/outputs/apk/debug/dualdesk-app-arm64-v8a-debug.apk` (67 Mo).
Vérifié dans l'APK : `package com.dualdesk.rdp`, `label DualDesk`, chaînes RustDesk présentes.

## Ce que le fork ajoute

- Module `dualdesk-app` : même code et mêmes ressources que `freeaRDP-app`, `applicationId`
  **`com.dualdesk.rdp`**. Le suffixe « rdp » n'est pas décoratif : `Utils.isRdp()` déduit le
  protocole de l'application **du nom de paquet** (`packageName.contains("rdp")`).
- `Constants.CONN_TYPE_RUSTDESK = 6` : Pas de changement de schéma. L'identifiant d'appareil tient
  dans `ADDRESS`, le mot de passe dans `PASSWORD`.
- `bVNC/src/main/java/com/dualdesk/rustdesk/` : `RustDeskContract` (paquet, activité, deep link) et
  `RustDeskLauncher` (deep link → activité → presse-papiers), portés de DualDesk.
- `ConnectionGridActivity` : menu « New RustDesk access » (deux champs), insertion de l'accès, et
  aiguillage dans `launchConnection` — la session est remise à RustDesk, la liste reste au premier
  plan.

## Éprouvé sur le POCO (26/10/2026, 22:00)

- `adb install -r` passe — après avoir activé Options développeur → « Installer via USB »
  (MIUI renvoyait `INSTALL_FAILED_USER_RESTRICTED` sans cela).
- L'application démarre sous le nom **DualDesk**, interface en français (locale du téléphone),
  et le menu ⋮ contient bien **« New RustDesk access »** entre « Nouvelle connexion » et
  « Aide, évaluer, partager ».
- La boîte de dialogue s'ouvre avec les deux champs (« RustDesk device ID », « Password
  (optional) ») et l'accès créé apparaît dans la grille, persistant après redémarrage de l'app.
- Appuyer sur la carte n'ouvre **aucune** session RDP : la branche de délégation s'exécute
  (`I/ConnectionGridActivity: RustDesk hand-off: none`) et un message le dit —
  « RustDesk is not installed on this device. »
- Deux défauts d'affichage trouvés à cette occasion et corrigés : la carte annonçait
  « 123456789:0 » (port inutilisé) puis répétait l'identifiant sur deux lignes.

## Le passage de relais, exercé pour de vrai (26/10/2026, 22:07)

RustDesk 1.5.0 (`com.carriez.flutter_hbb`, GitHub officiel) installé sur le POCO. En appuyant sur
un accès RustDesk, la stratégie réellement employée est le **deep link** :

```
I/ConnectionGridActivity: RustDesk hand-off: deep link rustdesk://
I ActivityTaskManager: START u0 {act=VIEW dat=rustdesk://123456789/… cmp=com.carriez.flutter_hbb/.MainActivity}
                         callingPackage com.dualdesk.rdp
I flutter: A uri was received: rustdesk://123456789. handleByFlutter true
```

RustDesk ne s'est pas contenté de s'ouvrir : il a **traité l'identifiant** (écran « Erreur de
connexion — the target device is offline or does not exist », attendu pour un identifiant
fictif). Le presse-papiers n'est donc pas utilisé dans ce chemin, et le mot de passe ne quitte
jamais l'application.

**Défaut trouvé en l'exerçant** : sans l'élément `<queries>` du manifeste, le filtrage de
visibilité d'Android 11+ rend RustDesk invisible (`getPackageInfo` lève, `resolveActivity` renvoie
null) et le relais était annoncé « RustDesk is not installed » alors qu'il était installé.
Déclaré dans `dualdesk-app/src/main/AndroidManifest.xml` (paquet + filtre `rustdesk`).

## Identité et langues (26/10/2026, 22:18)

- **Icône** : `dualdesk-app/src/main/res/mipmap-anydpi-v26/ic_dualdesk.xml` (adaptative : fond
  `@color/dualdesk_icon_background` #0E6E6E, avant-plan vectoriel `ic_dualdesk_foreground`)
  plus `mipmap-anydpi/ic_dualdesk.xml` pour l'API 21–25. L'ancien `banner_ardp` a été retiré du
  manifeste. Vérifié à l'écran : tuile teal, deux écrans blancs superposés, libellé « DualDesk ».
- **Chaînes** : les textes RustDesk ne sont plus écrits en dur dans le lanceur ; ils sont dans
  `values/strings.xml` (anglais, défaut) et `values-fr/strings.xml` (français). L'application
  suit la langue du téléphone — sur ce POCO en français, le menu dit « Nouvel accès RustDesk » et
  les champs « Identifiant RustDesk » / « Mot de passe (facultatif) ».
- **Barre d'état en thème clair** : les icônes restaient blanches sur fond blanc (horloge et
  batterie invisibles). Corrigé dans `ConnectionGridActivity.onCreate` via
  `WindowInsetsControllerCompat.setAppearanceLightStatusBars(!nightMode)`. Vérifié dans les deux
  thèmes (`adb shell cmd uimode night yes|no`) : icônes sombres en clair, blanches en sombre.

## Identité : plus rien ne renvoie à aRDP (26/10/2026, 23:20)

L'écran « ⭐ Aide, évaluer, partager » du menu renvoyait encore vers l'application d'origine :
forum Google du projet amont, dépôt amont (bogues et versions), recherche Play sur l'auteur, adresse
`support@morpheusly.com`, et le bouton Partager copiait la fiche Play **d'un paquet DualDesk qui
n'existe pas**. Il n'y a pas de page « À propos » dans cette application : le dialogue d'aide est le
seul écran de ce genre, et le dialogue de bienvenue n'est jamais montré ici (réservé aux paquets dont
le nom contient « free »).

**Structure retenue** : les cibles sortantes sont des ressources (`project_url_bug_reports`,
`project_url_help_forum`, `project_url_releases`, `project_url_more_apps`, `project_email_support`,
`project_url_share`) déclarées dans `bVNC` avec les valeurs amont, et **surchargées par la variante**
dans `dualdesk-app/src/main/res/values{,-fr}/strings.xml`. Une valeur vide veut dire « cette
application ne l'a pas » : `RateOrShareFragment` masque alors le bouton, et `Utils.hasProjectLink`
tient la même règle côté `ConnectionGridActivity`. Les autres applications du dépôt (aRDP, bVNC,
aSPICE, Opaque) ne changent donc pas de comportement.

Pour DualDesk : bogues et partage pointent vers `https://github.com/Endymi0n74/DualDesk`
(+ `/issues`), le dépôt d'où le fork est réellement construit et publié — et non vers
`DualDeskOld` (ex-`dualdesk`, **dépôt supprimé le 28/10/2026** — le banc a été absorbé), qui était la
réécriture Compose, un autre projet (autre paquet :
`com.dualdesk` contre `com.dualdesk.rdp`). Depuis le 28/10/2026 le dépôt est **public** : ces deux
liens s'ouvrent donc pour tout le monde (ils étaient réservés au propriétaire tant qu'il était
privé).
Le forum, les versions précédentes, « Plus d'applications » et l'adresse de contact sont vides, donc absents.
Le don était déjà masqué (paquet sans « free ») et l'évaluation aussi (application installée hors Play).
Le titre du menu devient « ⭐ Aide et partage » et le nom que le code donne à l'application RDP
(`rdp_intro_title`) devient « DualDesk », en anglais et en français.

**Ce qui a été vérifié** : `:dualdesk-app:assembleDebug` passe ; `adb install -r` sur le POCO passe ;
`aapt2 dump resources` sur l'APK montre que les valeurs de la variante gagnent bien sur celles de
`bVNC` (`project_url_share` = l'URL du dépôt, les quatre autres vides, `action_rate_or_share_app` et
`rdp_intro_title` en `()` et `(fr)`). **Pas encore vu à l'écran** : le téléphone était verrouillé et
utilisé, `am start` ne reprenait pas le premier plan (le verrou n'est pas contourné).

## Ce qui reste ouvert

- Les liens sortants de l'écran d'aide ne mènent plus à l'application d'origine : voir la section
  « Identité : plus rien ne renvoie à aRDP ». Reste du texte d'aRDP **inatteignable dans cette
  variante** dans les ressources partagées : `rdp_intro_text` (dialogue de bienvenue jamais montré
  ici), `pro_feature_rdp`, `ad_donate_*`, et `freeardp_app_name` — cette dernière nomme
  `freeaRDP-app`, une autre application du même dépôt, à ne pas renommer.
- Le bouton **Morpheusly** de la barre d'action a été retiré, puis la fonction entière avec lui
  (voir « Morpheusly retiré du fork » en fin de fichier).
- Les traductions autres que l'anglais et le français gardent les libellés d'origine (par exemple
  le titre du menu « évaluer/partager » en espagnol) : ce sont des étiquettes, pas des liens.
- Les deux stratégies de repli (activité, presse-papiers) sont désormais exercées **sur banc JVM**
  (voir « Chaîne de repli du lanceur, exercée sur banc JVM » en fin de fichier). Ce qui reste non
  prouvé, c'est que RustDesk accepte réellement l'intent d'activité sur un appareil : le banc vérifie
  l'ordre et le compte rendu, pas l'accueil de l'application d'en face.
- Un accès RustDesk est désormais **modifiable et supprimable** (éprouvé le 26/10 à 22:23) :
  l'appui long sur la carte propose « Modifier » et « Supprimer » seulement, sans l'option
  raccourci d'écran d'accueil qui n'a pas de sens sans hôte ni port ; « Modifier » rouvre le
  formulaire pré-rempli au lieu de l'écran de configuration RDP. Une carte RDP garde ses trois
  options : aucune régression.
- Aucun accès RustDesk de démonstration ne subsiste sur le téléphone : celui des essais a été
  supprimé, la liste ne contient que le modèle `:3389` fourni par aRDP.
- Le mot de passe RustDesk est désormais **scellé sous une clé Keystore** (voir « Mot de passe
  RustDesk scellé sous Keystore » en fin de fichier), donc lisible sans dépendre du mot de passe
  maître. À l'export, il ne sort que si l'utilisateur donne une phrase de passe ; sinon le fichier
  n'en contient aucun (voir « L'export des mots de passe RustDesk »).
- Icône, nom d'application et traductions restent ceux d'aRDP (`icon_ardp`, chaînes en anglais).
- Douze tests JVM couvrent deux logiques pures (chaîne de repli du lanceur, format du sceau) ; le
  reste n'est couvert que par les parcours `_check`, qui mesurent l'application installée.

## Éprouvé de nouveau sur le POCO, chaîne complète (27/10/2026, 18:46–18:54)

Motif : la mesure de bout en bout du dépôt voisin venait d'être refaite sur ce même POCO, et le fork
produit n'avait pas été ré-éprouvé depuis les corrections d'identité (dont l'écran d'aide, jamais vu
à l'écran). Tout se tient, sur la version installée `1.0.0-dualdesk`.

- **Menu** : « Nouvel accès RustDesk » est la deuxième entrée, entre « ✚ Nouvelle connexion » et
  « ⭐ Aide et partage ». Le titre de l'application est « DualDesk » et son icône teal est dans la
  barre d'action.
- **Création** : boîte « Nouvel accès RustDesk », deux champs (« Identifiant RustDesk », « Mot de
  passe (facultatif) »), boutons ANNULER / OK. L'accès `987654321` apparaît sur **une seule ligne** :
  le « :0 » et la répétition de l'identifiant ne sont plus là.
- **Modification** : appui long → « Modifier » et « Supprimer » **seulement**, sans l'option raccourci
  d'écran d'accueil. « Modifier » rouvre « Modifier l'accès RustDesk », pré-rempli (identifiant en
  clair, mot de passe masqué) — pas l'écran de configuration RDP. Passé à `555000111`, la carte suit
  et la valeur survit au redémarrage (`am force-stop` puis relance).
- **Passage de relais** : appui sur la carte →
  `I/ConnectionGridActivity: RustDesk hand-off: deep link rustdesk://` puis
  `START u0 {act=android.intent.action.VIEW dat=rustdesk://555000111/... cmp=com.carriez.flutter_hbb/.MainActivity} ... callingPackage com.dualdesk.rdp`.
  RustDesk passe au premier plan et **traite l'identifiant** (« Erreur de connexion — WebRTC failed
  … the target device is offline or does not exist.; fallback failed … ») : c'est le résultat attendu
  pour un identifiant fictif, et il porte la valeur **modifiée** — la modification est donc effective
  jusque dans le relais.
- **Suppression** : « Supprimer la connexion 555000111 ? » avec ANNULER / OK ; après validation la
  grille revient à « Aucune connexion pour l'instant », et cet état vide survit au redémarrage.
- Aucun accès d'essai ne subsiste sur le téléphone.

### Ce que l'essai a fait apparaître

- **Le gestionnaire de mots de passe d'Android s'interpose** juste après la création : la feuille
  « Enregistrer le mot de passe dans Gestionnaire… » recouvre l'application, identifiant et mot de
  passe proposés à l'enregistrement. Rien n'a été enregistré. **Corrigé le 27/10** : voir la section
  « La feuille du gestionnaire de mots de passe, neutralisée ».
- L'étiquette de la carte porte un **saut de ligne final** (`"987654321
"` dans la hiérarchie des
  vues). Le rendu tient sur une ligne : c'est cosmétique, mais c'est ce qui reste de l'ancien défaut.
- **Piège de mesure** : `uiautomator dump` peut rendre une hiérarchie **antérieure** à la frappe qui
  vient d'être faite — le champ montrait encore l'ancien texte alors que la capture d'écran montrait
  le nouveau. Recouper systématiquement un relevé par une capture avant d'en conclure quoi que ce soit.
- Les deux stratégies de repli du relais (activité, presse-papiers) restent **non exercées** : le deep
  link aboutit à chaque fois, donc elles ne se déclenchent pas.

### Preuves

`preuve-poco/01-menu-rustdesk.png`, `02-dialogue-creation.png`, `03-dialogue-rempli.png`,
`04-liste-apres-creation.png`, `05-appui-long-options.png`, `06-modification-pre-remplie.png`,
`07-modification-champ.png`, `08-liste-apres-modification.png`, `09-relais-rustdesk.png`,
`10-suppression.png`, `11-liste-apres-suppression.png`, `12-etat-final.png`.

## La feuille du gestionnaire de mots de passe, neutralisée (27/10/2026, 20:15–20:50)

Le défaut signalé le 26/10 (« la première création se termine par une feuille système à refuser »)
est corrigé. La mesure qui a mené au correctif mérite d'être gardée, parce que le premier correctif
naturel ne marche pas.

### Ce qui ne marche pas

Marquer les deux champs `IMPORTANT_FOR_AUTOFILL_NO` (premier correctif essayé) **ne suffit pas**.
Mesuré avec le journal du framework au niveau `verbose` (`adb shell cmd autofill set log_level verbose`) :

```
AutofillManager: setTrackedViews(): … trackedIds=[1073741827, 1073741828],
                 saveOnAllViewsInvisible=true, saveOnFinish=true
```

Les deux champs restent suivis, et le service Google les reçoit :
`PasswordSaveInfoFactory: Form has a username and is not a password only form`. C'est cohérent avec la
documentation Android 14+ : une vue marquée `NO` peut malgré tout être incluse dans la structure
transmise au service.

### Ce qui marche

1. `excludeTreeFromAutofill` marque la **racine** du formulaire et le décor de la fenêtre de la boîte
   avec `IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS` ;
2. `abandonAutofillContext()` appelle `AutofillManager.cancel()` à la validation, **avant** de fermer
   la boîte : laisser le contexte se terminer est ce qui amène la proposition d'enregistrer.

Preuve directe dans le journal, au moment de l'appui sur OK :

```
AutofillManager: cancel() called by app or augmented autofill service
AutofillManager: cancelSessionLocked(): ACTIVE
```

C'est le second point qui décide : les vues restent suivies (`trackedIds` inchangé) mais le contexte
est abandonné, donc plus rien n'est proposé à l'enregistrement.

### Comment la mesure a été rendue concluante

- **Piège majeur** : appuyer sur « Jamais » dans la feuille inscrit un refus **par nom de paquet** chez
  le service, qui **survit à la désinstallation et à la réinstallation**. Après cet appui, la feuille
  ne revient plus même avec le code fautif : la mesure devient muette. Ne pas refuser la feuille
  pendant une campagne ; l'écarter par « Pas maintenant ».
- Pour retrouver une application « jamais vue par le service », une **identité de paquet temporaire**
  suffit (`applicationIdSuffix` dans `dualdesk-app/build.gradle`, retiré après mesure). Le suffixe doit
  garder les lettres « rdp » : `Utils.isRdp()` déduit le protocole du nom de paquet.

| Code | Identité d'essai | Feuille après validation |
|---|---|---|
| marquage des champs seul | `com.dualdesk.rdp.avtest` | **présente** |
| marquage des champs seul | `com.dualdesk.rdp` (installation neuve) | absente — refus mémorisé, mesure muette |
| correctif complet | `com.dualdesk.rdp.avtest2` | **absente** (deux essais) |

- Outils de mesure, qui pilotent l'interface au lieu de dépendre d'une lecture manuelle :
  `_check/flow_rustdesk.py` (parcours complet + verdict), `_check/install_apk.py` (franchit la
  confirmation MIUI), `_check/tap_text.py`, `_check/delete_rustdesk_access.py`. Ils forcent l'encodage
  UTF-8 : sans cela, le décodage cp1252 du poste rejette les accents du relevé et le résultat est vide,
  ce qui ressemble à tort à « aucune feuille ».
- Deux pièges retrouvés : le **clavier** remonte la boîte (le champ mot de passe passe de y≈1468 à
  y≈981) — sans relire la hiérarchie entre deux appuis, le suivant tombe hors de la boîte et **annule**
  la saisie ; et `uiautomator dump` peut échouer en laissant le **fichier précédent**, ce qui fait lire
  un écran qui n'existe plus.

### État

- Correctif installé sur le POCO sous le paquet réel (`com.dualdesk.rdp`, `1.0.0-dualdesk`, 20:49:59).
  Identités de mesure désinstallées, accès d'essai supprimé, grille vide.
- Non isolé par deux constructions séparées : la part de l'exclusion de la racine et celle de
  `cancel()`. Le journal montre que les vues restent suivies et que `cancel()` est appelé, ce qui
  désigne l'abandon du contexte comme décisif.
- Non revérifié sur le paquet réel : le refus mémorisé y rend la feuille muette. Pour la revoir, il
  faudrait retirer DualDesk de la liste des applications refusées du gestionnaire Google.

Le même défaut dans le **formulaire d'accès RDP** a été mesuré et corrigé le 27/10 au soir : voir la
section suivante, qui isole cette fois la part de chaque mécanisme et met au jour un second chemin
d'enregistrement.

### Preuves

`preuve-poco/13-autofill-feuille-presente-avant-correctif.png` (la feuille s'interpose malgré le
marquage des champs), `14-autofill-temoin-identite-neuve.png` (témoin : identité neuve et code sans
abandon, la feuille recouvre l'application) et `15-autofill-apres-correctif.png` (après le correctif :
la validation crée l'accès et rien ne recouvre l'application).

## Le formulaire RDP : mesuré puis neutralisé (27/10/2026, 21:35–22:05)

L'éditeur d'accès RDP (`main_rdp.xml`, activité `aRDP` au-dessus de `MainConfiguration`) se comportait
comme la boîte RustDesk : après « Enregistrer », le système proposait d'enregistrer l'identifiant et le
mot de passe de la machine distante. Mesure comparative sur des **identités de paquet neuves**, pour
qu'aucun refus mémorisé ne puisse expliquer le résultat.

| Code | Identité | Sortie | `cancel()` | `setSaveUiState` | Feuille |
|---|---|---|---|---|---|
| témoin : ni exclusion, ni annulation | `…rdp.mesrdp3` | bouton « Enregistrer » | non | **oui** | **présente** (+6 s et +16 s) |
| corrigé : exclusion de la racine + annulation | `…rdp.mesrdp4` | bouton « Enregistrer » | oui (1) | non | absente |
| corrigé | `…rdp.mesrdp4` | retour arrière | **non** | **oui** | **présente** |
| corrigé, `onPause` annule aussi | `…rdp.mesrdp4` | retour arrière | oui (1) | non | absente |
| corrigé, `onPause` annule aussi | `…rdp.mesrdp4` | bouton « Enregistrer » | oui (2) | non | absente |

Dans chaque essai l'accès est bien créé (la grille montre `essaidualdesk@192.0.2.10:3389`) : la
neutralisation ne coûte pas l'enregistrement.

Ce que la mesure établit :

- **L'exclusion de la racine ne suffit pas.** Les deux champs restent suivis (`setTrackedViews`,
  `trackedIds=[1073741834, 1073741836]`) et le service reçoit encore le formulaire
  (`PasswordSaveInfoFactory: Form has a username and is not a password only form`). C'est
  `AutofillManager.cancel()` qui décide : partout où il est appelé, `setSaveUiState` n'apparaît jamais.
- **Un second chemin d'enregistrement existait.** Quitter l'écran par le retour arrière enregistre
  l'accès par `onPause()`, sans passer par « Enregistrer » : l'annulation posée dans
  `saveConnectionAndCloseLayout()` ne couvrait donc pas ce chemin, et la feuille y revenait. Le
  correctif appelle maintenant `abandonAutofillContext()` dans `onPause()` **et** à l'enregistrement.
- Le bouton « Enregistrer » passe par `aRDP.save(View)` → `MainConfiguration.save(int)` →
  `saveConnectionAndCloseLayout()` ; doublonner l'annulation dans `onPause` est sans effet (annuler
  deux fois ne fait rien).

### Pièges de mesure trouvés cette fois

- **Rotation entre le relevé et l'appui** : l'écran a pivoté en cours d'essai, les coordonnées lues
  sur la hiérarchie (x=2340 pour un écran large de 1220) ne valaient plus, l'appui est tombé hors
  écran et **rien ne s'est produit** : le verdict « aucune feuille » portait alors sur une mesure
  vide. Le parcours verrouille désormais l'orientation (`accelerometer_rotation=0`, restauré ensuite).
- **Vérifier que l'action a abouti** : le parcours confirme que l'éditeur s'est fermé après l'appui et
  relève les marqueurs du journal (`save called`, `saveConnectionAndCloseLayout called`,
  `cancel() called by app`, `onActivityFinishing`, `setSaveUiState`) au lieu de conclure sur l'écran.
- `_check/dismiss_sheet.py` écarte la feuille par « Pas maintenant » : « Jamais » inscrirait un refus
  par paquet, qui survit à la réinstallation et rendrait muets tous les essais suivants.

### État

- Paquet réel `com.dualdesk.rdp` (`1.0.0-dualdesk`) réinstallé sans identité de mesure, accès d'essai
  effacé, grille vide (« Aucune connexion pour l'instant »). Les quatre identités d'essai sont
  désinstallées, `accelerometer_rotation` remis à 1 et le journal du framework remis à `off`.
- Le paquet réel ne peut pas servir de témoin (refus mémorisé) : la démonstration repose sur les
  identités neuves du tableau.

### Preuves

`preuve-poco/16-autofill-formulaire-rdp-temoin.png` (témoin : la feuille s'interpose après
l'enregistrement du formulaire RDP) et `18-etat-final-paquet-reel.png` (état final : grille vide sous
le nom DualDesk). La ligne de compte Google du paragraphe est masquée dans les captures 13, 14 et 16.

## Brouillons de rapport supprimés (27/10/2026)

Treize fichiers vivaient à la racine, restes de la présentation du rapport de six heures : neuf
`README-<variante>.md` (la même conclusion en neuf reformulations, chacune listant les autres),
trois lanceurs `generate_rapport_*` et `make_rapport.py`. Ils ont été **supprimés**, pour trois
raisons vérifiables : leur fond est déjà ici, leur forme portait le chemin
`C:\Users\<utilisateur>\appdata\local\temp\dualdesk-rapport` (et deux APK du bureau pour le
script), et ils décrivaient un dépôt d'avant les deux commits du 27/10 en présentant l'absence de
RustDesk comme un point ouvert — clos depuis.

Conséquence à connaître : **le rapport ne se régénère plus par un script**. Il vit dans
[RAPPORT.md](RAPPORT.md), réécrit le 27/10 à partir de ce fichier — c'est ce fichier qui alimente le
rapport, et non l'inverse. `rapport.txt` (10 021 octets, généré le 27/10 à 00:42) reste dans le
dossier temporaire de la session précédente ; il est périmé sur ces deux points précis. Reste non suivi à la racine :
`bVNC/libs/`, deux jars que rien ne référence (les classes `com.antlersoft.android.db` sont suivies
en source dans `common/src/main/java`). **Conservés** : le tarball des dépendances qui les a posés
n'est plus sur le disque, donc les supprimer serait sans retour.

### Dossier de travail vidé (27/10/2026)

Le dossier temporaire de la session (`%TEMP%`) portait mes restes : trois copies de dépôt ou de
paquets décompressés (`aRDP/`, `ardp-checkout/`, `dualdesk-verify/`), vingt journaux de compilation
et de logcat, neuf scripts de vérification ad hoc, quatre brouillons de message de commit et une
capture. Supprimés, soit environ 78 Mo. Deux vérifications avant de le faire : les 177 images du
dossier de rapport sont **des copies conformes** d'images déjà dans les dépôts (comparaison md5 —
une seule leur échappait, `rapport.txt`), et les scripts ad hoc ont leurs équivalents suivis dans
`_check/`.

Il reste dans ce dossier temporaire : `dualdesk-rapport/rapport.txt` seul. Les sorties de
compilation des dépôts n'ont **pas** été touchées : `ardp/dualdesk-app/build` (1,2 Go) contient les
cinq APK construits, dont l'universel de 190 Mo — les purger obligerait à refaire une compilation
qui a demandé onze essais.

### Témoin GFX côté poste — verdict (28/10/2026)

Le test témoin qui manquait est fait, avec deux clients de référence installés sur le téléphone
(aRDP 6.4.9 Free arm64 depuis F-Droid, aFreeRDP 3.32.1 build arm64-v8a — attention, F-Droid publie un
paquet **par architecture** : 3320102 = armeabi-v7a, 3320103 = arm64-v8a, 3320104 = riscv64,
3320108 = x86, 3320109 = x86_64 ; l'APK x86_64 ne s'installe pas sur le POCO).

**Résultat : le poste fait du GFX.** aFreeRDP, journal poussé en DEBUG, annonce onze jeux de
capacités (8.0 → 10.7) ; le poste confirme **RDPGFX 10.7** (`0x000A0701`, AVC à 0), ouvre une surface
1220 × 2530 et l'alimente à **160 commandes/s** (1 787 sur onze secondes), en **CLEARCODEC** et
**CAPROGRESSIVE** — pas de H.264. L'image du bureau s'affiche et se rafraîchit. Extrait conservé :
`preuve-poco/temoin-afreerdp-extrait.txt`.

aRDP (même moteur que le fork, FreeRDP 2.11.7) : NLA OK, session ouverte, canal `rdpgfx` chargé, puis
`PDU_TYPE_DATA not properly parsed, 562 bytes remaining unhandled` — **et l'écran s'affiche quand
même**, en bandeau (bureau distant 21:9 logé dans les 1220 × 2530 du client), avec des mises à jour
vivantes : une fenêtre ouverte côté poste apparaît dans la capture suivante. Ce client ne fait donc
pas de GFX utilisable mais il rend l'écran par le chemin classique.

**Conséquence pour le fork** : le poste est innocent (prouvé deux fois) et le fork n'a pas d'écran
noir — il affiche le bureau par le chemin classique (mesure du 26/10 : 102 images en 40 s, sans GFX ;
les 5 s de noir au démarrage étaient l'attente du garde-fou, supprimée depuis). Ce qui manque au fork
n'est donc pas l'image mais le **GFX**, et l'écart se lit maintenant dans les annonces de capacités :

aFreeRDP 3.32.1 annonce **11** jeux et le poste confirme 10.7 ; le fork en annonce **8** (10.7 avec
`SCALEDMAP_DISABLE` 0x80) et n'obtient **rien** en 25 s. Piste la plus courte, deux essais : annoncer
les onze jeux comme aFreeRDP, puis retirer `SCALEDMAP_DISABLE`.

Attention, il y a **deux applications** et deux moteurs, d'où la confusion facile :

- `com.dualdesk.rdp` (module `dualdesk-app` de ce dépôt) — le fork aRDP, moteur **FreeRDP 2.x**
  (`libfreerdp2.so`), c'est celui qu'on installe sur le téléphone ;
- `com.dualdesk.debug` (projet `dualdesk`) — l'appli autonome à moteur natif **FreeRDP 3**
  (`libfreerdp3.so`), celle qui a servi aux mesures GFX du 26/10 ; elle n'est pas installée sur le POCO.

Les annonces de capacités relevées (8 jeux, `SCALEDMAP_DISABLE`) viennent de la seconde, pas du fork
aRDP. Ne pas mélanger les deux quand on parle de « notre fork ».

Pièges d'outillage rencontrés, à ne pas repayer :

- **MIUI refuse l'installation tant que l'écran est verrouillé** (`INSTALL_FAILED_USER_RESTRICTED`,
  « Install canceled by user ») : déverrouiller, puis relancer `adb install`.
- **aRDP Free lève une exception au premier essai** (`DIALOG_RDP_CERT` →
  `View ... not attached to window manager`) : le dialogue de certificat ne se montre pas et la
  connexion reste bloquée sur « poignée de main ». Au deuxième essai, la fenêtre est attachée et le
  dialogue s'affiche. Ne pas conclure à un refus du poste.
- **Le certificat du poste est auto-signé au nom de la machine** : le client avertit d'un nom qui ne
  correspond pas à l'adresse IP, il faut accepter.
- **Arbitrage de session Windows** : le téléphone prend la session du compte (même utilisateur), donc
  il voit le bureau de la console — pratique pour provoquer un changement d'image et vérifier que
  l'affichage suit. Mais dès que la console reprend la main, la session distante est coupée (vu une
  fois, au bout de 11 s) : garder les mesures courtes, ou laisser le poste tranquille pendant l'essai.
- Le journal des capacités GFX n'est visible qu'en **DEBUG** : aRDP Free est figé en INFO (rien à
  régler dans l'interface), aFreeRDP expose « Debug Settings → Debug Level ».
- Les journaux graphiques **du poste** (`Rdp-Graphics-RdpAvenc`, `Rdp-Graphics-RdpLite`) restent
  vides : la machine ne journalise pas ses échanges GFX, tout se lit côté client.
- Le journal client RDP de Windows (`TerminalServices-RDPClient`) ne contient que des connexions
  **sortantes** : l'événement 1401 « 0x80004 » qui y figure ne dit rien de ce poste-ci.

## Banc émulateur : monté et vérifié (28/10/2026)

Le fork n'a pas besoin du téléphone pour tourner : `dualdesk-app` publie ses `.so` FreeRDP 2.11.7
pour les quatre ABI (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`), donc l'APK universel s'installe
sur un émulateur x86_64. Mise en place, une seule fois :

```bash
export JAVA_HOME="C:/Program Files/Zulu/zulu-21"   # le JAVA_HOME ambiant est un JRE 11 :
export ANDROID_SDK_ROOT=D:/android-sdk              # sdkmanager refuse de démarrer avec
sdkmanager --install "system-images;android-34;default;x86_64"
avdmanager create avd -n dualdesk-test -k "system-images;android-34;default;x86_64" -d pixel_6
emulator -avd dualdesk-test -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect -no-window
```

WHPX est utilisable (`emulator -accel-check`), démarrage complet en ~55 s, `emulator-5554`.
Installation de l'APK **universel** (190 Mo) : les APK par ABI ne contiennent qu'une architecture.

Ce qui a été mesuré, sans rien interpréter :

- l'application se lance (`ConnectionGridActivity`, version `1.0.0-dualdesk_1`) et son interface
  répond : grille vide, feuille « Add Connection » → « ENTER MANUALLY », éditeur RDP complet
  (type, serveur, port, utilisateur, domaine, mot de passe, mode couleur) ;
- le poste est joignable depuis l'émulateur (ping 2/2, `nc` sur 33899 sort en 0) ;
- session RDP réelle, d'abord jusqu'à l'authentification (`RDP_NEG_RSP` avec
  `DYNVC_GFX_PROTOCOL_SUPPORTED`, `Attempting NLA security`, certificat auto-signé accepté, puis
  `STATUS_LOGON_FAILURE` faute de mot de passe), ensuite jusqu'au **bureau** une fois le mot de passe
  saisi dans la fenêtre. Moteur, canaux (`rdpdr`, `rdpsnd`, `cliprdr`, `drdynvc`) et pile réseau
  fonctionnent donc bien en x86_64.

Une seule limite de départ, contournable : **le mot de passe manque** sur un émulateur neuf. La base
du téléphone n'est pas transposable — elle est chiffrée par SQLCipher avec une clé du Keystore,
propre à l'appareil. Il faut donc le saisir une fois dans la fenêtre de l'émulateur ; ensuite la
session se rouvre sans le redemander (vérifié : reconnexion après changement de réglage).

Piège d'outillage : sous Git Bash, les chemins `/sdcard/...` passés à `adb` sont réécrits en chemins
Windows. Exporter `MSYS_NO_PATHCONV=1` pour `uiautomator dump` et `adb pull`.

### Le GFX du fork est une case du profil, et elle est cochée par défaut

Le chemin graphique n'est ni absent ni compilé à part : il est **piloté par les préférences du
profil**. `remoteClientLib/.../LibFreeRDP.java` n'ajoute `/gfx` que si `flags.getGfx()`, et
`/gfx:AVC444` que si `flags.getH264()` **et** que le décodeur existe ; ces drapeaux viennent de
`RdpCommunicator` (`performanceFlags.setGfx(enableGfx)`), eux-mêmes du profil.

- `ConnectionBean` met les deux à **vrai** à la création, et l'éditeur les expose : sections
  avancées, « RDP Gfx » et « RDP Gfx H264 » (`checkboxEnableGfx`, `checkboxEnableGfxH264`).
  Un profil neuf sur l'émulateur les affiche bien cochés.
- `rdpgfx` est un canal **dynamique** : contrairement à `rdpdr`/`rdpsnd`/`cliprdr`/`drdynvc`, il
  n'apparaît pas dans les lignes `loading channelEx` au démarrage. Ne pas conclure de leur absence
  que le GFX est désactivé — c'était l'erreur à éviter.
- Le poste, lui, annonce `DYNVC_GFX_PROTOCOL_SUPPORTED` dans ses `RDP_NEG_RSP::flags` à chaque
  connexion : la porte est ouverte côté serveur.

Réglage retenu pour le banc : « RDP Gfx » coché, « RDP Gfx H264 » décoché en premier essai, parce
que `c2.goldfish.h264.decoder` échoue à la **sonde** du démarrage (`AMediaCodec_configure failed:
-10000`). Ce n'est pas une fatalité : en session, la même configuration H264 activée décode sans
erreur (voir plus bas). La sonde et le chemin réel ne se comportent donc pas pareil — ne pas
conclure d'un `-10000` au démarrage que l'AVC est inutilisable.

### Le GFX du fork s'engage — mesuré en session (28/10/2026)

Session réelle depuis l'émulateur vers le poste (`RDP Gfx` coché, `RDP Gfx H264` décoché), journal
`logcat` en DEBUG. Déroulé, tel que le client le raconte :

```
CONNECTION_STATE_NEGO -> CONNECTION_STATE_NLA -> CONNECTION_STATE_MCS_CONNECT
-> MCS_ATTACH_USER -> MCS_CHANNEL_JOIN -> LICENSING -> CAPABILITIES_EXCHANGE
-> FINALIZATION -> CONNECTION_STATE_ACTIVE
Loading Dynamic Virtual Channel rdpgfx
```

Puis des tuiles progressives décodées sans interruption (`com.freerdp.codec.progressive`,
`ProgressiveTileFirst` … `ProgressiveFrameEnd`), **zéro** `Error while processing GFX cmdId`.

| Mesure | Valeur |
|---|---|
| Bureau au repos, 20 s | 7 images en 10,6 s (0,66 img/s), 500 tuiles |
| Pendant un mouvement provoqué | 7 images en 0,4 s (**16 img/s**), 621 tuiles |

**Verdict : le fork fait du GFX, et l'image arrive — dans les deux modes.** Le poste avait donc
raison d'annoncer `DYNVC_GFX_PROTOCOL_SUPPORTED`, le canal dynamique s'ouvre après
l'authentification, et le codec servi suit le réglage du profil :

| Réglage du profil | Ce que le poste envoie | Mesure | Image |
|---|---|---|---|
| `RDP Gfx` seul | tuiles **progressives** (`ProgressiveTileFirst` → `ProgressiveFrameEnd`) | 0,66 img/s au repos (7 en 10,6 s, 500 tuiles) ; **16 img/s** en mouvement (7 en 0,4 s, 621 tuiles) | oui |
| `RDP Gfx` + `RDP Gfx H264` | **H264_METABLOCK** (AVC444) | 31,7 metablocs/s (322 en 10,2 s), **0 erreur de décodeur**, aucune tuile progressive | oui |

Dans les deux cas : `Loading Dynamic Virtual Channel rdpgfx`, aucun `Error while processing GFX
cmdId`, et le bureau distant s'affiche (clichés pris en session, non publiés car ils montrent le
bureau réel).

Ce que cela change pour la question ouverte : **le couple fork + poste fonctionne en GFX sur un banc
neuf**, y compris avec le décodeur logiciel de l'émulateur (`c2.goldfish.h264.decoder`), qui se
configure sans erreur en session alors qu'il échoue à la sonde du démarrage. L'écran noir / l'absence
de GFX constatés sur le POCO ne se reproduisent donc pas ici : la cause est à chercher côté appareil
(version de MediaCodec, thermique, ou profil différent sur le téléphone), pas dans le client ni dans
le poste. Prochaine mesure utile : rejouer exactement ce filtre de journal sur le POCO, avec le même
profil, et comparer les deux lignes `Loading Dynamic Virtual Channel rdpgfx` et `H264_METABLOCK`.

Piège à ne pas oublier : le dialogue d'identifiants RDP exige une saisie dans la fenêtre de
l'émulateur (la base du téléphone n'est pas transposable, clé Keystore). Une fois la session ouverte,
les mesures se font entièrement en `adb` : `logcat`, `screencap`, `input swipe` pour provoquer du
trafic, et un simple comptage des `ProgressiveFrameEnd` avec leurs horodatages donne la cadence.

## Chaîne de repli du lanceur, exercée sur banc JVM (28/10/2026)

Motif : le lanceur promet trois stratégies (deep link, activité explicite, presse-papiers) et **seul le
deep link avait tourné**, parce qu'il aboutit à chaque essai sur les appareils de mesure. Une promesse
qu'aucun essai ne déclenche n'est pas vérifiée. Aucun téléphone n'était disponible ce jour-là : la
chaîne a donc été exercée hors appareil.

Ce qui a été fait, et pourquoi ainsi :

- l'ordre des tentatives sort de `RustDeskLauncher` et vit dans `RustDeskLaunchChain`, un fichier
  **sans aucun import Android**, qui reçoit un `RustDeskTransport` (installé ? deep link accepté ?
  activité démarrée ? presse-papiers) ; `RustDeskLauncher` ne garde que le `Context`, les `Intent` et
  les chaînes traduites. Sans cette couture, `startActivity` et `getString` rendent le code
  intestable hors d'un appareil (Robolectric aurait ajouté une dépendance lourde pour le même
  résultat) ;
- les sept tests sont dans `bVNC/src/test/java/com/dualdesk/rustdesk/RustDeskLaunchChainTest.kt`, avec
  un transport factice qui **enregistre l'ordre des appels** ; `junit:junit:4.13.2` a été ajouté en
  `testImplementation` à `bVNC/build.gradle` (premier source set de test du dépôt ; l'artefact était
  déjà dans le cache Gradle).

Résultat mesuré, pas déclaré :

```bash
export JAVA_HOME="/c/Program Files/Zulu/zulu-21"
export GRADLE_USER_HOME="D:/codex/android-tools/gradle-home"
./gradlew :bVNC:testDebugUnitTest --tests "com.dualdesk.rustdesk.*" --console=plain --offline
```

`7 tests, 0 échec, 0 erreur` (lu dans `bVNC/build/test-results/testDebugUnitTest/`). Les cas couverts :

| Situation | Ordre des appels | Stratégie annoncée | Message |
|---|---|---|---|
| deep link accepté | `deepLink` seul | `deep link rustdesk://` | aucun |
| deep link refusé, mot de passe | `deepLink` → `clipboard` → `activity` | `activity + clipboard` | presse-papiers |
| deep link refusé, sans mot de passe | idem | `explicit activity` | « identifiant copié » |
| presse-papiers indisponible | idem | `explicit activity` | « presse-papiers indisponible » |
| activité refusée aussi | idem, puis échec | `none` | « n'a pas répondu » |
| RustDesk absent | aucun appel | `none` | « pas installé » |
| identifiant vide | aucun appel | `none` | « aucun identifiant » |

**Un défaut trouvé par l'exercice, et corrigé** : quand le service de presse-papiers manquait
(`getSystemService` rend `null`, ce que `copyForPaste` traitait en `false`), le message affiché
annonçait quand même « Identifiant copié : collez-le dans RustDesk » — une information fausse. Le
retour du presse-papiers est devenu un `RustDeskClipboard` à trois états (`PASSWORD_AND_ID`,
`ID_ONLY`, `UNAVAILABLE`), et le troisième a sa chaîne : `rustdesk_clipboard_unavailable`, en anglais
et en français.

**Une nuance laissée telle quelle, volontairement** : sans mot de passe, l'identifiant est copié mais
la stratégie journalisée dit `explicit activity`. La trace ne mentionne donc pas le presse-papiers
alors qu'il a servi. Seul le journal est concerné (le message à l'écran est juste) ; le renommer
changerait le texte sur lequel repose la lecture des journaux de campagne, donc il est consigné ici
plutôt que modifié sans décision.

**Ce que le banc ne prouve pas** : que RustDesk accepte l'intent d'activité sur un appareil réel. Le
test dit que DualDesk essaie ce qu'il annonce, dans l'ordre annoncé, et rend compte de ce qu'il a
obtenu ; l'accueil par l'application d'en face reste à voir sur le POCO.

Pièges d'outillage rencontrés :

- `:bVNC:processDebugResources` est **ambigu** en AGP 8.13 (quatre tâches candidates) : utiliser
  `:bVNC:assembleDebug` pour valider les ressources ;
- `assembleDebug --offline` échoue sur `extractDebugAnnotations` (il réclame
  `com.android.tools.lint:lint-gradle:31.13.2`), sans rapport avec le code : lancer cette tâche en
  ligne ; `testDebugUnitTest --offline` passe, lui, sans réseau.

## Mot de passe RustDesk scellé sous Keystore (28/10/2026)

Point ouvert du rapport : le mot de passe d'un accès RustDesk était écrit **en clair** dans la colonne
`PASSWORD` de la base commune, faute d'un chiffrement propre ; la base n'est chiffrée (SQLCipher)
que si le mot de passe maître de l'application est activé, ce qui n'est pas le cas par défaut. Un
`adb backup` ou un appareil rooté lisait donc le secret de la machine distante.

Ce qui a été fait :

- `RustDeskSecretCodec` (pur, `javax.crypto` seulement) scelle en **AES-GCM**, forme
  `ks1:<iv hex>:<chiffré hex>`, IV aléatoire de 12 octets à chaque sceau. L'hexadécimal est un choix :
  `android.util.Base64` n'existe pas sur la JVM et `java.util.Base64` demande l'API 26 alors que
  `SDK_VERSION=21` ;
- `RustDeskSecretStore` fournit la clé : alias `dualdesk.rustdesk.password.v1` dans `AndroidKeyStore`,
  générée au premier usage, non exportable. Sous l'API 23 (Android 5.x), où `KeyGenParameterSpec`
  n'existe pas, la valeur est enregistrée comme avant et un avertissement est journalisé ;
- la grille (`ConnectionGridActivity`) scelle à l'enregistrement, ouvre à l'affichage et au lancement,
  et **scelle les anciens enregistrements en clair au chargement** (`sealStoredRustDeskPasswords`),
  sinon « scellé » ne serait vrai que pour les accès créés après ce correctif ;
- si le Keystore refuse, rien n'est enregistré et un message le dit
  (`rustdesk_password_not_sealed`), plutôt que d'écrire le secret en clair.

Mesure : `5 tests` du codec (aller-retour, deux sceaux d'un même mot de passe différents, mauvaise
clé rend `null`, enregistrement altéré rend `null`, ancienne valeur en clair rendue telle quelle),
soit **12 tests, 0 échec** avec la chaîne de repli. `:bVNC:assembleDebug` et
`:dualdesk-app:assembleDebug` passent.

## L'export des mots de passe RustDesk (décidée le 28/10/2026, phrase de passe ajoutée le même jour)

Le point laissé ouvert par le scellement : la clé est liée à l'appareil, et `Utils.exportSettingsToXml`
écrit la base **entière** — le sceau partait donc dans un fichier fait pour changer d'appareil, alors
qu'aucune autre installation (ni la même après une réinstallation, qui détruit les clés du Keystore)
ne peut l'ouvrir.

Décision initiale : le mot de passe reste sur l'appareil. Puis la question « et si je veux vraiment
restaurer sur un autre téléphone ? » a sa réponse propre, ajoutée le même jour : **une phrase de passe
facultative**. L'export a donc deux modes, et c'est l'utilisateur qui choisit à l'écran :

- `RustDeskExportSanitizer` (pur) réécrit les mots de passe du **document**, pas de la base : le
  fichier garde l'accès, son identifiant, son surnom ;
- sans phrase, chaque valeur est retirée (`PASSWORD=""`) ; avec une phrase, elle est remplacée par un
  sceau portable `pw1:<itérations>:<sel hex>:<IV hex>:<chiffré hex>` ;
- `RustDeskPortableSeal` porte la cryptographie : PBKDF2 (**HMAC-SHA1**, 120 000 tours) puis AES-GCM
  128 bits de tag, sel de 16 octets et IV de 12 octets neufs à chaque scellement. **Pas de SHA-256 :**
  `PBKDF2WithHmacSHA256` n'existe qu'à partir de l'API 26, et le projet supporte l'API 21 — HMAC-SHA1
  reste un PRF correct malgré les collisions de SHA-1 ;
- l'écriture passe par un tampon (`Utils.exportSettingsToXml` rend un `Sanitized` ou `null`) pour
  vérifier le document **avant** de le poser sur le disque ; l'export est **refusé** si un `ks1:`
  subsiste, si un mot de passe RustDesk n'est ni vide ni portable, ou si un attribut `PASSWORD`
  apparaît dans une forme que le nettoyage ne sait pas lire ;
- à l'import, `restorePortableRustDeskPasswords` demande la phrase, rouvre et rescellle sur
  l'appareil ; ce qui n'a pas été rouvert est retiré par `sealStoredRustDeskPasswords` (même message
  que pour un sceau illisible) plutôt que d'afficher une carte au mot de passe faux ;
- `usablePassword` verrouille une seconde fois : une valeur portable ne doit jamais partir vers
  RustDesk ni s'afficher dans le formulaire, même si le chargement ne l'avait pas encore retirée.

Détail de format qui rend le passage sûr : l'export de `SqliteElement` écrit une ligne par
enregistrement, colonnes en **attributs** (`DatabaseUtils.cursorRowToContentValues` puis
`ContentValuesElement`), et les valeurs sont échappées — un `<` ou un `"` n'apparaît jamais dedans.
Le nettoyage lit donc `PASSWORD="…"` et `CONNECTIONTYPE="6"` sans analyseur XML, et refuse tout
`ks1:` qui subsisterait.

Mesure : **39 tests, 0 échec** (`RustDeskExportSanitizerTest` 15, `RustDeskPortableSealTest` 9,
`RustDeskLaunchChainTest` 7, `RustDeskExportRoundTripTest` 3, `RustDeskSecretCodecTest` 5).
`:dualdesk-app:assembleDebug` passe, et `pw1:` est présent dans `classes2.dex` de l'APK produit —
le code embarqué est bien celui-là, pas seulement celui qui compile.

Ce qui reste non prouvé : l'export et l'import **réels sur un appareil** (le document est reconstitué
en test à partir du format lu dans la bibliothèque, pas produit par une base SQLCipher sous Android),
et les dialogues de phrase de passe — jamais affichés hors téléphone. Le test de bout en bout
`RustDeskExportRoundTripTest` couvre le trajet des données avec une clé AES ordinaire à la place du
Keystore, ce qui valide le format et la composition, pas le magasin de clés d'Android.

Ce qui n'est pas prouvé : le comportement **sur le POCO** (création, modification, relance d'un accès
scellé, et migration d'un accès existant). Le banc JVM couvre le format et l'aller-retour, pas le
Keystore d'un appareil réel ; l'essai est à faire au retour du téléphone.

## Morpheusly retiré du fork (28/10/2026)

Motif : dernier résidu de l'application d'origine. L'icône « Obtenez Morpheusly » occupait un
emplacement **permanent** de la barre d'action de la grille (`showAsAction="always"`), à côté de la
recherche et de l'overflow, pour une fonction que le fork n'utilise pas.

Fait en deux temps, parce que la demande l'a été : d'abord le bouton (diff de deux fichiers), puis la
fonction entière — feuille, layout, icône, chaînes — une fois la suppression complète demandée.

- `grid_view_activity_actions.xml` : l'élément `actionMorpheusly` est retiré (9 entrées → 8) ;
- `ConnectionGridActivity` : le handler `openMorpheusly`, l'import `MorpheuslyBottomSheet` et le
  contournement de teinte (`MenuItemCompat.setIconTintList(morpheusly, null)`, qui n'avait de sens
  que pour cette icône multicolore) disparaissent avec lui ; dans `launchConnection`, la branche
  `ActivityNotFoundException` n'ouvre plus `market://search?q=pub:"Morpheusly"` — le Snackbar
  `no_application_to_handle_vpn` reste, mais l'utilisateur n'est plus renvoyé vers la fiche Play de
  l'application d'origine ;
- fichiers supprimés : `bVNC/.../dialogs/MorpheuslyBottomSheet.java`,
  `res/layout/morpheusly_bottom_sheet.xml`, `res/drawable/ic_morpheusly.xml` ;
- chaînes : `action_morpheusly` et `morpheusly_cta_{title,body,download,dismiss}` retirées de
  **53 fichiers** `values*/strings.xml`, soit 265 lignes (5 par fichier, aucun fichier n'a rien perdu
  d'autre) ;
- `no_application_to_handle_vpn` : la marque disparaît des **53 langues**. Ce n'était pas une
  chaîne à supprimer (le message reste nécessaire quand une connexion VPN n'a pas d'application
  pour la prendre en charge) mais une phrase à réécrire : seule la clause qui nommait l'application
  a été remplacée, par « cette application VPN » dans la langue du fichier, le reste de la
  traduction d'origine étant conservé. Deux exceptions, réécrites entièrement : `gu`, dont la
  phrase était un charabia (« આ connection external app VPN launch set… »), et `pt-rBR`, restée en
  anglais. `es` était tronquée (première phrase seulement) et a été complétée. Piège rencontré :
  dans `bg` et `tr` la valeur commence **à la ligne suivante**, donc une recherche ligne à ligne
  les rate — c'est un `grep` sur le nom de la marque, pas sur `name=`, qui les a rattrapées.

Reste volontairement en place, avec la raison :

- `support@morpheusly.com` (`project_email_support`) : adresse de contact de l'amont, toujours
  fonctionnelle ; la retirer supprimerait un moyen d'écrire à l'auteur du code d'origine ;
- le **paquet** `com.morpheusly.common` (module `common`, `namespace 'com.morpheusly.common'`) :
  homonyme sans rapport avec le bouton, dont le renommage toucherait des dizaines d'`import` dans
  les activités RDP, SPICE et VNC.

Mesure :

- `:dualdesk-app:assembleDebug` : **BUILD SUCCESSFUL**, ressources compilées (c'est le vrai test ici,
  puisqu'une chaîne retirée alors qu'un layout la référence casse `processDebugResources`) ;
- dans l'APK, `aapt2 dump resources` ne contient plus ni `id/actionMorpheusly`, ni
  `string/action_morpheusly`, ni `layout/morpheusly_bottom_sheet`, ni `drawable/ic_morpheusly`, ni
  aucune occurrence de la marque — seule reste l'adresse `support@morpheusly.com` ;
- `aapt2 dump xmltree` sur `res/menu/grid_view_activity_actions.xml` : 8 `item` ;
- écran : AVD `dd-verif` (image android-34 x86_64, `ANDROID_AVD_HOME=D:/Codex/android-tools/avd`,
  donc **rien dans `C:`** ; `-no-window -gpu swiftshader_indirect`, WHPX), APK
  `dualdesk-app-x86_64-debug.apk` installé. Captures
  `android-tools/witness/dualdesk-grille-sans-morpheusly.png` et `dualdesk-menu-sans-morpheusly.png` :
  barre d'action inchangée pour le reste, menu ⋮ à 8 entrées, « New RustDesk access » toujours entre
  « New Connection » et « Help and share », `AndroidRuntime:E` vide.

Toujours pas vérifié sur le POCO : le téléphone était branché mais `adb` le voyait `unauthorized`
(l'invite « Autoriser le débogage USB » n'avait pas été acceptée sur l'appareil). L'émulateur dit que
l'écran se construit et s'affiche ; il ne dit rien du Keystore ni du chemin RustDesk, qui restent à
éprouver sur l'appareil.

Note d'outillage : `./gradlew :dualdesk-app:assembleDebug` avec `JAVA_HOME` sur Zulu 21 suffit, sans
patcher `common/build.gradle` — le fichier tel qu'il est versionné compile. `build-run.sh`, lui, ne
peut pas aboutir : sa vérification cherche `jvmTarget = "21"` (valeur entre guillemets) alors que le
fichier écrit `jvmTarget = 21`, donc il restaure et sort en code 2 avant Gradle. Constat de lecture,
pas d'exécution.

## Publication de la 1.10 (28/10/2026)

- `dualdesk-app/src/main/AndroidManifest.xml` : `versionName` `1.10-dualdesk`, `versionCode` 2
  (la 1.0.0 était en 1). Le suffixe `-dualdesk` est conservé : il fait partie de l'identité affichée,
  pas du numéro.
- Release `v1.10` publiée (https://github.com/Endymi0n74/DualDesk/releases/tag/v1.10), tag poussé sur
  `origin/master`. Assets **signés par la clé de publication** :
  `dualdesk-app-arm64-v8a-release.apk` (62 455 477 o,
  sha256 `305053980eaec6ea20ad21e0d412bd5671aa3e7398c69c940d6b194dad6c4a88`) et
  `dualdesk-app-universal-release.apk` (177 138 551 o,
  sha256 `5982e5a67baf3d5c3959c032d11a1e6e500689f6d95dc2d2160563e1f890bd2e`), copies dans
  `_released/v1.10/`. Les octets téléchargés depuis la page ont été hachés et comparés : identiques au
  disque. Les deux assets signés en débogage, publiés en premier, ont été **supprimés** de la release
  plutôt que laissés à côté — deux fichiers du même nom et de signature différente auraient été un
  piège pour qui télécharge. `_released/` est dans `.gitignore` : 240 Mo de binaires n'ont rien à
  faire dans l'historique.
- `CHANGELOG.md` créé (bilingue, 1.10 puis 1.0.0) ; le README gagne une section « Version and
  releases » / « Version et publications » qui pointe la page de release et le journal.

**Piège majeur, à ne pas repayer : la clé de signature a changé.** `~/.android/debug.keystore` a été
recréé le 28/10 à 20:31 (le dossier `~/.android` lui-même date de 18:14), donc la 1.10 est signée
par un certificat différent de celui de la 1.0.0 publiée :

| | certificat SHA-256 |
|---|---|
| 1.0.0 publiée | `021c5d573307945705af6c66e8cf3c361bec04cb926452efb04cb0c63df70a65` |
| 1.10 | `9277aa137025267495f5e0bf64f89d70aae730875ac586d2666d63ec203e492f` |

Conséquence mesurée, pas supposée : la 1.10 ne s'installe pas par-dessus une 1.0.0
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`), et la désinstallation qui débloque la situation **détruit les
clés du Keystore**, donc les mots de passe RustDesk scellés. C'est écrit dans le README et dans le
journal des versions, avec le conseil d'exporter avec phrase de passe avant.

La clé d'origine est perdue : aucune trace d'un `debug.keystore` ailleurs (ni dans le profil
utilisateur ni sur le disque de travail, vérifié par recherche). Tant que la publication se fait avec
la clé de débogage, **chaque nouvelle machine ou chaque nettoyage du profil casse la mise à jour**.
D'où la clé de publication stable décrite ci-dessous, et la republication de la 1.10 avec elle.

## Cle de publication stable (28/10/2026)

Motif : la 1.0.0 et la 1.10 ont ete signees avec des cles de debogage **differentes**, donc
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` a chaque fois. Publier ainsi, c'est casser la mise a jour a
chaque machine ou a chaque nettoyage du profil.

- cle creee : `_keystore/dualdesk-release.jks` (PKCS#12, RSA 4096, alias `dualdesk`, validite 30 ans
  jusqu'en 2056), empreinte SHA-256 du certificat
  `72:FB:AB:59:83:38:FA:A7:C4:48:FD:65:AA:30:BA:91:39:ED:44:9C:30:E4:64:77:29:C1:B0:86:44:98:B7:85` ;
- `_keystore/keystore.properties` porte le chemin, l'alias et les phrases de passe, tirees au hasard
  (32 caracteres) et ecrites **uniquement** la. Le mot de passe n'a ete affiche ni dans la
  conversation ni dans un journal de build ;
- `_keystore/` est ajoute a `.gitignore`. C'est le point qui compte : une cle de signature dans un
  depot public laisse n'importe qui publier une mise a jour sous ce nom de paquet ;
- `dualdesk-app/build.gradle` lit ces proprietes et les applique au variant `release` seulement. Si
  le fichier manque, la construction aboutit mais les fichiers sortent en `…-release-unsigned.apk`
  et `assembleDebug` reste intact — c'est ce qui permet de compiler un clone neuf sans la cle.
  Verifie en renommant `_keystore/` le temps d'un build : c'est le suffixe qui change, pas le code de
  sortie.

Mesure : `:dualdesk-app:assembleRelease` passe ; `apksigner verify --print-certs` sur
`dualdesk-app-arm64-v8a-release.apk` rend le DN `CN=DualDesk, OU=Release signing, O=DualDesk, C=FR`
et l'empreinte `72fbab59…` — celle du magasin, donc la signature est bien celle qu'on croit. L'APK
x86_64 de release s'installe sur l'emulateur apres desinstallation et demarre :
`onResume of version 1.10-dualdesk_2`.

La 1.10 **publiee** a ensuite ete re-signee avec cette cle et ses deux fichiers remplaces sur la
release, pour que la rupture de signature s'arrete la et pas a la version suivante. Consequence a
connaitre : ceux qui auraient pris la premiere 1.10 (signee en debogage, quelques minutes en ligne)
doivent aussi desinstaller — meme version, signature differente, meme refus. C'est ecrit dans les
notes de version.

Reste ouvert, et c'est le vrai risque : la cle n'existe qu'a un seul endroit sur cette machine. Sans
sauvegarde hors ligne, une panne de disque equivaut a une cle perdue.

Garde de la cle : deux fichiers, une archive chiffree a deux endroits, phrase de passe dans un
gestionnaire. Perdue, elle interdit toute mise a jour future.

## Sauvegarde chiffree de la cle (28/10/2026)

Une cle qui n'existe qu'a un seul endroit n'est pas une cle de publication, c'est une date de peremption.

- archive : `_keystore/backup/dualdesk-keys-2026-10-28.tar.enc`, 4736 octets,
  sha256 `ff9920ebbe50d8db249cca478227336fb299b78262fc728910a9fe38acb182dd`. Elle contient
  `dualdesk-release.jks` et `keystore.properties`, rien d'autre ;
- chiffrement : `openssl enc -aes-256-cbc -pbkdf2 -iter 600000 -md sha512`, phrase de passe passee
  par l'environnement (`-pass env:`) et non par la ligne de commande, pour qu'elle ne traine ni dans
  l'argv des processus ni dans un journal de shell ;
- la phrase de passe est **celle du magasin**, lue depuis `keystore.properties` : l'archive seule ne
  suffit donc pas a ouvrir la cle, ce qui est voulu. Elle doit aller dans un gestionnaire de mots de
  passe, et surtout pas dans le meme dossier ni dans la meme sauvegarde que l'archive.

Restauration **repetee pour de vrai**, pas decrite : dechiffrement dans un dossier temporaire, puis
`keytool -list -v` rend `72:FB:AB:59:…`, identique a la cle en service. Un faux mot de passe est
refuse (verifie aussi : le dechiffrement n'aboutit pas, `tar` ne lit rien). Les commandes exactes
sont dans le README, section « Backup and restore » / « Sauvegarde et restauration ».

Ce qui reste a faire, et qui n'est pas dans mon perimetre : copier l'archive ailleurs que sur ce
disque. Une sauvegarde dans le dossier d'a cote ne protege de rien.

## Controle de signature avant publication (28/10/2026)

`assembleRelease` reussit avec la bonne cle, avec la cle de debogage, ou sans aucune cle : le
fichier s'appelle simplement `…-release-unsigned.apk` dans le dernier cas. La signature etait donc le
seul point qu'aucune verification ne couvrait, alors que c'est exactement ce qui a casse deux fois.

- `_check/verifie_signature.py` lit le certificat avec `apksigner` (le plus recent des build-tools du
  SDK, trouve par `ANDROID_SDK_ROOT` ou `local.properties`) et le compare a l'empreinte de la cle de
  publication, codee en dur. Sans argument il controle les deux fichiers publies ;
- tache Gradle `:dualdesk-app:verifieSignatureRelease` : `dependsOn assembleRelease`, puis execution
  du script, dont le code de sortie fait echouer le build. `PYTHON` permet de designer
  l'interpreteur si `python` n'est pas dans le PATH ;
- la tache reste **hors** de `assembleRelease`, volontairement : un clone neuf sans `_keystore/` doit
  continuer de compiler, et l'erreur doit tomber au moment de publier, pas a chaque build.

Cas eprouves, tous executes :

| Situation | Sortie | Code |
|---|---|---|
| APK signes par la cle de publication | `clé de publication` | 0 |
| APK de debogage passe en argument | `MAUVAISE CLÉ (9277aa13…)` | 1 |
| build fait sans `_keystore/` (dossier renomme) | `NON SIGNÉ (clé absente pendant le build)` | 1 |
| chemin inexistant | `ABSENT` | 1 |
| interpréteur qui sort en 1 (faux `PYTHON`) | `BUILD FAILED` | 1 |

Le dernier cas est celui qui compte pour la tache Gradle : sans lui, on saurait seulement que le
script sait echouer, pas que le build le remarque. Un premier essai de test — remplacer les APK de
release par ceux de debogage — n'a rien prouve : `dependsOn assembleRelease` les a reconstruits
avant le controle. C'est un comportement correct, mais il faut le savoir avant de conclure d'un
« BUILD SUCCESSFUL » que le controle a vu quelque chose.

Piege d'affichage retrouve : la console Windows en cp1252 rend les accents en « � » ; le script
bascule `sys.stdout` en UTF-8 au demarrage, comme le recommande la note d'outillage du README.


## Script de publication (28/10/2026)

`publish-release.sh` (racine) : contrôles d'avant-vol, `:dualdesk-app:assembleRelease`, contrôle de
signature, copie dans `_released/<tag>/`, notes de version (section du journal + tableau des
SHA-256), tag annoté, poussée du tag, `gh release create`. La version est lue dans le manifeste :
ce qui est publié est ce qui a été compilé. `--dry-run` s'arrête avant le tag ; `--skip-build`
reprend avec les APK déjà produits — utile quand `gh release create` a échoué, puisque le tag est
alors déjà en ligne et qu'une nouvelle exécution le retrouve sur HEAD (mode reprise).

Trois pièges mesurés cette fois :

- **`gh` ne vise pas ce dépôt.** Dans `ardp/`, `gh repo view` répond
  `iiordanov/remote-desktop-clients` : la présence du remote `upstream` fait choisir l'amont au CLI.
  Tous les appels portent donc `-R "$DEPOT"`, `$DEPOT` étant lu dans l'URL de `origin`, et le script
  vérifie le droit d'écriture (`gh api repos/<depot> --jq .permissions.push`). Sans cela, le contrôle
  « la release existe-t-elle déjà ? » interrogeait le mauvais dépôt.
- **`gh repo view` n'accepte pas `-R`** (le dépôt y est un argument positionnel) : d'où `gh api`.
- **`import verifie_signature` écrivait `_check/__pycache__/`**, ce qui salissait l'arbre et faisait
  échouer le contrôle suivant du même script. L'empreinte est désormais lue dans la source par
  expression régulière, et `__pycache__/` / `*.pyc` sont dans `.gitignore`.

L'environnement par défaut ne suffit pas : `JAVA_HOME` désigne un JRE 11, qui ne compile pas une
cible Java 21. Le script cherche un JDK au moins 21 (`JAVA_HOME`, `/usr/lib/jvm/*21*`,
`/c/Program Files/Zulu/zulu-21`, …) et refuse de continuer s'il n'en trouve pas.

Éprouvé sans rien publier : arbre sale, `_keystore/` absent, origin injoignable, commit absent de
`origin/master`, tag déjà posé sur un autre commit, reprise quand le tag est sur HEAD, APK signé en
débogage (arrêt avant toute copie), et chemin nominal en `--dry-run --skip-build` (notes, tailles,
SHA-256). Non éprouvé : la création effective de la release GitHub, qui n'a pas été lancée.

## Décision : ce dépôt est le produit (28/10/2026)

Deux dépôts portent le nom DualDesk et se confondaient dans les liens comme dans les versions :
`ardp` (celui-ci) et `dualdesk` (réécriture Compose). Le partage est désormais écrit, et il est
partout : README des deux dépôts, ici.

- **`ardp` livre.** C'est ici que les versions sont construites, signées par la clé de publication
  et publiées. C'est aussi ici, et non dans le banc, que l'export emporte les mots de passe RustDesk
  scellés derrière une phrase de passe (l'export de `dualdesk` ne contient aucun secret, par choix).
  Les liens « partage » et « signaler un bug » pointent donc ici (`project_url_share`,
  `project_url_bug_reports`), pas vers `dualdesk`. **Vérifié** : les deux scellent sous Keystore
  (`KeystoreVault.kt` dans `dualdesk`), donc ce n'est pas le scellement qui distingue les deux, mais
  la capacité de l'export à traverser d'un appareil à l'autre.
- **`dualdesk` est le banc d'essai.** Public, AGPL-3.0, FreeRDP 3.x : il mesure la voie GFX/H.264,
  le canal CLIPRDR et la session RustDesk embarquée. Il ne publie aucune version stable.
- **Seul le savoir traverse, pas le code** : faire entrer du code AGPL-3.0 dans ce fork GPLv3 en
  change la licence. Reporter une mesure, une cause racine ou un piège documenté, oui ; copier un
  fichier, seulement si on décide de passer le fork en AGPL-3.0.
- Conséquence pratique déjà rencontrée : les deux applications s'appellent `DualDesk` à l'écran et
  cohabitent sur le téléphone (paquets `com.dualdesk.rdp` et `com.dualdesk`). Une capture d'écran ne
  dit pas laquelle est ouverte — la vérifier par le paquet (`adb shell dumpsys activity | grep`).

### Renommage et visibilité (28/10/2026)

- GitHub : `ardp` → **`DualDesk`** (public), `dualdesk` → **`DualDeskOld`** (privé). Ordre
  obligatoire : renommer `dualdesk` **d'abord**, sinon le nom `DualDesk` reste pris (les noms de
  dépôt sont insensibles à la casse chez un même propriétaire).
- **Collecte de casse a verifier dans les deux sens** : GitHub ne distinguant pas la casse, l'URL
  `github.com/Endymi0n74/dualdesk` ne redirige **pas** vers `DualDeskOld` — elle sert ce dépôt-ci,
  `DualDesk` (vérifié : HTTP 200, titre du fork). Tout ancien lien en minuscules pointe donc
  désormais vers le fork, pas vers le banc. `.../DualDeskOld` anonyme renvoie bien 404 (privé).
- `origin` des deux clones locaux a été réécrit en conséquence. Le **répertoire de travail reste
  `D:/Codex/ardp`** : `build-run.sh`, `publish-release.sh` et les mémoires y réfèrent par chemin, un
  renommage du dossier casserait tout sans rien apporter. `publish-release.sh` lit le dépôt dans
  l'URL d'`origin`, il suit donc le renommage sans modification.
- **Adresse personnelle retirée de l'historique (28/10/2026, après la mise en public).** Onze
  commits de la queue portaient l'auteur (et le committer) avec l'adresse personnelle, et les deux
  tags annotés la portaient en tagger. Les onze commits ont été **rejoués** avec l'identité noreply
  du compte (`102302606+Endymi0n74@users.noreply.github.com`), `git commit-tree` conservant arbre,
  message et dates ; les tags `v1.0.0` et `v1.10` ont été recréés sur leurs nouvelles cibles avec la
  même date de tagger. `master` et les deux tags ont ensuite été poussés en force.
  - **Vérifié** : arbre de la tête identique avant/après (`a7797364…`), messages et 11 dates
    d'auteur identiques, aucune adresse dans les métadonnées de `master` ni des tags ; l'API GitHub
    renvoie la noreply sur les dix derniers commits ; les deux releases gardent leurs deux assets.
  - **Non vérifiable / hors de portée** : les anciens commits restent joignables par leur SHA chez
    GitHub un certain temps, et une copie déjà clonée ou un miroir peut les garder. Le nettoyage
    local est complet (reflogs expirants + `gc --prune=now`, ancien commit absent du dépôt).
- Le reste a été vérifié : aucun keystore, aucun `.env` réel (le `.env` suivi contient la ligne
  `docker/.env` de l'amont), aucune adresse IP réelle (`192.0.2.10` est du TEST-NET), et les 21
  fichiers de `preuve-poco/` ne contiennent ni identifiant ni bureau réel.

## Cœur RustDesk embarqué, absorbé depuis `dualdesk` (28/10/2026)

Le banc `dualdesk` (aujourd'hui supprimé) portait une chose que le fork n'avait pas : un cœur
RustDesk **dans son processus**. Il est maintenant ici, dans le fork, et `dualdesk` a disparu.

- Ce qui est venu du banc : la recette `scripts/compile-rustdesk-android.sh` (copiée telle quelle,
  avec ses dix pièges commentés), le `.so` déjà compilé, et le **contrat JNI** — les symboles sont
  `Java_com_dualdesk_rdp_RustDeskNative_*`, donc le paquet `com.dualdesk.rdp` est imposé : le
  renommer donnerait un `UnsatisfiedLinkError` à la première session.
- Ce qui a été écrit ici : la façade Kotlin (`RustDeskNative`), la session et le rendu
  (`RustDeskEmbeddedActivity`, Java, sans coroutines — le fork n'en a pas), le point d'entrée
  `RustDeskEmbedded`, et le câblage dans `ConnectionGridActivity.launchRustDesk` : embarqué d'abord,
  report par Intent **en repli** si la bibliothèque est absente.
- La bibliothèque **n'est pas versionnée** (32 Mo). Elle passe par
  `-Pdualdesk.rustdeskLib=<chemin>`, copiée dans `build/rustdeskJni` (dossier généré, jamais `src/`),
  et ce dossier est une source set `jniLibs`. Sans la propriété, l'APK est celui de la 1.10.
- **Vérifié** : `assembleDebug` passe avec le cœur ; `lib/arm64-v8a/librustdesk.so`
  (32 868 352 octets) dans l'APK ; activité présente dans le manifeste fusionné ; classes dans
  `classes2.dex` ; et les **16** méthodes `external` de la façade correspondent aux **16** symboles
  exportés (`llvm-nm -D`). APK annoncé `com.dualdesk.rdp` `1.11-dualdesk` `versionCode 3`.
- **Non vérifié** : aucune session réelle ouverte depuis le fork (POCO débranché, et un émulateur
  x86_64 ne peut pas charger une bibliothèque arm64). Gestes, rendu et reprise après perte de
  surface restent à confirmer sur appareil.
- **Piège de build payé au passage** : Kotlin compilait en cible 17 alors que `compileOptions`
  demande 21, ce qui faisait refuser la compilation par Gradle ; `build-run.sh` contournait avec
  `-D kotlin.jvm.target.validation.mode=warning`, drapeau qui ne s'applique pas comme prévu. C'est
  réglé à la racine (`subprojects { ... jvmTarget JVM_21 }`). Deuxième piège : le JDK — la cible 21
  exige un JDK 21, et `JAVA_HOME` pointait vers un JRE/JDK 17.
- **Licence** : le cœur est AGPL-3.0 (`LICENCE-RustDesk.txt`). Une build qui l'embarque est
  distribuée sous AGPL-3.0 **dans son ensemble** ; sans le cœur, elle reste GPLv3. C'est la première
  fois que le fork change de licence selon la build : à dire clairement au moment de publier.

### Publication de la 1.11 — 29/10/2026

La 1.11 est publiée (`https://github.com/Endymi0n74/DualDesk/releases/tag/v1.11`), tag sur
`17802f6c`, deux APK signés de la clé de publication : arm64-v8a 75 797 212 o
(`5fe11ef7…`) et universel 190 480 273 o (`9d7f7099…`).

- **Le piège évité** : `publish-release.sh` compilait sans `-Pdualdesk.rustdeskLib`. Les notes
  extraites du journal annonçaient le cœur embarqué, et les APK ne l'avaient pas — une release
  signée, installable, et fausse sur son point principal. Le script accepte maintenant
  `--rustdesk-lib <fichier>` ou `DUALDESK_RUSTDESK_LIB`, passe la propriété à Gradle et **relit
  l'APK** (`zipfile`, entrée `lib/arm64-v8a/librustdesk.so`) avant de copier quoi que ce soit. Il
  écrit aussi dans les notes si le cœur est présent ou non, au lieu de le supposer.
- **Repère de taille** : le `.so` pèse 32 868 352 o et l'APK arm64 passe de 62,4 Mo (1.10) à
  75,8 Mo. Un arm64 autour de 62 Mo est une build sans cœur.
- `assembleRelease` complet : 1 min 48 à chaud, cinq splits plus l'universel.
- La licence de la release est dite explicitement (AGPL-3.0 pour cette build), la mention n'est
  pas laissée au seul journal.

### Session POCO du 29/10 — ce que la 1.11 publiée ne fait pas

Deux défauts trouvés en installant l'APK arm64 publié sur le POCO (Android 16, arm64-v8a).

- **Le cœur ne se charge pas.** `nativeloader` : `dlopen failed: cannot locate symbol
  "_ZNSt6__ndk122__libcpp_verbose_abortEPKcz" referenced by librustdesk.so`. Le cœur est compilé
  avec le NDK 29 (libc++ 17+), l'APK embarque la `libc++_shared.so` des prébuilt FreeRDP
  (1 027 408 o, plus ancienne, sans ce symbole). Conséquence muette : l'application retombe sur le
  report vers RustDesk installé et la release annonce une session embarquée qu'elle ne fait pas.
  Correctif : `-Pdualdesk.libcxxShared=<libc++ du NDK 29>` (copie dans `build/rustdeskJni`,
  `pickFirst` pour `lib/arm64-v8a/libc++_shared.so`). Vérifié sur l'appareil : le symbole est là,
  `RustDeskEmbeddedActivity` s'ouvre et négocie. `publish-release.sh` refuse désormais de publier
  un cœur sans `--libcxx`.
- **Le cœur ignore les serveurs privés.** `setOption` existait dans le JNI mais n'était appelé
  nulle part : le cœur interrogeait `rs-ny.rustdesk.com` et répondait « target device is offline »
  pour un pair enregistré ailleurs. Ajout d'une entrée « Serveur RustDesk » (adresse + clé) et de
  l'appel avant `connect`. Piège : `setOption` doit être appelé **même avec une valeur vide** — le
  cœur persiste ses options dans son dossier et l'ancien serveur survit sinon à l'effacement du
  champ.
- **Où le test s'est arrêté.** Le serveur privé du pair (`82.64.x.x:21116`) ne répond plus
  (« test nat: Failed to connect »), y compris depuis le PC. Basculé temporairement sur le serveur
  public : le trou est percé, TCP s'établit, puis `Handshake failed: invalid public key from
  rendezvous server` — le service RustDesk lit son propre profil et garde l'ancienne clé. Le mot de
  passe permanent du pair est chiffré dans sa configuration, donc inaccessible.
  **Rendu, gestes et reprise après perte de surface restent non vérifiés.**
- Machine laissée en l'état : configuration RustDesk restaurée à l'identique, service installé puis
  retiré, accès RDP du téléphone recréé à la main (la désinstallation de la 1.0.0 signée en debug
  détruit la base ; la sauvegarde `tar` des données est dans le dossier temporaire de la session).
- **APK de la `v1.11` remplacés (29/10).** Les deux actifs de la release ont été reconstruits avec
  le cœur et la libc++ du NDK 29, puis remplacés (`gh release upload --clobber`) ; les notes font
  foi avec les nouvelles empreintes.
  - `arm64-v8a` : 75 950 278 o, `0a373e42aae322f5897321fe4bbe14611b7ee69310eab2dbc0550329e723d921`
  - `universal` : 190 633 348 o, `c9a15ec8b85a01cf8df77d4bb6fa54d7fa7078df9c62abf3718433db7b3add44`
  - Vérifié après coup par téléchargement : l'empreinte du fichier servi est celle des notes.
  - `versionCode` reste 3 : la build se réinstalle par-dessus la précédente sans désinstallation.
- **La libc++ du NDK doit être détourée avant d'entrer dans l'APK.** La copie des `jniLibs` passe
  par `copieRustDeskLib`, donc Gradle ne la `strip` pas : le fichier du NDK fait 9 290 184 o et
  gonfle l'APK de ~2 Mo compressés. Version détourée conservée hors dépôt :
  `D:\Codex\dualdesk-artifacts\rustdesk\libc++_shared.so` (1 374 336 o, `--strip-unneeded`),
  le symbole `__libcpp_verbose_abort` y reste défini (ils sont dynamiques).
- **Piège `gh` dans ce dépôt :** `gh` résout tout seul et choisit le remote `upstream`
  (`iiordanov/remote-desktop-clients`) ; `gh release list` y répond `RELEASE-v6.4.9`. Toujours
  passer `--repo Endymi0n74/DualDesk`, et attention à la syntaxe : `upload` et `edit` n'acceptent
  que le tag en positionnel (`gh release upload v1.11 --repo … --clobber …`).
- **Correctif commité et publié (29/10).** Commit `97c5b2d6` (dix fichiers : cœur, libc++, entrée
  « Serveur RustDesk », script de publication, journal, mémoire), poussé sur `origin/master`. Le tag
  `v1.11` a été recréé sur ce commit et poussé en force : GitHub répond bien
  `v1.11 -> commit 97c5b2d6`, et les deux actifs de la release gardent leurs empreintes
  (`0a373e42…`, `c9a15ec8…`). Déplacer un tag ne touche pas les actifs attachés à la release, mais
  il faut `git push --force origin refs/tags/v1.11` — un `push` ordinaire est refusé.
- **Actifs de la `v1.11` remplacés une seconde fois, avec les gestes (29/10).** `publish-release.sh`
  ne sait pas republier une version déjà sortie : il s'arrête sur « la release $TAG existe déjà »
  et, le tag ne désignant plus `HEAD`, sur « le tag existe déjà et désigne … pas HEAD ». Le
  remplacement se fait donc à la main, dans cet ordre : `./gradlew :dualdesk-app:assembleRelease
  -I /d/Codex/tmpshot/init.gradle -Pdualdesk.rustdeskLib=… -Pdualdesk.libcxxShared=…` (JDK 21),
  `python _check/verifie_signature.py` sur les deux APK, `gh release upload v1.11 --clobber`, puis
  `gh release edit v1.11 --notes-file _released/v1.11/notes.md`.
  - `arm64-v8a` : 75 950 627 o, `198ae3165bd71e7ebd9337424ac7cec7df30f672ea3b2d7619c6b6be201dfef7`
  - `universal` : 190 633 656 o, `1fd116ebd091bac1d25cc09e5a77ed9ca89a723fab3453976b3c001e8282e295`
  - Vérifié par téléchargement : le fichier servi a bien cette empreinte ; le dex contient
    `press type=` (donc le correctif et son journal), `librustdesk.so` est présent, la libc++ est
    celle du NDK (1 374 336 o).
  - Le tag `v1.11` a été recréé sur `74efc2c7` (correctif des gestes + note de journal) et poussé
    en force : la release désigne le code qui a produit les fichiers.
  - `_released/` n'est pas versionné (`.gitignore`) : seuls `CHANGELOG.md` et `memory.md` portent
    la trace dans le dépôt.
- **Le masque souris n'est pas un numéro de bouton (29/10).** C'était la cause des gestes inertes :
  l'activité envoyait `1` puis `2`, que le cœur interprète comme *down* et *up* **sans bouton**
  (`mask & 0x7` = type, `mask >> 3` = bouton). Un clic gauche est donc
  `MOUSE_TYPE_DOWN | MOUSE_BUTTON_LEFT << 3` = `9`, puis `10` ; le droit, `17` puis `18`. Le poste
  accepte ces messages vides et n'en fait rien — aucune erreur nulle part, d'où le diagnostic par
  lecture du code du cœur (`common.rs`, `input_service.rs`) plutôt que par les journaux.
  Correctif dans `RustDeskEmbeddedActivity` : constantes exactes, appui gauche posé seulement quand
  le geste est connu (toucher bref = clic, glissement = glisser avec le bouton gauche, appui long =
  clic droit), bouton relâché sur `ACTION_CANCEL`, et appui dans la bande noire ignoré au lieu d'être
  ramené sur le bord du bureau.
- **Vérification des gestes bloquée par le poste, pas par l'application (29/10).** Le client
  s'authentifie bien (les infos du pair arrivent, donc `LoginResponse` est passé), mais aucune trame
  n'arrive : le journal du service du PC répète
  `Error of monitor0 service: Failed to copy screen to Windows buffer`, précédé de
  `HDR DuplicateOutput1 failed: hr=0x80070005` avec `gdi: false`. Le service voit `session id 1` —
  la session RDP depuis laquelle le poste est utilisé — et n'a pas le droit de capturer son bureau.
  Redémarrer le service ne change rien (contrairement à la panne précédente) : **il faut le poste à
  la console**, ou RustDesk en mode utilisateur dans la session RDP, pour revoir des trames.
  Piège utile : sur cette machine le journal du service n'est pas dans le profil local habituel mais
  dans `C:\Windows\ServiceProfiles\LocalService\AppData\Roaming\RustDesk\log\server\`, et il faut
  l'élévation pour le lire.
- **Gestes vérifiés sur le bureau de la console (29/10).** Poste à la console et déverrouillé :
  l'appui simple ouvre le menu Démarrer (14,7 % des pixels de la bande changent), l'appui long
  ouvre le menu contextuel de la barre des tâches, `ESC` ferme les deux (donc le clavier passe
  aussi), et le survol suit le doigt à l'endroit visé. Le journal de l'activité confirme la
  séquence émise, deux lignes par geste :
  - appui simple : `press type=1 button=1` puis `type=2 button=1`, même point ;
  - appui long : `type=1 button=2` puis `type=2 button=2` — clic droit seul, aucun appui gauche
    parasite avant ;
  - glissement : `type=1 button=1` au point de départ (`1049,602`), `type=2 button=1` au point
    d'arrivée (`1678,854`), les mouvements entre les deux n'étant pas journalisés.
- **Deux limites du poste, pas du client.** L'écran de verrouillage est un bureau sécurisé : les
  appuis y sont ignorés (aucun pixel ne bouge sur le champ PIN), comme le veut RustDesk quand le
  service n'a pas la main dessus. Et une session **RDP** ne peut être ni capturée ni pilotée par le
  service : `DuplicateOutput1 hr=0x80070005` pour l'image, `input_service.rs:1377 "Accès refusé"`
  pour chaque appui. Ces deux erreurs sont donc des diagnostics de session, pas de régression
  applicative — et le journal de l'activité, lui, continue de montrer les appuis émis.
- **Reste à faire :** rendre le scellement des accès RustDesk compatible d'un appareil à l'autre
  (phrase de passe à l'export), et vérifier réellement les gestes de la session embarquée sur le
  POCO — bloqué jusqu'ici par le serveur privé injoignable, puis par la capture du poste.

## Session embarquée : établie et authentifiée, toujours sans image (29/10)

- **APK de la release installé tel quel.** Téléchargé depuis `v1.11`, empreinte identique à celle
  des notes (`0a373e42…`), `adb install -r` passe ; `librustdesk.so` (32,8 Mo) et
  `libc++_shared.so` sont bien dans l'archive. Rien à redire du produit publié.
- **Une clé de serveur orpheline casse tout le rendez-vous public.** Vider le champ « Serveur » ne
  suffit pas : la clé du serveur privé reste dans le champ « Clé », le cœur s'en sert pour vérifier
  l'identité du pair, et chaque tentative meurt sur `Handshake failed: invalid public key from
  rendezvous server`, même une fois le trou percé. En y inscrivant la clé publique des serveurs
  RustDesk — celle que le cœur embarque déjà : `OeVuKk5nlHiXp+APNn0Y3pC1Iwpwn44JGqrQCsWqmBw=` — la
  poignée de main passe (`Connection secured`).
  **Défaut à corriger :** les deux champs sont indépendants ; avec un serveur vide, la clé devrait
  être ignorée. Aujourd'hui une clé oubliée suffit à rendre le mode public inutilisable, et le
  message affiché ne parle pas de la clé.
- **Jusqu'où va la session, mesuré :** rendez-vous sur `rs-ny`, punch TCP/UDP/WebRTC, `Connection
  secured`, `peer info supported_encoding`, `Start client clipboard loop` — l'authentification est
  donc acceptée par le pair. **Aucune trame** pour autant : le HUD affiche `0x0 — 0 fps — 0 frames`
  en continu, et rien ne dessine le bureau distant.
- **Piste écartée : la demande de flux partait trop tôt.** `requestDisplay` n'était appelé qu'une
  fois, quand `state()` passe à 2 — or ce 2 est posé dès que le socket est établi
  (`set_connected`), donc avant l'authentification, et une demande reçue si tôt n'est pas honorée.
  Correctif dans `RustDeskEmbeddedActivity.pumpFrames` : la demande est refaite toutes les 2 s tant
  qu'aucune trame n'est arrivée. Reconstruit, réinstallé, retesté — **toujours 0 trame** à ce
  moment-là. Le correctif reste juste (la première demande course bien l'authentification) mais
  **ce n'était pas la cause** : voir plus bas.
- **La vraie cause était le processus hôte périmé.** Après un redémarrage de l'hôte fait avec
  l'élévation — arrêt de tous les `rustdesk.exe`, `--password` sur la configuration publique, puis
  `Start-Service rustdesk` — les trames arrivent immédiatement : `new video handler for display #0,
  format: AV1`, `create AV1 decoder success`, `remote desktop: 1920x1080`, `Set fps to 30`, et le
  HUD passe à `1920x1080 — 15 fps — 171 frames` puis continue. L'application de session gardait
  donc bien en mémoire l'ancien serveur et l'ancien mot de passe ; elle ne se recharge pas tant
  qu'elle tourne.
- **Rendu vérifié.** Le bureau Windows distant s'affiche, redimensionné dans la bande utile de
  l'écran (échelle `min(cw/iw, ch/ih)` : 1920x1080 tient dans 1220x686, bande de y=1013 à y=1699
  sur un écran de 2712 px de haut). Tout appui hors de cette bande est ignoré : c'est ce qui a fait
  croire à une panne d'entrée au premier essai.
- **Reprise après perte de surface vérifiée.** Écran éteint puis rallumé : la même activité reprend,
  le compteur garde ses trames (3163) et l'image se redessine sans reconnexion. Le `lockCanvas` par
  trame et le `unlockCanvasAndPost` protégé suffisent donc à survivre à la destruction de la
  surface.
- **Gestes : toujours non concluants.** Des appuis et un glissement dans la bande utile (bouton
  Démarrer distant, barre des tâches, glissement au centre) ne produisent **aucune trame nouvelle**
  et aucun changement visible côté poste. `sendMouse` est bien appelé avec les coordonnées
  converties, sans erreur ; soit l'événement n'atteint pas le poste, soit il y est ignoré.
  À reprendre : `mouse()` du pont appelle `send_mouse(mask, x, y, …)` — vérifier la convention de
  masque attendue par le cœur (down/up séparés) et l'état d'autorisation de la session.
- **Côté poste, ce qui a été vu.** Sans service, le pair n'écoute qu'en UDP 21119 et rien n'arrive ;
  `--install-service` et `--password` exigent l'élévation. Le socket qui accepte la session
  appartient à l'**application de session**, lancée avant le changement de mot de passe : elle
  garde l'ancien en mémoire et `taskkill` est refusé (autre contexte de sécurité), donc la relancer
  demande aussi l'élévation. La configuration RustDesk du poste a été restaurée à l'identique après
  l'essai — les trois `.toml` sont identiques à la sauvegarde, octet pour octet.
- **Pièges de compilation à ne pas repayer :**
  - `JAVA_HOME` doit pointer sur le JDK 21 (`D:\Codex\tools\jdk-21`) ; en 17, `:common` échoue sur
    `invalid source release: 21`.
  - `lintVitalAnalyze*` et `lintVitalReport*` plantent sous JDK 21 (`Unexpected lint invalid
    arguments`). Un script d'init qui désactive les tâches `lintVital*` règle le problème ; `-x`
    échoue dès qu'une tâche n'existe pas dans un module.
  - `COMPILE_API` attend un nom de plateforme (`android-36`) mais `TARGET_API` un **nombre** (36).
    Avec `android-36` des deux côtés, l'APK se construit mais l'installation est refusée :
    `INSTALL_FAILED_OLDER_SDK: Requires development platform android-36`.
  - `libc++_shared.so` se reprend directement dans l'APK publié (`unzip -j … lib/arm64-v8a/`) au
    lieu du NDK : c'est exactement le fichier qui a compilé le cœur.

**Rendu et reprise après perte de surface vérifiés ; gestes non concluants.**

- **État laissé sur le poste :** configuration RustDesk basculée sur les serveurs publics, mot de
  passe permanent remplacé par un mot de passe temporaire, service RustDesk installé et démarré.
  Sauvegarde de la configuration d'origine (serveur privé + mot de passe d'origine) :
  `D:\Codex\tmpshot\rd-backup\` — la restaurer remet tout en état, mais casse la session publique.

**Rendu et reprise vérifiés avec l'APK publié ; gestes vérifiés aussi.**

## APK publié de la 1.11 installé tel quel : gestes vérifiés (29/10)

- **Installation depuis la release, pas depuis la sortie de build.** `gh release download` du seul
  `arm64-v8a`, empreinte `198ae3165bd71e7ebd9337424ac7cec7df30f672ea3b2d7619c6b6be201dfef7`
  conforme aux notes, puis `adb install -r` en 4,7 s par-dessus la build locale : `versionCode` 3
  inchangé, donc les accès, les mots de passe scellés et le serveur enregistré survivent.
- **Session ouverte depuis la carte de la grille** (le carré de droite), pas par `am start` du
  manifeste : `new video handler for display #0, format: AV1`, `remote desktop: 2560x1440`,
  `Set fps to 30`, HUD `2560x1440 — 29 fps — 356 frames`. Poste à la console, donc les trames
  arrivent — le blocage venait bien de la session RDP, jamais du client.
- **Bande utile :** image 2560x1440 à l'échelle 0,4766 dans 1220x2712, soit y=1013..1699. Un appui
  hors de cette bande n'est pas converti du tout (c'est voulu) ; convertir une pression sur l'écran
  vers le bureau plutôt que l'inverse demande de retrancher la bande *avant* de diviser.
- **Témoin objectif, à refaire ainsi.** Le poste piloté est la session de la console elle-même
  (celle où tourne l'agent), donc PowerShell peut y relever la fenêtre au premier plan (classe +
  titre) et la position du curseur : cela sépare « l'application a émis » de « le poste a appliqué ».
  Les deux petits scripts de relevé sont restés hors dépôt, dans `D:\Codex\tmpshot\`.
- **Appui simple** (bouton Démarrer, 130,1669 côté téléphone) : journal `press type=1 button=1` puis
  `type=2 button=1` au **même** point (20,1399) ; 22 % de la bande changent, le poste passe au
  premier plan sur la surface shell « Rechercher » et le curseur se pose en 19,1398 ; `ESC` rend le
  foyer à Firefox.
- **Appui long** (barre des tâches, 429,1680) : `type=1 button=2` puis `type=2 button=2`, aucun
  appui gauche parasite avant ; le poste ouvre la « Liste de raccourcis » de l'application survolée
  (900,1399).
- **Glissement** de 610,1032 à 477,1251 : `type=1 button=1` en (1280,40), `type=2 button=1` en
  (1000,499) — un seul appui, tenu pendant tout le trajet — et le poste se comporte comme pour un
  vrai glisser (glissement d'onglet dans la barre de titre) : un simple clic au départ n'aurait rien
  produit de tel. Le curseur finit en 1000,497, à deux pixels du point de relâchement.
- **Reprise après perte de surface :** écran éteint puis rallumé, la même activité revient au premier
  plan et le compteur passe de 2 509 à 3 397 trames sans reconnexion.
- **Pièges de mesure, à ne pas repayer :**
  - `input swipe x y x y 900` produit bien un appui long ; mais sur la barre des tâches il tombe sur
    l'application survolée, donc la différence de pixels seule (0,8 %) ne dit pas grand-chose — le
    titre de la fenêtre au premier plan, si.
  - Le POCO se reverrouille après extinction : `NotificationShade` au premier plan, et une capture
    pendant l'extinction est noire. `input keyevent 224` réveille sans toucher au verrou.
  - Chercher un élément par `uiautomator dump` puis `grep` : le XML tient sur **une** ligne, donc
    découper sur `>` avec `tr` ne marche pas — parser les `<node …>` en Python.

## 1.12 : README réécrit et release publiée par le script (29/10)

- **Le README est passé de 732 à 204 lignes.** Il mélangeait quatre documents : présentation, manuel
  de signature, recette de compilation, et la documentation entière de l'amont recopiée après un
  « # Intro ». Il ne garde que le parcours d'un utilisateur — ce que c'est, installer, ce qu'elle
  fait, construire, publier, licence — en français puis en anglais, et renvoie à `docs/`.
- **Le détail opérationnel a déménagé dans `docs/PUBLICATION.md`** (clé, sauvegarde, contrôle de
  signature, chaîne `publish-release.sh`, pièges de `gh`). Avant d'écrire un nouveau document, regarder
  `docs/` : `RUSTDESK-NATIF.md` portait déjà le détail du cœur, c'est le même partage.
- **La documentation de l'amont n'est plus recopiée**, seulement liée. Elle n'est pas à nous et
  vieillissait ici.
- **Publication faite par le script, comme prévu** : `publish-release.sh --dry-run` construit,
  contrôle la signature et laisse les APK dans `build/outputs/apk/release` ; `--skip-build` les
  réutilise et ne fait que la copie, le tag et la release. C'est le chemin le plus court quand la
  compilation n'a pas à être rejouée — 50 s de construction incrémentale, un seul build.
  - `cœur` : `D:\Codex\dualdesk-artifacts\rustdesk\librustdesk.so` (32 868 352 o) et
    `libc++_shared.so` (1 374 336 o), passés par `DUALDESK_RUSTDESK_LIB` et `DUALDESK_LIBCXX`.
- **Release `v1.12`** : tag sur `2d138df6` (= `HEAD` de `master`), 
  `dualdesk-app-arm64-v8a-release.apk` 75 950 636 o `301603c2f69a6edda0e97c50945b5fec83c2292f0e32278a1b8fedf53104ab6a`
  et `dualdesk-app-universal-release.apk` 190 633 686 o `d28b489976acf5dbc0ea0dd3d08f13cfd593289c24d10055552f7692af5196cd`.
  Vérifié après coup : l'APK arm64 retéléchargé porte l'empreinte des notes, `aapt2 dump badging`
  répond `versionCode='4' versionName='1.12-dualdesk'`, et les deux fichiers natifs sont bien dans
  l'archive (`librustdesk.so` 32 868 352 o, `libc++_shared.so` 1 374 336 o).
- **1.12 ne change aucun comportement** : le code est celui de la 1.11 corrigée, seul le
  `versionCode` bouge (3 → 4) pour que les appareils distinguent les deux builds. Le dire dans les
  notes évite qu'un utilisateur cherche la nouveauté fonctionnelle.
- **Rendu vérifié sur GitHub** : la page du dépôt et celle de la release affichent bien les titres,
  le tableau des fichiers et les liens relatifs vers `docs/`.
