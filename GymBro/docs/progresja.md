# Progresja: ciężar, powtórzenia czy seria

Dokument projektowy. Zbieranie RIR jest już zaimplementowane; sam algorytm
propozycji - jeszcze nie.

Cel: po zakończonym treningu telefon proponuje zmiany w planie. Nic nie
zmienia się samo - propozycję trzeba przyjąć.

---

## 1. Źródła

Tym razem udało się dotrzeć do pełnych tekstów. Oznaczenia: **[V]** cytat
dosłowny z pobranego tekstu, **[?]** niepotwierdzone.

| praca | co daje |
|---|---|
| Peterson 2010, PMC4215195 | reguła progresji w opublikowanej metodologii |
| Schoenfeld 2018, PMC6303131 | 1 vs 3 vs 5 serii, 8 tyg., wytrenowani |
| Baz-Valle 2022, PMC8884877 | przegląd objętości, zakres zalecany |
| Ralston 2017, PMC5684266 | objętość a siła, meta-analiza |
| Schoenfeld 2016, PMC5131226 | ciężar vs powtórzenia, serie zrównane |
| Mitchell 2012, PMC3404827 | 30% vs 80% 1RM do upadku |
| Refalo 2022, PMC9935748 | bliskość upadku a hipertrofia |
| Grgic 2021, PMC9068575 | upadek vs brak upadku, siła i hipertrofia |

Nie udało się: Halperin (404 na SportRxiv), Russo 2025 (błąd certyfikatu),
Schoenfeld 2017 dose-response (PDF binarny, tekstu nie wyciągnięto).

---

## 2. Co literatura faktycznie mówi

### Reguła 2-for-2 ma wreszcie źródło

Peterson 2010, sekcja Metody **[V]**: ciężar rósł o **0.5-2 kg po wykonaniu
dwóch lub więcej powtórzeń ponad cel w dwóch kolejnych treningach**.

To jest dokładnie ta reguła, której poprzednio nie dało się potwierdzić.
Nie jest to wynik badania, tylko protokół, który zadziałał - ale to i tak
więcej niż folklor z internetu.

Z tej samej pracy: objętość treningowa korelowała z przyrostem siły
(mężczyźni β=0.66, p<0.01) i z hipertrofią u kobiet (β=0.12, p<0.01).

### Ciężar buduje siłę, objętość buduje masę

To rozdzielenie jest najmocniej udokumentowane i rozstrzyga połowę naszego
problemu.

**Schoenfeld 2016** (PMC5131226), serie zrównane, 8 tygodni, 19 wytrenowanych:
ciężki (2-4 powt.) kontra umiarkowany (8-12 powt.)

| wynik | ciężki | umiarkowany | p |
|---|---|---|---|
| 1RM przysiad | g=1.12 | g=0.71 | **0.03** na korzyść ciężkiego |
| grubość uda (boczna) | g=0.33 | g=1.17 | **0.007** na korzyść umiarkowanego |

Wnioski autorów **[V]**: *"Heavy loads maximize muscular strength when the
numbers of sets are equated"* i *"Moderate loads maximize muscle hypertrophy
when the number of sets are equated"*.

**Mitchell 2012** (PMC3404827), 30% vs 80% 1RM, obie do upadku: przyrost
objętości mięśnia **6.8% vs 7.2%, p=0.18** - bez różnicy. Ale 1RM wyraźnie
po stronie ciężkiego (p=0.04). Wniosek **[V]**: *"a lower load lifted to
failure resulted in similar hypertrophy as a heavy load lifted to failure"*.

**To znaczy: dla masy powtórzenia są pełnoprawnym zamiennikiem ciężaru.
Dla siły nie są.**

### Serie: działają na masę, słabiej na siłę

**Schoenfeld 2018** (PMC6303131), 34 wytrenowanych, 8 tygodni, 1 vs 3 vs 5
serii na ćwiczenie:

- **Hipertrofia**: istotnie na korzyść większej objętości. Udo boczne
  P=0.006, BF10=63.87; 5 serii kontra 1 seria BF10=38.14.
- **Siła**: **bez różnicy między grupami**. Przysiad P=0.22, wyciskanie
  P=0.15, w obu BF10<1.

Tygodniowo na partię: 1SET = 6-9 serii, 3SET = 18-27, 5SET = 30-45.

**Ralston 2017** (PMC5684266) na siłę daje jednak stopniowaną zależność:
≤5 kontra ≥10 serii tygodniowo na ćwiczenie, ES 0.18 (0.06-0.30), p=0.003.
Efekt jest mały, ale istotny.

