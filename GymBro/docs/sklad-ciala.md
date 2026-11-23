# Skład ciała z oporu

Dokument projektowy. Dla składu ciała jest tym, czym `progresja.md` dla
progresji i `kalorie.md` dla jedzenia: **źródłem prawdy dla liczb**.
Zmieniając wzór, zmień i ten plik.

Kod: `data/Bia.kt` (wzory, czyste funkcje), `data/BiaTest.kt` (testy),
`ble/` (sterowniki wag podają wyłącznie surowe opory).

---

## 1. Zasada

Waga z elektrodami przesyła przez Bluetooth **masę i opór**. Nic więcej.
Procent tłuszczu, mięśnie, woda, „wiek metaboliczny" - to wszystko są wzory
liczone już w aplikacji producenta, z oporu, wzrostu, wieku i płci.
Wzrost, wiek i płeć mamy w profilu, więc te wzory możemy liczyć sami:

- **każda waga podająca opór daje ten sam skład ciała, liczony tak samo** -
  zmiana wagi nie robi skoku w historii;
- **w bazie leżą opory, nie wyniki** (`bia_value`). Poprawka wzoru albo
  wzrostu w profilu przelicza całą historię - aplikacja producenta tego nie
  potrafi;
- **wzory są opublikowane**, nie tajne, i każdy jest tu opisany z pracą,
  z której pochodzi.

