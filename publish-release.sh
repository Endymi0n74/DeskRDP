#!/usr/bin/env bash
# ============================================================
# publish-release.sh — construire, contrôler et publier une version de DualDesk
# ============================================================
# Une seule commande, et dans cet ordre :
#   1. contrôles d'avant-vol (clé, outillage, arbre propre, commit poussé, tag libre,
#      section de journal) — tout ce qui peut échouer sans coûter une compilation ;
#   2. construction du variant release ;
#   3. contrôle de signature (refus si ce n'est pas la clé de publication) ;
#   4. copie dans `_released/<tag>/`, taille et SHA-256 de chaque fichier ;
#   5. notes de version = section du journal + tableau des fichiers ;
#   6. tag annoté, poussée du tag, `gh release create` avec les deux APK.
#
# Rien n'est publié si un contrôle échoue : le script s'arrête avant la copie, donc avant le
# tag. C'est la raison d'être de ce fichier — l'étape 3 est la seule qu'une compilation ne sait
# pas faire, et son échec ne se voit que sur le téléphone (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).
#
# La version vient du manifeste, pas d'un argument : `versionName="1.10-dualdesk"` donne la
# version `1.10` et le tag `v1.10`. On ne peut donc pas publier une version qui n'est pas celle
# qui est compilée.
#
# Usage :
#   ./publish-release.sh                 # tout, jusqu'à la release GitHub
#   ./publish-release.sh --dry-run       # construit et contrôle, mais ne pousse ni ne publie
#   ./publish-release.sh --skip-build    # réutilise les APK déjà dans build/outputs (reprise)
#
# Prérequis : JDK 21 (cherché ci-dessous), SDK Android (local.properties ou ANDROID_SDK_ROOT),
# `python`, `gh` authentifié, `_keystore/keystore.properties`.
# ============================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

MANIFESTE="dualdesk-app/src/main/AndroidManifest.xml"
JOURNAL="CHANGELOG.md"
SORTIE="dualdesk-app/build/outputs/apk/release"
APK_ARM64="$SORTIE/dualdesk-app-arm64-v8a-release.apk"
APK_UNIVERSEL="$SORTIE/dualdesk-app-universal-release.apk"
PYTHON="${PYTHON:-python}"

SEC_DRY_RUN=0
SEC_SANS_BUILD=0

# Sortie en erreur, avec la marche à suivre.
die() {
  printf '\n[arrêt] %s\n' "$*" >&2
  exit 1
}

etape() {
  printf '\n==> %s\n' "$*"
}

usage() {
  sed -n '3,32p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

for arg in "$@"; do
  case "$arg" in
    --dry-run) SEC_DRY_RUN=1 ;;
    --skip-build) SEC_SANS_BUILD=1 ;;
    -h|--help) usage 0 ;;
    *) printf 'argument inconnu : %s\n\n' "$arg" >&2; usage 2 ;;
  esac
done

# ------------------------------------------------------------
# 0. Outillage
# ------------------------------------------------------------
etape "outillage"

command -v git >/dev/null || die "git introuvable."
command -v gh  >/dev/null || die "gh (GitHub CLI) introuvable : https://cli.github.com"
command -v "$PYTHON" >/dev/null || die "interpréteur python introuvable (variable PYTHON pour le désigner)."

gh auth status >/dev/null 2>&1 || die "gh n'est pas authentifié : lancez \`gh auth login\`."

