# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Co to jest

Dziennik treningu siłowego: aplikacja na Androida plus mini-aplikacja na zegarek Amazfit Balance. Jeden użytkownik, jedna siłownia, prawdziwe dane - w bazie na telefonie leży cała historia treningów.

Trening prowadzi się **klikając wyłącznie na zegarku**: seria, przerwa, zapas powtórzeń, zmiana ciężaru. Telefon zostaje w szatni. Po treningu aplikacja proponuje progresję wyliczoną z tego, co faktycznie poszło, i z talerzy, które naprawdę leżą na tej siłowni.

Dwa katalogi, jedno repozytorium:

| Katalog | Co | Technologie |
|---|---|---|
| `GymBro/` | aplikacja na Androida | Kotlin, Jetpack Compose (Material3), SQLite bez ORM |
| `gymbro-watch/` | mini-aplikacja na Zepp OS 3.0 | JavaScript, `@zos/*`, Side Service |

Leżą razem, bo protokół między nimi jest jeden: zmiana w `GymBro/app/src/main/java/pl/gymbro/net/WatchApi.kt` prawie zawsze pociąga zmianę w `gymbro-watch/page/index.js`.

## Polecenia

### Aplikacja na telefon

**W repozytorium nie ma wrappera Gradle'a** - `gradlew` nie istnieje, mimo że README go wspomina. Zamiast niego jest `GymBro/tools/gr.sh`, który ustawia JDK 21 i woła dystrybucję z cache Android Studio:

```sh
GymBro/tools/gr.sh :app:testDebugUnitTest        # wszystkie testy jednostkowe
GymBro/tools/gr.sh :app:assembleDebug            # APK
GymBro/tools/gr.sh :app:assembleRelease          # do mierzenia płynności, patrz niżej
```

Pojedyncza klasa albo pojedynczy test:

```sh
GymBro/tools/gr.sh :app:testDebugUnitTest --tests "pl.gymbro.gym.ProgressionAdvisorTest"
GymBro/tools/gr.sh :app:testDebugUnitTest --tests "*duze przekroczenie zakresu*"
```

Domyślne JDK na tej maszynie jest za nowe dla Gradle 8.9 - `gr.sh` dlatego wymusza `JAVA_HOME`. Nie wołaj `gradle` bezpośrednio.

Demon Gradle'a zostaje w tle po buildzie i **trzyma uchwyty do katalogu projektu**. Jeśli coś blokuje zmianę nazwy albo usunięcie folderu, najpierw `GymBro/tools/gr.sh --stop`.

### Zegarek

```sh
cd gymbro-watch
node tools/check.js          # 453 sprawdzenia, bez zegarka
zeus build
zeus preview -t "Amazfit Balance"
```

`zeus preview` bez `-t` pyta interaktywnie i zawiesza się w nieinteraktywnej powłoce. Kończy się kodem QR do zeskanowania w aplikacji Zepp - to jedyna droga instalacji, nie ma odpowiednika `adb install`.

W PowerShellu wołaj `zeus.cmd`, nie `zeus`.

**Każda zmiana w `gymbro-watch/` kończy się kodem QR.** Bez niego poprawka leży w repozytorium, a na nadgarstku zostaje stara wersja - i następny trening wygląda tak, jakby nikt niczego nie naprawił. Więc po zmianie: `node tools/check.js`, `zeus build`, `zeus preview -t "Amazfit Balance"`, a obrazek QR podaj użytkownikowi do zeskanowania. Nie pytaj, czy go chce.

`tools/check.js` ładuje prawdziwy `page/index.js` z zaślepkami pod `@zos/*` i pod telefon, po czym przechodzi scenariusze tak, jak robi to palec. **Sprawdza zachowanie, nie składnię** - przepływ ekranów, licznik serii, kolejkę offline, liczbę zapisów do widgetów. Nie powie, że napis się obcina.

### Telefon przez adb