Waga, która oporu nie podaje (np. domowa „Yoda0", OKOK C0), daje samą masę.
Nie zgadujemy składu z BMI - to byłaby liczba udająca pomiar.

---

## 2. Źródła

Oznaczenia jak w `kalorie.md`: **[V]** potwierdzone w pobranym tekście,
**[?]** z pamięci.

| praca | co daje |
|---|---|
| Chumlea 2002, Int J Obes 26:1596-1609 (NHANES III) | masa beztłuszczowa, kryterium: model 4-składnikowy **[V]** |
| Sun 2003, Am J Clin Nutr 77:331-340 | woda całkowita, kryterium: deuter **[V]** |
| Janssen 2000, J Appl Physiol 89:465-471 | mięśnie szkieletowe, kryterium: rezonans **[V]** |
| Matias 2021, Int J Sports Med 42:27-32 | masa beztłuszczowa sportowców, 4C - nieużywany, patrz rozdz. 6 **[V]** |

Potwierdzenia: współczynniki NHANES III i Sun 2003 z tabeli 1 w PMC7230013
(Nutrition 2020); wzór Janssena z abstraktu w PubMed 10926627. Wtórne źródło
(BMC Sports Sci Med Rehabil 2020, tabela 1) podaje współczynnik płci
Janssena jako 0,3825 - **to literówka**, abstrakt oryginału mówi 3,825.
`BiaTest` pada przy 0,3825.

---

## 3. Wzory

Wszystkie: wzrost w cm, masa w kg, opór R w omach przy 50 kHz,
`indeks = wzrost² / R`, mężczyzna = 1, kobieta = 0.

```
masa beztłuszczowa (NHANES III)
  M: -10.678 + 0.262 * masa + 0.652 * indeks + 0.015 * R
  K:  -9.529 + 0.168 * masa + 0.696 * indeks + 0.016 * R

woda całkowita, kg (Sun 2003)
  M:  1.20 + 0.45 * indeks + 0.18 * masa
  K:  3.75 + 0.45 * indeks + 0.11 * masa

mięśnie szkieletowe, kg (Janssen 2000)
  0.401 * indeks + 3.825 * płeć - 0.071 * wiek + 5.102

tłuszcz = masa - masa beztłuszczowa
```

Dlaczego NHANES III, a nie wzór z Genewy (Kyle 2001): genewski wymaga
reaktancji, a wagi łazienkowe jej nie podają. NHANES III bierze sam opór
i ma za kryterium model czteroskładnikowy - najlepszy dostępny.

Wiek do Janssena to **wiek w roku pomiaru** (`Bia.ageAt`), nie dzisiejszy -
inaczej stare pomiary zmieniałyby się z kalendarzem. Bez roku urodzenia
w profilu mięśni nie liczymy; reszta się liczy.

Waga mierzy |Z|, nie R. Przy 50 kHz reaktancja to około 10 % oporu, więc
|Z| = √(R² + Xc²) różni się od R o mniej niż 1 % - **konwencja**: traktujemy
|Z| jako R.

---

## 4. Droga prądu

Wzory z rozdz. 3 powstały na pomiarze **ręka - stopa**, na leżąco. Każdy
opór w bazie ma swoją drogę (`BiaPath`), a wynik dostaje podstawę:

| droga | skąd | podstawa |
|---|---|---|
| ręka - stopa wprost | analizator, waga z rączką | `DIRECT` |
| suma ręka + tułów + noga jednej strony | waga z rączką podająca segmenty | `DIRECT` |
| stopa - stopa | waga z 4 elektrodami w podstawie | `BORROWED` |

Kolejność wyboru: ręka - stopa wprost, potem segmenty prawej strony, potem
lewej, na końcu stopa - stopa. Prawa przed lewą, bo na prawej stronie mierzy
się w badaniach.

**Stopa - stopa to wzór pożyczony.** Dla dorosłych nie znalazłem
opublikowanego wzoru na tę drogę (szukane 2026-11-22). Prąd idzie przez nogi
i dół brzucha, więc wartość bezwzględna ma przesunięcie zależne od
człowieka. Ale opór maleje, gdy przybywa tkanki beztłuszczowej, więc
**kierunek zmian jest prawdziwy** - i tak to podpisuje ekran. Wynik `BORROWED`
**nie idzie do przemiany materii** (`NutritionReview`): przesunięcie poszłoby
prosto w cel kalorii.

Na stojąco opór jest o kilka procent inny niż na leżąco - **konwencja**:
pomijamy, bo nie zmienia drogi prądu, a stałe przesunięcie nie psuje trendu.

**Częstotliwość**: liczy się tylko 50 kHz ±5. Wagi wieloczęstotliwościowe
zapisują wszystkie wartości (przydadzą się do wzorów na wodę
wewnątrz- i zewnątrzkomórkową), ale do wzorów idzie tylko 50.

### Zakresy (konwencja)

- opór całego ciała 150-1500 Ω, segmentu 5-1000 Ω (tułów ma kilkadziesiąt);
- tłuszcz 2-70 % i masa beztłuszczowa mniejsza od masy ciała - inaczej
  opór był zły (stopa na jednej elektrodzie, skarpetki), a nie człowiek.

Poza zakresem: brak wyniku, nie wynik obcięty.

---

## 5. Segmenty i asymetria

Waga z rączką mierzy pięć segmentów. Wzorów regresji na masę mięśni
w ręce z oporu ręki nie ma opublikowanych dla wag łazienkowych - producenci
mają własne. Dlatego z segmentów bierzemy tylko to, co wynika z fizyki:

```
lewa wobec prawej [%] = (R_prawa / R_lewa - 1) * 100
```

Przy tej samej długości kończyny objętość tkanki przewodzącej jest
odwrotnie proporcjonalna do oporu. +3 % znaczy: lewa ręka ma o 3 % więcej
tkanki beztłuszczowej niż prawa. Poniżej 1 % ekran pisze „równo" -
**konwencja**, różnica mniejsza niż powtarzalność pomiaru.

---

## 6. Czego to nie wie

- **Nawodnienie** przesuwa opór bardziej niż tydzień treningu. Pomiar rano,
  na czczo, przed treningiem - inaczej porównuje się wodę, nie ciało.
  Dlatego ekran Masy pokazuje zmianę od najstarszego pomiaru z oporem,
  a nie od poprzedniego.
- **Sportowcy**: NHANES III to populacja ogólna. Matias 2021 ma wzór dla
  wyczynowców (4C, R² 0,94), ale dla kogoś, kto trenuje rekreacyjnie,
  ogólny jest bezpieczniejszy. Do rozważenia jako przełącznik, nie domyślnie.
- **Pojedynczy pomiar** ma błąd rzędu ±3-5 punktów tłuszczu wobec DXA **[?]**.
  Liczy się kierunek, jak przy masie.
- **Kość, tłuszcz trzewny, białko, wiek metaboliczny**: bioimpedancja tego
  nie mierzy. Nie liczymy. Wartości z aplikacji producenta, jeśli ktoś je
  wpisze, leżą w `body_measurement` jako liczby producenta.

---

## 7. Kolejność źródeł tkanki tłuszczowej

Do przemiany materii (Katch-McArdle, `kalorie.md` rozdz. 5):

1. obwody, US Navy - tak liczy ekran Obwodów, jedna liczba w obu miejscach;
2. opór ręka - stopa (`DIRECT`);
3. liczba z aplikacji producenta.

Stopa - stopa się tu nie liczy (rozdz. 4).

---

## 8. Nowa waga

Sterownik w `ble/` zwraca `WeightSample` z listą `BiaValue` - drogą,
częstotliwością i omami. Nic nie liczy. Waga z rączką dopisuje pięć
segmentów i reszta (zapis, wzory, ekran) działa bez zmian.

Warunek: **opór musi być w omach**. Rodzina Qingniu podaje surową liczbę
z przetwornika - nie wpuszczamy jej, bo wzór wziąłby ją za omy.

Wagi 4FIZJO (MovingLife, iMoni Health) to sprzęt Zhongshan Camry / Senssun,
protokół nieobsługiwany. Model BodyVision ma 8 elektrod i jest jedynym
w tej rodzinie, który dałby segmenty - wymaga rozpracowania protokołu
z wagą w ręku (log HCI snoop podczas pomiaru w iMoni Health).
