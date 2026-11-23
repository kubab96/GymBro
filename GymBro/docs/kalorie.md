# Kalorie: zapotrzebowanie, cele i dziennik

Dokument projektowy. Stan: logika policzona i przetestowana, interfejsu jeszcze
nie ma.

Cel: telefon mówi, ile masz dzisiaj zjeść, i poprawia tę liczbę na podstawie
tego, co faktycznie jesz i jak faktycznie zmienia się masa. Formuła jest tylko
punktem startowym, nie odpowiedzią.

---

## 1. Źródła

Oznaczenia: **[V]** potwierdzone cytatem z pobranego tekstu, **[?]** z pamięci,
tekstu nie pobrano.

| praca | co daje |
|---|---|
| Mifflin, St Jeor 1990, Am J Clin Nutr 51:241-247 | wzór na spoczynkową przemianę materii **[?]** |
| Frankenfield 2005, J Am Diet Assoc 105:775-789 | przegląd wzorów, Mifflin najdokładniejszy **[?]** |
| Katch, McArdle, podręcznik | wzór z masy beztłuszczowej: 370 + 21.6 x LBM **[V]** |
| Helms 2014, IJSNEM 24:127-138 | białko w deficycie: 2.3-3.1 g/kg masy beztłuszczowej **[V]** |
| Morton 2018, Br J Sports Med 52:376-384 | plateau białka ~1.6 g/kg masy ciała **[?]** |
| Garthe 2011, IJSNEM 21:97-104 | tempo redukcji 0.7 vs 1.4 % masy na tydzień **[?]** |
| Wishnofsky 1958, Am J Clin Nutr 6:542-546 | 3500 kcal na funt, źródło reguły 7700 kcal/kg **[?]** |
| Hall 2008, Int J Obes 32:573-576 | reguła 7700 zawyża ubytek w długim okresie **[?]** |
| Müller, Bosy-Westphal 2013, Obesity 21:218-228 | adaptacja metaboliczna przy redukcji **[?]** |

W odróżnieniu od `progresja.md` **większości tych tekstów nie pobrano w całości**.
Potwierdzone pobraniem są dwie rzeczy: stałe Katcha-McArdle'a i zakres białka
z pracy Helmsa. Reszta to liczby, które znam, ale których nie weryfikowałem
tutaj źródłem - stąd **[?]**. To nie jest powód, żeby ich nie użyć; to powód,
żeby przy zmianie reguły zacząć od sprawdzenia tych, które zmiana dotyka.

---

## 2. Dlaczego formuła nie wystarcza

Wszystkie aplikacje dietetyczne robią to samo: biorą wzór, mnożą przez
współczynnik aktywności wybrany z listy ("umiarkowanie aktywny") i podają
wynik jako liczbę. Dwa osobne błędy składają się tu w jeden:

1. **Sam wzór.** Mifflin przewiduje spoczynkową przemianę z dokładnością 10 %
   u czterech osób na pięć **[?]**. Przy 1800 kcal to 180 kcal na dobę i to jest
   ten dokładny wzór, bo pozostałe są gorsze.
2. **Współczynnik aktywności.** Wybierany ręcznie, z pięciu progów, przez osobę,
   która nie wie, ile naprawdę się rusza. Tu błąd jest większy niż we wzorze:
   różnica między "mało" a "umiarkowanie" to 15 % całości, czyli ~400 kcal.

Do tego dochodzi adaptacja metaboliczna: po kilku tygodniach deficytu wydatek
spada bardziej, niż wynika z ubytku masy **[?]**. Wzór tego nie widzi, bo zna
tylko dzisiejszą masę.

Alternatywa jest prosta i stara jak termodynamika: **masa ciała jest licznikiem
zapasu energii**. Jeśli jesz średnio 2400 kcal i masa stoi, to wydatek wynosi
2400 kcal, niezależnie od tego, co twierdzi wzór. Jeśli spada o 0.5 kg na
tydzień, wydatek jest o około 550 kcal na dobę wyższy niż to, co jesz.

To jest pomiar, nie przewidywanie, i to jest sedno tej zakładki.

---

## 3. Ile danych potrzeba, żeby ten pomiar coś znaczył

Tu jest cała trudność i tu większość aplikacji udaje, że problemu nie ma.

Masa ciała waha się z dnia na dzień o kilogram z powodów, które nie mają nic
wspólnego z zapasem energii: glikogen wiąże wodę w stosunku około 1:3, sól
zatrzymuje wodę, treść przewodu pokarmowego waha się o pół kilograma. Przyjmuję
odchylenie standardowe jednego ważenia **σ = 0.8 kg**. To konwencja, dobrana
jako około 1 % masy ciała.

Trend liczymy regresją liniową po punktach (dzień, masa). Niepewność
nachylenia to:

```
SE(nachylenie) = σ / sqrt( Σ (x - x̄)² )
```

Przy ważeniu **raz w tygodniu** suma w mianowniku wynosi `n(n²-1)/12` tygodni
kwadratowych, a nachylenie przeliczone na kalorie mnoży się przez 7700/7 = 1100
kcal na (kg/tydzień). Stąd:

| ważeń | okres | SE nachylenia | niepewność wydatku |
|---|---|---|---|
| 4 | 3 tyg. | 0.36 kg/tyg. | **394 kcal/d** |
| 6 | 5 tyg. | 0.19 kg/tyg. | **210 kcal/d** |
| 8 | 7 tyg. | 0.12 kg/tyg. | **136 kcal/d** |
| 12 | 11 tyg. | 0.07 kg/tyg. | **74 kcal/d** |

To jest najważniejsza tabela w tym dokumencie. Wniosek jest niewygodny:
**przy ważeniu raz w tygodniu pomiar przebija wzór dopiero po około pięciu
tygodniach**, a naprawdę pewna liczba jest po trzech miesiącach. Gdybyś ważył
się codziennie, te same słupki wypadłyby po dwóch-trzech tygodniach, bo
siedem razy więcej punktów to pierwiastek z siedmiu razy mniejszy błąd.

