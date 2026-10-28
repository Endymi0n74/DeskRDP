# DualDesk sur base aRDP — rapport d'état

Rapport d'une campagne d'essais sur appareil (POCO, MIUI) et sur émulateur, du 26 au 28 octobre 2026.
Il rend compte de ce que le fork apporte réellement, de ce qui est prouvé et de ce qui ne l'est pas,
et des décisions qui restent à prendre. Il se lit avec [memory.md](memory.md), qui porte le détail des
mesures, et [_check/](_check), qui porte les outils qui les ont produites.

Les faits ne sont retenus ici que s'ils ont été **observés sur l'appareil ou mesurés**. Quand une
vérification n'a pas été faite, c'est écrit.

---

## 1. Ce que le fork apporte aujourd'hui

### 1.1 Nature et base technique

Fork de `iiordanov/remote-desktop-clients` (GPLv3, v6.4.9, versionCode 116490), cloné le 26/10/2026.
Le but n'est pas de réécrire un client : c'est de garder le client RDP mature du projet amont
(FreeRDP 2.11.7, RDPGFX, décodage H.264 par MediaCodec) et d'y greffer la partie RustDesk.

**La bonne surprise de la campagne** : l'archive de dépendances du projet amont
(`remote-desktop-clients-libs-17.tar.gz`, 171 Mo) contient les **bibliothèques natives FreeRDP déjà
compilées** pour les quatre ABI, plus les sources Java de la glue. Autrement dit, **aucun NDK et
aucune compilation C ne sont nécessaires** pour produire l'application. C'est ce qui a rendu la
campagne possible en une soirée plutôt qu'en une semaine.

Trois corrections ont été indispensables pour que la construction aboutisse, toutes documentées dans
[memory.md](memory.md) :

| Correction | Ce qu'elle évite |
|---|---|
| `local.properties` avec le chemin du SDK | Échec Gradle « SDK location not found » après le téléchargement complet |
| Ajout de l'AAR SQLCipher 4.5.4 dans `common/aars` | `:common:generateDebugRFile` échoue : `common/build.gradle` attend un AAR absent de l'archive |
| `glyph-cache` dans `BookmarkBase`/`LibFreeRDP` | L'archive (mars 2025) est plus ancienne que le code : `PerformanceFlags.setGlyphCache` n'existe pas dans la glue livrée |

### 1.2 L'application DualDesk

Le module `dualdesk-app` reprend le code et les ressources de `freeaRDP-app`, avec
`applicationId = com.dualdesk.rdp`. Ce suffixe n'est pas décoratif : `Utils.isRdp()` déduit le
protocole d'une application **de son nom de paquet**, donc un identifiant sans les lettres « rdp »
ferait basculer l'application sur un autre protocole.

- **Nom et icône** : l'application s'installe et démarre sous le nom **DualDesk**, avec une icône
  adaptative teal (`#0E6E6E`) et son avant-plan vectoriel, aussi déclarée pour l'API 21 à 25.
  Vérifié à l'écran : tuile teal, libellé « DualDesk ».
- **Langue** : les textes de la greffe ne sont plus écrits en dur ; ils vivent dans
  `values/strings.xml` et `values-fr/strings.xml`, et l'application suit la langue du téléphone.
  Sur l'appareil en français, le menu dit « Nouvel accès RustDesk », les champs « Identifiant
  RustDesk » et « Mot de passe (facultatif) », et le titre d'accueil « DualDesk : client de bureau à
  distance ».
- **Barre d'état** : en thème clair, l'horloge et la batterie restaient blanches sur fond blanc.
  Corrigé dans `ConnectionGridActivity`, vérifié dans les deux thèmes.
- **Version** : `1.0.0-dualdesk` installée sur l'appareil.

### 1.3 Les accès RustDesk

