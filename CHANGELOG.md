# Journal des versions / Changelog

Les versions publiées sont sur la page [Releases](https://github.com/Endymi0n74/DualDesk/releases).
Published builds live on the [Releases](https://github.com/Endymi0n74/DualDesk/releases) page.

## 1.15 — 30 octobre 2026

### Français

- **L'appui long ne laisse plus le bouton gauche enfoncé.** Il l'enfonçait dès la détection de
  l'appui long, et le maintenait tant que le doigt restait posé : sur le poste, un doigt immobile
  une seconde suffisait à démarrer un glisser-déposer involontaire. Le bouton n'est désormais
  enfoncé qu'au premier vrai mouvement, à l'endroit où le doigt s'était posé.
- **L'appui long devient le clic droit.** Relâché sans avoir bougé, il envoie un clic droit ; jusque-là
  un doigt ne pouvait pas ouvrir un menu contextuel, il fallait la barre de touches supplémentaires
  ou une souris.
- **Le glissement fonctionne comme avant** pour qui enchaîne appui long puis déplacement, avec le
  bouton gauche tenu d'un bout à l'autre — mais il n'est enfoncé qu'au moment où le doigt glisse.
- **Mesuré sur un téléphone**, avec une fenêtre témoin sur le poste qui journalise les événements
  reçus : appui bref → `down/up button=Left`, appui long → `down/up button=Right` sans aucun `Left`
  parasite, appui long puis glissement → `down button=Left`, `drag`, `up button=Left`.
- `versionCode` 6 → 7. Le reste est inchangé : ni les accès, ni la reconnexion, ni le presse-papiers.

### English

- **A long press no longer leaves the left button held.** It used to press it as soon as the long
  press fired, and keep it pressed while the finger rested: on the host, a finger still for a
  second was enough to start an accidental drag-and-drop. The button is now only pressed on the
  first real movement, at the point where the finger first landed.
- **A long press now gives a right click.** Released without moving, it sends a right click; until
  now a finger could not open a context menu at all, it took the extra-keys bar or a mouse.
- **Dragging works as before** for anyone who holds then moves, with the left button held
  throughout — but it is pressed only when the finger starts to move.
- **Measured on a phone**, with a witness window on the host logging the events it receives: brief
  tap → `down/up button=Left`, long press → `down/up button=Right` with no stray `Left`, long press
  then drag → `down button=Left`, `drag`, `up button=Left`.
- `versionCode` 6 → 7. Everything else is unchanged: accesses, reconnection, clipboard.

## 1.14 — 30 octobre 2026

### Français

- **Reconnexion automatique.** Une session coupée n'affiche plus un message puis ne quitte plus :
  elle est relancée toute seule, à 2 s, 5 s, 10 s, 20 s puis 30 s — cinq essais en tout, chacun
  annoncé par un message disant quand la tentative suivante aura lieu. Un Wi-Fi qui saute ou un
  poste qui redémarre ne demandent donc plus de revenir sur la grille et de rouvrir l'accès.
- **Le compteur repart à zéro quand la session est vraiment revenue**, à la première image reçue du
  poste — pas au moment où la relance est lancée. Une tentative qui échoue à son tour consomme
  l'étape suivante du barème.
- **Une première connexion ratée garde son message immédiat** : cinq essais sur un mot de passe faux
  n'aideraient personne. Seules les relances consomment le budget.
- **Mesuré sur un téléphone en coupant Wi-Fi et données mobiles** : les cinq relances tombent à
  2,0 / 5,0 / 10,0 / 20,0 / 30,0 s dans le journal, puis l'application revient à la grille au
  cinquième échec. Les cinq essais couvrent donc une coupure d'une minute environ ; au-delà, il faut
  rouvrir l'accès à la main.
- `versionCode` 5 → 6. Aucun autre changement : ni les accès, ni les gestes, ni le presse-papiers,
  ni l'affichage ne bougent.

### English

- **Automatic reconnection.** A dropped session no longer shows a message and quits: it is restarted
  on its own — 2 s, then 5, 10, 20 and 30 s, five attempts in all, each announced by a message
  saying when the next one will happen. A Wi-Fi hiccup or a rebooting host no longer means going
  back to the grid and reopening the access by hand.
- **The counter resets only when the session is really back**, on the first frame received from the
  host — not when the retry is launched. A retry that fails in turn consumes the next step.
- **A first connection that fails keeps its immediate message**: five attempts against a wrong
  password would help nobody. Only retries consume the budget.
- **Measured on a phone with Wi-Fi and mobile data switched off**: the five retries land at
  2.0 / 5.0 / 10.0 / 20.0 / 30.0 s in the journal, then the app returns to the grid after the fifth
  failure. The five attempts therefore cover an outage of about a minute; past that, the access has
  to be reopened by hand.
- `versionCode` 5 → 6. Nothing else changes: accesses, gestures, clipboard and rendering are
  untouched.

## 1.13 — 29 octobre 2026

### Français

- **Retour au RDP seul.** DualDesk redevient ce qu'il était au départ : un fork d'aRDP. Toute la
  partie RustDesk est retirée — le cœur natif, l'activité de session embarquée, la chaîne de repli
  par deep link puis Intent, le scellement des mots de passe, l'export à phrase de passe qui
  n'existait que pour eux, la documentation, les scripts de compilation et les tests. Environ
  2 300 lignes en moins, et plus aucune bibliothèque native propriétaire dans l'APK.
- **Les accès RustDesk enregistrés sont supprimés** de la liste au premier lancement : leur type de
  connexion n'existe plus, les garder aurait laissé des entrées qui n'ouvrent rien. Le nombre
  d'entrées retirées va au journal, pas à un message — il n'y a rien à faire pour l'utilisateur.
- **Licence de retour en GPLv3** : le cœur RustDesk des 1.11 et 1.12 était en AGPL-3.0 et imposait
  cette licence à toute la build. Sans lui, l'application reprend la licence de l'amont.
- **Les 51 traductions sont nettoyées** des chaînes propres à RustDesk et des phrases d'aide
  VPN qui nommaient l'ancien produit.
- **README réécrit** en une page : ce qu'est l'application, comment l'installer, ce qu'elle fait,
  ses limites, la construction, la publication — en français puis en anglais. Il documente
  franchement la limite qui compte : **RDP n'ouvre qu'un bureau, il n'y a pas de choix de l'écran
  du poste**.
- `versionCode` 4 → 5. Les accès RDP, leurs réglages et le presse-papiers sont inchangés.

### English

- **Back to RDP only.** DualDesk is again what it started as: a fork of aRDP. The whole RustDesk
  side is gone — the native core, the embedded session activity, the deep-link-then-Intent fallback
  chain, the sealed passwords, the passphrase export that existed only for them, the documentation,
  the build scripts and the tests. About 2,300 lines fewer, and no proprietary native library left
  in the APK.
- **Recorded RustDesk accesses are dropped** from the list on first launch: their connection type no
  longer exists, and keeping them would have left entries that open nothing. The number of removed
  entries goes to the log, not to a message — there is nothing the user could do about it.
- **Licence back to GPLv3**: the RustDesk core of 1.11 and 1.12 was AGPL-3.0 and forced that licence
  on the whole build. Without it, the application returns to the upstream licence.
- **The 51 translations are cleaned** of RustDesk-only strings and of the VPN help sentences naming
  the former product.
- **README rewritten** to one page: what the application is, how to install it, what it does, its
  limits, how to build it, how to publish it — in French, then in English. It states the limit that
  matters plainly: **RDP opens one desktop, there is no host screen selection**.
- `versionCode` 4 → 5. RDP accesses, their settings and the clipboard are unchanged.

## 1.12 — 29 octobre 2026

### Français

- **README réécrit.** Il tenait 732 lignes et parlait de quatre choses à la fois : présentation,
  manuel de signature, recette de compilation, et la documentation entière de l'amont recopiée après
  un « # Intro ». Il tient désormais en une page — ce qu'est l'application, comment l'installer, ce
  qu'elle fait et ses limites, la construction, la publication — en français puis en anglais.
- **Le détail opérationnel a déménagé** dans `docs/PUBLICATION.md` : clé de publication et son
  empreinte, sauvegarde et restauration de l'archive chiffrée, contrôle de signature, chaîne
  `publish-release.sh`, et les pièges de `gh` (remote `upstream`, tag déplacé, actifs remplacés). Il
  rejoint `docs/RUSTDESK-NATIF.md`, qui portait déjà le détail du cœur embarqué.
- **La documentation de l'amont n'est plus recopiée**, seulement liée : elle n'est pas la nôtre et
  vieillissait ici sans que personne ne la mette à jour.
- **`docs/RUSTDESK-NATIF.md` dit maintenant ce qui est vérifié** : rendu, gestes et reprise après
  perte de surface l'ont été sur le POCO le 29 octobre, avec l'APK publié et un poste Windows à sa
  console — l'ancien texte les annonçait comme non mesurés.
- **Aucun changement fonctionnel** : le code est celui de la 1.11 corrigée, donc les gestes, les
  accès existants, leurs mots de passe scellés et l'export avec phrase de passe se comportent à
  l'identique. `versionCode` 3 → 4.

### English

- **README rewritten.** It ran to 732 lines and covered four things at once: presentation, signing
  manual, build recipe, and the entire upstream documentation copied in after an “# Intro”. It is now
  one page — what the application is, how to install it, what it does and its limits, how to build it,
  how to publish it — in French, then in English.
- **Operational detail moved out** into `docs/PUBLICATION.md`: the publication key and its fingerprint,
  backup and restore of the encrypted archive, the signature check, the `publish-release.sh` chain,
  and the `gh` traps (the `upstream` remote, moved tags, replaced assets). It joins
  `docs/RUSTDESK-NATIF.md`, which already held the embedded core's detail.
- **Upstream documentation is linked, no longer copied**: it is not ours and it aged here without
  anyone refreshing it.
- **`docs/RUSTDESK-NATIF.md` now states what has been verified**: rendering, gestures and recovery
  after surface loss were measured on the POCO on 29 October, with the published APK and a Windows
  host at its console — the old text presented them as unmeasured.
- **No functional change**: the code is that of the fixed 1.11, so gestures, existing accesses, their
  sealed passwords and the passphrase export behave identically. `versionCode` 3 → 4.

## 1.11 — 28 octobre 2026

### Français

- **Le cœur RustDesk est embarqué.** Un accès RustDesk peut s'ouvrir dans l'application : la
  bibliothèque native est liée par JNI, décode les images et les rend à notre surface. La session
  devient observable (état, taille du bureau, images par seconde) au lieu d'être remise à
  l'application RustDesk, qui ne rendait aucun compte. Le report par Intent reste le repli quand la
  bibliothèque n'est pas dans l'APK.
- **Limites connues, dites telles quelles** : arm64-v8a seulement (32 Mo de bibliothèque), clavier
  physique seulement — pas de clavier logiciel —, pas de presse-papiers, pas de H.264 matériel.
  Détail et recette de compilation : `docs/RUSTDESK-NATIF.md`.
- **Licence** : le cœur est en AGPL-3.0, donc une build qui l'embarque est distribuée sous AGPL-3.0
  dans son ensemble. Une build sans le cœur reste en GPLv3.
- **Cible JVM unifiée** : Kotlin restait en cible 17 alors que Java demande 21, ce qui obligeait à
  contourner le contrôle de Gradle (`kotlin.jvm.target.validation.mode=warning`). Le désaccord est
  réglé à la racine, et le contournement n'est plus nécessaire.
- `versionCode` 2 → 3. Les accès existants, leurs mots de passe scellés et l'export avec phrase de
  passe sont inchangés.

### English

- **The RustDesk core is embedded.** A RustDesk access can open inside the application: the native
  library is linked over JNI, decodes frames and draws them on our surface. The session becomes
  observable (state, desktop size, frame rate) instead of being handed to the RustDesk app, which
  reported nothing back. The Intent hand-off remains the fallback when the library is absent.
- **Known limits, stated plainly**: arm64-v8a only (a 32 MB library), physical keyboard only — no
  on-screen keyboard —, no clipboard, no hardware H.264. Details and build recipe:
  `docs/RUSTDESK-NATIF.md` (French).
- **Licence**: the core is AGPL-3.0, so a build embedding it is distributed under AGPL-3.0 as a
  whole. A build without the core stays GPLv3.
- **Unified JVM target**: Kotlin stayed on 17 while Java asks for 21, which forced the Gradle check
  to be bypassed (`kotlin.jvm.target.validation.mode=warning`). The mismatch is fixed at the root
  and the workaround is no longer needed.
- `versionCode` 2 → 3. Existing accesses, their sealed passwords and the passphrase export are
  unchanged.

### Correctif du 29 octobre — fichiers remplacés / 29 October fix — files replaced

- **Le cœur ne se chargeait pas dans les APK publiés.** La build du 28 octobre embarquait la libc++
  des prébuilt FreeRDP, antérieure au NDK qui a compilé le cœur : `dlopen` échouait sur
  `_ZNSt6__ndk122__libcpp_verbose_abortEPKcz` et la session retombait en silence sur le report vers
  l'application installée — la version annonçait donc une session embarquée qu'elle ne faisait pas.
  Les deux APK sont reconstruits avec la libc++ du NDK 29 (1,4 Mo, détourée) : le symbole est défini
  et la session embarquée s'ouvre sur le POCO.
- **Serveur RustDesk privé.** Le cœur interrogeait toujours le serveur public et ne trouvait jamais
  un hôte enregistré ailleurs. Une entrée « Serveur RustDesk » (adresse + clé) dans le menu de la
  grille applique le serveur choisi avant la connexion, et vide le champ suffit à revenir au public.
- **Gestes de la session embarquée.** Ils ne produisaient rien : le masque envoyé au cœur porte le
  type d'événement dans les trois bits bas et le bouton à partir du bit trois, si bien qu'un « clic »
  était un appui *sans bouton* — accepté par le poste, puis ignoré sans un mot. Un toucher bref
  clique désormais à gauche, un glissement fait glisser avec le bouton gauche tenu, un appui long
  clique à droite (sans appui gauche parasite) et un appui dans la bande noire autour du bureau est
  ignoré. Vérifié sur un poste Windows à sa console : menu Démarrer, menu contextuel de la barre des
  tâches, glisser, et `ESC` reçu du clavier.
- **The core did not load in the published APKs.** The 28 October build shipped the libc++ from the
  FreeRDP prebuilts, older than the NDK that compiled the core: `dlopen` failed on
  `_ZNSt6__ndk122__libcpp_verbose_abortEPKcz` and the session silently fell back to handing off to
  the installed application — the version promised an embedded session it did not deliver. Both
  APKs are rebuilt against the NDK 29 libc++ (1.4 MB, stripped): the symbol is defined and the
  embedded session opens on the POCO.
- **Private RustDesk server.** The core always queried the public server and never found a host
  registered elsewhere. A “RustDesk server” entry (address + key) in the grid menu applies the
  chosen server before connecting, and clearing the field returns to the public one.
- **Embedded session gestures.** They did nothing: the mask sent to the core carries the event type
  in the low three bits and the button from bit three upwards, so a “click” was a press *with no
  button* — accepted by the host, then silently ignored. A brief touch now left-clicks, a swipe
  drags with the left button held, a long press right-clicks (with no stray left click before it)
  and a touch in the black letterbox around the desktop is ignored. Verified against a Windows host
  at its console: Start menu, taskbar context menu, dragging, and `ESC` received from the keyboard.
- `versionCode` unchanged (3): the build replaces the release files and installs over the previous
  one without uninstalling. Hashes below.

## 1.10 — 28 octobre 2026

### Français

- **Morpheusly retiré entièrement.** Le bouton qui occupait un emplacement permanent de la barre
  d'action, la feuille qu'il ouvrait, son layout, son icône et ses cinq chaînes ont disparu — y
  compris des 53 langues du dépôt. Le message affiché quand aucune application ne peut prendre en
  charge une connexion VPN ne renvoie plus vers la fiche Play de l'application d'origine.
- **L'aide VPN ne nomme plus la marque**, dans aucune langue : la clause fautive a été réécrite sur
  place, le reste de chaque traduction étant conservé. Quatre langues ont été reprises à la main
  (gujarati et portugais du Brésil réécrits, bulgare et turc passés à côté du premier balayage,
  espagnol complété).
- **Aucun changement fonctionnel** côté RDP, RDPGFX ou RustDesk : les accès existants, leurs mots de
  passe scellés et l'export avec phrase de passe se comportent comme en 1.0.0.

### English

- **Morpheusly removed entirely.** The button that held a permanent slot in the action bar, the sheet
  it opened, its layout, its icon and its five strings are gone — including from the 53 languages in
  the repository. The message shown when no application can handle a VPN connection no longer sends
  the user to the original app's Play Store page.
- **The VPN hint no longer names the brand**, in any language: the faulty clause was rewritten in
  place and the rest of each translation kept. Four languages needed a hand (Gujarati and Brazilian
  Portuguese rewritten, Bulgarian and Turkish missed by the first sweep, Spanish completed).
- **No functional change** to RDP, RDPGFX or RustDesk: existing accesses, their sealed passwords and
  the passphrase export behave as in 1.0.0.

### À savoir avant d'installer / Before installing

- Première version signée par la **clé de publication du projet** (`72:FB:AB:59:…`), et toutes les
  suivantes s'appuieront sur la même : à partir d'ici, une mise à jour s'installe par-dessus la
  précédente. La 1.0.0, elle, était signée en débogage : poser la 1.10 dessus échoue en
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, et désinstaller d'abord **détruit les mots de passe RustDesk
  scellés** — exportez avec une phrase de passe avant. / First version signed with the **project's
  publication key** (`72:FB:AB:59:…`), and every later release will use the same one: from here on an
  update installs over the previous version. 1.0.0 itself was debug-signed, so installing 1.10 on top
  of it fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and uninstalling first **destroys the sealed
  RustDesk passwords** — export with a passphrase beforehand.
- `versionCode` passe de 1 à 2, `versionName` de `1.0.0-dualdesk` à `1.10-dualdesk`.

## 1.0.0 — 28 octobre 2026

### Français

Premier jalon publiable : un client **RDP** mature (aRDP, FreeRDP 2.11.7 avec RDPGFX) qui sait aussi
**passer la main à RustDesk** depuis la même liste d'accès. Paquet `com.dualdesk.rdp`, qui ne
remplace pas aRDP.

- type d'accès `CONN_TYPE_RUSTDESK = 6`, entrée de menu pour le créer, carte dans la grille,
  modification et suppression ;
- passage de relais à RustDesk : deep link, puis activité explicite, puis presse-papiers, avec un
  compte rendu de la stratégie réellement employée ;
- mots de passe RustDesk **scellés** sous une clé du Keystore Android, et migration des accès
  enregistrés en clair ;
- export de la liste avec **phrase de passe facultative** (`pw1:`), pour restaurer les accès sur un
  autre appareil ; sans phrase, aucun mot de passe ne quitte l'appareil ;
- mesures RDP : RDPGFX négocié, 16 img/s en mouvement, 322 métablocs H.264 en 10,2 s.

### English

First publishable milestone: a mature **RDP** client (aRDP, FreeRDP 2.11.7 with RDPGFX) that can also
**hand a session over to RustDesk** from the same access list. Package `com.dualdesk.rdp`, which does
not replace aRDP.

- connection type `CONN_TYPE_RUSTDESK = 6`, a menu entry to create one, a card in the grid, edit and
  delete;
- hand-off to RustDesk: deep link, then explicit activity, then clipboard, reporting the strategy
  actually used;
- RustDesk passwords **sealed** under an Android Keystore key, with migration of records stored in
  clear;
- list export with an **optional passphrase** (`pw1:`) to restore accesses on another device; with no
  passphrase, no password leaves the device;
- RDP measurements: RDPGFX negotiated, 16 fps while moving, 322 H.264 metablocks in 10.2 s.
