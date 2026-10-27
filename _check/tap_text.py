#!/usr/bin/env python3
"""Trouve un élément à l'écran par son texte et l'appuie.

MIUI interpose des confirmations (installation, autorisations) qui ne peuvent pas
être franchies par des coordonnées figées : la position change et un décompte court.
Cet outil relève la hiérarchie, cherche le texte demandé et appuie au centre du
premier nœud cliquable qui correspond.

Usage :
    python _check/tap_text.py "Installer"            # appuie sur le bouton
    python _check/tap_text.py "Installer" --dry-run  # affiche seulement les cibles
"""
import re
import subprocess
import sys


def adb(*args: str) -> str:
    # Encodage forcé : le dump contient des accents UTF-8 que cp1252 rejette.
    out = subprocess.run(["adb", *args], capture_output=True, text=True,
                         encoding="utf-8", errors="replace")
    return (out.stdout or "") + (out.stderr or "")


def dump() -> str:
    # Le fichier distant est supprimé avant : si le dump échoue, un relevé antérieur resterait
    # en place et serait relu comme s'il était actuel.
    adb("shell", "rm", "-f", "/sdcard/tap_text_ui.xml")
    adb("shell", "uiautomator", "dump", "/sdcard/tap_text_ui.xml")
    return adb("shell", "cat", "/sdcard/tap_text_ui.xml")


def nodes(xml: str) -> list[dict]:
    out = []
    for raw in re.findall(r"<node [^>]*/?>", xml):
        attrs = dict(re.findall(r'([a-zA-Z-]+)="([^"]*)"', raw))
        out.append(attrs)
    return out


def center(bounds: str) -> tuple[int, int] | None:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not m:
        return None
    x1, y1, x2, y2 = (int(v) for v in m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def main() -> int:
    wanted = sys.argv[1]
    dry = "--dry-run" in sys.argv
    xml = dump()
    matches = []
    for n in nodes(xml):
        text = (n.get("text") or "").strip()
        desc = (n.get("content-desc") or "").strip()
        if wanted.lower() in text.lower() or wanted.lower() in desc.lower():
            point = center(n.get("bounds", ""))
            if point:
                matches.append((text or desc, point, n.get("class", ""), n.get("clickable")))

    if not matches:
        print(f"aucune cible pour {wanted!r}")
        print("textes présents :", sorted({(n.get('text') or '').strip()
                                           for n in nodes(xml) if (n.get('text') or '').strip()}))
        return 1

    # Un titre peut contenir le même mot que le bouton : les nœuds cliquables passent d'abord.
    matches.sort(key=lambda m: (m[3] != "true", len(m[0])))
    for text, point, cls, clickable in matches[:6]:
        print(f"{text!r} {point} {cls} cliquable={clickable}")
    if dry:
        return 0

    text, (x, y), _cls, _c = matches[0]
    print(f"appui sur {text!r} en {x},{y}")
    print(adb("shell", "input", "tap", str(x), str(y)).strip())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
