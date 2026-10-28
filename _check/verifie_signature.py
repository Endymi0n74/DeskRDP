#!/usr/bin/env python3
"""Vérifie que les APK d'une publication portent bien la clé de signature attendue.

Motif : deux jalons ont déjà été publiés avec des clés différentes — une clé de débogage
recréée entre-temps — et le seul symptôme est apparu chez l'utilisateur, sous la forme d'un
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` qu'aucun test de construction ne pouvait annoncer. La
signature est le seul point qu'une compilation ne contrôle pas : `assembleRelease` réussit aussi
bien avec la bonne clé qu'avec aucune.

Le contrôle lit le certificat avec `apksigner` et le compare à l'empreinte de la clé de
publication. Il refuse aussi un fichier `…-unsigned.apk` : c'est ce que produit Gradle quand
`_keystore/` manque, et publier cela donnerait un APK que personne ne peut installer.

Usage :
    python _check/verifie_signature.py [apk...]

Sans argument, il contrôle les deux fichiers publiés pour la 1.10 :
`dualdesk-app-arm64-v8a-release.apk` et `dualdesk-app-universal-release.apk`.
Code de sortie : 0 si tout est conforme, 1 sinon, 2 si l'outillage manque.
"""
import os
import re
import subprocess
import sys

# La console Windows est en cp1252 : sans cela, chaque accent sort en « � » (voir memory.md).
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

RACINE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SORTIE = os.path.join(RACINE, "dualdesk-app", "build", "outputs", "apk", "release")

# Empreinte SHA-256 du certificat de la clé de publication (voir README, « Signing the release »).
EMPREINTE_ATTENDUE = (
    "72fbab598338faa7c448fd65aa30ba9139ed449c30e4647729c1b0864498b785"
)

DEFAUT = [
    os.path.join(SORTIE, "dualdesk-app-arm64-v8a-release.apk"),
    os.path.join(SORTIE, "dualdesk-app-universal-release.apk"),
]


def sdk():
    """Racine du SDK Android : variable d'environnement, sinon local.properties."""
    for nom in ("ANDROID_SDK_ROOT", "ANDROID_HOME"):
        valeur = os.environ.get(nom)
        if valeur and os.path.isdir(valeur):
            return valeur
    proprietes = os.path.join(RACINE, "local.properties")
    if os.path.isfile(proprietes):
        with open(proprietes, "r", encoding="utf-8") as fichier:
            for ligne in fichier:
                if ligne.startswith("sdk.dir="):
                    return ligne.split("=", 1)[1].strip().replace("\\\\", "\\")
    return None


def apksigner():
    """Le plus récent `apksigner` des build-tools installés."""
    racine = sdk()
    if racine:
        outils = os.path.join(racine, "build-tools")
        versions = []
        if os.path.isdir(outils):
            for nom in os.listdir(outils):
                chemin = os.path.join(outils, nom, "apksigner.bat")
                if os.path.isfile(chemin):
                    versions.append((tuple(int(p) for p in nom.split(".") if p.isdigit()), chemin))
        if versions:
            return sorted(versions)[-1][1]
    return "apksigner"


def cert(apk):
    """Empreinte SHA-256 du certificat, sans deux-points ni majuscules, ou None."""
    resultat = subprocess.run(
        [apksigner(), "verify", "--print-certs", apk],
        capture_output=True, text=True,
    )
    for ligne in (resultat.stdout + resultat.stderr).splitlines():
        if "SHA-256 digest" in ligne:
            brute = ligne.split(":", 1)[1].strip()
            return re.sub(r"[^0-9a-f]", "", brute.lower())
    return None


def main():
    cibles = sys.argv[1:] or DEFAUT
    if not os.path.isfile(apksigner()) and apksigner() != "apksigner":
        print("outillage : apksigner introuvable (SDK Android ?)")
        return 2

    echecs = 0
    for apk in cibles:
        nom = os.path.basename(apk)
        if not os.path.isfile(apk):
            # Un build sans clé ne supprime pas la cible : il la renomme « -unsigned ».
            sans_cle = apk.replace("-release.apk", "-release-unsigned.apk")
            if os.path.isfile(sans_cle):
                print("%-42s NON SIGNÉ (clé absente pendant le build)" % nom)
            else:
                print("%-42s ABSENT" % nom)
            echecs += 1
            continue
        if nom.endswith("-unsigned.apk"):
            print("%-42s NON SIGNÉ (clé absente au moment du build)" % nom)
            echecs += 1
            continue
        empreinte = cert(apk)
        if empreinte is None:
            print("%-42s AUCUN SIGNATAIRE" % nom)
            echecs += 1
        elif empreinte == EMPREINTE_ATTENDUE:
            print("%-42s clé de publication" % nom)
        else:
            print("%-42s MAUVAISE CLÉ (%s…)" % (nom, empreinte[:16]))
            echecs += 1

    if echecs:
        print("\n%d fichier(s) à ne pas publier." % echecs)
        print("Empreinte attendue : %s" % EMPREINTE_ATTENDUE)
        return 1
    print("\nsignature conforme : ces fichiers peuvent être publiés.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
