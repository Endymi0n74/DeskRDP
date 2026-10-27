#!/usr/bin/env python3
"""Rejoue le parcours « créer un accès RustDesk » et dit si la feuille du gestionnaire
de mots de passe s'interpose.

Le parcours est joué entièrement par l'interface (comme un doigt), sans supposer que
l'application coopère : relever l'écran entre chaque étape évite le piège du clavier, qui
remonte la boîte de dialogue et déplace les champs sous les coordonnées.

Usage :
    python _check/flow_rustdesk.py             # parcours complet + verdict
    python _check/flow_rustdesk.py --keep      # ne supprime pas l'accès créé
"""
import re
import subprocess
import sys
import time

APP = "com.dualdesk.rdp"
for _arg in sys.argv:
    if _arg.startswith("--package="):
        APP = _arg.split("=", 1)[1]
GRID = "com.undatech.opaque.ConnectionGridActivity"
SHEET_MARKERS = ("Gestionnaire de mots de passe", "autofill_save_title")


def adb(*args: str) -> str:
    # L'encodage doit être forcé : le dump d'interface contient des accents UTF-8 que
    # le décodage par défaut du poste (cp1252) rejette, ce qui vide la réponse.
    out = subprocess.run(["adb", *args], capture_output=True, text=True,
                         encoding="utf-8", errors="replace")
    return (out.stdout or "") + (out.stderr or "")


def ui() -> list[dict]:
    # Supprimer d'abord : un dump qui échoue laisserait le relevé précédent, relu comme actuel.
    adb("shell", "rm", "-f", "/sdcard/flow_ui.xml")
    adb("shell", "uiautomator", "dump", "/sdcard/flow_ui.xml")
    xml = adb("shell", "cat", "/sdcard/flow_ui.xml")
    return [dict(re.findall(r'([a-zA-Z-]+)="([^"]*)"', raw))
            for raw in re.findall(r"<node [^>]*/?>", xml)]


def center(node: dict) -> tuple[int, int] | None:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not m:
        return None
    x1, y1, x2, y2 = (int(v) for v in m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap_label(label: str, exact: bool = False) -> bool:
    """Appuie sur le libellé demandé.

    Certains libellés (entrées de menu) sont portés par un TextView non cliquable, la
    ligne parente recevant le toucher : on vise donc le texte, en préférant un nœud
    cliquable quand il en existe un.
    """
    wanted = label.lower()
    candidates = []
    for node in ui():
        text = (node.get("text") or "").strip()
        desc = (node.get("content-desc") or "").strip()
        hit = (text == label or desc == label) if exact else (wanted in text.lower()
                                                              or wanted in desc.lower())
        point = center(node)
        if hit and point:
            candidates.append((node.get("clickable") == "true", point, text or desc))
    candidates.sort(key=lambda c: (not c[0], len(c[2])))
    if candidates:
        _clickable, (x, y), shown = candidates[0]
        adb("shell", "input", "tap", str(x), str(y))
        print(f"  appui {shown!r} en {(x, y)}")
        return True
    return False


def foreground_package() -> str:
    """Paquet de la fenêtre au premier plan, pour détecter un écran système interposé."""
    for line in adb("shell", "dumpsys", "window").splitlines():
        if "mCurrentFocus" in line:
            m = re.search(r"u0 ([\w.]+)/", line)
            if m:
                return m.group(1)
    return ""


def texts() -> list[str]:
    return [n.get("text", "").strip() for n in ui() if n.get("text", "").strip()]


def sheet_visible() -> bool:
    on_screen = texts()
    pkgs = {n.get("package", "") for n in ui()}
    if any("com.google.android.gms" in p for p in pkgs):
        return True
    return any(any(marker in t for marker in SHEET_MARKERS) for t in on_screen)


def main() -> int:
    keep = "--keep" in sys.argv
    adb("logcat", "-c")
    adb("shell", "am", "force-stop", APP)
    time.sleep(2)
    adb("shell", "am", "start", "-n", f"{APP}/{GRID}")
    time.sleep(5)

    # Une installation fraîche peut laisser un écran d'information MIUI au premier plan
    # (par exemple les détails de batterie) : on l'écarte et on reprend l'application.
    if foreground_package() != APP:
        print(f"ecran interpose ({foreground_package()}), retour a l'application")
        adb("shell", "input", "keyevent", "4")
        time.sleep(1)
        adb("shell", "input", "keyevent", "3")
        time.sleep(1)
        adb("shell", "am", "start", "-n", f"{APP}/{GRID}")
        time.sleep(5)
    print("au premier plan :", foreground_package())

    print("ouverture du menu")
    if not tap_label("Plus d'options"):
        adb("shell", "input", "tap", "1121", "254")
        print("  appui de repli sur le bouton de menu en 1121,254")
    time.sleep(2)

    print("ouverture de la boîte RustDesk")
    if not tap_label("Nouvel accès RustDesk"):
        print("ECHEC: entree de menu introuvable ; ecran :")
        for t in texts():
            print("   -", t)
        return 2
    time.sleep(3)

    print("saisie de l'identifiant")
    if not tap_label("Identifiant RustDesk"):
        print("ECHEC: champ identifiant introuvable")
        return 2
    adb("shell", "input", "text", "444000555")
    time.sleep(2)

    print("saisie du mot de passe")
    if not tap_label("Mot de passe"):
        print("ECHEC: champ mot de passe introuvable")
        return 2
    adb("shell", "input", "text", "EssaiDualDesk42")
    time.sleep(3)

    if sheet_visible():
        print("feuille deja presente AVANT validation")
    else:
        print("aucune feuille apres saisie")

    print("validation")
    if not tap_label("OK", exact=True):
        print("ECHEC: bouton OK introuvable")
        return 2
    time.sleep(6)

    verdict = "FEUILLE PRESENTE" if sheet_visible() else "aucune feuille"
    print(f"VERDICT: {verdict}")

    log = adb("logcat", "-d")
    for line in log.splitlines():
        if "setTrackedViews" in line or "startSessionLocked" in line:
            print("  " + line.split(": ", 1)[-1].strip())

    if not keep:
        print("suppression de l'acces d'essai")
        adb("shell", "am", "force-stop", APP)
        time.sleep(2)
        adb("shell", "am", "start", "-n", f"{APP}/{GRID}")
        time.sleep(4)
        if tap_label("Supprimer"):
            time.sleep(2)

    return 0 if verdict == "aucune feuille" else 1


if __name__ == "__main__":
    raise SystemExit(main())