# Le dépôt est lu sur `origin`, et passé explicitement à chaque appel de gh. Sans cela, gh
# résout tout seul : dans ce dossier il existe un remote `upstream` (le dépôt amont dont ceci
# est un fork), et gh choisit l'amont — `gh repo view` répond iiordanov/remote-desktop-clients.
# Une release créée sans -R partirait donc ailleurs que dans ce dépôt.
depot_github() {
  local url casse
  url="$(git remote get-url origin)" || return 1
  casse="${url%.git}"
  printf '%s\n' "${casse#*github.com[:/]}"
}
DEPOT="$(depot_github)" || die "remote `origin` illisible : impossible de savoir où publier."
case "$DEPOT" in
  */*) ;;
  *) die "`origin` ne pointe pas sur GitHub ($DEPOT) : impossible de publier." ;;
esac

# Droit d'écriture : publier suppose de créer un tag et une release, pas seulement de lire.
# `gh repo view` n'accepte pas -R (le dépôt y est un argument positionnel) ; `gh api`, si.
if [ "$(gh api "repos/$DEPOT" --jq '.permissions.push' 2>/dev/null)" != "true" ]; then
  die "le compte gh n'a pas le droit d'écrire dans $DEPOT.
        Vérifiez l'URL de `origin` et le compte connecté (\`gh auth status\`)."
fi

# JDK 21 : la cible Java est 21, et un JDK 11 sur le PATH fait échouer la compilation. On ne
# devine pas un chemin unique — on essaie JAVA_HOME puis quelques emplacements usuels, et on
# vérifie la version majeure avant de l'utiliser.
version_java() {
  "$1/bin/java" -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p'
}

JDK=""
for candidat in \
    "${JAVA_HOME:-}" \
    /usr/lib/jvm/*21* \
    "/c/Program Files/Zulu/zulu-21" \
    "/c/Program Files/Microsoft/jdk-21"* \
    "/c/Program Files/Eclipse Adoptium/jdk-21"* \
    "/c/Program Files/Java/jdk-21"* ; do
  [ -n "$candidat" ] && [ -x "$candidat/bin/java" ] || continue
  majeure="$(version_java "$candidat")"
  if [ -n "$majeure" ] && [ "$majeure" -ge 21 ] 2>/dev/null; then
    JDK="$candidat"
    break
  fi
done
[ -n "$JDK" ] || die "aucun JDK 21+ trouvé (JAVA_HOME=${JAVA_HOME:-<vide>}). Exportez JAVA_HOME vers un JDK 21."

export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"
printf '    JDK        : %s (Java %s)\n' "$JDK" "$(version_java "$JDK")"
printf '    python     : %s\n' "$(command -v "$PYTHON")"
printf '    gh         : %s\n' "$(gh --version | sed -n '1s/gh version \([^ ]*\).*/\1/p')"
printf '    dépôt      : %s (écriture autorisée)\n' "$DEPOT"