C'est l'apport proprement dit. Le type de connexion `CONN_TYPE_RUSTDESK = 6` est ajouté aux types
existants, **sans changement de schéma** : l'identifiant d'appareil tient dans la colonne `ADDRESS`
et le mot de passe dans `PASSWORD`. Deux classes nouvelles portent le contrat et le lancement
(`bVNC/src/main/java/com/dualdesk/rustdesk/RustDeskContract`, `…RustDeskLauncher`), et la grille des
accès reçoit une entrée de menu, le cycle de vie complet et l'aiguillage du lancement.

Chaque point ci-dessous a été exercé sur l'appareil le 27/10 entre 18:46 et 18:54 :

| Fonction | État réel | Preuve |
|---|---|---|
| Entrée de menu | présente, deuxième position, entre « Nouvelle connexion » et « Aide et partage » | `preuve-poco/01-menu-rustdesk.png` |
| Création | boîte à deux champs, l'accès apparaît sur **une seule ligne** (plus de `:0` ni d'identifiant répété) | `02`, `03`, `04` |
| Modification | appui long → « Modifier » ; le formulaire se rouvre **pré-rempli** (et non l'écran de configuration RDP) ; la valeur modifiée survit à un `force-stop` | `05`, `06`, `07`, `08` |
| Suppression | appui long → « Supprimer », confirmation, la grille revient à son état vide — qui survit au redémarrage | `10`, `11`, `12` |
| Passage de relais | appui sur la carte → **deep link** `rustdesk://<identifiant>`, RustDesk passe au premier plan et **traite** l'identifiant | `09` |

Le point important du relais, c'est la dernière colonne : RustDesk ne s'est pas contenté de
s'ouvrir, il a reçu l'identifiant et a répondu par une erreur de connexion — le résultat attendu pour
un identifiant fictif. La modification d'un accès a été vérifiée jusque-là : après passage de
`987654321` à `555000111`, l'erreur renvoyée porte la **nouvelle** valeur.

**Un défaut trouvé en exerçant ce chemin** : sans l'élément `<queries>` du manifeste, le filtrage de
visibilité d'Android 11+ rend RustDesk invisible (`getPackageInfo` lève, `resolveActivity` renvoie
`null`), et le fork annonçait « RustDesk n'est pas installé » alors qu'il l'était. Déclaré depuis
dans `dualdesk-app/src/main/AndroidManifest.xml`. Ce défaut ne se voyait ni à la compilation ni à la
lecture : il fallait installer RustDesk et appuyer sur une carte.

**Le mot de passe n'est plus écrit en clair.** Il occupait la colonne `PASSWORD` telle quelle, et
cette base n'est chiffrée que si le mot de passe maître de l'application est activé : sur une
installation par défaut, une sauvegarde `adb` ou un appareil rooté le lisait. Il est désormais scellé
par une clé AES-GCM du Keystore Android (`ks1:<iv>:<chiffré>`, IV neuf à chaque sceau), sans dépendre
de ce réglage. Les accès créés avant le correctif restent lisibles — leur valeur en clair est scellée
au premier chargement de la grille — et si le Keystore refuse, l'accès n'est pas enregistré plutôt que
de l'être en clair, avec un message qui le dit. Cinq tests JVM couvrent le format : aller-retour, deux
sceaux différents, mauvaise clé, enregistrement altéré, ancienne valeur en clair.

### 1.4 Rien ne renvoie plus vers l'application d'origine

L'écran « ⭐ Aide, évaluer, partager » pointait encore vers le projet amont : forum, dépôt des bogues,
versions précédentes, recherche Play sur l'auteur, adresse de contact, et le bouton Partager copiait
la fiche Play d'un paquet DualDesk **qui n'existe pas**.

La structure retenue évite de modifier les autres applications du dépôt : les cibles sortantes sont
des ressources déclarées dans `bVNC` avec les valeurs amont, et **surchargées par la variante** dans
`dualdesk-app/src/main/res/values{,-fr}/strings.xml`. Une valeur vide veut dire « cette application
ne l'a pas » : le bouton correspondant est masqué, au lieu de mener ailleurs. Pour DualDesk, les
bogues et le partage pointent vers son propre dépôt ; forum, versions, « Plus d'applications » et
adresse de contact sont vides, donc absents.