**Baz-Valle 2022** (PMC8884877) - wniosek **[V]**: *"a range of 12-20 weekly
sets per muscle group may be an optimum standard recommendation"*. Różnice
między objętością umiarkowaną a wysoką były w większości nieistotne.

### Upadek nie jest potrzebny

**Grgic 2021** (PMC9068575): upadek kontra brak upadku -
siła ES **-0.09** (-0.22 do 0.05), hipertrofia **0.22** (-0.11 do 0.55).
Wniosek **[V]**: *"Training to muscle failure does not seem to be required
for gains in strength and muscle size"*.

Istotny szczegół: gdy objętość **nie** była zrównana, trening do upadku
**szkodził** sile - ES -0.32 (-0.57 do -0.07), p=0.025. Upadek kosztuje
objętość, a objętość jest tym, co pracuje.

**Refalo 2022** (PMC9935748): upadek kontra brak, hipertrofia ES **0.19**
(0.00 do 0.37), p=0.045, określone przez autorów jako *"trivial advantage"*.
Sam upadek chwilowy: 0.12 (-0.13, 0.37), p=0.343 - nieistotne.

**Dla nas:** celem nie jest RIR 0. Zapas 1-2 to miejsce, w którym chcemy być,
a zapas 3+ znaczy, że ciężar jest za lekki.

### Dokładność RIR

Nie udało się dotrzeć do źródeł pierwotnych w tej rundzie. Dwa niezależne
źródła wtórne zgadzają się co do **zaniżania o około jedno powtórzenie**
(Halperin 2021; Refalo 2023: 0.65 ± 0.78). **[?]**

Traktuję to jako prawdopodobne, nie pewne, i dlatego **korekta jest ostrożna:
zapas 3+ czytamy jako "co najmniej 3"**, bez dodawania sztywnej jedynki.

---

## 3. Dlaczego ziarnistość ciężaru decyduje

Twój sprzęt, policzone z bazy (gryf 10 kg; 10×4, 5×8, 3.4×4, 1.15×4):

| sprzęt | najmniejszy skok | przy ciężarze roboczym |
|---|---|---|
| sztanga | 0.90 kg | 1.4 kg przy 80 kg = **1.8%** |
| hantel pojedynczy | 0.40 kg | 0.9 kg przy 20 kg = **4.5%** |
| para hantli | 0.50 kg | 1.8 kg przy 20 kg = **9.0%** |
| wyciąg / bez ograniczeń | dowolny | ~2.5% |

Mówiłeś, że **w większości ciężar będzie dowolny** - wtedy ten problem
znika i gramy ciężarem. Problem dotyczy pary hantli, gdzie jeden próg to 9%.

Mitchell i Schoenfeld dają tu wyjście: skoro przy równej bliskości upadku
powtórzenia budują masę tak samo jak ciężar, to przy grubym progu
**powtórzenia są pełnoprawnym zamiennikiem**, nie protezą.


### Próg liczbowy to nie to samo co próg wykonalny

Tabela wyżej liczy, co da się **złożyć**. To nie to samo, co da się
**zmienić między seriami**. Przy 80 kg na sztandze leży gryf 10 + po
10+10+5+5+5 na stronę. Najbliższy próg w górę to 81,4 kg, czyli
10 + 2 × (10+10+5+5+3,4+1,15+1,15): żeby zyskać 1,4 kg, trzeba zdjąć
piątkę i założyć trzy inne krążki. Z każdej strony. Nikt tego nie robi
w przerwie, a propozycja, której nikt nie wykona, jest gorsza niż jej brak.

Dlatego kalkulator talerzy liczy teraz **koszt przełożenia**: ile krążków
na stronę trzeba ruszyć (zdjąć plus dołożyć), żeby z obecnego kompletu
dojść do danego ciężaru. Progi droższe niż **dwa ruchy na stronę** nie
wchodzą do drabinki, z której wybiera algorytm progresji - ani pod
przyciski +/− w treningu i w planie.

| z 80 kg | ciężar | co trzeba zrobić | koszt |
|---|---|---|---|
| próg liczbowy | 81,4 kg | zdejmij 5, załóż 3,4 + 1,15 + 1,15 | 4 |
| **próg wykonalny** | **82,3 kg** | **wsuń 1,15** | **1** |

Efekt dla sztangi przy 80 kg: krok rośnie z 1,4 kg (1,8%) na 2,3 kg (2,9%).
To przypadkiem ląduje poniżej progu 3% z kroku 4, więc podbicie nadal
wymaga tylko jednej dobrej sesji, nie dwóch.