Dlatego **nie ma tu progu, po którym formuła zostaje wyrzucona**. Jest ciągłe
przejście: oba oszacowania mieszają się z wagami odwrotnie proporcjonalnymi
do kwadratu ich niepewności.

```
w = 1 / sd²
wydatek = (w_pomiar * pomiar + w_wzór * wzór) / (w_pomiar + w_wzór)
sd_wyniku = sqrt( 1 / (w_pomiar + w_wzór) )
```

Niepewność wzoru przyjmuję **sd = 300 kcal/d**: 180 kcal z samego wzoru plus
około 250 kcal z nieznanego współczynnika aktywności, dodane geometrycznie.
Konwencja, ale konwencja o znanym pochodzeniu.

Przy takim zestawieniu wzór waży więcej niż pomiar do czwartego-piątego
ważenia, po ośmiu ma już tylko około 17 % głosu, a po dwunastu niecałe 6 %.
Liczba na ekranie przesuwa się więc płynnie od przewidywania do pomiaru, bez
dnia, w którym skacze.

### Zabezpieczenie przed fałszywą pewnością

Odchylenie reszt z czterech punktów bywa z przypadku bardzo małe - cztery
ważenia mogą ułożyć się w prostą idealnie. Wtedy wzór na SE wypluwa liczbę
w rodzaju 30 kcal i aplikacja ogłasza pewność, której nie ma. Dlatego
**σ do wzoru to `max(odchylenie reszt, 0.6 kg)`**. Podłoga jest ważniejsza
od elegancji.

### Progi wejścia

Poniżej tego nie liczymy nic i pokazujemy samą formułę, z informacją, czego
brakuje:

- co najmniej **4 ważenia**,
- rozpiętość co najmniej **28 dni**,
- dziennik wypełniony w co najmniej **70 % dni** okna.

Okno to **56 dni**. Przy ważeniu raz w tygodniu daje osiem punktów, czyli
±136 kcal - najmniejsze okno, przy którym pomiar ma sens, a jeszcze nie
uśrednia dwóch różnych faz.

---

## 4. Przeliczanie masy na energię

| kierunek | kcal/kg | skąd |
|---|---|---|
| ubytek | **7700** | reguła Wishnofsky'ego, konwencja **[?]** |
| przyrost | **5500** | mieszanka tkanki tłuszczowej i beztłuszczowej |

Tkanka tłuszczowa to w około 87 % triglicerydy po 9.4 kcal/g, czyli nieco
ponad 8000 kcal/kg; 7700 jest liczbą zaokrągloną w dół i tak się ją podaje od
1958 roku. Tkanka beztłuszczowa jest tania: to głównie woda, około
1800 kcal/kg. Przyrost na nadwyżce przy treningu siłowym jest mieszanką obu,
stąd wartość pośrednia.

Asymetria nie tworzy skoku: przy nachyleniu zero oba współczynniki dają to
samo zero.

Hall pokazał, że reguła 7700 zawyża ubytek w długim okresie, bo wydatek spada
razem z masą **[?]**. Nas to nie dotyczy w tym miejscu: my nie przewidujemy
ubytku z deficytu, tylko odwrotnie - liczymy wydatek ze znanego ubytku. Spadek
wydatku w czasie jest dokładnie tym, co to oszacowanie ma wyłapać.

---

## 5. Ścieżka formuły, kiedy pomiaru jeszcze nie ma

### Spoczynkowa przemiana materii

Dwa wzory, wybór zależy od tego, czy znamy skład ciała:

```
Mifflin-St Jeor:   10 * kg + 6.25 * cm - 5 * lat + 5        (mężczyzna)
                   10 * kg + 6.25 * cm - 5 * lat - 161      (kobieta)

Katch-McArdle:     370 + 21.6 * masa beztłuszczowa [kg]
```

Jeśli znamy procent tkanki tłuszczowej, bierzemy Katcha-McArdle'a. Skąd
procent, w tej kolejności: obwody (US Navy), opór ręka - stopa liczony
w `data/Bia.kt`, na końcu liczba z aplikacji producenta. Opór stopa - stopa
się nie liczy - powody w `sklad-ciala.md`, rozdział 4 i 7. Powód: wzór Mifflina nie wie nic o składzie ciała, więc dla dwóch osób o tej
samej masie i wzroście daje tę samą liczbę - a osoba z 12 % tłuszczu ma
istotnie więcej tkanki metabolicznie czynnej niż osoba z 30 %. Dla kogoś, kto
trenuje siłowo, to nie jest różnica teoretyczna.

`bmr_kcal` z wagi **nie jest** używane jako źródło. Waga liczy je z własnego,
nieujawnionego wzoru na podstawie tej samej bioimpedancji, która daje procent
tłuszczu. Trzymamy to jako liczbę do porównania na ekranie, nie jako wejście.

### Od spoczynkowej do całodobowej

```
wydatek = (RMR * 1.15 + kroki * masa * 0.0004 + trening) / 0.90
```

- **1.15** to wszystko, co nie jest ani krokami, ani treningiem: siedzenie,
  stanie, wiercenie się. Konwencja.
- **0.0004 kcal na kilogram na krok** wychodzi z kosztu marszu około
  0.53 kcal/kg/km i długości kroku 0.75 m. Dla 85 kg i 10 000 kroków daje
  340 kcal - zgodne z rzędem wielkości.
- **trening** to średnia dobowa z sesji siłowych w oknie, licząc około
  350 kcal na sesję. Dane o sesjach są w bazie, nie trzeba ich zgadywać.
- **dzielenie przez 0.90** to efekt termiczny pożywienia, 10 % spożycia.