Vérifié sur l'APK (`aapt2 dump resources`) : les valeurs de la variante gagnent bien sur celles de
`bVNC`. Restent des textes d'aRDP **inatteignables dans cette variante** (dialogue de bienvenue jamais
montré, libellés de fonctionnalités pro, libellés de don) — sans effet visible, listés dans
[memory.md](memory.md) pour ne pas les chercher deux fois.

### 1.5 Le gestionnaire de mots de passe, neutralisé — et pourquoi le correctif évident ne marche pas

Créer un accès se terminait par une **feuille système** proposant d'enregistrer l'identifiant et le
mot de passe de la machine distante. Le défaut touchait la boîte RustDesk *et* le formulaire RDP.

Le correctif naturel — marquer les deux champs « ne pas utiliser pour la saisie automatique » — **ne
suffit pas**. Mesuré avec le journal du framework au niveau `verbose` : les champs restent suivis
(`setTrackedViews`, `trackedIds=[…]`) et le service reçoit encore le formulaire
(`PasswordSaveInfoFactory: Form has a username and is not a password only form`). Ce qui décide, c'est
`AutofillManager.cancel()` : partout où il est appelé, `setSaveUiState` n'apparaît jamais.

Deux mesures ont été nécessaires, et la seconde a mis au jour un chemin que personne n'avait vu :

- **Le formulaire RDP s'enregistre aussi par le retour arrière**, via `onPause()`, sans passer par
  « Enregistrer ». L'annulation posée dans le seul `saveConnectionAndCloseLayout()` ne couvrait donc
  pas ce chemin, et la feuille y revenait. Le correctif appelle maintenant l'abandon du contexte à
  l'enregistrement **et** dans `onPause()`.
- La démonstration repose sur des **identités de paquet neuves** à chaque essai : appuyer une fois sur
  « Jamais » dans la feuille inscrit un refus par nom de paquet chez le service, qui **survit à la
  désinstallation et à la réinstallation** et rend muets tous les essais suivants. C'est le piège
  central de cette campagne.

| Code | Sortie de l'écran | Feuille |
|---|---|---|
| témoin : ni exclusion, ni annulation | bouton « Enregistrer » | **présente** |
| exclusion de la racine + annulation | bouton « Enregistrer » | absente |
| exclusion de la racine + annulation (avant le second correctif) | retour arrière | **présente** |
| correctif complet : `onPause` annule aussi | retour arrière | absente |
| correctif complet | bouton « Enregistrer » | absente |

Dans chaque essai l'accès est bien créé : la neutralisation ne coûte pas l'enregistrement.
Preuves : `preuve-poco/13` (feuille présente avant correctif), `14` (témoin sur identité neuve),
`15` (boîte RustDesk, après correctif), `16` (formulaire RDP, témoin), `18` (état final).

### 1.6 Outillage de mesure

Les essais ne dépendent plus d'une lecture manuelle : les parcours pilotent l'interface, forcent
l'encodage UTF-8 et **relèvent les marqueurs du journal** avant de conclure.

- `_check/flow_rustdesk.py` — parcours complet d'un accès RustDesk (création, modification,
  suppression, relais) avec verdict.
- `_check/flow_rdp_access.py` — le même parcours pour le formulaire RDP, avec les marqueurs
  d'enregistrement et de saisie automatique.
- `_check/install_apk.py` — franchit la confirmation MIUI qui bloque `adb install`.
- `_check/tap_text.py`, `_check/dismiss_sheet.py`, `_check/delete_rustdesk_access.py`,
  `_check/dexcheck.py` — appui par texte, écartement de la feuille par « Pas maintenant », nettoyage
  des accès, inspection d'un APK.

Les pièges qu'ils neutralisent sont consignés dans [memory.md](memory.md) : la hiérarchie de vues qui
peut être **antérieure** à la frappe ou rester d'un écran précédent, les coordonnées qui ne valent
plus après l'ouverture du clavier, et la rotation d'écran qui fait tomber un appui hors de la cible —
un essai qui « ne montre aucune feuille » peut n'être qu'un essai qui n'a rien fait.

