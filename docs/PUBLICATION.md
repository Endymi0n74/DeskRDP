# Signer et publier une version

Tout ce qui concerne la clé de publication et la mise en ligne d'une version. Le
[README](../README.md) ne garde que l'essentiel ; ce fichier est la référence.

## La clé de publication

`assembleDebug` ne demande aucune clé. `assembleRelease` signe avec la clé du projet, qui vit
**hors du dépôt**, dans `_keystore/` : ce dossier est ignoré par git, et il doit l'être — une clé de
signature dans un dépôt public laisserait n'importe qui publier une mise à jour sous le nom de cette
application.

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

## Sauvegarde et restauration

**Gardez-en une copie.** Une archive chiffrée, stockée à deux endroits distincts, et la phrase de
passe dans un gestionnaire de mots de passe. Perdre le magasin ou sa phrase, c'est ne plus jamais
pouvoir publier de mise à jour : Android refuse une APK signée d'une autre clé, donc chaque
utilisateur installé devrait désinstaller — en perdant les mots de passe RustDesk scellés sous le
Keystore.

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

## Contrôle de signature avant publication

Une construction de release **réussit qu'elle soit signée ou non** — et le fichier s'appelle
`…-release.apk` dans les deux cas. La signature est la seule chose qu'une compilation ne peut pas
garantir, et sa défaillance n'apparaît que sur le téléphone de l'utilisateur
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). D'où la dernière étape, avant tout envoi :

```bash
./gradlew :dualdesk-app:verifieSignatureRelease
```

Elle reconstruit, lit le certificat des deux APK et échoue si ce n'est pas la clé ci-dessus, en
nommant le fautif (`MAUVAISE CLÉ`, `NON SIGNÉ`, `ABSENT`). Le script derrière est
`_check/verifie_signature.py`, utilisable seul sur n'importe quel APK :
`python _check/verifie_signature.py mon.apk`. Il reste volontairement hors de `assembleRelease` : un
clone neuf, sans `_keystore/`, doit continuer de compiler.

## Publier en une commande

`publish-release.sh` enchaîne la séquence — contrôles, construction, vérification de signature,
copie, tag, release GitHub — et s'arrête au premier refus :

```bash
./publish-release.sh                # construire, vérifier, taguer, publier
./publish-release.sh --dry-run      # construire et vérifier, sans rien pousser ni publier
./publish-release.sh --skip-build   # reprendre après une création de release qui a échoué
```

La version n'est pas un argument : elle est lue dans `versionName` de
`dualdesk-app/src/main/AndroidManifest.xml`, donc ce qui est publié est ce qui a été compilé. Avant
de construire quoi que ce soit, il refuse un arbre sale, un commit absent de `origin/master`, une
version dont le tag ou la release existe déjà, un `_keystore/` manquant, et une version sans section
dans `CHANGELOG.md`. Les notes de version sont cette section du journal, plus le SHA-256 que le
script a lui-même mesuré sur les fichiers copiés.

Une version qui annonce le cœur RustDesk le désigne explicitement, et le script vérifie qu'il est
bien **dans** les APK produits :

```bash
DUALDESK_RUSTDESK_LIB=/chemin/vers/librustdesk.so \
DUALDESK_LIBCXX=/chemin/vers/libc++_shared.so \
./publish-release.sh
```

La libc++ est obligatoire avec le cœur : celle des prébuilt FreeRDP est trop ancienne et `dlopen`
échoue sur `__libcpp_verbose_abort`, ce qui fait retomber la session sur le report par Intent — sans
erreur visible, d'où le refus.

## Pièges connus

- **`gh` résout vers l'amont.** Dans ce dossier il existe un remote `upstream`
  (`iiordanov/remote-desktop-clients`) : `gh release list` y répond `RELEASE-v6.4.9`. Chaque appel
  porte donc `-R <origin>`, et `gh release upload` / `edit` n'acceptent que le tag en positionnel.
- **Déplacer un tag ne déplace pas les actifs.** `git push --force origin refs/tags/vX` est nécessaire
  après recréation ; les fichiers attachés à la release restent ceux déjà envoyés, il faut les
  remplacer par `gh release upload <tag> --clobber`.
- **Remplacer les actifs n'est pas rejouable par le script.** Il refuse une version déjà publiée :
  le remplacement se fait à la main, puis les empreintes des notes sont corrigées.
- **`versionCode` doit augmenter à chaque version.** Le nom de version suffit à nommer la release,
  mais c'est le code que les appareils comparent : laissé identique, deux builds différentes portent
  la même identité et aucune mise à jour ne peut être distinguée.
