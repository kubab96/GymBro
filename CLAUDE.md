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
node tools/check.js          # 344 sprawdzenia, bez zegarka
zeus build
zeus preview -t "Amazfit Balance"
```

`zeus preview` bez `-t` pyta interaktywnie i zawiesza się w nieinteraktywnej powłoce. Kończy się kodem QR do zeskanowania w aplikacji Zepp - to jedyna droga instalacji, nie ma odpowiednika `adb install`.

W PowerShellu wołaj `zeus.cmd`, nie `zeus`.

`tools/check.js` ładuje prawdziwy `page/index.js` z zaślepkami pod `@zos/*` i pod telefon, po czym przechodzi scenariusze tak, jak robi to palec. **Sprawdza zachowanie, nie składnię** - przepływ ekranów, licznik serii, kolejkę offline, liczbę zapisów do widgetów. Nie powie, że napis się obcina.

### Telefon przez adb

```sh
adb install -r GymBro/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n pl.gymbro/.MainActivity
```

W Git Bashu ścieżki androidowe wymagają `MSYS_NO_PATHCONV=1`, inaczej bash przerobi `/data/local/tmp` na ścieżkę windowsową.

Serwer adb też dziedziczy katalog roboczy i trzyma folder - `adb kill-server`, gdy przeszkadza.

`GymBro/tools/bench.sh` mierzy płynność przez `dumpsys gfxinfo`. Uwaga: ma zaszyte pięć pozycji zakładek, a aplikacja ma ich cztery - wymaga poprawki przed użyciem. Płynność oceniaj na wariancie release; debug Compose jest kilkukrotnie wolniejszy i pomiar z niego nic nie znaczy.

## Architektura

### Łańcuch łączności

```
zegarek  --Bluetooth-->  Side Service (wewnątrz aplikacji Zepp na telefonie)
         --HTTP 127.0.0.1:48627-->  WatchLinkService  -->  WatchApi  -->  baza
```

Mini-aplikacja Zepp OS nie umie sięgnąć do innej aplikacji na telefonie. Umie tylko oddać robotę swojej usłudze towarzyszącej (`gymbro-watch/app-side/index.js`), która działa już na telefonie i potrafi zrobić zwykłe `fetch`. Adres nigdy nie opuszcza telefonu, więc działa to bez zasięgu.

`LocalServer` wiąże gniazdo jawnie z pętlą zwrotną, nie z `0.0.0.0`, i wymaga nagłówka `X-GymBro-Token`. `WatchLinkService` jest usługą pierwszoplanową ze `START_STICKY` i `BootReceiver`, bo telefon leży w kieszeni z wygaszonym ekranem.

**GymBro nie musi być otwarty** - usługa działa niezależnie od aktywności.

### Podział pracy: zegarek nic nie liczy

Zegarek wysyła intencję („zapisz serię", „ciężar w górę") i rysuje to, co wróci. Ciężary, talerze i progresję liczy telefon, w jednym miejscu (`gym/LiveSession.kt`), nieświadomym tego, kto pyta. Dwa miejsca liczące propozycję ciężaru rozjechałyby się przy pierwszej zmianie reguł.

Wyjątkiem jest **reakcja ekranu**: licznik podbija się lokalnie od razu, żądanie leci w tle. Niepotwierdzone serie liczone są osobno, inaczej odpowiedź na starsze żądanie cofałaby licznik.

### Trening bez łączności

Zegarek działa, gdy telefon jest wyłączony albo poza zasięgiem:

- **kolejka wyjściowa** w `@zos/storage` z ponawianiem od 5 s z podwajaniem do 2 min
- każde polecenie ma `cid`; telefon trzyma LRU ostatnich 200 w pamięci
- sesja założona na zegarku ma `local_id` z unikalnym indeksem częściowym w bazie - to **trwała** ochrona przed duplikatem, gdy telefon odpowie po restarcie
- po 8 godzinach (`STALE_AFTER_MS`, ta sama granica po obu stronach) zaległe polecenia są nieaktualne i lecą do kosza, bo telefon i tak zamknął sesję na ostatniej serii

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

### Baza

`data/Schema.kt`, `SQLiteOpenHelper`, schemat w wersji 15. Bez Room celowo: Room wymaga KSP, czyli kolejnej wtyczki Gradle z własną macierzą zgodności.

Każda zmiana schematu podbija `VERSION`, dopisuje `Vn_STATEMENTS` i gałąź w `onUpgrade`. **Nigdy nie kasujemy tabel z danymi użytkownika.** Trzynaście tabel; `session` / `set_entry` / `plan_item` to rdzeń.

Zapisując ciężary, przepuszczaj je przez `data/Kg.kt` (`roundKg`). Arytmetyka zmiennoprzecinkowa wpisywała do bazy wartości w rodzaju `81.39999999999999`.

### Zakładki

Cztery: Trening, Waga, Plan, Profil (`MainActivity.kt`, `ui/TabHost.kt`).

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

**Token pilota.** `gymbro-watch/utils/config/link.js` jest w `.gitignore` - paruje konkretny zegarek z konkretnym telefonem. Wzór: `link.example.js`, wartość: GymBro → Profil → Pilot na zegarku. Bez tego pliku paczka się zbuduje, ale telefon odrzuci każde żądanie z 401.

**Baza to jedyna kopia historii treningów.** Nie ma eksportu. `GymBro/kopie/` trzyma ręczne kopie ściągnięte z telefonu i jest poza repozytorium. Przed czymkolwiek, co dotyka danych, zrób kopię.

**Mierz, nie dedukuj.** Lagi przycisków na zegarku dwa razy „naprawiono" bez pomiaru i dwa razy wróciły. Przyczyny okazały się trzy naraz: animacja nazwy chodząca bez przerwy, operacja na pamięci w ścieżce kliknięcia i 134 zapisy do widgetów przy przejściu, z których 110 nic nie zmieniało. `tools/check.js` liczy zapisy i zapisy do pamięci - używaj tego. Po naprawie cofnij poprawkę i sprawdź, czy test faktycznie pada.

**Zmieniając zachowanie, przepisz test, nie osłabiaj go.** Testy wielokrotnie padały dlatego, że zachowanie zmieniło się celowo. Nowy test ma kodować nową regułę, nie omijać ją.