Sprawdzenie na przykładzie: mężczyzna 30 lat, 180 cm, 85 kg, 20 % tłuszczu,
10 000 kroków, 4 treningi w tygodniu. RMR z Mifflina 1830, z Katcha 1839.
Wydatek wychodzi 2938 kcal, czyli mnożnik 1.61 względem spoczynkowej - środek
zakresu, jaki podaje się dla osoby trenującej. Para konwencji 1.15 i 0.90 jest
dobrana właśnie tak, żeby suma wypadała w tym zakresie.

**Efektu termicznego nie odejmuje się po stronie pomiaru.** Wydatek policzony
z bilansu energii zawiera go już w sobie, bo wynika ze zjedzonych kalorii.
Dotyczy wyłącznie ścieżki formuły. Pomyłka w tym miejscu to 10 % błędu.

---

## 6. Cele: kalorie i makroskładniki

### Kalorie

Tempo podawane jako procent masy ciała na tydzień, nie w kilogramach - ta sama
utrata 0.5 kg/tydzień jest umiarkowana przy 100 kg i agresywna przy 55 kg.

| faza | zakres | domyślnie |
|---|---|---|
| redukcja | 0.5-1.0 %/tyg. | **0.7 %** |
| utrzymanie | 0 | 0 |
| nadwyżka | 0.25-0.5 %/tyg. | **0.35 %** |

Garthe porównał 0.7 % z 1.4 % na tydzień u sportowców: wolniejsza redukcja
lepiej chroniła masę beztłuszczową i wyniki siłowe **[?]**. Domyślna wartość
to ta wolniejsza. Nadwyżka jest wyraźnie mniejsza, bo nadmiar nadwyżki idzie
w tłuszcz, a nie w mięśnie - tempa przyrostu nie da się przyspieszyć jedzeniem
ponad pewien pułap.

```
zmiana celu [kcal/d] = tempo[%] * masa * 7700 / (100 * 7)
cel = wydatek - zmiana celu       (redukcja: zmiana dodatnia)
```

Deficyt jest **ograniczony do 25 % wydatku**. Dla 85 kg przy 0.7 % wychodzi
654 kcal deficytu z wydatku 2939, czyli 22 % - przechodzi. Przy agresywnym
tempie i małej masie limit zaczyna działać i to jest jego cel.

### Makroskładniki, w tej kolejności

**Białko pierwsze.** Helms podaje dla osób trenujących w deficycie
2.3-3.1 g/kg masy beztłuszczowej **[V]**, Morton plateau około 1.6 g/kg masy
ciała przy utrzymaniu **[?]**.

| faza | gdy znamy masę beztłuszczową | gdy nie znamy |
|---|---|---|
| redukcja | 2.4 g/kg LBM | 2.2 g/kg masy |
| utrzymanie / nadwyżka | 2.0 g/kg LBM | 1.8 g/kg masy |

Dla 85 kg przy 20 % tłuszczu: redukcja daje 163 g, utrzymanie 136 g.

**Tłuszcz drugi, z podłogą.** Domyślnie 25 % kalorii, nigdy mniej niż
**0.6 g/kg masy ciała**. Podłoga jest konwencją zdrowotną, nie wynikiem
badania: bardzo niski tłuszcz przy jednoczesnym deficycie bywa wiązany
z gospodarką hormonalną, ale to nie jest rzecz, którą bym tu cytował jako
ustaloną.

**Węglowodany to resztka.** Jeśli resztka wychodzi ujemna - agresywna
redukcja przy wysokim białku - **tłuszcz schodzi do podłogi, a potem obniżane
jest białko**. Węglowodany nigdy nie wychodzą poniżej zera; to przypadek,
który musi mieć test, bo przy 55 kg i 1 % na tydzień zdarza się naprawdę.

**Błonnik** jako cel pomocniczy: 14 g na 1000 kcal.

---

## 7. Dziennik

### Posiłki

Pięć stałych plus przekąski: śniadanie, drugie śniadanie, obiad, podwieczorek,
kolacja, przekąski. Stałe, nie dowolne - krótka lista to krótsze wybieranie
przy każdym wpisie, a porównanie śniadań między tygodniami wymaga, żeby
"śniadanie" znaczyło to samo w każdym dniu.

### Makro jest przepisywane, nie liczone z katalogu

Wpis w dzienniku trzyma **gotowe wartości za zjedzoną ilość**, a nie samo
odwołanie do produktu i gramaturę. To ta sama decyzja, co przy `StoredAdvice`:
produkt w Open Food Facts zmieni się za pół roku, bo ktoś poprawi etykietę
albo producent zmieni recepturę - a wtorek z marca ma zostać wtorkiem z marca.
Historia, która zmienia się sama, jest bezwartościowa jako historia.

Koszt: zmiana danych produktu nie poprawia przeszłości. To jest cecha, nie
usterka.

### Co śledzimy

Kalorie, białko, węglowodany, tłuszcz - wymagane. Błonnik, cukry, tłuszcze
nasycone, sól - opcjonalne, bo Open Food Facts wypełnia je nierówno.

Opcjonalne są **naprawdę opcjonalne, czyli `null`, nie zero.** Zero znaczy
"produkt nie ma błonnika", `null` znaczy "nie wiadomo". Suma dnia zachowuje tę
różnicę: `null + null = null`, `null + 5 = 5`. Dzień bez żadnych danych
o błonniku pokazuje brak danych, a nie zero gramów - zero gramów błonnika
byłoby informacją, i to fałszywą.

### Dzień to numer dnia od epoki

Tak samo jak `DailyMetric.day`. Nie znacznik czasu, bo "co zjadłem we wtorek"
nie ma nic wspólnego ze strefami czasowymi, a porównywanie znaczników wymagało
by za każdym razem liczenia granic doby.

---

## 8. Czego ten algorytm nie wie