### 1.7 État du dépôt

Le travail est committé sur `master`, en local : `8d04ad5` pour le formulaire RDP neutralisé,
`e01b1e0` pour l'identité DualDesk, la greffe RustDesk, le module `dualdesk-app` et l'AAR SQLCipher,
puis la mémoire du projet et ce rapport.

Le dépôt distant est le **projet amont** (`iiordanov/remote-desktop-clients`), donc rien n'a été
poussé et rien ne pouvait l'être : le fork vit pour l'instant sur ce disque. Reste non suivi :
`bVNC/libs`, deux jars que rien ne référence (les classes correspondantes sont suivies en source dans
`common/src/main/java`) et qui n'ont pas été supprimés parce que l'archive de dépendances qui les a
posés n'est plus sur le disque.

---

## 2. Les points ouverts, sans détour

**RustDesk absent au début de la campagne → aujourd'hui installé et éprouvé.** C'était le premier
point ouvert : au premier essai, l'application annonçait correctement « RustDesk n'est pas installé
sur cet appareil » et il n'y avait rien à mesurer. RustDesk 1.5.0 a depuis été installé sur l'appareil
(après activation de l'installation par USB dans les options développeur — MIUI refusait sinon
l'installation avec `INSTALL_FAILED_USER_RESTRICTED`), et **tout le cycle de la greffe a été exercé
avec lui en place**. Ce point est clos, preuve à l'appui.

**Les chemins de repli sont exercés sur banc, pas encore sur le téléphone.** Le lanceur prévoit trois
stratégies : le deep link, l'activité explicite, et le presse-papiers. Jusqu'ici seul le **deep link**
avait tourné — il aboutit à chaque essai, donc les deux replis ne se déclenchaient jamais. L'ordre des
tentatives a été sorti du code Android (`RustDeskLaunchChain`, sans import Android) et exercé par sept
tests JVM, avec un transport factice qui enregistre la séquence des appels : **7 tests, 0 échec**. La
promesse est donc vérifiée pour la logique, et un défaut en est sorti — le message annonçait
« identifiant copié » même lorsque le presse-papiers était indisponible, corrigé par un troisième état
et sa chaîne (anglais et français).

Ce qui reste non prouvé : que RustDesk installé **accueille** l'intent d'activité. Le banc dit que
DualDesk essaie ce qu'il annonce, dans l'ordre annoncé, et rend compte de ce qu'il obtient ; l'autre
bout de la chaîne ne se voit que sur l'appareil, une fois le téléphone rebranché.

**Le paquet réel ne peut plus servir de témoin pour la saisie automatique.** Le refus mémorisé par le
gestionnaire de mots de passe pour ce nom de paquet rend la feuille muette : la démonstration repose
sur des identités de paquet neuves. Pour la revoir sur le paquet réel, il faudrait retirer DualDesk de
la liste des applications refusées du gestionnaire.

---

## 3. Ce qui reste à faire ou à décider

**La question du GFX : témoin côté poste, puis banc émulateur — verdict du 28/10/2026.** La mesure de bout en
bout du projet voisin montrait des trames reçues et un débit réel, sans pouvoir dire **qui** ne fait
pas de GFX : l'appareil, la négociation, ou le poste lui-même. Le témoin a été fait : les deux
clients de référence officiels ont été installés sur le téléphone, pointés sur la même machine, avec
le canal graphique activé côté client.

- **aFreeRDP 3.32.1 (moteur FreeRDP 3.x), journal au niveau DEBUG** : le client annonce **onze** jeux
de capacités graphiques (8.0 à 10.7) ; le poste **confirme RDPGFX 10.7** (`0x000A0701`, indicateur
AVC à 0) ; il crée ensuite une surface de 1220 × 2530, la mappe sur la sortie et l'alimente sans
interruption — **de 159 à 167 commandes de surface par seconde** sur onze secondes consécutives
(1 787 au total), en **CLEARCODEC** (1 774 commandes) et **CAPROGRESSIVE** (13), donc **sans
H.264**.
L'image du bureau distant s'affiche et se rafraîchit.
- **aRDP 6.4.9 (moteur FreeRDP 2.11.7)** : l'authentification NLA passe, la session s'ouvre, le
client charge le canal dynamique `rdpgfx`, puis son journal signale une PDU qu'il n'interprète pas
(`PDU_TYPE_DATA not properly parsed, 562 bytes remaining unhandled`) — et **l'image arrive quand
même** : le bureau s'affiche, en bandeau (le bureau distant est en 21:9, le client le loge dans ses
1220 × 2530 avec du noir au-dessus et en dessous). Le rythme mesuré entre deux images est dans la
seconde. Autrement dit, ce client rend l'écran par le chemin classique.

