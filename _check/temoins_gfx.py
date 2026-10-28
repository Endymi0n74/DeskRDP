#!/usr/bin/env python3
"""Test temoin GFX cote poste : la machine annonce-t-elle le canal graphique RDP ?

Le probleme mesure : le client DualDesk negocie le canal GFX, envoie son annonce de
capacites, puis **rien ne revient** et aucune image n'arrive. Deux lectures possibles :
le poste n'annonce pas de GFX, ou le client annonce mal. Un troisieme client tranche.

Ce harnais fait le tour complet, sans lecture manuelle :

1. releve **cote serveur** avant l'essai : compteurs des journaux graphiques et
   evenements AVC de `RdpCoreTS` (les evenements 162/170 sont ceux que Microsoft
   documente pour l'encodage AVC d'une session) ;
2. capture le journal du client temoin (`logcat`, filtre sur son processus) ;
3. attend qu'une session RDP serveur s'ouvre, puis son terme ;
4. releve de nouveau le serveur et extrait du journal client les lignes de negociation
   (`rdpgfx`, `drdynvc`, `CapsAdvertise`, `CapsConfirm`, `ResetGraphics`, `surface`) ;
5. ecrit un compte rendu : base, lignes client, evenements serveur apparus, verdict.

Usage :
    python _check/temoins_gfx.py --releve-seul
    python _check/temoins_gfx.py --paquet com.iiordanov.freeaRDP --etiquette afreerdp

Le mot de passe n'est jamais manipule ici : la session est ouverte par le client,
avec l'acces que l'utilisateur a enregistre.
"""
from __future__ import annotations

import argparse
import datetime as dt
import re
import subprocess
import sys
import time
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
JOURNAUX = (
    "Microsoft-Windows-Rdp-Graphics-RdpAvenc/Operational",
    "Microsoft-Windows-Rdp-Graphics-RdpAvenc/Admin",
    "Microsoft-Windows-Rdp-Graphics-RdpLite/Operational",
    "Microsoft-Windows-Rdp-Graphics-RdpLite/Admin",
    "Microsoft-Windows-RemoteDesktopServices-RdpCoreTS/Operational",
)
# Ce que Microsoft documente comme marqueur de l'encodage AVC cote serveur.
AVC_EVENEMENTS = (162, 163, 170, 171)
MOTS_CLIENT = (
    "rdpgfx", "drdynvc", "capsadvertise", "capsconfirm", "resetgraphics",
    "surface", "avc", "gfx", "graphique", "codec",
)


def powershell(script: str, delai: int = 120) -> str:
    out = subprocess.run(
        ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script],
        capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=delai,
    )
    return (out.stdout or "") + (out.stderr or "")


def releve_serveur() -> dict:
    """Compteurs des journaux graphiques et evenements AVC presents."""
    liste = ",".join(f"'{j}'" for j in JOURNAUX)
    script = f"""
$journaux = @({liste})
foreach ($j in $journaux) {{
  $o = Get-WinEvent -ListLog $j -ErrorAction SilentlyContinue
  if ($o) {{ 'COMPTEUR|' + $j + '|' + $o.RecordCount }}
}}
$ev = Get-WinEvent -LogName 'Microsoft-Windows-RemoteDesktopServices-RdpCoreTS/Operational' -MaxEvents 800 -ErrorAction SilentlyContinue |
  Where-Object {{ $_.Id -in {','.join(str(i) for i in AVC_EVENEMENTS)} }}
foreach ($e in $ev) {{
  $m = ($e.Message -replace '\\s+', ' ')
  'AVC|' + $e.TimeCreated.ToString('dd/MM HH:mm:ss') + '|' + $e.Id + '|' + $m.Substring(0, [Math]::Min(160, $m.Length))
}}
'FIN'
"""
    texte = powershell(script)
    compteurs: dict[str, int] = {}
    avc: list[str] = []
    for ligne in texte.splitlines():
        ligne = ligne.strip()
        if ligne.startswith("COMPTEUR|"):
            _, nom, valeur = ligne.split("|", 2)
            try:
                compteurs[nom] = int(valeur)
            except ValueError:
                pass
        elif ligne.startswith("AVC|"):
            avc.append(ligne[4:])
    return {"compteurs": compteurs, "avc": avc}


def session_rdp_active() -> str:
    """Nom de la session RDP active cote serveur, ou chaine vide."""
    texte = powershell("(query session 2>&1 | Out-String)")
    for ligne in texte.splitlines():
        # «  rdp-tcp#12  compte  2  Actif  » : le mot Actif ne suit que la session distante.
        if re.search(r"rdp-tcp#\d+", ligne) and re.search(r"Actif|Active", ligne):
            return " ".join(ligne.split())
    return ""