```sh
adb install -r GymBro/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n pl.gymbro/.MainActivity
```

W Git Bashu ścieżki androidowe wymagają `MSYS_NO_PATHCONV=1`, inaczej bash przerobi `/data/local/tmp` na ścieżkę windowsową.

Serwer adb też dziedziczy katalog roboczy i trzyma folder - `adb kill-server`, gdy przeszkadza.

`GymBro/tools/bench.sh` mierzy płynność przez `dumpsys gfxinfo`. Ma zaszyte pięć pozycji zakładek (`XS="98 319 540 761 980"`) i od czasu dołożenia Jedzenia zgadza się to z aplikacją - wcześniej wymagał poprawki. Płynność oceniaj na wariancie release; debug Compose jest kilkukrotnie wolniejszy i pomiar z niego nic nie znaczy.

## Architektura

### Łańcuch łączności

```
zegarek  --Bluetooth-->  Side Service (wewnątrz aplikacji Zepp na telefonie)
         --HTTP 127.0.0.1:48627-->  WatchLinkService  -->  WatchApi  -->  baza
```

Mini-aplikacja Zepp OS nie umie sięgnąć do innej aplikacji na telefonie. Umie tylko oddać robotę swojej usłudze towarzyszącej (`gymbro-watch/app-side/index.js`), która działa już na telefonie i potrafi zrobić zwykłe `fetch`. Adres nigdy nie opuszcza telefonu, więc działa to bez zasięgu.

`LocalServer` wiąże gniazdo jawnie z pętlą zwrotną, nie z `0.0.0.0`, i wymaga nagłówka `X-GymBro-Token`. `WatchLinkService` jest usługą pierwszoplanową ze `START_STICKY` i `BootReceiver`, bo telefon leży w kieszeni z wygaszonym ekranem.

**GymBro nie musi być otwarty** - usługa działa niezależnie od aktywności.

**Od Androida 14 pilot nie wstanie bez zgody na urządzenia w pobliżu.** Typ `connectedDevice` wymaga jej już przy `startForeground`, inaczej leci `SecurityException` - na świeżej instalacji była to awaria przy pierwszym uruchomieniu. Dlatego `WatchLinkService.start` sprawdza `canStart` i bez zgody nie startuje wcale, a o zgodę pyta `MainActivity` przy starcie i przycisk w Profilu. Niesprawdzone na urządzeniu: telefon ma Androida 13, a emulator na tej maszynie nie rusza bez sterownika hypervisora.

### Podział pracy: zegarek nic nie liczy

Zegarek wysyła intencję („zapisz serię", „ciężar w górę") i rysuje to, co wróci. Ciężary, talerze i progresję liczy telefon, w jednym miejscu (`gym/LiveSession.kt`), nieświadomym tego, kto pyta. Dwa miejsca liczące propozycję ciężaru rozjechałyby się przy pierwszej zmianie reguł.

Wyjątkiem jest **reakcja ekranu**: licznik podbija się lokalnie od razu, żądanie leci w tle. Niepotwierdzone serie liczone są osobno, inaczej odpowiedź na starsze żądanie cofałaby licznik.

### Trening bez łączności

Zegarek działa, gdy telefon jest wyłączony albo poza zasięgiem:

