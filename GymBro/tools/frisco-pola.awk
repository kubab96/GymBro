# Czyta surowa karte produktu Frisco.pl ze standardowego wejscia i wypisuje
# wiersz paczki sklepowej: nazwa, kcal, bialko, wegle, tluszcz, blonnik,
# cukry, nasycone, sol, gramatura. Jeden przebieg, bez pliku tymczasowego.
#
# Poprzednia wersja byla potokiem: gzip do pliku, sed po calych 1,6 MB,
# dwa tr, grep po tytul i wc po rozmiar - czterokrotne czytanie karty
# i piec procesow na produkt. Schodzilo pol karty na sekunde, czyli godzine
# na komplet.
#
# Tu korzystamy z tego, ze HTML jest zminifikowany: tabela siedzi w jednym
# wierszu (w zaobserwowanych kartach 262. z 521), a tytul w innym. Wystarczy
# rozebrac znaczniki w tym jednym wierszu, nie w calym pliku.
#
# Bierzemy PIERWSZE wystapienie naglowka. Drugie jest w stanie Reacta na
# koncu pliku, gdzie znaczniki sa zapisane jako \u003c i nie dalyby sie
# rozebrac.

# Twarda spacja U+00A0. Frisco stawia ja miedzy liczba a jednostka
# ("250<NBSP>g") i w tabeli. Nie jest ani spacja, ani tabulatorem, wiec
# wzorzec " ?(g|kg)$" jej nie trafial, a przycinanie bialych znakow jej nie
# usuwalo - gramatura wychodzila pusta dla KAZDEGO produktu, we wszystkich
# wersjach tego czytnika. Budujemy ja przez sprintf, bo wpisana wprost gubi
# sie przy kazdym kopiowaniu pliku.
BEGIN {
    # gawk w tym srodowisku pracuje na BAJTACH, nie na znakach: sprintf("%c", 160)
    # daje pojedynczy bajt A0, ktory dopasowuje sie do drugiego bajtu sekwencji
    # C2 A0 i po podmianie zostawia wiszacy C2 - w nazwie widac wtedy "500? g".
    # Dlatego podmieniamy najpierw cala pare, a potem pojedynczy znak: w trybie
    # bajtowym zadziala pierwsze, w znakowym drugie.
    NBSP_PARA = sprintf("%c%c", 194, 160)
    NBSP_ZNAK = sprintf("%c", 160)
}

function liczba(s,   i, c, out, widzial) {
    out = ""; widzial = 0
    for (i = 1; i <= length(s); i++) {
        c = substr(s, i, 1)
        if (c >= "0" && c <= "9") { out = out c; widzial = 1 }
        else if ((c == "," || c == ".") && widzial) out = out "."
        else if (widzial) break
    }
    return out
}

function po(tekst, etykieta,   p) {
    p = index(tekst, etykieta)
    if (p == 0) return ""
    return liczba(substr(tekst, p + length(etykieta)))
}

# Pole opcjonalne: pusto znaczy "sklep tego nie podal", nie "zero". Zero
# gramow blonnika to twierdzenie, i zwykle falszywe.
function moze(tekst, etykieta,   v) {
    v = po(tekst, etykieta)
    return (v == "" || v + 0 < 0) ? "" : v
}

tytul == "" && /<title>/ {
    if (match($0, /<title>[^<]*/)) {
        tytul = substr($0, RSTART + 7, RLENGTH - 7)
        sub(/ - Frisco\.pl.*/, "", tytul)
        gsub(NBSP_PARA, " ", tytul); gsub(NBSP_ZNAK, " ", tytul)
        gsub(/^[ \t]+|[ \t]+$/, "", tytul)
    }
}

tabela == "" {
    linia = tolower($0)
    p = index(linia, "wartości odżywcze")
    if (p > 0) {
        t = substr(linia, p, 8000)
        gsub(/<[^>]*>/, " ", t)
        gsub(/&nbsp;/, " ", t)
        gsub(NBSP_PARA, " ", t); gsub(NBSP_ZNAK, " ", t)
        gsub(/[ \t]+/, " ", t)

        # Odcinek ze stanu Reacta pomijamy i szukamy dalej.
        #
        # Kazda karta ma tabele dwa razy: raz jako HTML, raz w stanie
        # Reacta, gdzie znaczniki sa zapisane jako "<". Zalozenie, ze
        # blok Reacta stoi zawsze na koncu pliku, bylo bledne - w karcie
        # serka Almette z czosnkiem niedzwiedzim PIERWSZE wystapienie
        # naglowka jest wlasnie w nim. Rozbieranie znacznikow go nie tyka,
        # a odczyt liczby po "(kcal)" bral cyfry z "003c".
        #
        # Rozpoznajemy go po tym, czym JEST, nie po tym, czym nie jest.
        # Pierwsza wersja odrzucala odcinek zawierajacy "<" gdziekolwiek
        # w oknie osmiu tysiecy znakow - i odrzucala przez to tabele dobre,
        # bo escapowany JSON pojawia sie na tej samej linii dalej. W karcie
        # rurki ze smietana zabralo to prawdziwa tabele (319 kcal, 15 g
        # tluszczu) i przepuscilo blok Reacta, z ktorego wyszlo 754 kcal
        # i 127 gramow makro na sto gramow produktu.
        #
        # W tabeli rozebranej z HTML-a zaraz za naglowkiem stoi pierwszy
        # wiersz, czyli sama tresc. W bloku Reacta stoi tam JSON, a w nim
        # cudzyslowy - i to wystarcza, zeby je rozroznic.
        if (index(substr(t, 1, 80), "\"") > 0) next

        # Tabele trzeba ODCIAC, nie tylko znalezc: etykieta w niej
        # nieobecna dopasowywala sie dalej na stronie. W karcie jogurtu
        # Bakomy slowo "blonnik" trafilo w nazwe innego produktu z listy
        # polecanych ("...blonnikiem i probiotykami 400 g") i czytnik
        # wzial 400 gramow blonnika - wzor dawal wtedy 945 kcal wobec 147
        # deklarowanych. Granica od gory to zdanie "dzienna porcja
        # obliczona", stojace zaraz za tabela.
        koniec = index(t, "dzienna porcja")
        if (koniec > 0) t = substr(t, 1, koniec - 1)
        tabela = substr(t, 1, 1500)
    }
}