Dwa ruchy to **moja konwencja**, nie literatura. Dobrane tak, żeby
przejście "dołóż najmniejszy talerz" (koszt 1) i "wymień talerz na
większy" (koszt 2) przechodziły, a składanie kompletu od nowa - nie.

Zabezpieczenie przed zbyt dużym skokiem: jeśli w zasięgu 10% nie ma
żadnego wygodnego progu, bierzemy ten liczbowo najbliższy. Inaczej
przy rzadkim zestawie talerzy przycisk "+" potrafiłby podnieść o 7 kg.
Przy typowym sprzęcie siłowni (gryf 20, talerze 1,25-25 kg) reguła nie
zmienia nic: 80 → 82,5 kg to dołożenie 1,25 na stronę, koszt 1.

---

## 4. Algorytm

### Zasada naczelna: koszt milczenia jest wyzszy

Propozycja, ktora zignorujesz, kosztuje jedno spojrzenie. Propozycja, ktorej
nigdy nie bylo, kosztuje tygodnie stania w miejscu. Przy watpliwosciach
**mowimy**, a nie milczymy - z jednym wyjatkiem: propozycje zejscia z ciezaru
i skrocenia planu sa domyslnie odznaczone, bo te kosztuja wiecej niz spojrzenie.

Z tego wynika tez stosunek do zapasu powtorzen: **RIR nigdy nie blokuje
propozycji, ktora uzasadniaja same powtorzenia.** Moze ja przyspieszyc albo
zmniejszyc skok. Nic wiecej. Czesc osob nie wypelni go wcale, a czesc trenuje
zawsze do upadku i bedzie miala stale zero - algorytm musi dzialac dla obu.

Liczymy po ostatniej sesji z danym ćwiczeniem. Oznaczenia: `n` = liczba serii
roboczych, `minR` = najmniejsza liczba powtórzeń wśród serii roboczych,
`minRIR` = najmniejszy zapas, `krok%` = najmniejszy dostępny skok ciężaru
jako procent obecnego.

### Krok 0: czy w ogóle się wypowiadamy

Milczymy, gdy: ćwiczenie pominięte; serii mniej niż w planie; `n < 2`;
powtórzenia spadły względem poprzedniej sesji na tym samym ciężarze; ciężar
był niższy niż cel w planie; od poprzedniej sesji minęło ponad 21 dni.

*Ostatnie dwa progi to moje konwencje.*

### Krok 1: czy jest zapas

```
jeśli minRIR >= 3            -> ciężar zdecydowanie za lekki   (PRZYSPIESZ)
jeśli minRIR 1-2             -> jesteś tam, gdzie trzeba        (NORMALNIE)
jeśli minRIR = 0             -> pracowałeś na granicy           (HAMUJ)
jeśli brak RIR               -> decyduj po samych powtórzeniach
```

**HAMUJ nie znaczy STOP.** Zapas zero zmniejsza *wielkość* skoku, nie blokuje
go. Pierwsza wersja tego dokumentu miała tu weto i była błędna: ktoś, kto
trenuje do upadku, ma RIR 0 zawsze - i progresja stałaby u niego na zawsze,
niezależnie od tego, ile powtórzeń zrobi.

Rozstrzygają powtórzenia, bo to one mówią, czy coś się poprawia:

| zapas | powtórzenia | co to znaczy | propozycja |
|---|---|---|---|
| 0 | szczyt zakresu | wyczerpałeś zakres na tym ciężarze | **podnieś, najmniejszy próg** |
| 0 | poniżej szczytu, rosną | poprawiasz się wewnątrz zakresu | zostaw, pracuj dalej |
| 0 | poniżej szczytu, stoją 3 sesje | dajesz maksimum i nic z tego | zastój - zostaw albo zejdź |
| 1-2 | szczyt zakresu | podręcznikowa podwójna progresja | podnieś wg kroku 3 |
| 3+ | dowolne | ciężar za lekki | podnieś, większy skok |

Podstawa dla pierwszego wiersza: Peterson 2010 podnosił ciężar po wykonaniu
powtórzeń ponad cel **bez względu na zapas** - zapas w ogóle nie wchodził do
jego reguły. Grgic mówi o upadku jako metodzie treningowej, a nie o tym, co
robić po wyczerpaniu zakresu, więc powoływanie się na niego przy wecie było
nadużyciem.