- **kolejka wyjściowa** w `@zos/storage` z ponawianiem od 5 s z podwajaniem do 2 min
- każde polecenie ma `cid`; telefon trzyma LRU ostatnich 200 w pamięci
- sesja założona na zegarku ma `local_id` z unikalnym indeksem częściowym w bazie - to **trwała** ochrona przed duplikatem, gdy telefon odpowie po restarcie
- **zaległa kolejka nigdy nie jest wyrzucana** - baza na telefonie to jedyna kopia historii, więc trening dojeżdża choćby po dobie. Po 8 godzinach (`STALE_AFTER_MS`, ta sama granica po obu stronach) kolejce bez `FINISH` dopisywane jest zakończenie na godzinie ostatniego polecenia, żeby zaległy trening nie dojechał jako trwający
- zaległy `START` ze znacznikiem zegarka telefon dopisuje **od razu zamkniętym** (`startClosed`) i rutuje do niego dalsze polecenia po `sid`: inaczej `active()` kasował właśnie założoną sesję jako porzuconą i cały trening przepadał z błędem „brak trwającego treningu"
- współczynniki spalania leżą w zapisie **jednostek**, nie sesji - ten drugi ginie na koniec treningu i dlatego kalorie stały na zerze przez cały trening bez telefonu
- stan kolejki widać na zegarku: na wyborze jednostki, na ekranie czasu i w podsumowaniu, a jedna wibracja mówi, że wszystko dojechało
- **kolejka leży wyłącznie pod `gymbro-outbox`**, nigdy w zapisie sesji. Dwie kopie sumowały się przy każdym restarcie strony i bez łączności kolejka podwajała się przy każdym opuszczeniu nadgarstka (zmierzone: 3 polecenia → 24 po trzech restartach) - stąd lagi i ubita aplikacja przy końcu treningu
- **licznik serii, dziennik i sumy liczą się z jednego widoku** (`nakladka()`): ostatni stan od telefonu plus polecenia, które jeszcze czekają. Nie ma osobnego licznika niepotwierdzonych - rozjeżdżał się z dziennikiem
- trening wraca po restarcie z własnej pamięci (plan w `gymbro-session-big`) także wtedy, gdy ma już numer od telefonu - inaczej bez łączności zegarek pokazywał wybór jednostki w środku treningu
- zapas i poprawka serii, której numeru zegarek jeszcze nie zna, wskazują ją znacznikiem polecenia SET (`ref`); telefon zamienia go na numer (`seen` pamięta `cid → setId`). Seria jeszcze niewysłana dostaje zmianę wprost w swoim poleceniu
- trzy rodzaje odpowiedzi: wykonane, odmowa (`reject`, 4xx - wypada tylko to polecenie) i awaria po drodze (`retry` - polecenie zostaje). Ustala je usługa towarzysząca; wcześniej każdy błąd wyrzucał polecenie z kolejki
- zaległość leci paczkami (`BATCH` → `/batch`, do 15 poleceń) z lekkim stanem bez drabinek (`lite`); zegarek dokleja drabinki z poprzedniego stanu. Telefon bez `/batch` dostaje polecenia po jednym
- START z zegarka zamyka trening, który jeszcze trwa na telefonie (`closeAbandoned`), zamiast odpowiadać 409 - odmowa kończyła się tym, że serie nowego treningu szły do starego. Stan od telefonu niesie `localId`, po którym zegarek poznaje, czy to jego trening

### Progresja

Cztery pliki, rozdzielone celowo:

| Plik | Rola |
|---|---|
| `gym/ProgressionEngine.kt` | ciężar na dzisiejszą sesję (podwójna progresja) |
| `gym/ProgressionAdvisor.kt` | propozycje zmian w planie po treningu, czysta funkcja |
| `gym/ProgressionReview.kt` | ładowanie z bazy i zapis propozycji |
| `gym/PlateCalculator.kt` | co da się złożyć z posiadanych talerzy |

`ProgressionAdvisor` nie dotyka bazy - dzięki temu reguły z `GymBro/docs/progresja.md` testuje się na wymyślonych danych, bez emulatora. Ten dokument jest źródłem prawdy dla liczb i wyraźnie oznacza, gdzie liczba jest konwencją, a gdzie wynikiem badania. **Zmieniając regułę, zmień i jego.**

Zasada naczelna doradcy: koszt milczenia jest wyższy niż koszt zbędnej propozycji. Przy wątpliwościach mówimy; wyjątkiem są propozycje zejścia z ciężaru, bo te kosztują więcej niż spojrzenie - przychodzą odznaczone.

