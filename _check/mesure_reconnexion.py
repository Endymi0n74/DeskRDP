#!/usr/bin/env python3
"""Mesure de la reconnexion automatique sur une session RDP reellement etablie.

Le test unitaire de ReconnectPolicy prouve la table des delais, pas le cablage : seule une
session vivante qu'on coupe montre que le handler programme bien les relances, aux bons
delais, et s'arrete au cinquieme essai.

Deroulement :
  1. verifier qu'une session est ouverte (et refuser de mesurer sinon) ;
  2. couper Wi-Fi et donnees mobiles (le tunnel USB d'adb reste, lui) ;
  3. relever les jalons du journal avec leurs horodatages ;
  4. rendre le reseau et confronter chaque ecart a la table attendue.

Usage :
    python _check/mesure_reconnexion.py            # mesure complete
    python _check/mesure_reconnexion.py --etat     # seulement l'etat de la session
"""
import re
import subprocess
import sys
import time

APP = "com.deskrdp.rdp"
CANVAS = "com.iiordanov.bVNC.RemoteCanvasActivity"
TAG = "RemoteCanvasHandler"

# Table de ReconnectPolicy : delai avant l'essai n, en secondes.
ATTENDUS = [2, 5, 10, 20, 30]
MAX_ATTEMPTS = 5

# Un dialogue modal (identifiants, certificat, echec) s'interpose entre l'activite et l'ecran.
DIALOGUES = (
    "Veuillez entrer les identifiants",
    "identifiants RDP",
    "certificat",
)

RE_SCHEDULE = re.compile(r"(\d\d-\d\d) (\d\d:\d\d:\d\d\.\d+).*Scheduling reconnect (\d+) in (\d+)s")
RE_ATTEMPT = re.compile(r"(\d\d-\d\d) (\d\d:\d\d:\d\d\.\d+).*RECONNECT attempt (\d+) of (\d+)")


def adb(*args: str, timeout: int = 30) -> str:
    return subprocess.run(
        ["adb", *args], capture_output=True, text=True, timeout=timeout,
        encoding="utf-8", errors="replace",
    ).stdout


def focus() -> str:
    for line in adb("shell", "dumpsys", "window").splitlines():
        if "mCurrentFocus" in line:
            return line.strip()
    return ""


def etat_session() -> tuple[bool, str]:
    """Vrai si la toile RDP est au premier plan sans dialogue modal devant elle."""
    courant = focus()
    if any(d.lower() in courant.lower() for d in DIALOGUES):
        return False, f"un dialogue est ouvert devant la session : {courant}"
    if CANVAS not in courant:
        return False, f"l'activite RDP n'est pas au premier plan : {courant or '(illisible)'}"
    return True, courant


def reseau(couper: bool, data_aussi: bool = True) -> dict:
    """Coupe ou rend le reseau. Retourne l'etat constate, pas l'etat demande."""
    adb("shell", "svc", "wifi", "disable" if couper else "enable")
    if data_aussi:
        adb("shell", "svc", "data", "disable" if couper else "enable")
    time.sleep(5 if couper else 8)
    return {
        "wifi": adb("shell", "settings", "get", "global", "wifi_on").strip(),
        "data": adb("shell", "settings", "get", "global", "mobile_data").strip(),
    }


def secondes(horodatage: str) -> float:
    """'HH:MM:SS.mmm' en secondes depuis minuit, pour les ecarts."""
    heures, minutes, reste = horodatage.split(":")
    return int(heures) * 3600 + int(minutes) * 60 + float(reste)


def jalons(depuis: str) -> tuple[list, list]:
    """Relit le journal et extrait les programmations et les tentatives."""
    journal = adb("logcat", "-d", "-v", "time", timeout=60)
    schedules, tentatives = [], []
    for ligne in journal.splitlines():
        if TAG not in ligne:
            continue
        m = RE_SCHEDULE.search(ligne)
        if m:
            schedules.append((m.group(2), int(m.group(3)), int(m.group(4))))
            continue
        m = RE_ATTEMPT.search(ligne)
        if m:
            tentatives.append((m.group(2), int(m.group(3))))
    return schedules, tentatives


def mesurer(duree_max: int = 300) -> int:
    vivante, detail = etat_session()
    print(f"etat de la session : {'ouverte' if vivante else 'PAS ouverte'}")
    print(f"  premier plan     : {detail}")
    if not vivante:
        print()
        print("Mesure refusee : sans session etablie, il n'y a rien a couper.")
        print("Ouvrir l'acces dans la grille et saisir le mot de passe une fois")
        print("(case « Conserver » cochee), puis relancer ce script.")
        return 2

    print("journal vide avant la coupure")
    adb("logcat", "-c")

    etat = reseau(True)
    print(f"reseau coupe : wifi_on={etat['wifi']} mobile_data={etat['data']}")
    if etat["wifi"] != "0":
        print("ECHEC: le Wi-Fi n'a pas pu etre coupe, la mesure n'aurait aucun sens")
        reseau(False)
        return 3

    debut = time.time()
    schedules: list = []
    tentatives: list = []
    while time.time() - debut < duree_max:
        time.sleep(10)
        schedules, tentatives = jalons("")
        print(f"  +{int(time.time() - debut):3d} s : {len(schedules)} programmation(s), "
              f"{len(tentatives)} tentative(s)")
        # Le budget est consomme quand la derniere programmation a eu lieu et que sa relance
        # a echoue : inutile d'attendre la fin du delai pour conclure.
        if len(schedules) >= MAX_ATTEMPTS and len(tentatives) >= MAX_ATTEMPTS:
            break

    etat = reseau(False)
    print(f"reseau rendu : wifi_on={etat['wifi']} mobile_data={etat['data']}")

    print()
    print("--- programmations ---")
    for horodatage, essai, delai in schedules:
        print(f"  {horodatage}  essai {essai} dans {delai} s")
    print("--- relances effectuees ---")
    for horodatage, essai in tentatives:
        print(f"  {horodatage}  essai {essai}")

    print()
    print("--- delais constates ---")
    corrects = 0
    for horodatage, essai, delai in schedules:
        relance = next((t for t, e in tentatives if e == essai and t >= horodatage), None)
        attendu = ATTENDUS[min(essai, len(ATTENDUS)) - 1]
        if relance is None:
            print(f"  essai {essai} : programme dans {delai} s, relance non vue")
            continue
        ecart = secondes(relance) - secondes(horodatage)
        tolerance = 1.5
        ok = abs(ecart - attendu) <= tolerance and delai == attendu
        corrects += 1 if ok else 0
        print(f"  essai {essai} : {delai} s annonce, {ecart:.1f} s mesure "
              f"(attendu {attendu} s) {'OK' if ok else 'ECART'}")

    print()
    if not schedules:
        print("VERDICT : aucune relance programmee — la coupure n'a pas ete vue par la session, "
              "ou la session n'etait plus maintenue (maintainConnection).")
        return 4
    if corrects == len(ATTENDUS) and len(tentatives) >= MAX_ATTEMPTS:
        print(f"VERDICT : les {len(ATTENDUS)} delais sont conformes et la relance s'arrete "
              f"apres {MAX_ATTEMPTS} essais.")
        return 0
    print(f"VERDICT : {corrects}/{len(schedules)} delais conformes, "
          f"{len(tentatives)} relance(s) vue(s).")
    return 1


def main() -> int:
    if "--etat" in sys.argv:
        vivante, detail = etat_session()
        print(f"{'ouverte' if vivante else 'PAS ouverte'} : {detail}")
        return 0 if vivante else 2
    return mesurer()


if __name__ == "__main__":
    raise SystemExit(main())
