# GymBro - aplikacja na Androida

Dziennik treningu siłowego. Prowadzi trening, zapisuje serie, liczy propozycje
progresji i obsługuje pilota na zegarku; obok tego dziennik jedzenia i pomiary
masy ciała z wagi łazienkowej. Opis całości i drugiej połowy projektu jest
w [README w korzeniu](../README.md).

Kotlin, Jetpack Compose (Material3), SQLite bez ORM. `minSdk` 29, `compileSdk` 35.

## Budowanie i testy

W repozytorium **nie ma wrappera Gradle'a**. Zamiast `gradlew` jest
[`tools/gr.sh`](tools/gr.sh), który ustawia JDK 21 i woła dystrybucję Gradle 8.9
z cache Android Studio:

```sh
tools/gr.sh :app:testDebugUnitTest        # wszystkie testy jednostkowe
tools/gr.sh :app:assembleDebug            # APK
tools/gr.sh --stop                        # zatrzymuje demona
```

Pojedyncza klasa albo pojedynczy test:

```sh
tools/gr.sh :app:testDebugUnitTest --tests "pl.gymbro.gym.ProgressionAdvisorTest"
tools/gr.sh :app:testDebugUnitTest --tests "*duze przekroczenie zakresu*"
```

Android Studio radzi sobie samo: **Open** → wskaż folder `GymBro`, a przy
zgłoszeniu braku wrappera zgódź się na jego wygenerowanie.

Testy chodzą na JVM, bez telefonu i emulatora - cała logika progresji, doboru
talerzy i analiz jest od Androida niezależna i tak jest celowo.

### Płynność mierz na wariancie release

```sh
tools/gr.sh :app:assembleRelease
```

Wariant release jest podpisany kluczem debugowym, żeby dało się go uruchomić
lokalnie bez własnego keystore'a. To nie jest jeszcze wersja do publikacji -
służy do pomiarów. Compose w buildzie debug działa kilkukrotnie wolniej, bo
kompilator pracuje w trybie diagnostycznym, a ART nie optymalizuje kodu
oznaczonego jako debuggable; wrażenia z debuga nic nie mówią o rzeczywistości.
Po minifikacji wydanie waży niecałe 5 MB, debug prawie 70.

[`tools/bench.sh`](tools/bench.sh) mierzy przełączanie zakładek przez
`dumpsys gfxinfo` na podłączonym telefonie. Ma zaszyte pięć pozycji zakładek,
tyle, ile ma aplikacja.

### Diagnostyka jest tylko w wariancie debug

Surowy zrzut z Health Connect (`ui/Diagnostics.kt`), log skanera wagi
w logcacie i adres pilota w Profilu zależą od `BuildConfig.DEBUG`. Ekranu
podsłuchu BLE w zakładce Waga już nie ma - był do testów.
W wydaniu ich nie ma, a R8 wycina cały kod, do którego tylko one sięgają - do
rozgryzania wagi instaluj debug.

## Układ pakietów

```
app/src/main/java/pl/gymbro/
├── MainActivity.kt    pięć zakładek: Trening, Kalorie, Waga, Plan, Profil
├── data/              SQLite bez ORM: schemat, migracje, repozytoria, modele,
│                      zestaw i paczki produktów
├── gym/               logika treningu: progresja, talerze, analizy, stan sesji
├── nutrition/         cele, wydatek z bilansu energii, porcje, własne dania
├── net/               serwer na pętli zwrotnej dla zegarka, usługa go trzymająca,
│                      zapytania do Open Food Facts
├── health/            Health Connect: import treningów i metryk dziennych
├── ble/               waga łazienkowa: skan, sterowniki rodzin wag, ustalanie wyniku
└── ui/                ekrany Compose i wspólne elementy
```

Testy jednostkowe leżą równolegle w `app/src/test/java/pl/gymbro/`.

## Baza

`data/Schema.kt`, `SQLiteOpenHelper`, obecnie wersja 24, dwadzieścia jeden
tabel. Świadomie bez Room: Room wymaga procesora adnotacji KSP, czyli kolejnej
wtyczki Gradle z własną macierzą zgodności wersji.

Każda zmiana schematu podbija `VERSION`, dopisuje listę `Vn_STATEMENTS`
i gałąź w `onUpgrade`. **Tabel z danymi użytkownika nie kasujemy przy
aktualizacji** - plik `gymbro.db` na telefonie to jedyna kopia historii
treningów, nie ma eksportu.

