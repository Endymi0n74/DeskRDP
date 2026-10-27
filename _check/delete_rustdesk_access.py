#!/usr/bin/env python3
"""Supprime un accès de la grille, pour ne pas laisser traîner les accès d'essai.

Le geste est un appui long (un glissement de coordonnées identiques, maintenu), puis
la confirmation proposée par l'application.

Usage : python _check/delete_rustdesk_access.py <identifiant>
"""
import re
import subprocess
import sys
import time

APP = "com.dualdesk.rdp"
GRID = "com.undatech.opaque.ConnectionGridActivity"


def adb(*args: str) -> str:
    out = subprocess.run(["adb", *args], capture_output=True, text=True,
                         encoding="utf-8", errors="replace")
    return (out.stdout or "") + (out.stderr or "")


def nodes() -> list[dict]:
    # Supprimer d'abord : un dump qui échoue laisserait le relevé précédent, relu comme actuel.
    adb("shell", "rm", "-f", "/sdcard/del_ui.xml")
    adb("shell", "uiautomator", "dump", "/sdcard/del_ui.xml")
    xml = adb("shell", "cat", "/sdcard/del_ui.xml")
    return [dict(re.findall(r'([a-zA-Z-]+)="([^"]*)"', raw))
            for raw in re.findall(r"<node [^>]*/?>", xml)]


def center(node: dict) -> tuple[int, int] | None:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not m:
        return None
    x1, y1, x2, y2 = (int(v) for v in m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap_text(fragment: str) -> bool:
    """Appuie sur le libellé demandé.

    Le titre du dialogue reprend souvent le mot du bouton (« … modifier ou supprimer la connexion
    … ? » face au bouton « Supprimer ») : on préfère un texte exact, puis un nœud cliquable, puis
    le libellé le plus court, avant de se rabattre sur le premier texte qui contient le mot.
    """
    candidates = []
    for node in nodes():
        text = (node.get("text") or "").strip()
        if fragment.lower() in text.lower():
            point = center(node)
            if point:
                exact = 0 if text == fragment else 1
                clickable = 0 if node.get("clickable") == "true" else 1
                candidates.append((exact, clickable, len(text), text, point))
    if not candidates:
        return False
    candidates.sort()
    _exact, _clickable, _len, text, point = candidates[0]
    adb("shell", "input", "tap", str(point[0]), str(point[1]))
    print(f"  appui {text!r} en {point}")
    return True


def main() -> int:
    wanted = sys.argv[1]
    adb("shell", "am", "force-stop", APP)
    time.sleep(2)
    adb("shell", "am", "start", "-n", f"{APP}/{GRID}")
    time.sleep(5)

    target = None
    for node in nodes():
        if wanted in (node.get("text") or ""):
            target = center(node)
            break
    if not target:
        print(f"acces {wanted!r} absent de la grille : rien a supprimer")
        return 0

    x, y = target
    print(f"appui long sur l'acces en {x},{y}")
    adb("shell", "input", "swipe", str(x), str(y), str(x), str(y), "1200")
    time.sleep(2)

    if not tap_text("Supprimer"):
        print("aucune action de suppression proposee")
        return 1
    time.sleep(2)
    tap_text("Supprimer")
    time.sleep(3)

    remaining = [n.get("text", "").strip() for n in nodes() if n.get("text", "").strip()]
    gone = not any(wanted in t for t in remaining)
    print("VERDICT:", "acces supprime" if gone else f"acces encore present ({remaining[:6]})")
    return 0 if gone else 1


if __name__ == "__main__":
    raise SystemExit(main())
