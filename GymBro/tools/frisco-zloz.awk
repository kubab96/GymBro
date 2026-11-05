# Sklada paczke sklepowa z wyjscia tools/frisco-zbierz.sh: odrzuca wiersze
# oznaczone ODRZUT i odsiewa powtorzone nazwy.
#
# Klucz liczymy tak samo, jak searchKey() w Nutrition.kt: male litery,
# polskie znaki na lacinskie, wszystko poza literami i cyframi na pojedyncza
# spacje. Inaczej liczba wierszy w pliku nie zgadzalaby sie z liczba
# zaimportowanych - ShopPack odsiewa po kluczu, nie po nazwie, wiec
# "Chleb zytni, 500 g" i "Chleb zytni 500 g" to dla niego jeden produkt.
#
# Zostawiamy wiersz pierwszy. Kolejnosc wejscia idzie z adresy.txt, czyli
# jest ustalona - przy tych samych kartach wynik jest ten sam.

function klucz(s,   i, c, out, spacja) {
    s = tolower(s)
    gsub(/ą/, "a", s); gsub(/ć/, "c", s); gsub(/ę/, "e", s)
    gsub(/ł/, "l", s); gsub(/ń/, "n", s); gsub(/ó/, "o", s)
    gsub(/ś/, "s", s); gsub(/ź/, "z", s); gsub(/ż/, "z", s)

    out = ""; spacja = 1
    for (i = 1; i <= length(s); i++) {
        c = substr(s, i, 1)
        if ((c >= "a" && c <= "z") || (c >= "0" && c <= "9")) { out = out c; spacja = 0 }
        else if (!spacja) { out = out " "; spacja = 1 }
    }
    sub(/ $/, "", out)
    return out
}

BEGIN { FS = OFS = "\t" }

$2 == "ODRZUT" { odrzucone++; next }
NF != 10 { zle++; next }

{
    k = klucz($1)
    if (k in widziane) { powtorki++; next }
    widziane[k] = 1
    print
    wziete++
}

END {
    printf "wziete: %d, odrzucone przez zbieracz: %d, powtorki: %d, zle kolumny: %d\n",
        wziete + 0, odrzucone + 0, powtorki + 0, zle + 0 > "/dev/stderr"
}
