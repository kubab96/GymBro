#!/bin/sh
# Pobiera jedna karte produktu Frisco.pl i wypisuje wiersz paczki
# sklepowej. Uzycie i pelny przebieg: docs/kalorie.md, rozdzial
# "Jak odtworzyc paczke sklepowa".
#
# Karte odkladamy spakowana w karty/. Bez tego poprawka czytnika oznacza
# pobranie wszystkiego od nowa, a to tysiac osiemset zadan do cudzego
# serwisu - i wlasnie dlatego da sie przeparsowac komplet w szesc minut,
# bez ani jednego zadania.
#
# FRISCO_D wskazuje katalog roboczy; musi miec podkatalog karty/.

D="${FRISCO_D:-.}"
TOOLS=$(dirname "$0")

# Przegladarkowa nazwa klienta, bo Frisco odrzuca domyslna nazwe curla.
# To nie obejscie zabezpieczenia: robots.txt tej witryny zezwala i na
# listy kategorii, i na karty produktow.
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36"

SCIEZKA="$1"
ID=$(echo "$SCIEZKA" | sed 's|/pid,||; s|/.*||')
GZ="$D/karty/$ID.html.gz"

if [ ! -s "$GZ" ]; then
    TMP="$D/karty/$ID.pobierane"

    # --compressed nie jest ozdoba: karta wazy 1,6 MB bez kompresji
    # i 148 kB z nia. Bez tej flagi dwanascie kart schodzilo minutami
    # i wygladalo, jakby sklep ograniczal ruch.
    curl -sL -A "$UA" --compressed --max-time 60 "https://www.frisco.pl$SCIEZKA" -o "$TMP"

    # Odpowiedz krotsza niz 50 kB to nie karta produktu, a blad albo
    # zaslona. Nie wolno jej zapisac jako "produkt bez wartosci
    # odzywczych" - nieudane pobranie i puste dane musza sie roznic,
    # inaczej luka w danych wyglada dokladnie jak luka w asortymencie.
    if [ "$(wc -c < "$TMP")" -lt 50000 ]; then
        printf '%s\tODRZUT\tpobranie nieudane: %s B\n' "$ID" "$(wc -c < "$TMP")"
        rm -f "$TMP"; exit 0
    fi
    gzip -c "$TMP" > "$GZ"
    rm -f "$TMP"
fi

# Nazwe czytnik bierze sam z tytulu strony: sciezka jest bez polskich
# znakow i bez wielkich liter, a tytul podaje tez gramature sztuki.
# Zapas na wypadek karty bez tytulu.
ZAPAS=$(echo "$SCIEZKA" | sed 's|.*/n,||; s|/stn,product||; s|-| |g')

gzip -dc "$GZ" | awk -v zapas="$ZAPAS" -f "$TOOLS/frisco-pola.awk"