**Conclusion du témoin : le poste fait bien du GFX.** Il annonce le niveau de capacités le plus
récent (10.7) et remplit la surface à ~160 commandes par seconde, en CLEARCODEC et CAPROGRESSIVE —
sans H.264.

**Confusion à lever au passage** : le fork `com.dualdesk.rdp` embarque **FreeRDP 2.11.7**
(`libfreerdp2.so`), pas FreeRDP 3. L'observation « 8 jeux de capacités annoncés, aucune réponse du
poste en 25 s, `SCALEDMAP_DISABLE` » vient de l'application **autonome du projet voisin**
(`com.dualdesk.debug`, `libfreerdp3.so`), qui n'est plus installée. Les deux ne doivent pas être
mêlées dans une même conclusion.

**Le fork fait du GFX — mesuré en session, sur banc émulateur (28/10/2026).** Le doute « le client du
fork ne fait pas de GFX » est levé par une mesure, pas par une lecture de code. Le fork a été
installé sur un émulateur Android 34 x86_64 — ses `.so` FreeRDP 2.11.7 sont publiés pour les quatre
ABI, donc l'APK universel y tourne — et connecté au même poste, journal `logcat` en DEBUG, après
saisie du mot de passe dans la fenêtre de l'émulateur.

Ce que le client raconte en s'ouvrant :

```
CONNECTION_STATE_NEGO → NLA → MCS_CONNECT → MCS_ATTACH_USER → MCS_CHANNEL_JOIN
→ LICENSING → CAPABILITIES_EXCHANGE → FINALIZATION → CONNECTION_STATE_ACTIVE
Loading Dynamic Virtual Channel rdpgfx
```

Trois points de méthode, parce qu'ils avaient égaré les essais précédents :

- `rdpgfx` est un canal **dynamique** : il n'apparaît pas dans les lignes `loading channelEx` du
démarrage, donc leur absence ne prouve rien ;
- le GFX est une **case du profil** — `LibFreeRDP` n'ajoute `/gfx` que si `flags.getGfx()`, et
  `/gfx:AVC444` que si `flags.getH264()` ; l'éditeur les expose (« RDP Gfx », « RDP Gfx H264 ») et
  `ConnectionBean` les met à vrai à la création ;
- la sonde MediaCodec du démarrage n'est pas le chemin réel : elle échoue sur l'émulateur
  (`AMediaCodec_configure failed: -10000`) alors que la même configuration décode ensuite sans
  erreur.

| Réglage du profil | Ce que le poste envoie | Mesure | Bureau affiché |
|---|---|---|---|
| `RDP Gfx` seul | tuiles progressives (`ProgressiveTileFirst` → `ProgressiveFrameEnd`) | 0,66 img/s au repos (7 en 10,6 s) ; **16 img/s** en mouvement (7 en 0,4 s, 621 tuiles) | oui |
| `RDP Gfx` + H264 | `H264_METABLOCK` (AVC444) | **31,7 metablocs/s** (322 en 10,2 s), aucune erreur de décodeur | oui |

Aucun `Error while processing GFX cmdId` dans les deux cas.

