#!/usr/bin/env bash
# Attend qu'une session RDP soit reellement affichee, puis lance la mesure de reconnexion.
# Sort tout seul au bout de 40 minutes sans session (240 x 10 s).
cd "$(dirname "$0")/.." || exit 1
LOG=../tmpshot/reconnect
for _ in $(seq 1 240); do
  if python _check/mesure_reconnexion.py --etat >/dev/null 2>&1; then
    echo "$(date +%T) session ouverte, mesure lancee" >> "$LOG/veille.log"
    python _check/mesure_reconnexion.py >> "$LOG/mesure.log" 2>&1
    echo "$(date +%T) mesure terminee (code $?)" >> "$LOG/veille.log"
    exit 0
  fi
  sleep 10
done
echo "$(date +%T) aucune session apres 40 min, veille abandonnee" >> "$LOG/veille.log"
