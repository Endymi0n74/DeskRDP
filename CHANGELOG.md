# Journal des versions / Changelog

Les versions publiées sont sur la page [Releases](https://github.com/Endymi0n74/ardp/releases).
Published builds live on the [Releases](https://github.com/Endymi0n74/ardp/releases) page.

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

- L'APK est signé avec la **clé de débogage**, comme la 1.0.0, mais ce n'est **pas la même clé** :
  poser la 1.10 sur une 1.0.0 installée échoue en `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Il faut
  désinstaller d'abord, ce qui **détruit les mots de passe RustDesk scellés** — exportez avec une
  phrase de passe avant. / The APK is signed with the **debug key**, as 1.0.0 was, but **not the same
  key**: installing 1.10 over an existing 1.0.0 fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
  Uninstall first, which **destroys the sealed RustDesk passwords** — export with a passphrase
  beforehand.
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
