#!/usr/bin/env python3
"""Rejoue « créer un accès RDP » et dit si la feuille du gestionnaire de mots de passe
s'interpose à la validation, en relevant le journal du framework dans l'ordre.

L'écran d'un accès RDP est l'activité `com.iiordanov.bVNC.aRDP` (disposition `main_rdp.xml`).
Le champ mot de passe est sous la ligne de flottaison : le parcours fait défiler le formulaire
jusqu'à le trouver, comme le ferait un doigt.

La saisie est vérifiée champ par champ avant la validation : un formulaire incomplet (identifiant
vide) n'est pas un essai loyal, le service ne proposant d'enregistrer que ce qui ressemble à un
compte. `--path=back` sort par le retour arrière au lieu du bouton « Enregistrer », ce qui emprunte
le chemin `onPause()` de l'éditeur.

Usage :
    python _check/flow_rdp_access.py [--package=com.exemple.app] [--path=save|back] [--keep]
"""
import re
import subprocess
import sys
import time

APP = "com.dualdesk.rdp"
PATH = "save"
for _arg in sys.argv:
    if _arg.startswith("--package="):
        APP = _arg.split("=", 1)[1]
    if _arg.startswith("--path="):
        PATH = _arg.split("=", 1)[1]
GRID = "com.undatech.opaque.ConnectionGridActivity"
SHEET_MARKERS = ("Gestionnaire de mots de passe", "Google Password Manager",
                 "Enregistrer le mot de passe", "Save password")
HOST = "192.0.2.10"
USER = "essaidualdesk"
PASSWORD = "EssaiRdpDualDesk42"
# Lignes du journal qui disent ce qui s'est réellement passé.
WATCH = (
    "save called",
    "saveConnectionAndCloseLayout called",
    "onPause called",
    "cancel() called by app",
    "cancelSessionLocked",
    "setTrackedViews",
    "PasswordSaveInfoFactory",
    "SaveInfo",
    "AutofillManager",
)


def adb(*args: str) -> str:
    # Encodage forcé : le relevé contient des accents UTF-8 que cp1252 rejette.
    out = subprocess.run(["adb", *args], capture_output=True, text=True,
                         encoding="utf-8", errors="replace")
    return (out.stdout or "") + (out.stderr or "")


def nodes() -> list[dict]:
    # Supprimer d'abord : un dump qui échoue laisserait le relevé précédent, relu comme actuel.
    adb("shell", "rm", "-f", "/sdcard/rdp_ui.xml")
    adb("shell", "uiautomator", "dump", "/sdcard/rdp_ui.xml")
    xml = adb("shell", "cat", "/sdcard/rdp_ui.xml")
    return [dict(re.findall(r'([a-zA-Z-]+)="([^"]*)"', raw))
            for raw in re.findall(r"<node [^>]*/?>", xml)]


