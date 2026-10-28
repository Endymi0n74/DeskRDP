# Le cœur RustDesk embarqué

DualDesk peut ouvrir une session RustDesk **dans son propre processus** : le cœur RustDesk
(`librustdesk`) est lié par JNI, décode les images et les rend à l'application. Aucune application
tierce n'est lancée, et la session devient observable — état, taille du bureau, images par seconde.

La version 1.10 faisait l'inverse : elle remettait la session à l'application RustDesk installée, par
deep link puis par Intent, et ne pouvait donc rien dire de la session après le lancement. Ce
comportement reste le **repli**, pour toute build qui ne contient pas la bibliothèque.

## Ce qui est livré, et sous quelle forme

| Élément | Emplacement | Versionné ? |
|---|---|---|
| Façade JNI | `bVNC/src/main/java/com/dualdesk/rdp/RustDeskNative.kt` | oui |
| Session et rendu | `bVNC/src/main/java/com/dualdesk/rdp/RustDeskEmbeddedActivity.java` | oui |
| Point d'entrée pour la grille | `bVNC/src/main/java/com/dualdesk/rdp/RustDeskEmbedded.java` | oui |
| Bibliothèque native, arm64-v8a, 32 Mo | `librustdesk.so`, copiée dans les `jniLibs` du variant | **non** |
| Recette de compilation | `scripts/compile-rustdesk-android.sh` | oui |
| Licence du cœur | `LICENCE-RustDesk.txt` (AGPL-3.0) | oui |

La bibliothèque n'est **pas** versionnée : le dépôt reste un client, et la bibliothèque se
reconstruit à partir de `rustdesk-src`. Une build qui veut la session embarquée désigne le fichier :

```bash
./gradlew :dualdesk-app:assembleRelease \
    -Pdualdesk.rustdeskLib=/chemin/vers/librustdesk.so
```

Sans cette propriété, l'APK n'embarque aucun `librustdesk.so` et la grille repasse au report par
Intent : c'est exactement la 1.10. Le fichier est copié dans un dossier généré (`build/rustdeskJni`),
jamais dans `src/`, donc l'arbre de travail reste propre.

## Ce qui a été vérifié, et ce qui ne l'a pas été

**Vérifié** — la compilation complète de `:dualdesk-app:assembleDebug` avec le cœur ; la présence de
`lib/arm64-v8a/librustdesk.so` (32 868 352 octets) dans l'APK arm64 ; la déclaration de
`com.dualdesk.rdp.RustDeskEmbeddedActivity` dans le manifeste fusionné ; la présence des classes dans
le dex (`classes2.dex`) ; et la correspondance exacte entre les 16 méthodes `external` de la façade
et les 16 symboles exportés par la bibliothèque (`Java_com_dualdesk_rdp_RustDeskNative_*`, lus avec
`llvm-nm`). Un décalage de paquet ou de nom aurait donné un `UnsatisfiedLinkError` à la première
session : c'est précisément ce que ce contrôle écarte.

**Non vérifié** — aucune session réelle n'a été ouverte depuis ce fork. Le POCO n'était pas branché,
et la bibliothèque est arm64-v8a uniquement : un émulateur x86_64 ne peut pas la charger. Les gestes
(tap = clic gauche, appui long = clic droit, glisser = déplacement), le rendu des images et la
reprise après perte de surface sont donc écrits d'après une mesure qui a réussi dans `dualdesk`, pas
mesurés ici. Les trois premiers essais sur appareil devront confirmer : première image, position du
pointeur au pixel, et clic droit.

## Limites assumées

- **ABI** : arm64-v8a seulement. Sur armeabi-v7a, x86 ou x86_64 la bibliothèque est absente, et le
  repli par Intent s'applique — sans message trompeur, puisque la grille teste la présence réelle du
  cœur avant de décider.
- **Clavier** : les touches listées dans `vkNameFor` (lettres, chiffres, F1-F12, Entrée, Retour,
  Tabulation, Échap, Espace, flèches, Suppr, Début/Fin, Page précédente/suivante, Maj, Ctrl, Alt)
  atteignent le poste. Aucune autre, et **aucun clavier logiciel** : un appui sur une touche non
  listée ne fait rien du tout plutôt que d'envoyer un caractère faux.
- **Presse-papiers** : absent. Le canal existe dans le cœur, mais la façade ne l'expose pas ; on ne
  prétend pas le contraire.
- **Pas de décodage H.264 matériel** : `hwcodec` ne se compile pas pour Android depuis un hôte
  Windows (son `build.rs` décide de la plateforme avec `cfg(windows)`, qui décrit l'hôte). Le
  décodeur logiciel est utilisé, comme lors de la mesure de référence.
- **Adresse du serveur** : la façade expose `setOption`, mais la session ne renseigne aujourd'hui que
  l'identifiant et le mot de passe de l'accès. Un serveur d'auto-hébergement n'est donc pas encore
  configurable depuis l'interface.

## Compiler le cœur

`scripts/compile-rustdesk-android.sh` porte la recette complète, avec ses dix pièges commentés là où
ils se produisent (Perl d'OpenSSL, `SODIUM_LIB_DIR` global, `-isystem` qui casse libc++, `hwcodec`,
`--lib` obligatoire, etc.). Elle suppose `rustdesk-src`, le NDK 29 et vcpkg déjà en place ; elle
produit `librustdesk.so` et un journal dans `build-logs/`.

## Licence

Le cœur RustDesk est sous **AGPL-3.0** (`LICENCE-RustDesk.txt`), et le fork est un dérivé d'aRDP
(GPLv3). Une build qui embarque le cœur est donc distribuée sous **AGPL-3.0** : les deux licences
sont compatibles dans ce sens (l'AGPL-3.0 absorbe la GPLv3), pas dans l'autre. La build sans le cœur
reste GPLv3. Ce n'est pas une formalité : quiconque reçoit l'APK avec le cœur embarqué doit pouvoir
en obtenir les sources correspondantes, y compris celles de `rustdesk-src`.
