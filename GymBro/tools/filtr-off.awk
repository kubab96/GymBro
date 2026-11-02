# Filtr zrzutu Open Food Facts do paczki polskich produktow.
#
# Uzycie i uzasadnienie: GymBro/docs/kalorie.md, rozdzial o odtwarzaniu paczki.
#
# Kolumny podane numerami, bo zrzut ma ich 211. Przy zmianie ukladu zrzutu
# trzeba je sprawdzic od nowa:
#   gzip -dc zrzut.csv.gz | head -1 | tr '\t' '\n' | cat -n
#
# Zmierzone na zrzucie z listopada 2026: z 39 565 produktow oznaczonych jako
# sprzedawane w Polsce az 13 638 nie ma zadnych wartosci odzywczych - ktos
# zeskanowal kod i nigdy nie uzupelnil reszty. Tych nie da sie uzyc w zadnej
# aplikacji liczacej kalorie.

BEGIN {
    FS = "\t"; OFS = "\t"
    # Litery, ktorych nie ma poza polskim alfabetem - poza "o z kreska",
    # ktore bywa tez w hiszpanskim i czeskim. Szukamy ich przez index(),
    # a nie wyrazeniem regularnym: awk porownuje bajty, a znaki spoza ASCII
    # zajmuja dwa. Klasa znakow /[acelnosz z ogonkami]/ dopasowywala wtedy
    # pojedyncze bajty i przepuszczala szwedzkie "SAS", niemieckie
    # "Haselnusse" i czeskie "Rajcatovy".
    ile = split("ą ć ę ł ń ó ś ź ż Ą Ć Ę Ł Ń Ó Ś Ź Ż", POLSKIE, " ")
}
NR == 1 { next }

function ogonki(s,   i) {
    for (i = 1; i <= ile; i++) if (index(s, POLSKIE[i])) return 1
    return 0
}

{
    # Produkt uznajemy za polski na trzy sposoby, w tej kolejnosci pewnosci.
    #
    # Prefiks 590 w kodzie kreskowym jest najmocniejszy: przypisala go
    # Polsce GS1, wiec firma, ktora zarejestrowala kod, jest polska.
    #
    # Nazwa z polskimi literami to drugi trop - produkt obcej firmy, ale
    # z polska etykieta, czyli taki, ktory faktycznie lezy na polskiej polce.
    #
    # Sam znacznik kraju NIE wystarcza. Oznacza "sprzedawane rowniez
    # w Polsce" i wpuszczal szwedzkie klopsiki, niemieckie lody i tajska
    # pastke curry - produkty, ktorych nikt nie wyszuka po polsku.
    polski = ($1 ~ /^590/) || ogonki($11)
    if (!polski) next

    kcal = $90 + 0
    if ($90 == "" || kcal <= 0 || kcal > 900) next

    prot = $151; carb = $130; fat = $93
    if (prot == "" || carb == "" || fat == "") next
    if (prot + 0 < 0 || prot + 0 > 100) next
    if (carb + 0 < 0 || carb + 0 > 100) next
    if (fat + 0 < 0 || fat + 0 > 100) next
    if (prot + 0 + carb + 0 + fat + 0 > 105) next

    name = $11
    gsub(/^[ \t]+|[ \t]+$/, "", name)
    if (name == "") next
    if (length(name) > 90) next

    # Nazwa musi dac sie wpisac z polskiej klawiatury. W zrzucie siedza
    # produkty oznaczone jako sprzedawane w Polsce, ale z etykieta cyrylica
    # albo po japonsku, oraz takie nazwane sama interpunkcja - w katalogu sa
    # wylacznie szumem, bo nikt ich nie wyszuka.
    lacinskie = name
    gsub(/[^a-zA-Z0-9]/, "", lacinskie)
    if (length(lacinskie) < 2) next

    marka = $19
    gsub(/^[ \t]+|[ \t]+$/, "", marka)
    # Pole bywa lista producenta, dystrybutora i marki wlasnej.
    # Bierzemy pierwsza, tak samo jak klient po kodzie kreskowym.
    sub(/,.*$/, "", marka)
    if (length(marka) > 40) marka = ""

    # Ten sam produkt pod kilkoma kodami kreskowymi.
    #
    # Zdarza sie, bo producent zmienia opakowanie albo ktos dodaje wpis
    # drugi raz. Na liscie wynikow wygladaja jak dwa rozne produkty
    # o identycznej nazwie i identycznych kaloriach - i nie da sie zgadnac,
    # ktory wybrac.
    klucz = tolower(name "|" marka "|" kcal)
    if (klucz in widziane) { powtorki++; next }
    widziane[klucz] = 1

    print $1, name, marka, kcal, prot, carb, fat, $147, $131, $94, $155, $52, $73
    wziete++

    if ($1 ~ /^590/) prefiks++; else po_nazwie++
}

END {
    print "WZIETE: " wziete + 0 > "/dev/stderr"
    print "  po prefiksie 590:   " prefiks + 0 > "/dev/stderr"
    print "  po polskiej nazwie: " po_nazwie + 0 > "/dev/stderr"
    print "  odrzucone powtorki: " powtorki + 0 > "/dev/stderr"
}