def center(node: dict):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not m:
        return None
    x1, y1, x2, y2 = (int(v) for v in m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def find(by_id: str = "", by_text: str = "", desc: str = ""):
    for node in nodes():
        if by_id and node.get("resource-id", "").endswith(by_id):
            return node
        if desc and desc.lower() in (node.get("content-desc") or "").lower():
            return node
        if by_text and by_text.lower() in (node.get("text") or "").lower():
            return node
    return None


def tap(node: dict) -> bool:
    point = center(node)
    if not point:
        return False
    adb("shell", "input", "tap", str(point[0]), str(point[1]))
    print(f"  appui {node.get('resource-id', '') or node.get('text', '') or node.get('content-desc', '')} en {point}")
    return True


def foreground() -> str:
    for line in adb("shell", "dumpsys", "window").splitlines():
        if "mCurrentFocus" in line:
            return line.split("mCurrentFocus")[-1].strip()
    return "?"


def current_package() -> str:
    for line in adb("shell", "dumpsys", "window").splitlines():
        if "mCurrentFocus" in line:
            m = re.search(r"u0 ([\w.]+)/", line)
            if m:
                return m.group(1)
    return ""


def field_state(res_id: str):
    node = find(by_id=res_id)
    return None if node is None else (node.get("text") or "")


def type_into(node: dict, text: str) -> bool:
    """Écrit dans un champ vide, sans chercher à effacer : les évènements d'effacement dépendent
    du focus et du clavier, et un échec silencieux donnerait une valeur concaténée."""
    if not tap(node):
        return False
    time.sleep(1)
    adb("shell", "input", "text", text)
    time.sleep(1)
    return True


def scroll_to(res_id: str, tries: int = 10):
    """Le glissement reste dans la moitié haute : le clavier ouvert occupe le bas, et un
    glissement qui commence dedans ne fait pas défiler le formulaire."""
    for _ in range(tries):
        node = find(by_id=res_id)
        if node:
            return node
        adb("shell", "input", "swipe", "610", "1250", "610", "560", "500")
        time.sleep(1)
    return None


def sheet_visible() -> bool:
    parsed = nodes()
    if any("com.google.android.gms" in n.get("package", "") for n in parsed):
        return True
    return any(any(m in (n.get("text") or "") for m in SHEET_MARKERS) for n in parsed)


def grid_entries() -> list[str]:
    """Textes relevés dans la grille, pour prouver que l'accès a bien été créé."""
    seen = []
    for node in nodes():
        text = (node.get("text") or "").strip()
        if text and text not in seen:
            seen.append(text)
    return seen


def raw_log() -> list[str]:
    return [line.strip() for line in adb("logcat", "-d", "-v", "time").splitlines()]


def lock_rotation(portrait_only: bool) -> None:
    """Verrouille l'orientation pendant la mesure.

    Sans cela l'écran peut pivoter entre le relevé de la hiérarchie et l'appui : les
    coordonnées relevées ne valent plus, l'appui tombe à côté et la mesure devient
    silencieusement vide (constaté le 27/10 : appui calculé à x=2340 sur un écran de 1220).
    """
    if portrait_only:
        adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
        adb("shell", "settings", "put", "system", "user_rotation", "0")
    else:
        adb("shell", "settings", "put", "system", "accelerometer_rotation", "1")
    time.sleep(1)


def save_and_confirm():
    """Appuie sur « Enregistrer » et vérifie que l'éditeur s'est bien fermé.

    L'enregistrement abouti appelle `finish()` : l'éditeur disparaît. Un appui qui n'a rien
    déclenché laisse l'écran ouvert, et conclure « aucune feuille » sur cet état ne veut rien dire.
    """
    for attempt in range(1, 4):
        node = find(by_id="itemSave") or find(desc="Enregistrer")
        if not node:
            return False, "bouton introuvable"
        point = center(node)
        if not point:
            return False, "bouton sans coordonnees"
        adb("shell", "input", "tap", str(point[0]), str(point[1]))
        print(f"  appui itemSave en {point} (tentative {attempt})")
        time.sleep(3)
        if find(by_id="textPASSWORD") is None and find(by_id="itemSave") is None:
            return True, "editeur ferme"
        print("    l'editeur est toujours ouvert : nouvel essai")
    return False, "l'appui n'a pas abouti"


def main() -> int:
    keep = "--keep" in sys.argv
    lock_rotation(True)
    adb("logcat", "-c")
    adb("shell", "am", "force-stop", APP)
    time.sleep(2)
    adb("shell", "am", "start", "-n", f"{APP}/{GRID}")
    time.sleep(5)

    if current_package() != APP:
        print(f"ecran interpose ({current_package()}), retour a l'application")
        adb("shell", "input", "keyevent", "4")
        time.sleep(1)
        adb("shell", "input", "keyevent", "3")
        time.sleep(1)
        adb("shell", "am", "start", "-n", f"{APP}/{GRID}")
        time.sleep(5)

    print("ouverture de l'assistant de nouvelle connexion")
    fab = find(by_id="addNewConnection")
    if not fab or not tap(fab):
        print("ECHEC: bouton + introuvable")
        return 2
    time.sleep(4)

    print("saisie manuelle")
    manual = find(by_id="enterManually") or find(by_text="SAISIR MANUELLEMENT")
    if not manual or not tap(manual):
        print("ECHEC: entree de saisie manuelle introuvable")
        return 2
    time.sleep(5)

    if current_package() != APP:
        print(f"ECHEC: l'editeur n'est pas au premier plan ({current_package()})")
        return 2
    print("editeur ouvert :", foreground())

    print("remplissage de l'adresse")
    host = find(by_id="textIP")
    if not host or not type_into(host, HOST):
        print("ECHEC: champ adresse introuvable")
        return 2
    print("  adresse dans le champ :", repr(field_state("textIP")))

    print("remplissage de l'identifiant")
    user = find(by_id="textUsername")
    if user:
        type_into(user, USER)
        print("  identifiant dans le champ :", repr(field_state("textUsername")))

    print("defilement jusqu'au mot de passe")
    password = scroll_to("textPASSWORD")
    if not password:
        print("ECHEC: champ mot de passe introuvable apres defilement")
        return 2
    type_into(password, PASSWORD)
    print("  mot de passe dans le champ (masque) :",
          repr(field_state("textPASSWORD")), "password =",
          (find(by_id="textPASSWORD") or {}).get("password"))
    if not field_state("textPASSWORD"):
        print("ECHEC: le mot de passe n'a pas ete saisi")
        return 2

    print("aucune feuille apres saisie" if not sheet_visible() else "feuille deja presente AVANT validation")

    if PATH == "back":
        print("sortie par retour arriere (chemin onPause)")
        for _ in range(3):
            adb("shell", "input", "keyevent", "4")
            time.sleep(1)
            if current_package() != APP or find(by_id="textPASSWORD") is None:
                break
    else:
        print("enregistrement")
        ok, message = save_and_confirm()
        print("  enregistrement :", message)
        if not ok:
            print("ECHEC: l'action d'enregistrement n'a pas abouti, la mesure serait vide")
            lock_rotation(False)
            return 3

    time.sleep(6)
    first = sheet_visible()
    print("a +6 s :", "feuille presente" if first else "aucune feuille",
          "| premier plan :", foreground())
    time.sleep(10)
    second = sheet_visible()
    print("a +16 s :", "feuille presente" if second else "aucune feuille",
          "| premier plan :", foreground())

    if not first and not second:
        adb("shell", "am", "force-stop", APP)
        time.sleep(2)
        adb("shell", "am", "start", "-n", f"{APP}/{GRID}")
        time.sleep(5)
    entries = grid_entries()
    print("grille apres validation :", entries or "(aucune carte relevee)")

    raw = raw_log()
    print("--- marqueurs du journal ---")
    for key in ("save called", "saveConnectionAndCloseLayout called", "onPause called",
                "cancel() called by app", "onActivityFinishing", "setSaveUiState",
                "PasswordSaveInfoFactory", "setTrackedViews"):
        hits = [line for line in raw if key in line]
        print(f"  {'OUI' if hits else 'non'} : {key}" + (f" ({len(hits)})" if hits else ""))

    print("--- extralits, dans l'ordre ---")
    for line in raw:
        if any(w in line for w in WATCH):
            print("  " + line[:170])

    verdict = "FEUILLE PRESENTE" if (first or second) else "aucune feuille"
    print(f"VERDICT ({PATH}) : {verdict}")

    if not keep:
        print("fin de mesure : application arretee")
        adb("shell", "am", "force-stop", APP)
    lock_rotation(False)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