`PlateCalculator` rozróżnia dwa zastosowania, które łatwo pomylić: **wybierak** (`nextUp`/`nextDown`) musi dosięgnąć każdego osiągalnego ciężaru, a **propozycja** (`handyLadder`) preferuje progi wymagające najwyżej dwóch zmian talerzy. Zlanie ich w jedno odcina użytkownika od okrągłych liczb.

### Jedzenie

`GymBro/docs/kalorie.md` jest dla tej zakładki tym, czym `progresja.md` dla
progresji: **źródłem prawdy dla liczb**, z wyraźnym oznaczeniem, co jest
konwencją, a co wynikiem pomiaru. Zmieniając regułę, zmień i jego.

**Węglowodany nie zawierają błonnika.** Tak podaje je etykieta europejska
i tak zapisuje je Open Food Facts dla produktów z Europy. W Stanach `Total
Carbohydrate` obejmuje błonnik, więc dane z tamtych źródeł trzeba przeliczyć.
Energia ze składu to 4/4/9 **plus 2 kcal za gram błonnika**. Na tej konwencji
wywracał się już i kod, i test - a służy teraz za sprawdzian poprawności
wsadów masowych.

Katalog produktów zbiera dane z trzech miejsc o ustalonej kolejności:

| Plik | Co wnosi |
|---|---|
| `data/FoodSeed.kt` | zestaw podstawowy, 311 pozycji wpisanych ręcznie |
| `data/FoodPack.kt` | paczka Open Food Facts, produkty z kodem kreskowym |
| `data/ShopPack.kt` | tabele ze stron sklepów - towar sprzedawany na wagę |

Trzeci istnieje dlatego, że Open Food Facts jest indeksowana kodami
kreskowymi, a bułka z lady kodu nie ma. Pełna hierarchia źródeł i powody, dla
których przy imporcie bywa odwrotna niż przy wyszukiwaniu, są w `kalorie.md`.

**`FoodSeed.populate` jest idempotentne i rozszerzalne**, ale woła się
wyłącznie z migracji. Dodanie pozycji albo porcji do zestawu wymaga więc
nowej gałęzi w `onUpgrade` - bez niej zmiana zostaje w kodzie i nigdy nie
dociera na telefon, a migracja wygląda na udaną.

**Własne dania** (`recipe`, `nutrition/Recipes.kt`, `ui/MyMeals.kt`) są
przeliczane na zwykły wiersz katalogu ze źródłem `RECIPE`. Dzięki temu
wpisanie dania do dziennika jest tym samym, co wpisanie jogurtu, i nie ma
drugiej ścieżki do utrzymania. „Danie" to celowo inne słowo niż „posiłek" -
posiłek to pora dnia (Śniadanie, Obiad), danie to złożony produkt.

**Wydatek liczy się z bilansu energii**, nie z wzoru, gdy tylko jest z czego:
regresja liniowa po ważeniach z 56 dni, mieszana z oszacowaniem ze wzoru
odwrotnie proporcjonalnie do wariancji. Ważenie raz w tygodniu wyklucza
średnią kroczącą po dniach - progi wejścia i niepewność pomiaru opisuje
`kalorie.md`, rozdział 3.

**Klawiatura podnosi się tu sama** i zasłania dolne kilkaset pikseli. Każdy
ekran z polem tekstowym musi to uwzględniać: przyciski idą nad pole albo
dostają `imePadding()`. Trzy razy okazało się, że przycisk kończący robotę
jest niewidoczny dokładnie wtedy, gdy się go potrzebuje.

### Baza

`data/Schema.kt`, `SQLiteOpenHelper`, schemat w wersji 23. Bez Room celowo: Room wymaga KSP, czyli kolejnej wtyczki Gradle z własną macierzą zgodności.