def journal_client(paquet: str | None) -> list[str]:
    """Lignes du client temoin pendant l'essai."""
    if paquet:
        pid = subprocess.run(["adb", "shell", "pidof", paquet], capture_output=True,
                             text=True, encoding="utf-8", errors="replace").stdout.strip()
        # Un APK non encore lance n'a pas de processus : on capture alors tout le journal.
        cible = ["--pid", pid.split()[0]] if pid else []
    else:
        cible = []
    subprocess.run(["adb", "logcat", "-c"], capture_output=True)
    capture = subprocess.run(["adb", "logcat", "-v", "time", *cible],
                             capture_output=True, text=True, encoding="utf-8",
                             errors="replace", timeout=600)
    return capture.stdout.splitlines()


def lignes_utiles(lignes: list[str]) -> list[str]:
    utiles = []
    for ligne in lignes:
        bas = ligne.lower()
        if any(mot in bas for mot in MOTS_CLIENT):
            utiles.append(ligne.rstrip())
    return utiles


def verdict(avant: dict, apres: dict, lignes: list[str], session: str) -> str:
    """Une phrase : le poste annonce-t-il le graphique, ou est-ce le client qui faute ?"""
    client_confirm = any("capsconfirm" in l.lower() for l in lignes)
    client_avc = any("avc" in l.lower() and "activ" in l.lower() for l in lignes)
    gfx_ouvert = any("rdpgfx" in l.lower() or "drdynvc" in l.lower() for l in lignes)

    if not session:
        return ("AUCUNE SESSION RDP n'a ete ouverte pendant l'essai : le temoin n'a pas "
                "abouti, le verdict reste en suspens.")
    if client_confirm:
        return ("Le POSTE ANNONCE le graphique : le temoin a recu un CapsConfirm"
                + (" avec AVC disponible." if client_avc else " (sans AVC apparent).")
                + " L'echec observe avec DualDesk vient donc du client.")
    if gfx_ouvert:
        return ("Le POSTE ouvre bien le canal dynamique mais le temoin n'a pas recu de "
                "CapsConfirm : la reponse graphique du poste est en cause, pas notre client.")
    return ("Session ouverte mais rien de graphique dans le journal du temoin : a relire "
            "avec le niveau de journal du client augmente.")


def main() -> int:
    parseur = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parseur.add_argument("--paquet", help="paquet Android du client temoin (filtre logcat)")
    parseur.add_argument("--etiquette", default="temoin", help="nom du compte rendu produit")
    parseur.add_argument("--duree", type=int, default=180,
                         help="attente maximale d'une session RDP, en secondes")
    parseur.add_argument("--releve-seul", action="store_true",
                         help="relever le serveur et sortir, sans essai client")
    args = parseur.parse_args()

    avant = releve_serveur()
    print("== releve serveur initial ==")
    for nom, valeur in avant["compteurs"].items():
        print(f"   {nom} : {valeur}")
    for ligne in avant["avc"]:
        print(f"   AVC -> {ligne}")
    if args.releve_seul:
        return 0

    print(f"== essai temoin : en attente d'une session RDP (max {args.duree} s) ==")
    print("   ouvrez la session depuis le client temoin ; le journal est capture en fond.")
    import threading

    lignes: list[str] = []

    def capturer() -> None:
        lignes.extend(journal_client(args.paquet))

    fil = threading.Thread(target=capturer, daemon=True)
    fil.start()

    session, fin = "", time.time() + args.duree
    while time.time() < fin:
        session = session_rdp_active()
        if session:
            break
        time.sleep(2)

    if session:
        print(f"   session ouverte : {session}")
        # On suit la session jusqu'a sa fermeture (ou 3 minutes), puis on arrete la capture.
        limite = time.time() + 180
        while time.time() < limite and session_rdp_active():
            time.sleep(3)
    else:
        print("   aucune session RDP detectee dans le delai.")

    subprocess.run(["adb", "logcat", "-d"], capture_output=True)
    fil.join(timeout=30)

    apres = releve_serveur()
    utiles = lignes_utiles(lignes)
    sentence = verdict(avant, apres, utiles, session)

    horodatage = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    sortie = RACINE / f"temoins-{args.etiquette}-{horodatage}.txt"
    with sortie.open("w", encoding="utf-8") as f:
        f.write(f"Test temoin GFX — {args.etiquette} — {horodatage}\n")
        f.write(f"client : {args.paquet or 'non precise'}\n")
        f.write(f"session RDP vue cote serveur : {session or 'aucune'}\n\n")
        f.write("== journal serveur, compteurs ==\n")
        for nom in JOURNAUX:
            f.write(f"   {nom} : {avant['compteurs'].get(nom, '?')} -> {apres['compteurs'].get(nom, '?')}\n")
        f.write("\n== evenements AVC cote serveur (162/163/170/171) ==\n")
        nouveaux = [l for l in apres["avc"] if l not in avant["avc"]]
        for ligne in nouveaux:
            f.write(f"   {ligne}\n")
        if not nouveaux:
            f.write("   aucun nouvel evenement\n")
        f.write(f"\n== journal du client ({len(utiles)} lignes utiles) ==\n")
        for ligne in utiles:
            f.write(f"   {ligne}\n")
        f.write(f"\n== verdict ==\n   {sentence}\n")

    print(f"\n== verdict ==\n   {sentence}")
    print(f"\ncompte rendu : {sortie}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
