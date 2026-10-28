# ardp — mémoire du fork DualDesk

Fork de [iiordanov/remote-desktop-clients](https://github.com/iiordanov/remote-desktop-clients) (GPLv3),
cloné le 26/10/2026 (v6.4.9, versionCode 116490). But : garder le client RDP mature (FreeRDP 2.11.7,
RDPGFX + H.264 MediaCodec) et y greffer la partie DualDesk — les accès **RustDesk**.

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

Pour DualDesk : bogues et partage pointent vers `https://github.com/Endymi0n74/dualdesk` (+ `/issues`) ;
forum, versions précédentes, « Plus d'applications » et adresse de contact sont vides, donc absents.
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
- Release `v1.10` publiée (https://github.com/Endymi0n74/ardp/releases/tag/v1.10), tag poussé sur
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