# ------------------------------------------------------------
# 1. Version, depuis le manifeste
# ------------------------------------------------------------
version_name="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' "$MANIFESTE" | head -n1)"
version_code="$(sed -n 's/.*android:versionCode="\([^"]*\)".*/\1/p' "$MANIFESTE" | head -n1)"
[ -n "$version_name" ] || die "versionName illisible dans $MANIFESTE."
[ -n "$version_code" ] || die "versionCode illisible dans $MANIFESTE."

# « 1.10-dualdesk » -> « 1.10 » : le suffixe fait partie de l'identité affichée, pas du numéro.
VERSION="${version_name%%-*}"
TAG="v$VERSION"
DEST="$ROOT/_released/$TAG"
NOTES="$DEST/notes.md"

etape "version $VERSION (versionCode $version_code, versionName $version_name)"
printf '    tag         : %s\n' "$TAG"
printf '    fichiers    : %s\n' "$DEST"

# ------------------------------------------------------------
# 2. Contrôles d'avant-vol
# ------------------------------------------------------------
etape "contrôles d'avant-vol"

# La clé : sans elle, `assembleRelease` réussit mais produit des fichiers `-unsigned`, et rien
# ne doit être publié. Autant le dire avant les dix minutes de compilation.
[ -f "_keystore/keystore.properties" ] ||
  die "clé de signature absente : _keystore/keystore.properties manque.
        Restaurez la clé (voir README, « Sauvegarde et restauration ») — publier sans elle
        produirait des APK non signés, impossibles à installer par-dessus une version existante."

# Arbre propre : une release pointe sur un commit, donc le manifeste et le journal doivent
# être commités avant, pas après.
if [ -n "$(git status --porcelain)" ]; then
  printf '%s\n' "$(git status --short)" >&2
  die "arbre de travail sale : une release pointe sur un commit, donc commitez ou rangez ces
        fichiers avant de publier (manifeste, journal, script lui-même…)."
fi

branche="$(git rev-parse --abbrev-ref HEAD)"

# Le commit publié doit déjà être en ligne, sinon le tag désigne un commit que personne ne voit.
etape "synchronisation avec origin"
git fetch --quiet origin master ||
  die "impossible de joindre origin : le commit publié doit être vérifiable en ligne."
git merge-base --is-ancestor HEAD origin/master ||
  die "HEAD ($branche) n'est pas sur origin/master : poussez d'abord (\`git push origin $branche\`)."

if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  tag_commit="$(git rev-parse "$TAG^{commit}")"
  if [ "$tag_commit" != "$(git rev-parse HEAD)" ]; then
    die "le tag $TAG existe déjà et désigne ${tag_commit:0:8}, pas HEAD.
        Incrémentez versionName dans $MANIFESTE — on ne republie pas une version publiée."
  fi
  if gh release view -R "$DEPOT" "$TAG" >/dev/null 2>&1; then
    die "la release $TAG existe déjà sur GitHub : incrémentez versionName."
  fi
  REPRISE=1
  printf '    tag         : %s existe déjà sur HEAD — reprise (tag poussé, release à créer)\n' "$TAG"
else
  REPRISE=0
  if [ -n "$(git ls-remote --tags origin "refs/tags/$TAG" 2>/dev/null)" ]; then
    die "le tag $TAG existe déjà sur origin : incrémentez versionName."
  fi
fi

# Les notes de version viennent du journal : pas de section, pas de publication — une release
# sans texte ne dit pas ce qui change.
if ! grep -qE "^## ${VERSION//./\\.}([^0-9]|$)" "$JOURNAL"; then
  die "aucune section « ## $VERSION » dans $JOURNAL : écrivez le journal avant de publier."
fi
printf '    journal     : section %s trouvée\n' "$VERSION"
printf '    branche     : %s\n' "$branche"

if [ "$SEC_DRY_RUN" = "1" ]; then
  printf '    mode        : --dry-run (ni tag, ni poussée, ni release)\n'
fi

# ------------------------------------------------------------
# 3. Construction
# ------------------------------------------------------------
if [ "$SEC_SANS_BUILD" = "1" ]; then
  etape "construction ignorée (--skip-build) : les APK présents dans $SORTIE sont réutilisés"
else
  etape "construction : :dualdesk-app:assembleRelease"
  ./gradlew :dualdesk-app:assembleRelease ||
    die "la compilation a échoué : rien n'est copié, rien n'est publié."
fi

[ -f "$APK_ARM64" ] || die "APK arm64-v8a absent après construction : $APK_ARM64"
[ -f "$APK_UNIVERSEL" ] || die "APK universel absent après construction : $APK_UNIVERSEL"

# ------------------------------------------------------------
# 4. Contrôle de signature — le point de non-retour
# ------------------------------------------------------------
etape "contrôle de signature"
if ! "$PYTHON" _check/verifie_signature.py "$APK_ARM64" "$APK_UNIVERSEL"; then
  die "signature non conforme : ces fichiers ne sont pas publiables.
        Rien n'a été copié dans _released/, aucun tag n'a été créé."
fi

# ------------------------------------------------------------
# 5. Copie, empreintes, notes
# ------------------------------------------------------------
etape "copie dans $DEST"
mkdir -p "$DEST"
cp -f "$APK_ARM64" "$APK_UNIVERSEL" "$DEST/"

sha256() {
  if command -v sha256sum >/dev/null; then
    sha256sum "$1" | cut -d' ' -f1
  elif command -v shasum >/dev/null; then
    shasum -a 256 "$1" | cut -d' ' -f1
  else
    "$PYTHON" -c 'import hashlib,sys;print(hashlib.sha256(open(sys.argv[1],"rb").read()).hexdigest())' "$1"
  fi
}

taille_lisible() {
  "$PYTHON" -c 'import sys;n=int(sys.argv[1]);print("{:,}".format(n).replace(","," "))' "$1"
}

lignes_fichiers=""
for fichier in "$APK_ARM64" "$APK_UNIVERSEL"; do
  nom="$(basename "$fichier")"
  octets="$(wc -c <"$fichier" | tr -d ' ')"
  empreinte="$(sha256 "$fichier")"
  printf '    %-38s %s o  %s\n' "$nom" "$(taille_lisible "$octets")" "$empreinte"
  lignes_fichiers+="| \`$nom\` | $(taille_lisible "$octets") o / bytes | \`$empreinte\` |"$'\n'
done

EMPREINTE_CLE="$("$PYTHON" - <<'PY'
import io, re
# Lue dans la source du contrôle, sans l'importer : un import écrirait `_check/__pycache__/`
# dans l'arbre, et le script refuse justement de publier depuis un arbre sale.
source = io.open("_check/verifie_signature.py", encoding="utf-8").read()
e = re.search(r'EMPREINTE_ATTENDUE\s*=\s*\(\s*"([0-9a-f]+)"', source).group(1)
print(":".join(e[i:i + 2] for i in range(0, len(e), 2)).upper())
PY
)"

# Notes = la section du journal, telle quelle (elle est déjà bilingue), plus ce que le script
# seul sait : les fichiers réellement produits et la clé qui les signe. Le titre de section
# devient le titre de la release ; le tableau est celui des fichiers copiés, pas de ceux qu'on
# espérait produire.
corps="$(awk -v v="$VERSION" '
  BEGIN { gsub(/\./, "\\.", v); motif = "^## " v "([^0-9]|$)" }
  $0 ~ motif { dedans = 1; next }
  dedans && /^## / { exit }
  dedans { print }
' "$JOURNAL" | sed -e '/./,$!d')"
[ -n "$corps" ] || die "la section $VERSION du journal est vide : rien à annoncer, rien à publier."

{
  printf '# DualDesk %s\n\n' "$VERSION"
  printf '%s\n' "$corps"
  printf '\n## Fichiers / Files\n\n'
  printf '| Fichier / File | Taille / Size | SHA-256 |\n|---|---|---|\n'
  printf '%s' "$lignes_fichiers"
  printf '\nSignés par la clé de publication du projet / signed with the project publication key —\ncertificat SHA-256 `%s`.\n' "$EMPREINTE_CLE"
  printf '\nAndroid 5.0 (API 21) ou plus récent / Android 5.0 (API 21) or newer. Paquet `com.dualdesk.rdp`.\n'
} >"$NOTES"

etape "notes de version : $NOTES"
printf '    %s lignes\n' "$(wc -l <"$NOTES" | tr -d ' ')"

# ------------------------------------------------------------
# 6. Tag et release
# ------------------------------------------------------------
if [ "$SEC_DRY_RUN" = "1" ]; then
  etape "arrêt en dry-run : rien n'est poussé ni publié"
  if [ "$REPRISE" = "0" ]; then
    printf '    aurait lancé : git tag -a %s -m "DualDesk %s"\n' "$TAG" "$VERSION"
    printf '                   git push origin %s\n' "$TAG"
  fi
  printf '                   gh release create -R %s %s --title "DualDesk %s" --notes-file %s --verify-tag %s %s\n' \
    "$DEPOT" "$TAG" "$VERSION" "$NOTES" "$DEST/$(basename "$APK_ARM64")" "$DEST/$(basename "$APK_UNIVERSEL")"
  printf '\nContrôles passés ; la publication elle-même reste à faire.\n'
  exit 0
fi

if [ "$REPRISE" = "0" ]; then
  etape "tag $TAG"
  git tag -a "$TAG" -m "DualDesk $VERSION"
  git push origin "$TAG" || die "poussée du tag refusée : la release n'a pas été créée.
        Le tag local existe ; relancez pour reprendre à l'étape suivante."
else
  etape "tag $TAG déjà en place, reprise directe par la release"
fi

etape "release GitHub"
if ! gh release create -R "$DEPOT" "$TAG" \
      --title "DualDesk $VERSION" \
      --notes-file "$NOTES" \
      --verify-tag \
      "$DEST/$(basename "$APK_ARM64")" \
      "$DEST/$(basename "$APK_UNIVERSEL")"; then
  die "la création de la release a échoué. Le tag $TAG est en ligne et pointe sur ce commit :
        corrigez la cause puis relancez avec --skip-build pour reprendre."
fi

etape "publié"
gh release view -R "$DEPOT" "$TAG" --json tagName,url,assets \
  --template '{{.tagName}} — {{.url}}{{"\n"}}{{range .assets}}    {{.name}}  {{.size}} o{{"\n"}}{{end}}'
