#!/usr/bin/env python3
"""Mesure des gestes sur une session RDP ouverte : appui, appui long, glissement.

Le temoin est la fenetre « Cible de clic DualDesk » (voir `../dualdesk/proof/click-target.ps1`
dans le workspace) : elle journalise chaque evenement souris recu, avec le bouton. Comme elle est
agrandie et au premier plan, elle couvre le bureau distant et recoit donc reellement les appuis :
ce qu'on lit dans son journal est ce que le poste a vu, pas ce que l'application croit avoir envoye.

La bande d'image est reperee par la couleur de fond du temoin, ce qui evite de figer des
coordonnees qui changent avec le zoom et le cadrage.

Ce qui est verifie :
  - appui bref            -> clic gauche a l'endroit touche ;
  - appui long sans bouger -> clic droit, et aucun bouton gauche maintenu ;
  - appui long puis glissement -> bouton gauche enfonce au point de depart, puis deplacements
    avec le bouton tenu, puis relachement.

Usage :
    python _check/gestes.py --log ../dualdesk/proof/gestes-click-log.txt
"""
import re
import subprocess
import sys
import time

CANVAS = "com.iiordanov.bVNC.RemoteCanvasActivity"
DIALOGUES = ("identifiants", "certificat")
TEAL = (20, 90, 90)
TOLERANCE = 20


def adb(*args: str, timeout: int = 60) -> str:
    return subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout,
                          encoding="utf-8", errors="replace").stdout


def focus() -> str:
    for line in adb("shell", "dumpsys", "window").splitlines():
        if "mCurrentFocus" in line:
            return line.strip()
    return ""


def session_ouverte() -> tuple[bool, str]:
    courant = focus()
    if any(d.lower() in courant.lower() for d in DIALOGUES):
        return False, f"un dialogue est ouvert : {courant}"
    if CANVAS not in courant:
        return False, f"l'activite RDP n'est pas au premier plan : {courant or '(illisible)'}"
    return True, courant


def bande_image(chemin: str) -> tuple[int, int, int, int] | None:
    """Repere la zone du temoin a l'ecran du telephone, en pixels du telephone."""
    from PIL import Image

    with open(chemin, "wb") as sortie:
        sortie.write(subprocess.run(["adb", "exec-out", "screencap", "-p"],
                                    capture_output=True, timeout=120).stdout)
    image = Image.open(chemin).convert("RGB")
    pixels = image.load()
    xs, ys = [], []
    for y in range(0, image.height, 4):
        for x in range(0, image.width, 4):
            r, g, b = pixels[x, y]
            if abs(r - TEAL[0]) < TOLERANCE and abs(g - TEAL[1]) < TOLERANCE \
                    and abs(b - TEAL[2]) < TOLERANCE:
                xs.append(x)
                ys.append(y)
    if len(xs) < 200:
        return None
    return min(xs), min(ys), max(xs), max(ys)


def lignes(chemin: str, depuis: int) -> list[str]:
    with open(chemin, encoding="utf-8-sig", errors="replace") as f:
        return [l.rstrip("\n") for l in f.readlines()[depuis:]]


def compter(chemin: str) -> int:
    with open(chemin, encoding="utf-8-sig", errors="replace") as f:
        return len(f.readlines())


def geste(nom: str, commandes: list[list[str]], pauses: list[float]) -> None:
    print(f"  {nom}")
    for commande, pause in zip(commandes, pauses):
        adb("shell", "input", *commande)
        time.sleep(pause)


def main() -> int:
    log = "../dualdesk/proof/gestes-click-log.txt"
    if "--log" in sys.argv:
        log = sys.argv[sys.argv.index("--log") + 1]

    ouverte, detail = session_ouverte()
    print(f"session : {'ouverte' if ouverte else 'PAS ouverte'} - {detail}")
    if not ouverte:
        print("Mesure refusee : ouvrir l'acces et revenir quand la session est affichee.")
        return 2

    zone = bande_image("../tmpshot/gestes/bande.png")
    if not zone:
        print("Temoin introuvable a l'ecran : lancer click-target.ps1, l'agrandir, relancer.")
        return 3
    gauche, haut, droite, bas = zone
    print(f"bande d'image : x {gauche}..{droite}, y {haut}..{bas}")

    # Trois points repartis dans la bande, loin des bords et de la barre flottante.
    marge_x = (droite - gauche) // 6
    marge_y = (bas - haut) // 3
    x1 = gauche + marge_x
    x2 = gauche + (droite - gauche) // 2
    x3 = droite - marge_x
    y = haut + marge_y

    depart = compter(log)

    print("1) appui bref")
    geste("appui", [["tap", str(x1), str(y)]], [1.5])
    apres_appui = compter(log)

    print("2) appui long sans bouger")
    geste("appui long",
          [["motionevent", "DOWN", str(x2), str(y)]], [1.4])
    adb("shell", "input", "motionevent", "UP", str(x2), str(y))
    time.sleep(1.5)
    apres_long = compter(log)

    print("3) appui long puis glissement")
    pas = max(40, (droite - gauche) // 10)
    geste("glissement",
          [["motionevent", "DOWN", str(x3), str(y)]], [1.0])
    for i in range(1, 4):
        adb("shell", "input", "motionevent", "MOVE", str(x3 + i * pas), str(y))
        time.sleep(0.08)
    time.sleep(0.3)
    adb("shell", "input", "motionevent", "UP", str(x3 + 3 * pas), str(y))
    time.sleep(1.5)

    appui = lignes(log, depart)[:apres_appui - depart]
    long = lignes(log, apres_appui)[:apres_long - apres_appui]
    glisse = lignes(log, apres_long)

    def resume(titre: str, lignes_: list[str]) -> None:
        print(f"\n--- {titre} ---")
        for ligne in lignes_:
            print("   " + ligne)
        if not lignes_:
            print("   (aucun evenement)")

    resume("appui bref", appui)
    resume("appui long sans bouger", long)
    resume("appui long puis glissement", glisse)

    def a_bouton(lignes_: list[str], bouton: str) -> bool:
        return any(f"button={bouton}" in l for l in lignes_)

    ok_appui = a_bouton(appui, "Left") and any("down" in l for l in appui) \
        and any("up" in l for l in appui)
    ok_long = a_bouton(long, "Right") and not a_bouton(long, "Left")
    # Windows regroupe les deplacements : un seul evenement de glissement suffit a prouver que le
    # bouton etait tenu pendant le mouvement.
    ok_glisse = a_bouton(glisse, "Left") and any("drag" in l for l in glisse)

    print()
    print(f"appui bref         : {'OK clic gauche' if ok_appui else 'ECHEC'}")
    print(f"appui long         : {'OK clic droit' if ok_long else 'ECHEC'}"
          + ("" if ok_long else " (bouton gauche envoye ou clic droit absent)"))
    print(f"glissement         : {'OK bouton gauche tenu' if ok_glisse else 'ECHEC'}")
    return 0 if (ok_appui and ok_long and ok_glisse) else 1


if __name__ == "__main__":
    raise SystemExit(main())