END {
    if (tytul == "") tytul = zapas

    # Gramatura: "500 g" albo "1 kg" na KONCU nazwy. Mililitry i sztuki
    # pomijamy, bo nie da sie z nich wyliczyc masy.
    masa = ""
    if (match(tytul, /[0-9]+([.,][0-9]+)? ?(g|kg)$/)) {
        ogon = substr(tytul, RSTART, RLENGTH)
        jed = (ogon ~ /kg$/) ? 1000 : 1
        gsub(/,/, ".", ogon)
        gsub(/[^0-9.]/, "", ogon)
        masa = ogon * jed
    }

    if (tabela == "") { print tytul "\tODRZUT\tbrak sekcji"; exit }

    kcal    = po(tabela, "(kcal)")
    tluszcz = po(tabela, "tłuszcz")
    wegle   = po(tabela, "węglowodany")
    bialko  = po(tabela, "białko")
    blonnik = moze(tabela, "błonnik")
    cukry   = moze(tabela, "w tym cukry")
    nasyc   = moze(tabela, "nasycone")
    sol     = moze(tabela, "sól")

    if (kcal == "" || bialko == "" || wegle == "" || tluszcz == "") {
        # Czesc kart podaje komplet makro bez wiersza z energia. Nie
        # dopisujemy jej ze wzoru, bo wzor jest tu jedynym sprawdzeniem,
        # czy czytnik zlapal wlasciwe liczby - policzona energia zgadzalaby
        # sie zawsze, takze z blednie odczytanej tabeli.
        print tytul "\tODRZUT\t" (kcal == "" ? "brak wiersza z energia" : "niepelne makro")
        exit
    }
    if (kcal + 0 < 1 || kcal + 0 > 900) { print tytul "\tODRZUT\tkcal poza zakresem: " kcal; exit }
    if (bialko + 0 > 100 || wegle + 0 > 100 || tluszcz + 0 > 100) {
        print tytul "\tODRZUT\tmakro powyzej stu gramow"; exit
    }

    # Suma makro tez nie moze przekroczyc stu gramow - i tego wzor NIE
    # sprawdza, bo wylapuje tylko niespojnosc miedzy skladem a kaloriami.
    #
    # Karta Monokla z cynamonem podaje 738 kcal, 37 g tluszczu, 83 g
    # weglowodanow i 15 g bialka "na 100 g". Wzor daje z tego 725 kcal,
    # czyli zgadza sie w granicach dziesieciu procent - a 135 gramow makro
    # w stu gramach produktu nie istnieje. Podzielone przez gramature
    # opakowania (160 g) daja sensowne 461 kcal, wiec sklep podal kolumne
    # na opakowanie jako kolumne na sto gramow. Tego nie poprawiamy:
    # dzielenie przez gramature byloby zgadywaniem, ktora kolumne sklep
    # pomylil.
    #
    # Granica to 105, nie 100: maslo klarowane ze smalcem ma 99,8 g
    # tluszczu plus slady bialka i wegli, czyli 100,3 g po zaokragleniu,
    # i jest prawdziwe.
    if (bialko + wegle + tluszcz > 105) {
        printf "%s\tODRZUT\tsuma makro %.1f g na 100 g\n", tytul, bialko + wegle + tluszcz
        exit
    }

    ze_skladu = 4 * bialko + 4 * wegle + 9 * tluszcz + 2 * blonnik
    roznica = ze_skladu - kcal; if (roznica < 0) roznica = -roznica
    granica = 0.1 * kcal; if (granica < 10) granica = 10
    if (roznica > granica) {
        printf "%s\tODRZUT\twzor: %.0f vs %s\n", tytul, ze_skladu, kcal
        exit
    }

    print tytul "\t" kcal "\t" bialko "\t" wegle "\t" tluszcz "\t" blonnik \
        "\t" cukry "\t" nasyc "\t" sol "\t" masa
}
