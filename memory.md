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
- Le **bouton Morpheusly** de la barre d'action (toujours visible) fait la promotion d'un produit
  tiers du même auteur. Ce n'est pas aRDP ; laissé en place faute de demande.
- Les traductions autres que l'anglais et le français gardent les libellés d'origine (par exemple
  le titre du menu « évaluer/partager » en espagnol) : ce sont des étiquettes, pas des liens.
- Les deux stratégies de repli (activité, presse-papiers) ne sont **pas** exercées : elles ne se
déclenchent que si le deep link échoue, ce qui n'est pas le cas sur cette version.
- Un accès RustDesk est désormais **modifiable et supprimable** (éprouvé le 26/10 à 22:23) :
  l'appui long sur la carte propose « Modifier » et « Supprimer » seulement, sans l'option
  raccourci d'écran d'accueil qui n'a pas de sens sans hôte ni port ; « Modifier » rouvre le
  formulaire pré-rempli au lieu de l'écran de configuration RDP. Une carte RDP garde ses trois
  options : aucune régression.
- Aucun accès RustDesk de démonstration ne subsiste sur le téléphone : celui des essais a été
  supprimé, la liste ne contient que le modèle `:3389` fourni par aRDP.
- Le mot de passe RustDesk est rangé dans la base commune, protégée seulement si le mot de passe
  maître est activé.
- Icône, nom d'application et traductions restent ceux d'aRDP (`icon_ardp`, chaînes en anglais).
- Aucun test automatisé propre au fork.

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