**Nie wie, czy logujesz uczciwie.** Jeśli systematycznie zaniżasz spożycie
o 300 kcal, oszacowany wydatek będzie o 300 kcal za niski. Jest tu jednak
własność, którą warto rozumieć: cel wyliczony z zaniżonego wydatku jest
zaniżony o tyle samo, więc jedząc "do celu" i licząc tak samo niedokładnie
jak zawsze, **zjesz tyle, ile trzeba**. Błąd stały się skraca. Nie skraca się
błąd zmienny - tydzień skrupulatny po trzech niedbałych psuje oszacowanie
bardziej niż trzy niedbałe tygodnie z rzędu.

**Nie wie o zmianie glikogenu.** Pierwsze 7-10 dni po wejściu w deficyt to
ubytek wody, nie tłuszczu, i w oknie ośmiu tygodni jeden taki tydzień zawyża
nachylenie. Stąd ręczne zerowanie oszacowania przy zmianie fazy.

**Nie wie o treningu siłowym jako takim.** Jeśli przybierasz masę
beztłuszczową przy stałej masie ciała, nachylenie jest zerem i wydatek wychodzi
równy spożyciu. To nie jest błąd arytmetyczny, to granica metody: bilans energii
widzi zapas, nie jego skład.

**Nie wie, czego nie zjadłeś.** Dzień bez wpisów nie jest dniem zerowego
spożycia i nie wchodzi do średniej. Stąd próg 70 % wypełnienia okna - bez
niego wystarczyłoby zapomnieć o kolacjach, żeby algorytm ogłosił niski wydatek.

**Nie wie nic o zdrowiu.** To licznik energii, nie dietetyk. Żadna liczba tutaj
nie jest zaleceniem medycznym.

---

## 9. Gdzie to żyje

| plik | rola |
|---|---|
| `nutrition/Bmr.kt` | spoczynkowa przemiana i ścieżka formuły |
| `nutrition/Tdee.kt` | oszacowanie z bilansu energii, mieszanie z formułą |
| `nutrition/Targets.kt` | cel kaloryczny i rozbicie na makroskładniki |
| `nutrition/Portions.kt` | porcje, gramatura, sumy dnia |
| `nutrition/Recipes.kt` | przepis na 100 g po ugotowaniu |
| `nutrition/NutritionReview.kt` | ładowanie z bazy i zapis celu oraz wydatku |
| `data/Kcal.kt` | zaokrąglanie, jak `Kg.kt` dla ciężarów |
| `data/Nutrition.kt` | model: produkt, porcja, wpis, cel, numer dnia |
| `data/FoodRepos.kt` | jedyna droga do tabel jedzenia |
| `data/FoodSeed.kt` | zestaw produktów podstawowych |
| `data/FoodPack.kt` | wczytanie paczki polskich produktów |
| `net/OpenFoodFacts.kt` | odczyt produktu po kodzie kreskowym |
| `net/Barcodes.kt` | skaner Google Play Services |
| `ui/FoodScreen.kt` | zakładka: widok dnia i podsumowanie |
| `ui/FoodPicker.kt` | szukanie, porcje, szybki wpis, własny produkt |

Wszystko w `nutrition/` poza `NutritionReview.kt` to **czyste funkcje** - zero
bazy, zero Androida. Dzięki temu reguły z tego dokumentu testuje się na
wymyślonych danych, tak jak reguły progresji. `NutritionReview` jest tu
odpowiednikiem `ProgressionReview`: całe ładowanie i zapisywanie siedzi
w jednym miejscu, osobno od reguł.

### Dwie konwencje, których nie wolno pomylić

**Węglowodany nie zawierają błonnika.** Tak podaje je etykieta europejska
i tak zapisuje je Open Food Facts dla produktów z Europy - to dwie osobne
pozycje deklaracji. W Stanach `Total Carbohydrate` obejmuje błonnik, więc dane
z amerykańskich źródeł trzeba przy wczytywaniu przeliczyć. Pomylenie tego
podwaja węglowodany w migdałach i w awokado. Z tej samej konwencji wynika, że
energia ze składu to 4/4/9 **plus 2 kcal za gram błonnika**.

**Dzień to numer dnia od epoki, liczony lokalnie.** Nie znacznik czasu i nie
`ms / 86_400_000` - ten skrót daje dzień UTC, czyli u nas przesuwa wczesny
poranek na dzień poprzedni. Ważenie o 6:30 i śniadanie o 7:00 muszą wylądować
w tym samym dniu.

### Zasady korzystania z Open Food Facts

**Offline najpierw.** Po skanie najpierw sprawdzamy katalog lokalny, dopiero
potem sieć. Każdy odczyt z sieci zostaje zapisany, więc drugi skan tego samego
produktu nie rusza już serwera - w sklepie bez zasięgu działa wszystko, co już
raz skanowałeś.

**Jedno wywołanie odpowiada jednemu prawdziwemu skanowi** - o to prosi sam
projekt. Masowego pobierania tędy nie robimy; od tego są nocne zrzuty, i to
jest droga na etap 4.

**Wyszukiwanie tekstowe nie idzie przez sieć.** Przy sprawdzaniu endpoint
`search` co drugie zapytanie zwracał „Page temporarily unavailable", a odczyt
po kodzie działał za każdym razem. Stąd podział: szukamy lokalnie, przez sieć
pytamy wyłącznie o konkretny kod.

**Nagłówek przedstawia aplikację i zostawia kontakt** - adres repozytorium,
nie adres użytkownika.

**Licencja ODbL wymaga wskazania źródła**, stąd linijka o Open Food Facts
w Profilu. Trzymanie kopii u siebie jest w porządku; obowiązek dzielenia się
zmianami dotyczy publikowania zmodyfikowanej bazy, czego nie robimy.

### Jak odtworzyć paczkę polskich produktów

Paczka w `app/src/main/assets/off-polska.tsv` powstaje z nocnego zrzutu
CSV, **nie przez API** - Open Food Facts prosi, żeby jedno wywołanie API
odpowiadało jednemu prawdziwemu skanowi, a do pobierania hurtem służą zrzuty.

Zrzut ma 1,5 GB po spakowaniu i około 9 GB po rozpakowaniu, więc filtruje się
go strumieniowo, bez zapisywania całości na dysk:

