#!/bin/sh
# Porównuje schemat świeżej instalacji ze schematem bazy po migracjach.
#
#   tools/schemat.sh kopie/gymbro-2026-11-15-przed-etapem-2.db
#
# Po co: onCreate i onUpgrade to dwie osobne drogi do tej samej bazy i nic
# nie pilnuje, żeby kończyły się w tym samym miejscu. Kolumna dopisana tylko
# w migracji działa na telefonie, który przeszedł wszystkie wersje, i wywraca
# się na świeżej instalacji - a tej drugiej nikt tu nie ogląda, bo telefon
# jest jeden. Testu jednostkowego na to nie ma: SQLiteOpenHelper wymaga
# Androida, a na JVM nie ma SQLite bez dokładania zależności.
#
# Jak: wyciąga SQL z data/Schema.kt (schemat-wyciag.awk), składa z niego bazę
# tak, jak robi to onCreate, i porównuje z podaną kopią - tabele, kolumny,
# typy, NOT NULL, wartości domyślne, klucze i indeksy. Kolejność kolumn się
# nie liczy, bo kod czyta je po nazwie. Dane z kopii nie są ruszane: pracuje
# na jej duplikacie w katalogu tymczasowym.
#
# Kolejność list w LISTY musi iść za onCreate. Po dopisaniu tam nowej listy
# trzeba dopisać ją i tutaj - inaczej porównanie pokaże jej brak.
SQ="/c/Users/kubab/AppData/Local/Android/Sdk/platform-tools/sqlite3.exe"
LISTY="CREATE V4_TABLES V5_TABLES V6_COLUMNS V7_STATEMENTS V8_STATEMENTS V16_STATEMENTS V19_STATEMENTS V20_STATEMENTS V24_STATEMENTS V27_STATEMENTS V29_STATEMENTS"

KOPIA="$1"
if [ -z "$KOPIA" ] || [ ! -f "$KOPIA" ]; then
  echo "użycie: tools/schemat.sh <kopia bazy z telefonu>" >&2
  exit 2
fi
KOPIA="$(cd "$(dirname "$KOPIA")" && pwd)/$(basename "$KOPIA")"
cd "$(dirname "$0")/.." || exit 1

T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT

awk -f tools/schemat-wyciag.awk app/src/main/java/pl/gymbro/data/Schema.kt > "$T/sql.tsv"
TAB="$(printf '\t')"
for s in $LISTY; do
  grep "^$s$TAB" "$T/sql.tsv" | cut -f2 | sed 's/$/;/'
done > "$T/swieza.sql"

if ! "$SQ" "$T/swieza.db" < "$T/swieza.sql" 2> "$T/bledy.txt" || [ -s "$T/bledy.txt" ]; then
  echo "Świeża baza nie daje się założyć z tego, co jest w Schema.kt:" >&2
  cat "$T/bledy.txt" >&2
  exit 1
fi
cp "$KOPIA" "$T/telefon.db"

opis() {
  for t in $("$SQ" "$1" "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name<>'android_metadata' ORDER BY name;" | tr -d '\r'); do
    "$SQ" "$1" "SELECT '$t', name, upper(type), \"notnull\", coalesce(dflt_value,''), pk FROM pragma_table_info('$t') ORDER BY name;"
    "$SQ" "$1" "SELECT '$t', 'FK', \"from\", \"table\", \"to\", on_delete FROM pragma_foreign_key_list('$t') ORDER BY \"from\";"
  done | tr -d '\r'
  "$SQ" "$1" "SELECT 'INDEX', name, replace(replace(replace(sql,'IF NOT EXISTS ',''),'  ',' '),char(10),' ') FROM sqlite_master WHERE type='index' AND sql IS NOT NULL ORDER BY name;" | tr -d '\r'
}

opis "$T/swieza.db" > "$T/swieza.txt"
opis "$T/telefon.db" > "$T/telefon.txt"

echo "wersja bazy w kopii: $("$SQ" "$T/telefon.db" 'PRAGMA user_version;' | tr -d '\r')"
echo "pozycji: świeża $(wc -l < "$T/swieza.txt"), kopia $(wc -l < "$T/telefon.txt")"
if diff "$T/swieza.txt" "$T/telefon.txt" > "$T/roznice.txt"; then
  echo "Schematy zgodne."
else
  echo "RÓŻNICE (< świeża instalacja, > kopia z telefonu):"
  cat "$T/roznice.txt"
  exit 1
fi