Podstawa: Grgic i Refalo - przewaga upadku jest zerowa lub trywialna, więc
RIR 1-2 to miejsce docelowe, a 3+ to zmarnowany bodziec.

**Sygnał STOP jest nadrzędny.** Przy `minRIR = 0` nie proponujemy niczego
w górę, nawet gdy wyrobiłeś szczyt zakresu. Dołożenie ciężaru komuś, kto
właśnie pracował na granicy, to droga do zastoju - a przy nierównej objętości
upadek wprost szkodzi sile (Grgic, ES -0.32).

### Krok 2: co podnosić

```
jeśli minR < repsTo:
    jeśli minRIR >= 3  -> DODAJ CIĘŻAR       (zapas mimo niewyrobionego zakresu)
    w przeciwnym razie -> NIC                (wyrabiaj zakres)

jeśli minR >= repsTo:
    (zapas 0 nie blokuje - zmniejsza tylko skok w kroku 3)
    jeśli krok% <= 5   -> DODAJ CIĘŻAR
    jeśli krok% > 5:
        jeśli zakres < 15 powtórzeń -> ROZSZERZ ZAKRES o 2
        w przeciwnym razie          -> DODAJ SERIĘ
```

Próg **5%** jest wyznaczony przez Twój sprzęt, nie przez literaturę: sztanga
(1.8%) i wyciąg (~2.5%) wpadają poniżej, para hantli (9%) powyżej. Żadna
praca nie podaje granicy wielkości skoku - *to moja konwencja*.

Granica **15 powtórzeń** bierze się stąd, że Mitchell pokazał równoważność
przy 30% 1RM, czyli mniej więcej 25-35 powtórzeń - ale zjeżdżanie tak nisko
zmienia charakter ćwiczenia. Powyżej 15 wolę dołożyć serię niż dalej wydłużać
zakres. *Konwencja, oparta na granicy między badanymi zakresami.*

### Krok 3: o ile ciężaru

Skala rośnie z zapasem, bo zapas mówi, ile zmarnowałeś:

| warunek | skok |
|---|---|
| `minR >= repsTo + 3` | **wylicz z równoważności** (patrz niżej) |
| `minRIR >= 3` | +3 progi |
| `minR >= repsTo + 2` lub `minRIR = 2` | +2 progi |
| `minRIR = 0` | +1 próg |
| pozostałe przypadki | +1 próg |

### Gdy powtórzeń jest dużo ponad zakres

Stałe liczby progów załamują się przy dużym przekroczeniu. Ktoś, kto trenuje
do upadku i robi 4×20 przy zakresie 8-12, dostałby ode mnie **+2 progi,
czyli 82.3 kg** - podczas gdy jego realny ciężar na 12 powtórzeń to około
95 kg. Przy takim tempie doszedłby tam po kilkunastu sesjach.

Przy przekroczeniu o 3 i więcej liczymy ciężar z równoważności powtórzeń
(wzór Epleya: `1RM = W × (1 + R/30)`, potem w drugą stronę na `repsTo`):

| wynik przy 80 kg | szacowany ciężar na 12 powt. | zmiana |
|---|---|---|
| 4×14 | 83.8 kg | +5% |
| 4×16 | 87.6 kg | +10% |
| 4×20 | 95.2 kg | +19% |
| 4×24 | 102.9 kg | +29% |

Z ograniczeniem do **10% naraz** - wzory na powtórzenia maksymalne są
niepewne powyżej kilkunastu powtórzeń, więc dochodzimy tam w dwóch krokach
zamiast jednego skoku w ciemno. *Wzór Epleya jest standardem praktycznym,
nie wynikiem badania; limit 10% to moja konwencja.*

W pozostałych przypadkach nigdy więcej niż **5% ciężaru naraz**. Peterson używał 0.5-2 kg na ćwiczeniu
izolowanym - przy Twoim przysiadzie 80 kg daje to maksymalnie 4 kg.

Wariant „+2 progi przy `repsTo + 2`" to wprost reguła 2-for-2 Petersona,
tylko bez wymogu dwóch kolejnych treningów - ten wymóg przenieśliśmy do
kroku 4, gdzie zależy od ziarnistości.

### Krok 4: ile potwierdzeń

| krok% | ile sesji pod rząd |
|---|---|
| ≤ 3% | 1 |
| 3-6% | 2 (Peterson) |
| > 6% | nie ruszamy ciężaru |

Peterson wymagał dwóch kolejnych treningów. Przy mikroładowaniu na sztandze
(1.8%) to zbyt ostrożne - koszt pomyłki to jedna słabsza sesja. Przy grubszym
progu wracamy do jego reguły.