Każda zmiana schematu podbija `VERSION`, dopisuje `Vn_STATEMENTS` i gałąź w `onUpgrade`. **Nigdy nie kasujemy tabel z danymi użytkownika.** Dwadzieścia jeden tabel; `session` / `set_entry` / `plan_item` to rdzeń treningu, `food` / `food_log` / `food_portion` to rdzeń dziennika jedzenia.

Zapisując ciężary, przepuszczaj je przez `data/Kg.kt` (`roundKg`). Arytmetyka zmiennoprzecinkowa wpisywała do bazy wartości w rodzaju `81.39999999999999`.

### Waga łazienkowa

`ble/`, pięć warstw, rozdzielonych tak, żeby wszystko poza samym radiem dało się testować na JVM:

| Plik | Rola |
|---|---|
| `ble/ScaleScanner.kt` | skan, połączenie GATT, log diagnostyczny - jedyny plik z Androidem |
| `ble/ScaleDecoders.kt` | jedno miejsce, które zna wszystkie rodziny wag; czyste funkcje na bajtach |
| `ble/OkOkParser.kt`, `YolandaParser.kt`, `MiScaleParser.kt`, `StandardScaleParser.kt` | po jednym protokole |
| `ble/WeightSettler.kt` | kiedy ciąg odczytów jest już wynikiem |

Nie ma protokołu wspólnego dla wszystkich wag - „łączy się z każdą" znaczy listę sterowników dobieranych po tym, czym waga się przedstawia. Nową rodzinę dopisuje się jako parser plus jedna gałąź w `ScaleDecoders`, z testem na prawdziwej ramce. Sprawdzona na sprzęcie jest tylko domowa „Yoda0" (OKOK C0, samo rozgłoszenie); Xiaomi, standard Bluetooth SIG i Qingniu przez GATT są napisane z otwartych opisów protokołów.

**Skan zawsze z filtrem, choćby pustym.** Zmierzone na telefonie (HyperOS, Android 13): skan bez filtra widzi 2 urządzenia na 10 s, z pustym filtrem 11-15. Do tego system i tak przycina tryb `LOW_LATENCY` do okna 114 ms co 456 ms (`configureRegularScanParams` w logcacie), więc do aplikacji dociera ułamek tego, co waga nadaje.

**Dlatego wynik nie może zależeć od jednej ramki.** Liczba idzie na ekran od pierwszego pakietu, a wynikiem staje się na trzy sposoby: waga ustawiła bit „ustalone", ta sama liczba stoi odpowiednio długo, albo waga zamilkła - wtedy ostatnia liczba przychodzi z zastrzeżeniem. Czekanie wyłącznie na bit kończyło się tym, że pomiar łapał się po kilkunastu sekundach albo wcale.

**Fałszywy pomiar jest gorszy niż brak pomiaru.** Dekoder dobiera się po ID producenta, UUID serwisu albo charakterystyce, nigdy po samym kształcie bajtów - dopasowany „na oko" wyciągnął kiedyś 26,50 kg z rozgłoszenia komputera.

`GymBro/tools/sztuczna-waga.ps1` nadaje z komputera ramki takie jak domowa waga (Windows PowerShell 5.1, nie `pwsh`). Pozwala przejść cały kreator pomiaru bez stawania na wadze; nie nadaje się do mierzenia opóźnień, bo Windows nadaje rzadko i nierówno. **Próbnego pomiaru nie zapisuj** - kończ przez Anuluj, inaczej 84,05 kg ląduje w historii.

### Zakładki

Pięć: Trening, Jedzenie, Waga, Plan, Profil (`MainActivity.kt`, `ui/TabHost.kt`).

Ukryta zakładka jest **mierzona, ale nierysowana i głucha na dotyk**. Pomiar zostaje, bo mierzenie rozbudowanego ekranu od nowa to kilkadziesiąt milisekund zwiechy przy każdym powrocie. Głuchota jest konieczna, bo ukryty ekran nadal leży w drzewie.