```sh
curl -sL "https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz" \
  | gzip -dc | awk -f tools/filtr-off.awk > polska.tsv
mv polska.tsv app/src/main/assets/off-polska.tsv
```

Filtr zostawia produkty polskie, które mają komplet: kalorie, białko,
węglowodany i tłuszcz. Produkt bez makro nie trafia do katalogu z tego samego
powodu co przy odczycie po kodzie - wpuszczony jako „0 g białka" kłamałby
przy każdym użyciu.

**Polskość rozpoznajemy po dwóch rzeczach, a znacznik kraju się nie liczy.**

Prefiks `590` w kodzie kreskowym jest najmocniejszy: przypisała go Polsce
GS1, więc firma, która zarejestrowała kod, jest polska. Drugi trop to polskie
litery w nazwie - produkt obcej firmy, ale z polską etykietą, czyli taki,
który faktycznie leży na polskiej półce.

Znacznik kraju z Open Food Facts **odrzuciliśmy**, choć początkowo był
jedynym kryterium. Oznacza „sprzedawane również w Polsce" i wpuszczał
szwedzkie klopsiki, niemieckie lody, tajską pastę curry i „Salt water taffy
autumn mix" - 5 620 produktów, których nikt nie wyszuka po polsku.

Szukanie polskich liter robi się przez `index()`, nie wyrażeniem regularnym.
awk porównuje bajty, a znaki spoza ASCII zajmują dwa - klasa znaków
dopasowywała wtedy pojedyncze bajty i przepuszczała szwedzkie „SÅS",
niemieckie „Haselnüsse" i czeskie „Rajčatový".

### Ile tam w ogóle jest

Zmierzone na zrzucie z listopada 2026, dla produktów ze znacznikiem Polski:

| | produktów |
|---|---|
| oznaczonych jako sprzedawane w Polsce | 39 565 |
| **bez jakichkolwiek wartości odżywczych** | **13 638 (34 %)** |
| z kaloriami | 25 927 |
| odrzucone za niepełne makro | 597 (2,3 %) |
| odrzucone za brak nazwy | 1 259 |
| odrzucone za nazwę niełacińską | 188 |

Warto mieć to w głowie, zanim zacznie się podejrzewać filtr o gubienie
produktów. **Jedna trzecia polskiej części bazy to same kody kreskowe**,
które ktoś zeskanował i nigdy nie uzupełnił - żadna aplikacja licząca
kalorie ich nie użyje. Własny filtr kosztuje około sześciuset pozycji.

Prefiks `590` okazał się wart więcej niż cały filtr makro: dokłada 11 235
produktów ponad te 23 824 ze znacznika kraju. To zwyczajne polskie rzeczy,
których nikt nie oznaczył krajem - skrobia ziemniaczana, włoszczyzna, miód
akacjowy, serek Łowicz.

Po odrzuceniu obcych i powtórek paczka ma **28 739 pozycji**.

### Hierarchia źródeł

Katalog zbiera dane z sześciu miejsc o bardzo różnej wiarygodności, więc mają
ustaloną kolejność. Miarą jest to, **czy ktoś te liczby widział na oczy**:

| | źródło | skąd pewność |
|---|---|---|
| 1 | produkt własny | przepisałeś z opakowania |
| 2 | przepis | policzony z Twoich składników |
| 3 | zestaw podstawowy | kurowany, sprawdzony arytmetyką |
| 4 | tabela ze strony sklepu | podaje je sprzedawca dla swojego asortymentu, każdy wiersz sprawdzony wzorem |
| 5 | zeskanowany z Open Food Facts | trzymałeś opakowanie, dane świeżo z serwera |
| 6 | paczka | wsad masowy, którego nikt nie oglądał |

Przy **imporcie** paczka sklepowa ustępuje jednak także paczce OFF, choć stoi
wyżej. To nie niekonsekwencja: import nie wybiera lepszego wiersza, tylko
unika dwóch wierszy o tej samej nazwie na liście wyników - a przy równej
nazwie użyteczniejszy jest ten z kodem kreskowym, bo wychodzi też przy
skanowaniu. Stąd kolejność wczytywania w `MainActivity`: najpierw OFF, potem
sklepy.

Kolejność działa w dwóch miejscach. W **wyszukiwaniu** decyduje o sortowaniu:
wpisanie „twaróg" daje najpierw twaróg chudy i półtłusty z zestawu, a dopiero
pod nimi wpisy z paczki. Przy **imporcie paczki** decyduje o odrzucaniu:
produkt, którego nazwa już w katalogu jest, nie wchodzi drugi raz - bo dwa
wiersze o tej samej nazwie na liście wyników to pytanie bez odpowiedzi.

Zeskanowane odróżniamy od paczkowych po `updated_at`: paczka wstawia zero,
a odczyt z serwera znacznik czasu.

### Czy są inne bazy niż Open Food Facts

**Są, i to zdanie zastępuje poprzednie, które twierdziło, że nie ma.** Ta
pomyłka miała konkretną przyczynę: wniosek wyciągnięto z wyszukiwarki, a nie
z odwiedzenia stron. Sklep sprzedający na odległość **musi** podać wartości
odżywcze, więc tam, gdzie jest sklep internetowy, są i dane.

Przeszukane, z wynikiem:

| źródło | wynik |
|---|---|
| `biedronka.pl/pl/pieczywo` | **ma dane**, w zwykłym HTML-u, razem z gramaturą sztuki - w `FoodSeed.kt` |
| `frisco.pl` | **ma dane**, karty produktów serwowane w HTML-u - w `sklepy-polska.tsv` |
| `zakupy.auchan.pl` | ma dane i własną mapę 25 109 produktów, ale odpowiada 403 na uczciwego klienta i 202 z pustą treścią na przeglądarkowego; przejście wymagałoby obejścia zabezpieczenia przed botami, więc odpada |
| `lidl.pl` | katalog internetowy tylko non-food |
| `kaufland.pl`, `carrefour.pl` | HTTP 403 |
| `marketdino.pl` | karty produktów są, wartości odżywczych nie ma |
| `netto.pl` | mapa strony bez kart produktów |
| `stokrotka.pl`, `polomarket.pl`, `aldi.pl` | zero wystąpień „kcal" |
| `vitalia.pl` | baza produktów zwraca HTTP 410 Gone, wycofana |
| `ilewazy.pl` | **ma wagi porcji**, czyli to, czego nie ma nigdzie indziej - ale regulamin § 7 zabrania kopiowania bez zgody, a serwis należy do Burda Media Polska i sprzedaje abonament na dziennik posiłków; nadaje się tylko do zaglądania ręcznie. Odpowiada po `http`, nie po `https` - stąd wcześniejsze „nie odpowiada" |
| `kalorie.pl` | zaparkowana domena na sprzedaż |
| `kcalmar.com`, `fitatu.com` | bazy komercyjnych konkurentów, nie publiczne dane |
| PZH, „Tabele wartości odżywczej produktów", 1045 pozycji | „udostępniana na podstawie umowy licencyjnej w formacie pliku .XLSX" - nie do swobodnego użycia |
| `dane.gov.pl` | zero zbiorów o wartościach odżywczych |
| USDA FoodData Central | domena publiczna i do pobrania, ale nazwy po angielsku i asortyment amerykański |
| Ciqual (FR), Fineli (FI), Frida (DK), Matvaretabellen (NO) | otwarte licencje, ale produkty nie nasze |

Wniosek, który z tego płynie: **kod kreskowy i jego brak to dwie różne
sprawy.** Produkty paczkowane pokrywa Open Food Facts, bo jest indeksowana
kodami. Czego tam nie ma i być nie może, to towar sprzedawany na wagę -
pieczywo, nabiał z lady, warzywa, wędliny. Na te trzeba iść do sklepu, który
publikuje własne tabele.

### Jak odtworzyć paczkę sklepową

Plik: `app/src/main/assets/sklepy-polska.tsv`, dziesięć kolumn rozdzielonych
tabulatorem - nazwa, kcal, białko, węglowodany, tłuszcz, błonnik, cukry,
nasycone, sól, gramatura. Cztery pierwsze są obowiązkowe, pozostałe mogą być
puste, bo puste znaczy „sklep tego nie podał", a nie „zero".

Narzędzia: `tools/frisco-zbierz.sh` pobiera jedną kartę i wypisuje wiersz,
`tools/frisco-pola.awk` czyta z niej liczby.

```sh
# 1. Adresy kart z czterech dzialow. Stronicowanie przez ?page=N,
#    osiemdziesiat produktow na strone.
for C in "18703/cat,warzywa-i-owoce:5" "18894/cat,z-piekarni-i-cukierni:4" \
         "18974/cat,nabial-jaja-sery:14" "19136/cat,mieso-i-wedliny:8"; do
  SCIEZKA=${C%:*}; for S in $(seq 1 ${C#*:}); do
    [ "$S" = 1 ] && Q="" || Q="?page=$S"
    curl -s --compressed "https://www.frisco.pl/c,$SCIEZKA/stn,searchResults$Q" \
      | grep -aoE '/pid,[0-9]+/n,[a-z0-9-]+/stn,product'
  done; done | sort -u > adresy.txt

# 2. Karty. FRISCO_D wskazuje katalog roboczy z podkatalogiem karty/.
export FRISCO_D=$PWD; mkdir -p karty
xargs -a adresy.txt -I{} -P 4 sh tools/frisco-zbierz.sh {} > wszystko.tsv

# 3. Tylko udane wiersze, bez powtorzen. Liczby na stderr.
awk -f tools/frisco-zloz.awk wszystko.tsv > sklepy-polska.tsv
```

Powtórki odsiewa `frisco-zloz.awk` po **kluczu wyszukiwania**, nie po nazwie -
tak samo jak `searchKey()` w `Nutrition.kt`. Inaczej liczba wierszy w pliku
nie zgadzałaby się z liczbą zaimportowanych, bo „Chleb żytni, 500 g" i „Chleb
żytni 500 g" to dla `ShopPack` jeden produkt.

**Pobieraj z `--compressed`.** Karta waży 1,6 MB bez kompresji i 148 kB z nią.
Bez tej flagi dwanaście kart schodziło minutami i wyglądało, jakby sklep
ograniczał ruch; z nią te same dwanaście pobiera się w cztery sekundy.

Karty odkładają się spakowane w `karty/`, bo poprawka czytnika nie może
oznaczać tysiąca ośmiuset żądań do cudzego serwisu od nowa.

Pobranie tysiąca ośmiuset kart zajmuje około trzydziestu minut, ale
**przeparsowanie ich od nowa - sześć minut i ani jednego żądania**, bo karty
leżą spakowane. Czytnik robi jeden przebieg po pliku: korzysta z tego, że
HTML jest zminifikowany i tabela siedzi w jednym wierszu, więc znaczniki
rozbiera tylko w tym jednym. Pierwsza wersja była potokiem `gzip` do pliku,
`sed` po całych 1,6 MB, dwa `tr`, `grep` po tytuł i `wc` po rozmiar - czyli
czterokrotne czytanie karty i pięć procesów na produkt. Schodziło pół karty
na sekundę, czyli godzinę na komplet.

**Pięć pułapek czytnika strony**, każda udająca brak danych:

1. Frisco używa **dwóch zestawów etykiet**: jedne karty mają „Wartość
   odżywcza (kJ)", inne „Wartość energetyczna (kJ)", w różnej wielkości
   liter. Pierwsza wersja znała jeden zestaw i uznała dwanaście kart
   z danymi za puste.