### Krok 5: seria, nie ciężar

Dodanie serii proponujemy, gdy:

- próg ciężaru > 5% **i** zakres powtórzeń już sięga 15, **albo**
- `minRIR >= 3` w **dwóch** kolejnych sesjach mimo podnoszenia ciężaru
  (ciężar nie jest wąskim gardłem - objętość jest).

Z twardym hamulcem: **nie przekraczamy 20 serii tygodniowo na partię**
(Baz-Valle: 12-20 jako zakres zalecany; powyżej różnice nieistotne).
Dodatkowo nie dokładamy więcej niż **2 serii tygodniowo na partię** naraz.
*Tempo jest moją konwencją - literatura go nie podaje.*

Szczera uwaga: **dla samej siły dokładanie serii ma słabe uzasadnienie.**
Schoenfeld 2018 nie znalazł różnicy w sile między 1, 3 a 5 seriami, a Ralston
daje efekt mały (ES 0.18). Dla masy jest odwrotnie - tam objętość działa
mocno. Jeśli kiedyś oznaczymy ćwiczenia jako siłowe albo masowe, ta gałąź
powinna się różnić.

### Krok 6: zastój i zejście

- **3 sesje** na tym samym ciężarze bez dobicia `repsTo` → propozycja:
  zostaw albo zejdź o ~10%.
- `minR < repsFrom` przez **2 sesje** → ciężar za duży, propozycja zejścia
  na najbliższy próg, przy którym dolna granica jest realna.
- `minRIR = 0` przez **3 sesje** z rzędu **przy stojących powtórzeniach** →
  dajesz maksimum i nic z tego nie wynika, propozycja zejścia o jeden próg.
  Przy rosnących powtórzeniach to nie jest zastój, tylko postęp.

### Zapas, któremu nie można wierzyć

Jeśli **wszystkie** serie **wszystkich** ćwiczeń mają zapas 0 przez kilka
sesji z rzędu, to nie jest informacja - to sposób, w jaki ta osoba wypełnia
pole. Wtedy przestajemy używać zapasu i wracamy do reguł opartych wyłącznie
na powtórzeniach, zamiast podejmować decyzje na stałej.

Tak samo, gdy wszystko jest zawsze 3+. Sygnał bez zmienności nie niesie nic.

*Wszystkie trzy liczby to konwencje.* Żadne dostępne źródło nie podaje
reguły zastoju; Grgic daje tylko argument, że długie siedzenie na zerowym
zapasie kosztuje siłę przez utratę objętości.

---

## 5. Przykłady na Twoich danych

**Przysiad, plan 4×8-12 @ 80 kg, sztanga (1.8%)**

| co zrobiłeś | propozycja | dlaczego |
|---|---|---|
| 4×12, RIR 3 | **80 → 84 kg** (+3 progi) | duży zapas mimo szczytu zakresu |
| 4×12, RIR 1 | **80 → 81.4 kg** (+1 próg) | szczyt zakresu, próg drobny |
| 4×12, RIR 0 | **80 → 81.4 kg** (+1 próg) | zakres wyczerpany, ale na granicy - najmniejszy skok |
| 4×20, RIR 0 | **80 → 88 kg** (+10%, limit) | ciężar grubo za lekki mimo upadku |
| 4×10, RIR 3 | **80 → 84 kg** | zapas ważniejszy niż zakres |
| 4×10, RIR 1 | **bez zmian** | wyrabiaj zakres |
| 4×7, RIR 0, drugi raz | **80 → 72 kg** | za ciężko |
| 4×10, RIR 0, trzeci raz z rzędu, bez poprawy | **80 → 72 kg** | maksimum wysiłku, zero postępu |
| 4×10, potem 4×11, potem 4×12, RIR 0 | **podnieś przy 12** | powtórzenia rosły - to był postęp, nie zastój |

**Uginanie hantli, plan 3×8-12 @ 16.8 kg, para hantli (9%)**

| co zrobiłeś | propozycja | dlaczego |
|---|---|---|
| 3×12, RIR 1 | **zakres 8-12 → 8-14** | skok ciężaru to 9%, za dużo |
| 3×14, RIR 1 (po rozszerzeniu) | **zakres → 8-16** | wciąż poniżej 15 |
| 3×16, RIR 1 | **dodaj serię: 3 → 4** | zakres wyczerpany |
| 3×12, RIR 3 dwa razy | **dodaj serię** | objętość jest wąskim gardłem |

---