**Opuszczenie zakładki zwija ją do widoku głównego.** Trzy drogi zmiany zakładki - pasek, systemowe wstecz, skok z wnętrza ekranu - przechodzą przez jedną lambdę `idzDo`, która podbija licznik `reselect` opuszczanej zakładki. Ekrany reagują przez `LocalTabReselect`.

`ui/Busy.kt` to **licznik, nie flaga** - kilka ekranów ładuje się naraz.

## Konwencje

**Krótkie myślniki, nigdy długie.** `-` wszędzie: w kodzie, w komentarzach, w tekstach w bazie.

**Komentarze po polsku i o tym, dlaczego.** Kod jest gęsto komentowany, ale komentarze nie powtarzają tego, co widać - tłumaczą decyzję, zwykle opisując, co się zepsuło bez niej. Pisząc nowy kod, trzymaj ten rejestr; zdanie w rodzaju „bez tego przy powrocie wracało się w szczegóły sprzed kwadransa" jest wzorem.

**Nazwy testów po polsku, w odwrotnych apostrofach:** ``fun `zapas zero nie blokuje, tylko zmniejsza skok`()``.

**Commity bez linijek o współautorstwie** - żadnego `Co-Authored-By`, żadnej wzmianki o narzędziu. Wiadomości pisz przez `git commit -F plik`; heredoc potrafił wstrzyknąć własny znacznik `EOF` do treści.

Gałąź domyślna to `master`, zdalne `origin` to `github.com/kubab96/GymBro`.

## Rzeczy, które łatwo zepsuć

**Token pilota.** `gymbro-watch/utils/config/link.js` jest w `.gitignore` - paruje konkretny zegarek z konkretnym telefonem. Wzór: `link.example.js`, wartość: GymBro → Profil → Pilot na zegarku, pole „Kod parowania". Bez tego pliku paczka się zbuduje, ale telefon odrzuci każde żądanie z 401.

**Baza to jedyna kopia historii treningów.** Nie ma eksportu. `GymBro/kopie/` trzyma ręczne kopie ściągnięte z telefonu i jest poza repozytorium. Przed czymkolwiek, co dotyka danych, zrób kopię.

**Diagnostyka jest tylko w wariancie debug.** Podsłuch BLE i surowy zrzut Health Connect (`ui/Diagnostics.kt`), log skanera wagi w logcacie, zgadywanie masy z nieznanych ramek i adres pilota w Profilu wiszą na `BuildConfig.DEBUG`. W release przycisków „Diagnostyka" i „Surowe" nie ma, a `adb logcat -s GymBroBLE` milczy - do rozgryzania wagi instaluj debug. Płynność dalej mierz na release.

**Na zegarku nic się nie animuje poza nazwą ćwiczenia** (raz tam i z powrotem). Pełnoekranowa zasłona przejścia i błysk w 60 klatkach były ćwierć sekundy głuchoty na palec przy każdej zmianie ekranu. Zapis do pamięci czeka na 1,2 s ciszy po ostatnim dotyku (`CISZA_MS`); tylko START i FINISH idą do pliku od razu.

**Mierz, nie dedukuj.** Lagi przycisków na zegarku dwa razy „naprawiono" bez pomiaru i dwa razy wróciły. Przyczyny okazały się trzy naraz: animacja nazwy chodząca bez przerwy, operacja na pamięci w ścieżce kliknięcia i 134 zapisy do widgetów przy przejściu, z których 110 nic nie zmieniało. `tools/check.js` liczy zapisy i zapisy do pamięci - używaj tego. Po naprawie cofnij poprawkę i sprawdź, czy test faktycznie pada.

**Zmieniając zachowanie, przepisz test, nie osłabiaj go.** Testy wielokrotnie padały dlatego, że zachowanie zmieniło się celowo. Nowy test ma kodować nową regułę, nie omijać ją.
