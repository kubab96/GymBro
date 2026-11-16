# Wyciąga instrukcje SQL z data/Schema.kt, z podziałem na sekcje.
#
# Wyjście: "sekcja<TAB>instrukcja", gdzie sekcja to CREATE (ciało onCreate)
# albo nazwa listy, np. V16_STATEMENTS. Ciała onUpgrade nie czytamy - świeżą
# bazę zakłada onCreate, a składa je z tych samych list co migracje.
#
# Czyta tekst źródła, nie kod, więc rozumie tylko to, co w Schema.kt faktycznie
# występuje: napisy w potrójnych cudzysłowach, zwykłe napisy i napisy łączone
# plusem na końcu linii. Instrukcji złożonej inaczej (pętla, szablon z "$")
# nie wyciągnie - wtedy porównanie w schemat.sh pokaże brak, a nie zgodność.
function emit(s) { gsub(/[ \t]+/, " ", s); print sec "\t" s }

/private val [A-Z0-9_]+ = listOf\(/ {
  match($0, /val [A-Z0-9_]+/); sec = substr($0, RSTART + 4, RLENGTH - 4); next
}
/override fun onCreate/ { sec = "CREATE"; next }
/override fun onUpgrade/ { sec = "UPGRADE"; next }
/override fun onConfigure|fun get\(context|@Volatile/ { sec = ""; next }
sec == "" || sec == "UPGRADE" { next }

{
  line = $0
  if (intriple) {
    if (index(line, "\"\"\"")) { intriple = 0; emit(buf); buf = "" }
    else buf = buf " " line
    next
  }
  t = line; sub(/^[ \t]+/, "", t)
  if (t ~ /^\/\// || t ~ /^\*/ || t ~ /^\/\*/) next
  if (index(line, "\"\"\"")) { intriple = 1; buf = ""; next }
  if (t ~ /^"/ || t ~ /execSQL\("/) {
    a = index(line, "\""); rest = substr(line, a + 1)
    n = length(rest); while (n > 0 && substr(rest, n, 1) != "\"") n--
    cat = cat substr(rest, 1, n - 1)
    # Plus na końcu linii: napis ciągnie się w następnej.
    if (substr(rest, n + 1) ~ /\+[ \t]*$/) next
    emit(cat); cat = ""
  }
}