**Ce que cela déplace.** L'écart constaté sur le téléphone — GFX qui ne s'engage pas, image par le
chemin classique — **ne se reproduit pas** sur un banc neuf. Ni le client du fork ni le poste ne sont
en cause : les deux font du GFX dès que le profil est réglé pour. La cause est donc **côté
appareil** : case du profil différente sur le téléphone, particularité de son MediaCodec, ou
arbitrage de session. Le prochain essai n'est plus un changement de moteur mais la même lecture de
journal sur le POCO, et deux lignes suffisent à trancher : `Loading Dynamic Virtual Channel rdpgfx`
et `H264_METABLOCK`. Présentes, la panne est en aval du décodage ; absentes, le profil du téléphone
n'a pas les mêmes cases cochées.

Deux limites de mesure, pour ne pas conclure au-delà de ce qui est prouvé :

- le journal d'aRDP est figé au niveau **INFO** : on voit qu'une PDU graphique n'est pas interprétée,
  mais pas quelles capacités il a annoncées ni lesquelles le poste a confirmées — impossible de dire
  s'il s'est rabattu faute d'accord ou après l'échec de lecture ;
- les journaux graphiques **du poste** (`Rdp-Graphics-RdpAvenc`, `Rdp-Graphics-RdpLite`) sont vides :
  la machine ne journalise pas ses échanges GFX, tout le verdict repose donc sur le journal du
  client — ce qui est la bonne source, puisque c'est lui qui reçoit. L'extrait exploitable est
  conservé sous `preuve-poco/temoin-afreerdp-extrait.txt`, et le rendu d'aRDP (recadré sur la fenêtre
  de contrôle, sans contenu du bureau) sous `preuve-poco/temoin-ardp-rendu.png`.

Un détail d'exploitation relevé au passage : l'arbitrage de session Windows coupe la session
sortante dès que la console reprend la main (constaté une fois, au bout de onze secondes). Toute mesure faite depuis le
téléphone vers ce poste pendant que la console est utilisée doit donc être courte, ou le poste doit
être laissé au clavier pendant l'essai.

**La décision de fond : fork continu, délégation, ou emprunt ciblé.** Les trois options sont
désormais documentées par des faits, pas par des intentions :

- **Fork continu** — le code est là, il se construit sans NDK grâce aux `.so` précompilés, et
  l'identité comme la greffe RustDesk sont en place. Le coût, c'est de suivre un projet amont en
  GPLv3 et de porter ses correctifs ; le bénéfice, c'est un client unique où RDP et RustDesk vivent
  côte à côte.
- **Délégation** — garder une application qui gère les profils et **remet la session** à RustDesk
  installé, exactement comme la greffe actuelle le fait pour RustDesk. C'est le chemin déjà prouvé
  pour RustDesk, et il ne demande aucun travail de suivi d'amont pour cette partie.
- **Emprunt ciblé** — ne reprendre du fork que ce qui est démontré utile (identité, greffe, et le
  client RDP tel qu'il est : son moteur FreeRDP 2.11.7 fait du GFX, c'est mesuré), sans en faire un
  produit à maintenir. Passer à FreeRDP 3.x n'est plus une condition : c'était une piste fondée sur
  l'hypothèse, aujourd'hui démentie, que le moteur 2.x ne savait pas faire de GFX.

C'est cette décision qui commande la suite : chacune des trois implique un travail différent sur les
points ci-dessous.

**Faire accueillir les stratégies de repli par l'appareil.** La logique est désormais testée (cf. § 2) ;
il reste à provoquer un repli réel sur le téléphone, en désactivant temporairement le filtre du deep
link, pour voir RustDesk accepter l'activité — c'est le seul maillon que le banc ne peut pas couvrir.

**Le mot de passe RustDesk est scellé, mais le sceau est lié à l'appareil** (cf. § 1.3). La clé ne
quitte pas le Keystore : une réinstallation, une restauration sur un autre téléphone, ou un
export/import d'accès — l'export écrit la base entière — transporte le sceau sans la clé, et le mot
de passe devient illisible (l'accès s'ouvre alors sans mot de passe, à ressaisir). À trancher :
refuser d'exporter un mot de passe scellé, l'exporter en l'annonçant à l'import, ou accepter la perte
et le documenter pour l'utilisateur.