2. **Kolejność wierszy nie jest stała** - na części kart wiersz
   z kilodżulami stoi na końcu tabeli. Zaczep o „(kJ)" ucinał wtedy całą
   tabelę, czytnik czytał stopkę i wypisywał liczby w rodzaju „003".
   Dlatego zaczepiamy się o nagłówek sekcji.
3. **Tabelę trzeba odciąć, nie tylko znaleźć.** Etykieta nieobecna
   w tabeli dopasowywała się dalej na stronie: w karcie jogurtu Bakomy
   słowo „błonnik" trafiło 185 344 znaki dalej, w nazwie innego produktu
   z listy polecanych, i czytnik wziął 400 gramów błonnika. Granica od
   góry to zdanie „Dzienna porcja obliczona", stojące zaraz za tabelą.
4. Między liczbą a jednostką Frisco stawia **twardą spację** (U+00A0,
   bajty `C2 A0`), nie zwykłą - „250 g" to nie „250" plus spacja plus
   „g". Wzorzec `" ?(g|kg)$"` jej nie trafiał i gramatura wychodziła pusta
   dla **każdego** produktu, we wszystkich wersjach czytnika. Gorzej:
   `gawk` pracuje tu na bajtach, nie na znakach, więc
   `sprintf("%c", 160)` daje pojedynczy bajt `A0`, który dopasowuje się do
   drugiego bajtu pary i po podmianie zostawia wiszący `C2` - w nazwie
   widać wtedy „500? g". Dlatego podmieniamy najpierw całą parę, potem
   pojedynczy znak: w trybie bajtowym działa pierwsze, w znakowym drugie.
5. Każda karta zawiera tabelę **dwa razy**: raz jako HTML, raz w stanie
   Reacta, gdzie znaczniki są zapisane jako `<`. Założenie, że ten
   blok stoi zawsze na końcu pliku, jest błędne - w karcie serka Almette
   z czosnkiem niedźwiedzim pierwsze wystąpienie nagłówka jest właśnie
   w nim, w wierszu 374 z 521. Rozbieranie znaczników go nie tyka,
   a odczyt liczby po „(kcal)" brał cyfry z „003c" i kalorie wychodziły
   „003". Odcinek z `<` pomijamy i szukamy dalej. Jeśli karta ma
   tabelę **tylko** w tym bloku, idzie do odrzutu - liczby są tam
   okrojone („966.", „145.") i zgadywanie ich byłoby gorsze niż brak.

Obroną przed szóstą taką pułapką jest **sprawdzenie wzorem unijnym**:
4·białko + 4·węglowodany + 9·tłuszcz + 2·błonnik musi zgadzać się
z deklarowanymi kaloriami w granicach dziesięciu procent. Wiersz, który tego
nie spełnia, idzie do odrzutu - i to właśnie ten warunek wyłapał wszystkie
pięć powyżej, każdą po tym, jak poprzednia wyglądała na ostatnią. Gramatura
jest tu wyjątkiem: wzór jej nie sprawdza, więc twarda spacja przeżyła trzy
wersje czytnika niezauważona, aż `od -c` na prawdziwym tytule pokazał, co tam
naprawdę stoi. **Pole, którego nic nie weryfikuje, bywa puste latami.**

Czego wzór **nie** sprawdza: czy sklep podał kolumnę na sto gramów, czy na
opakowanie. Karta Monokla z cynamonem podaje 738 kcal, 37 g tłuszczu, 83 g
węglowodanów i 15 g białka „na 100 g". Wzór daje z tego 725 kcal, czyli
zgadza się w granicach dziesięciu procent - bo kolumna na opakowanie jest
wewnętrznie spójna dokładnie tak samo jak ta właściwa. A 135 gramów makro
w stu gramach produktu nie istnieje; podzielone przez gramaturę (160 g) dają
sensowne 461 kcal.

Dlatego drugi warunek: **suma białka, węglowodanów i tłuszczu nie może
przekroczyć 105 g na 100 g produktu.** Granica to 105, nie 100, bo masło
klarowane ze smalcem ma 99,8 g tłuszczu plus śladowe białko i węglowodany,
czyli 100,3 g po zaokrągleniu - i jest prawdziwe.

Tego nie poprawiamy dzieleniem przez gramaturę: to byłoby zgadywanie, którą
kolumnę sklep pomylił. Wiersz idzie do odrzutu. I trzeba wiedzieć, że ten
warunek wyłapuje tylko przypadki rażące - produkt, którego wartości na
opakowanie zmieściłyby się pod stu gramami, przejdzie niezauważony.

Część kart **nie ma tabeli w ogóle** - i to nie usterka. Świeże mięso, surowe
warzywa i ziemniaki to produkty jednoskładnikowe, zwolnione z obowiązku
deklaracji żywieniowej, więc sklep nie podaje dla nich nic. Sprawdzone: w tych
plikach słowo „odżywcz" nie występuje ani raz. Dla takich produktów zostaje
zestaw własny w `FoodSeed.kt`, bo surowa marchew ma te same wartości niezależnie
od sklepu.

Czego **nie** dopisujemy ze wzoru: energii, gdy karta podaje komplet makro
bez wiersza z kaloriami. Policzona energia zgadzałaby się zawsze, także
z błędnie odczytanej tabeli, więc jedyne sprawdzenie przestałoby cokolwiek
sprawdzać.

Po podmianie pliku **podbij `ShopPack.VERSION`** - inaczej aplikacja uzna, że
paczka już jest wczytana.

### Skąd się biorą wagi porcji

Porcja - kromka, łyżka, sztuka, garść - jest po to, żeby wpis nie wymagał
wagi kuchennej. **Ma ją każda z 311 pozycji zestawu podstawowego**, i pilnuje
tego test: nowa pozycja bez porcji się nie przeciśnie. Wcześniej test
sprawdzał ręcznie wybraną listę jedenastu produktów „których nikt nie waży" -
to było obejście, nie reguła, bo porcje miało wtedy 248 pozycji z 311.

Liczby pochodzą z trzech miejsc i **różnią się pewnością**:

| | skąd | przykład |
|---|---|---|
| 1 | gramatura podana przez sprzedawcę | pieczywo Biedronki, `package_g` z kart Frisco |
| 2 | miara domowa | łyżka 15 g, łyżeczka 5 g, szklanka 250 ml, garść 30 g |
| 3 | typowa masa sztuki | jabłko 180 g, jajko 55 g, plaster boczku 20 g |

**Dwa ostatnie wiersze to konwencje, nie pomiary.** Są okrągłe i mieszczą się
w rzeczywistym zakresie, ale konkretna śliwka waży tyle, ile waży. Gdy
pojawia się liczba z wiersza pierwszego, zastępuje konwencję - dlatego
produkt z paczki sklepowej, który ma `package_g`, dostaje „opakowanie"
z prawdziwą gramaturą, a nie zgadywaną sztukę.

Granica rozsądku, też w teście: porcja większa od zera i nie większa niż
kilogram. Główka kalafiora (700 g) jest przy tej granicy i jest prawdziwa.

`ilewazy.pl` ma te wagi zmierzone i sfotografowane - to jego cała wartość -
ale regulamin zabrania kopiowania, więc służy wyłącznie do zajrzenia, gdy
konwencja wydaje się podejrzana. Tak właśnie poprawiono świeżą śliwkę: miała
w pierwszym podejściu 35 g, a waży 50-70.

### Zanim uznasz, że produktu nie ma

Pięć razy pod rząd wyglądało na brak danych, a nie było nim ani razu.

**Marka bywa zapisana na kilka sposobów.** Yeemy występuje w zrzucie jako
`Yeemy` (31 razy), `YEEMY` (6) i `Yemmy` (2). Szukanie po jednej pisowni
znajduje ułamek asortymentu i wygląda, jakby reszty nie było - a marka ma
w paczce 39 produktów.

**Brak wyniku bywa wadą wyszukiwania, nie danych.** „Skrzydełka panierowane"
marki Yeemy nie znajdowały się po „yeemy skrzydełka", bo szukaliśmy ciągłego
fragmentu, a między tymi słowami stoi „panierowane". Stąd dzielenie
zapytania na słowa - patrz [searchWords].

**Wyszukiwarka nie jest dowodem, że strony nie ma.** Pytanie, czy sieci
publikują wartości odżywcze własnego pieczywa, zamknięto raz odpowiedzią
„nie publikują" - na podstawie wyników wyszukiwania, które zwróciły same
serwisy wtórne. Biedronka podaje te dane pod adresem
`biedronka.pl/pl/pieczywo?page=skladniki`, w zwykłym HTML-u, razem
z gramaturą sztuki. Wniosek: zanim napiszesz, że źródła nie ma, **wejdź na
stronę sklepu.**

**Brak połączenia nie jest dowodem, że serwis nie żyje.** `ilewazy.pl`
zapisano jako „nie odpowiada", bo `curl` po `https` zwracał kod 000. Serwis
po prostu **nie ma HTTPS** - po `http` odpowiada 200. Przy kodzie 000
sprawdź drugi protokół, zanim uznasz stronę za martwą; 000 znaczy „nie
zestawiono połączenia", a nie „serwera nie ma".

**Odrzut czytnika nie jest dowodem, że karta jest pusta.** Trzy razy
„produkt bez wartości odżywczych" okazało się usterką czytnika strony -
nieznany zestaw etykiet, przestawiona kolejność wierszy, nieodcięta tabela.
Patrz rozdział o odtwarzaniu paczki sklepowej. Przy odrzucie otwórz kartę
i przeczytaj ją oczami, zamiast dopisywać wyjątek.

Kolejność sprawdzania przy podejrzeniu, że czegoś brakuje: najpierw wpisz
samą nazwę rodzajową bez marki („nuggetsy", „skrzydełka pikantne"), potem
policz wystąpienia marki w pliku paczki, potem sprawdź, czy produkt ma
w ogóle kod kreskowy - towar na wagę go nie ma i w Open Food Facts być nie
może - a dopiero na końcu podejrzewaj filtr.

Kolumny zrzutu są podane numerami, bo CSV ma ich 211 i nazwy w nagłówku są
jedynym sposobem, żeby je odnaleźć. Przy zmianie układu zrzutu trzeba je
sprawdzić od nowa: `gzip -dc | head -1 | tr '\t' '\n' | cat -n`.

Po podmianie pliku **podbij `FoodPack.VERSION`** - inaczej aplikacja uzna,
że paczka już jest wczytana, i nowych produktów nie dociągnie.

**Zmieniając którąkolwiek liczbę z tego dokumentu, zmień i jego.**

---

## 10. Stan implementacji

| rzecz | stan |
|---|---|
| schemat bazy, wersja 16 | migracja wykonana na telefonie 31.10.2026 |
| model i repozytoria | sprawdzone na telefonie przez ekran |
| wzory BMR i ścieżka formuły | zrobione, przetestowane |
| oszacowanie z bilansu energii | zrobione, przetestowane |
| cele i makroskładniki | zrobione, przetestowane |
| porcje, przepisy, sumy dnia | zrobione, przetestowane |
| zestaw produktów podstawowych | zrobione, ~150 pozycji, przetestowane |
| ładowanie i zapis (`NutritionReview`) | działa na telefonie |
| zakładka, widok dnia, szukanie, wpisy | sprawdzone na telefonie 31.10.2026 |
| skaner kodów kreskowych | sprawdzony na telefonie 02.11.2026 |
| Open Food Facts po kodzie | sprawdzone na telefonie, obie ścieżki |
| własny produkt z kodem kreskowym | sprawdzone na telefonie |
| przepisy w interfejsie | nie ma |
| paczka offline z Open Food Facts | nie ma, etap 4 |
| wykresy i krzywa wydatku | nie ma |
| tempo diety do ustawienia w Profilu | nie ma, na razie domyślne dla fazy |
