#!/usr/bin/env python3
"""Écarte la feuille du gestionnaire de mots de passe par « Pas maintenant ».

Ne jamais appuyer sur « Jamais » pendant une campagne : ce refus est mémorisé par le service
pour le paquet (il survit à la désinstallation et à la réinstallation) et rend la mesure muette :
la feuille ne revient plus même avec le code fautif.

Usage : python _check/dismiss_sheet.py
"""
import re
import subprocess
import sys
import time


def adb(*args: str) -> str:
    out = subprocess.run(["adb", *args], capture_output=True, text=True,
                         encoding="utf-8", errors="replace")
    return (out.stdout or "") + (out.stderr or "")


def tap_label(label: str) -> bool:
    adb("shell", "rm", "-f", "/sdcard/sheet_ui.xml")
    adb("shell", "uiautomator", "dump", "/sdcard/sheet_ui.xml")
    xml = adb("shell", "cat", "/sdcard/sheet_ui.xml")
    for raw in re.findall(r"<node [^>]*/?>", xml):
        attrs = dict(re.findall(r'([a-zA-Z-]+)="([^"]*)"', raw))
        if (attrs.get("text") or "").strip() != label:
            continue
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", attrs.get("bounds", ""))
        if not m:
            continue
        x1, y1, x2, y2 = (int(v) for v in m.groups())
        x, y = (x1 + x2) // 2, (y1 + y2) // 2
        adb("shell", "input", "tap", str(x), str(y))
        print(f"appui « {label} » en {x},{y}")
        return True
    return False


def main() -> int:
    if tap_label("Pas maintenant"):
        time.sleep(3)
        return 0
    print("aucune feuille à écarter")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