`onCreate` i `onUpgrade` to dwie osobne drogi do tej samej bazy.
[`tools/schemat.sh`](tools/schemat.sh) sprawdza, czy kończą się w tym samym
miejscu: składa świeżą bazę z SQL-a wyciągniętego z `Schema.kt` i porównuje
ją z kopią ściągniętą z telefonu - kolumny, typy, klucze i indeksy.

```sh
tools/schemat.sh kopie/gymbro-2026-11-15-przed-etapem-2.db
```

Ciężary przepuszczaj przez `data/Kg.kt`. Arytmetyka zmiennoprzecinkowa
wpisywała do bazy wartości w rodzaju `81.39999999999999`.

## Progresja

Metoda to podwójna progresja: przy stałym ciężarze dokładasz powtórzeń, aż
dojdziesz do góry zakresu we wszystkich seriach roboczych, i dopiero wtedy
ciężar idzie w górę.

| Plik | Rola |
|---|---|
| `gym/ProgressionEngine.kt` | ciężar na dzisiejszą sesję |
| `gym/ProgressionAdvisor.kt` | propozycje zmian w planie po treningu, czysta funkcja |
| `gym/ProgressionReview.kt` | ładowanie z bazy i zapis propozycji |
| `gym/PlateCalculator.kt` | co da się złożyć z posiadanych talerzy |

Doradca nie dotyka bazy, dzięki czemu reguły testuje się na wymyślonych danych.
Skąd się biorą liczby - źródła z literatury i wyraźnie oznaczone miejsca, gdzie
liczba jest konwencją, a nie wynikiem badania - opisuje
[`docs/progresja.md`](docs/progresja.md). Zmieniając regułę, zmień i ten dokument.

## Waga łazienkowa po BLE

Nie ma protokołu wspólnego dla wszystkich wag, więc jest lista sterowników
dobieranych po tym, czym waga się przedstawia: ID producenta, UUID serwisu albo
charakterystyka, nigdy sam kształt bajtów.

| Plik | Rola |
|---|---|
| `ble/ScaleScanner.kt` | skan, połączenie GATT, log diagnostyczny - jedyny plik z Androidem |
| `ble/ScaleDecoders.kt` | jedno miejsce, które zna wszystkie rodziny wag |
| `ble/OkOkParser.kt`, `YolandaParser.kt`, `MiScaleParser.kt`, `StandardScaleParser.kt` | po jednym protokole |
| `ble/WeightSettler.kt` | kiedy ciąg odczytów jest już wynikiem |

Sprawdzona na sprzęcie jest tylko domowa waga (OKOK, układ Chipsea, samo
rozgłoszenie - bez łączenia i parowania). Xiaomi, standard Bluetooth SIG
i Qingniu przez GATT są napisane z otwartych opisów protokołów.

Dla nieznanego egzemplarza `ble/ScaleProtocol.kt` ma `findWeightCandidates()`:
brute force, który czyta każdą pozycję w ramce jako liczbę 16-bitową i zgłasza
te wyglądające jak masa ciała. To narzędzie do rozgryzania protokołu, nie
dekoder - jego wynik trafia wyłącznie do logu diagnostycznego, czyli tylko
w wariancie debug. `tools/sztuczna-waga.ps1` nadaje z komputera ramki takie
jak domowa waga i pozwala przejść kreator pomiaru bez stawania na wadze.

## Health Connect

`health/HealthReader.kt` czyta dane, które zegarek wystawia przez aplikację
Zepp. Przy każdej pozycji raportujemy **pakiet źródłowy**, bo inaczej nie
odróżnisz tego, co przyszło z Zepp, od tego, co wstawił Google Fit albo ręczny
wpis.

Żeby cokolwiek przyszło: w aplikacji Zepp **Profil → Konta zewnętrzne →
Health Connect** → zezwól, potem zsynchronizuj zegarek.

`gym/WatchMatch.kt` łączy trening zapisany w aplikacji z tym, co w tym samym
czasie nagrał zegarek. Dopasowanie idzie po czasie, bo nic innego nie ma:
zegarek nie wie o planie, a aplikacja nie dostaje identyfikatora sesji.

## Sieć

Jedyne gniazdo nasłuchujące w tej aplikacji stoi na **pętli zwrotnej telefonu**
(`127.0.0.1:48627`) i obsługuje wyłącznie zegarek - szczegóły w
[README w korzeniu](../README.md).

Do internetu wychodzi jedna rzecz: zapytanie o produkt po zeskanowaniu kodu
kreskowego, do `world.openfoodfacts.org` (`net/OpenFoodFacts.kt`). Leci w nim
sam kod kreskowy. Skaner kodów to moduł Google Play Services, dociągany przez
system przy pierwszym użyciu. Trening, pomiary i dziennik jedzenia nie
opuszczają telefonu.