## 6. Czego ten algorytm nie wie

- **Nie rozróżnia ćwiczeń siłowych od masowych.** Literatura rozdziela je
  wyraźnie - ciężar na siłę, objętość na masę. Dopóki plan nie niesie tej
  informacji, traktujemy wszystko jednakowo i tracimy sporo precyzji.
- **Nie liczy objętości tygodniowej na partię**, bo ćwiczenia nie są
  przypisane do partii w sposób nadający się do sumowania. Hamulec „20 serii"
  wymaga tego mapowania.
- **Nie zna zmęczenia ogólnego.** Spadek powtórzeń wykrywamy per ćwiczenie,
  a nie na poziomie całego treningu czy tygodnia.
- **Opiera się na RIR, który bywa zaniżany** i którego dokładności nie udało
  się potwierdzić w źródle pierwotnym.

---

## 7. Cel użytkownika jako wejście do algorytmu

Dopisane po pytaniu: czy nie dodać w konfiguracji celu - schudnąć, przybrać,
zbudować mięśnie - i liczyć na tej podstawie.

Tak, ale **to są dwie różne rzeczy i zlanie ich w jedno pole dałoby błędne
zachowanie**. Literatura, którą czytałem wyżej, rozdziela siłę od hipertrofii
i nie mówi nic o chudnięciu - bo chudnięcie to bilans energii, a nie układ
serii i powtórzeń.

### Oś A: cel treningowy - co podnosić

To jest oś, którą badania opisują liczbami, i to ona decyduje o wyborze
między ciężarem, powtórzeniami a serią.

| ustawienie | co zmienia | podstawa |
|---|---|---|
| **Siła** | ciężar ma pierwszeństwo; serii nie dokładamy prawie nigdy; węższy, niższy zakres powtórzeń | Schoenfeld 2016: 1RM g=1.12 vs 0.71 na korzyść ciężkiego, p=0.03. Schoenfeld 2018: **brak różnicy w sile** między 1, 3 a 5 seriami (P=0.22 i 0.15, BF10<1) |
| **Masa** | ciężar, powtórzenia i serie równorzędne; przy grubym progu swobodnie gramy objętością | Mitchell 2012: 6.8% vs 7.2%, p=0.18 - powtórzenia równoważne ciężarowi. Schoenfeld 2018: hipertrofia mocno za objętością, BF10=63.87 |
| **Mieszany** (domyślny) | zachowanie opisane w rozdziale 4 | - |

**To powinno być ustawienie na ćwiczenie, z wartością domyślną globalną.**
W jednym FBW przysiad jest ćwiczeniem siłowym, a uginanie na wyciągu
masowym - i algorytm powinien je traktować inaczej. To zamyka dziurę
wypisaną w rozdziale 6.

### Oś B: faza sylwetkowa - czego się spodziewać

Ta oś **nie zmienia tego, co podnosić**. Zmienia to, jak czytać brak postępu.

| ustawienie | co zmienia |
|---|---|
| **Redukcja** | utrzymanie ciężaru to sukces, nie zastój. Regułę zastoju luzujemy z 3 sesji do 6. Nie proponujemy zejścia z ciężaru tylko dlatego, że stoisz. Nie dokładamy serii - w deficycie regeneracja jest ograniczona. Komunikat brzmi „utrzymałeś 80 kg na redukcji", nie „zastój" |
| **Utrzymanie** | zachowanie domyślne |
| **Budowa** | progresja agresywniejsza; objętość może rosnąć do górnej granicy 12-20 serii tygodniowo |

**Uczciwie: na tę oś nie mam źródeł.** Żadna z przeczytanych prac nie badała
progresji w deficycie energetycznym. To rozsądne wnioskowanie z tego, że
w deficycie trudniej o regenerację, ale nie jest to cytat - i tak powinno
być oznaczone w aplikacji.

### Czego ta opcja NIE zrobi

Ustawienie „chcę schudnąć" nie sprawi, że schudniesz. Decyduje o tym bilans
kaloryczny, którego aplikacja nie prowadzi. Zmieni tylko sposób, w jaki
silnik progresji czyta Twoje wyniki. Lepiej napisać to wprost przy opcji,
niż pozwolić jej udawać coś więcej.

### Wykrywanie fazy z ważenia

`weeklyChangeKg()` już istnieje i liczy tempo zmiany masy (nachylenie z ostatnich ośmiu tygodni, `data/WeightTrend.kt`). Docelowo aplikacja może
sama zauważyć rozjazd: *"ustawiłeś budowę, a ważysz się na minus 0.4 kg
tygodniowo"*.