**Trois résidus d'identité, sans effet visible** mais vérifiables : le bouton **Morpheusly** de la
barre d'action, qui fait la promotion d'un produit tiers du même auteur ; l'étiquette de carte qui
porte un **saut de ligne final** (le rendu tient sur une ligne, c'est cosmétique) ; et les
traductions autres que l'anglais et le français, qui gardent les libellés d'origine pour l'écran
d'aide — des étiquettes, pas des liens.

**Les tests automatisés couvrent une seule logique, et c'est assumé.** La chaîne de repli du lanceur a
sept tests JVM (cf. § 2), premier source set de test du dépôt. Le reste — écrans, greffe, identité —
n'est couvert que par les parcours `_check`, qui mesurent l'application installée : c'est le bon outil
pour ces défauts, mais rien ne protège d'une régression à la compilation. À trancher : étendre le même
banc aux autres logiques pures (formulaire d'accès, validation des champs), ou garder la mesure comme
seule vérification ailleurs.

---

## 4. Refaire la mesure

```bash
export JAVA_HOME=<un JDK 21>      # le java du PATH était un 1.8, ce qui échoue
export GRADLE_USER_HOME=<un dossier Gradle dédié>
./gradlew :dualdesk-app:assembleDebug
```

Les tests de la chaîne de repli se lancent sans appareil ni réseau (JUnit est déjà dans le cache) :

```bash
./gradlew :bVNC:testDebugUnitTest --tests "com.dualdesk.rustdesk.*" --console=plain --offline
```

La sortie est `dualdesk-app/build/outputs/apk/debug/dualdesk-app-arm64-v8a-debug.apk` (67 Mo, cinq
APK sont produits, un par ABI et un universel). L'installation sur l'appareil passe par
`_check/install_apk.py`, qui franchit la confirmation MIUI.

Les essais d'interface se font ensuite avec les parcours de `_check/`, et les clichés publiés vivent
dans [preuve-poco/](preuve-poco) — douze images du cycle RustDesk, sept de la campagne sur la saisie
automatique. Les autres clichés de la campagne montrent le bureau réel de la machine de mesure et
restent hors du dépôt.

### Rejouer la mesure GFX sans le téléphone (banc émulateur)

Le fork tourne sur émulateur : l'APK **universel** embarque ses `.so` FreeRDP 2.11.7 pour les quatre
ABI (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`). Mise en place, une seule fois :

```bash
export JAVA_HOME=<un JDK 21>       # le JAVA_HOME du poste est un JRE 11 : sdkmanager refuse de démarrer
export ANDROID_SDK_ROOT=<le SDK Android>
sdkmanager --install "system-images;android-34;default;x86_64"
avdmanager create avd -n dualdesk-test -k "system-images;android-34;default;x86_64" -d pixel_6
emulator -avd dualdesk-test -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect
adb install -r dualdesk-app/build/outputs/apk/debug/dualdesk-app-universal-debug.apk
```

Puis, dans l'éditeur d'accès du fork, vérifier « RDP Gfx » (et « RDP Gfx H264 » selon le mode
voulu), ouvrir la session et saisir le mot de passe une fois dans la fenêtre — il n'est pas
transférable depuis le téléphone (base chiffrée par SQLCipher, clé du Keystore propre à l'appareil).

Le relevé se fait ensuite sans interaction :

```bash
adb logcat -c && sleep 10 && adb logcat -d > mesure.txt   # pendant la session
# puis compter, avec leurs horodatages :
#   "Loading Dynamic Virtual Channel rdpgfx"   -> le canal GFX est monté
#   "ProgressiveFrameEnd" / "H264_METABLOCK"   -> les images arrivent
```

Sous Git Bash, exporter `MSYS_NO_PATHCONV=1` avant `adb pull` / `uiautomator dump` : les chemins
`/sdcard/...` sont sinon réécrits en chemins Windows.
