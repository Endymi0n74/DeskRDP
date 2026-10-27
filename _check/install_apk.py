#!/usr/bin/env python3
"""Installe un APK en franchissant la confirmation « Installer via USB » de MIUI.

MIUI affiche une boîte de confirmation avec un décompte de quelques secondes ; il faut
appuyer sur « Installer » avant l'expiration, sinon la demande est refusée. Les
coordonnées ne sont pas stables d'un essai à l'autre : cet outil relève l'écran en
boucle et appuie sur le bouton dès qu'il apparaît.

Usage : python _check/install_apk.py <chemin.apk>
"""
import re
import subprocess
import sys
import time

X = 0


def adb(*args: str) -> str:
    # Encodage forcé : le dump contient des accents UTF-8 que cp1252 rejette.
    out = subprocess.run(["adb", *args], capture_output=True, text=True,
                         encoding="utf-8", errors="replace")
    return (out.stdout or "") + (out.stderr or "")


def find_button(label: str) -> tuple[int, int] | None:
    # Supprimer d'abord : un dump qui échoue laisserait le relevé précédent, relu comme actuel.
    xml = adb("shell", "rm", "-f", "/sdcard/installer_ui.xml") + \
        adb("shell", "uiautomator", "dump", "/sdcard/installer_ui.xml") + \
        adb("shell", "cat", "/sdcard/installer_ui.xml")
    for raw in re.findall(r"<node [^>]*/?>", xml):
        attrs = dict(re.findall(r'([a-zA-Z-]+)="([^"]*)"', raw))
        if attrs.get("clickable") != "true":
            continue
        if (attrs.get("text") or "").strip() != label:
            continue
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", attrs.get("bounds", ""))
        if not m:
            continue
        x1, y1, x2, y2 = (int(v) for v in m.groups())
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None


def main() -> int:
    apk = sys.argv[1]
    proc = subprocess.Popen(["adb", "install", "-r", apk],
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    deadline = time.time() + 120
    while proc.poll() is None and time.time() < deadline:
        # Pas de drapeau « déjà appuyé » : si l'appui a manqué la boîte, il faut réessayer.
        point = find_button("Installer")
        if point:
            x, y = point
            print(f"confirmation detectee, appui en {x},{y}", flush=True)
            adb("shell", "input", "tap", str(x), str(y))
        time.sleep(0.5)

    output = proc.stdout.read() if proc.stdout else ""
    print(output.strip())
    print("code de sortie :", proc.returncode)
    return 0 if proc.returncode == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