Dziś się nie da - w bazie jest pięć pomiarów masy, cztery z jednego dnia,
trend wychodzi 0.00 kg/tydz. Potrzeba regularnego ważenia przez kilka
tygodni. Więc: pytamy wprost, a podpowiedź z trendu dokładamy później jako
kontrolę, nie jako źródło prawdy.

### Gdzie to umieścić

- **Pierwsze uruchomienie**: oś A jako jedno pytanie (siła / masa / mieszany),
  oś B jako drugie (redukcja / utrzymanie / budowa). Dwa ekrany, po jednym
  wyborze.
- **Profil**: oba do zmiany w każdej chwili.
- **Edycja planu**: nadpisanie osi A per ćwiczenie, domyślnie dziedziczone
  z profilu.

---

## 8. Wyłączenie propozycji dla ćwiczenia

Pole wyboru przy pozycji planu: **„nie proponuj progresji"**, domyślnie
odznaczone.

Po co: nie każde ćwiczenie ma rosnąć. Rozgrzewkowe, rehabilitacyjne,
technicznie trudne, takie gdzie świadomie siedzisz na jednym ciężarze -
przy nich propozycja co tydzień jest szumem, a szum uczy ignorować całą
kartę.

Odznaczone domyślnie, bo milczenie kosztuje więcej niż propozycja -
to ta sama zasada co w rozdziale 4. Wyciszenie ma być decyzją, nie stanem
wyjściowym.

W bazie: kolumna `no_progress` w `plan_item`, migracja dodająca ją z
wartością 0. Ćwiczenie wyciszone nie trafia do karty propozycji w ogóle -
nie pojawia się nawet jako „bez zmian", bo nie o to pytasz.

### Wyłączenie częściowe

Samo „wszystko albo nic" nie starczało. Przy jednej parze hantli ciężar nie
ma dokąd rosnąć, ale powtórzenia mają; przy schemacie ze stałą liczbą
powtórzeń jest odwrotnie. Wyciszenie całości zabierało wtedy także tę połowę
podpowiedzi, która miała sens. Stąd cztery tryby (`ProgressMode`), wybierane
na karcie ćwiczenia w edytorze jednostki:

| Tryb | Kod | Ciężar | Powtórzenia |
|---|---|---|---|
| pełna | 0 | rośnie | rosną |
| wyłączona | 1 | stoi | stoją |
| bez ciężaru | 2 | stoi | rosną |
| bez powtórzeń | 3 | rośnie | stoją |

Kod leży w tej samej kolumnie `no_progress` - zero i jedynka znaczą to, co
przed zmianą, więc migracji nie było. To konwencja zapisu, nie wynik badania.

Tryb działa w obu miejscach, które liczą progresję - także w propozycji na
dzisiejszą serię. Wcześniej wyciszenie dotyczyło tylko karty po treningu,
więc ćwiczenie rozgrzewkowe i tak dostawało co tydzień jedno powtórzenie
więcej na zegarku.

**Propozycja na dziś** (`ProgressionEngine`):

- *wyłączona* - ten sam ciężar i te same powtórzenia co ostatnio, zawsze;
- *bez ciężaru* - powtórzenia rosną o jedno aż do góry zakresu i tam stają;
  komplet powtórzeń ani wysoki zapas nie podbijają ciężaru, a stanie na jednym
  ciężarze nie jest zastojem (reguła czterech sesji nie działa);
- *bez powtórzeń* - cel powtórzeń zostaje tam, gdzie wyszedł ostatnio; ciężar
  idzie w górę jak zwykle, czyli po dobiciu góry zakresu albo przy zapasie 3+.

**Propozycja po treningu** (`ProgressionAdvisor`):

- *bez ciężaru* - ze szczytu zakresu idzie dłuższy zakres, a po jego
  wyczerpaniu seria, bez względu na to, jak drobny jest próg ciężaru. Sam
  zapas przy niedobitym zakresie nie daje nic: na powtórzenia jest jeszcze
  miejsce bez ruszania planu. Plan nie goni też cięższego treningu;
- *bez powtórzeń* - zakres się nie wydłuża. Przy grubym progu, gdzie zwykle
  ciężar ustępuje powtórzeniom, proponujemy następny próg wprost, po
  potwierdzeniu z drugiej sesji i **odznaczony** - to skok ponad limit
  z rozdziału 5, więc kosztuje więcej niż spojrzenie. Gdy wyższego progu nie
  ma, milczymy.

Blokada dotyczy **dokładania**. Zejście z ciężaru po spadku poniżej zakresu
albo po zastoju zostaje w obu trybach częściowych - to nie progresja, tylko
wyjście z dołka, i wyłącza je dopiero tryb „wyłączona".

Propozycja czekająca przy pozycji planu znika, gdy zmiana trybu ją wyklucza
(`stillFits`): „dołóż 2,5 kg" przy ćwiczeniu, w którym właśnie wyłączono
dokładanie ciężaru, wyglądałoby na zignorowane ustawienie.

---

## 9. Gdzie to zyje

Propozycje nie sa jednorazowa karta po treningu, tylko stanem przy pozycji
planu. Powod: decyzje o progresji rzadko podejmuje sie w minucie po ostatniej
serii, a karta, ktora wyskoczyla i znikla, nie zostawia sladu.

**Po treningu** - z telefonu albo z zegarka - propozycje sa liczone i zapisane
przy pozycjach planu. Kolejny trening je nadpisuje, wiec zawsze dotycza
ostatniej sesji; przy cwiczeniu zrobionym dzis inaczej stara podpowiedz jest
kasowana, a nie zostawiana jako falszywy slad.

**Zakladka Plan** dostaje plakietke z liczba nowych propozycji. Gasnie po
zajrzeniu w plan - ma przypomniec raz, a nie swiecic tygodniami. Same
propozycje zostaja az do decyzji.

**Przy cwiczeniu w planie** widac trzy linijki w stalej kolejnosci: co
zrobiles ostatnio, proponowana zmiana z czego na co, i powod. Plus przyciski
Zastosuj i Pomin. Odrzucenie kasuje wpis - nowa propozycja przyjdzie po
nastepnym treningu.

**Nad lista** sa przyciski zbiorcze: *Zastosuj wszystkie* i *Pomin wszystkie*.
Po treningu propozycji bywa kilka i klikanie ich po kolei zniechecaloby do
calego mechanizmu.

**W historii treningu** sesja, po ktorej cos czeka, pokazuje kafelek z lista
zmian i przechodzi dotknieciem do Planu.

## 10. Stan implementacji

Zaimplementowane:

- **Zbieranie RIR** - zegarek pyta w przerwie, czterema przyciskami
  w miejscu strzałek; telefon przyjmuje przez `/edit`. Pominięcie niczego
  nie psuje.
- **Schemat v12** - `training_goal` i `phase` w profilu, `goal`
  i `no_progress` w pozycji planu. Migracja sprawdzona na żywej bazie:
  362 serie przetrwały.
- **`ProgressionAdvisor`** - cała logika z rozdziałów 4-8, bez dostępu do
  bazy, pokryta 20 testami jednostkowymi odtwarzającymi przykłady
  z rozdziału 5.
- **`ProgressionReview`** - ładowanie historii i stosowanie przyjętych
  propozycji do planu.
- **Schemat v13** - propozycja zapisana przy pozycji planu, osiem kolumn.
  Migracja sprawdzona na żywej bazie: 385 serii i 18 pozycji przetrwało.
- **Propozycje w planie** - przy ćwiczeniu, z poprzednim wynikiem i powodem,
  plus przyciski zbiorcze nad listą.
- **Plakietka przy zakładce Plan**, gasnąca po zajrzeniu.
- **Kafelek w historii treningu** z przejściem do planu.
- **Ustawienia w Profilu** - cel treningowy i faza.
- **Wyciszanie w edytorze planu** - wybór trybu „Progresja": pełna, bez
  ciężaru, bez powtórzeń, wyłączona (rozdział 8).

Niezrobione:

- **Pierwsza konfiguracja.** Aplikacja nie ma ekranu wprowadzającego -
  ustawienia są dostępne wyłącznie w Profilu, z sensownymi wartościami
  domyślnymi (mieszany, utrzymanie). Zrobienie onboardingu to osobny temat,
  bo dotyczy też wzrostu, rocznika i sprzętu.
- **Nadpisanie celu per ćwiczenie w interfejsie.** Kolumna `goal`
  w `plan_item` istnieje i silnik ją czyta, ale edytor planu jeszcze jej
  nie pokazuje - na razie wszystkie ćwiczenia dziedziczą cel z profilu.
- **Hamulec objętości na partię mięśniową.** `MAX_SETS` ogranicza serie
  w obrębie ćwiczenia, ale sumowania tygodniowego po partiach nie ma, bo
  ćwiczenia nie są do nich przypisane w sposób nadający się do sumowania.
