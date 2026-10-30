# GymBro — Faza 0 (spike ryzyka)

To nie jest aplikacja. To **dwa testy**, które mają odpowiedzieć na pytania, od których
zależy cały projekt — zanim zainwestujemy w niego tygodnie pracy.

| Test | Pytanie | Gdzie |
|---|---|---|
| 1 | Czy da się czytać wagę po BLE bez aplikacji producenta? | zakładka **Waga (BLE)** |
| 2 | Co dokładnie Amazfit Balance wystawia przez Health Connect? | zakładka **Zegarek (HC)** |

Jeśli oba wyjdą na tak — reszta planu to już zwykłe programowanie.
Jeśli któryś wyjdzie na nie — zmieniamy podejście, a nie przepisujemy gotową aplikację.

---

## Czego potrzebujesz

Na tym komputerze nie ma w tej chwili JDK ani Android SDK. Wystarczy jedna instalacja:

**[Android Studio](https://developer.android.com/studio)** — zawiera JDK 17, Android SDK i emulator.
Przy pierwszym uruchomieniu zgódź się na pobranie SDK (ok. 1,5 GB łącznie).

Do tego **fizyczny telefon z Androidem**. Emulator nie ma radia Bluetooth ani Twoich danych
z Zepp, więc Faza 0 musi działać na prawdziwym urządzeniu.

## Uruchomienie

1. Android Studio → **Open** → wskaż folder `GymBro`.
2. Poczekaj na Gradle sync. Studio samo pobierze Gradle wskazany w `gradle/wrapper/gradle-wrapper.properties`.
   Gdyby zgłosił brak wrappera — zgódź się na jego wygenerowanie.
3. Gdyby zgłosił nieaktualne wersje bibliotek: przyjmij podpowiedzi Studia.
   Wersje w `gradle/libs.versions.toml` mogą już być starsze, niż to, co jest dostępne.
4. W telefonie: **Ustawienia → Informacje → Numer kompilacji** (7×) → wróć → **Opcje programisty → Debugowanie USB**.
5. Podłącz telefon kablem, wybierz go w pasku Studia, naciśnij **Run ▶**.

---

## Test 1 — waga

**Rozpoznana: OKOK International = układ Chipsea.** To dobra wiadomość, bo te wagi
nadają pomiar w **rozgłoszeniu BLE** (manufacturer data), a nie przez połączenie GATT.
Konsekwencje:

- nie trzeba się z wagą łączyć ani jej parować,
- nie walczymy z aplikacją OKOK o wyłączny dostęp — obie mogą słuchać naraz,
- odczyt jest natychmiastowy i tani bateryjnie.

Parser jest już napisany: [`OkOkParser.kt`](app/src/main/java/pl/gymbro/ble/OkOkParser.kt).

1. Zakładka **Waga** → przyznaj uprawnienia Bluetooth.
2. **Skanuj**.
3. **Stań na wadze** i poczekaj na ustabilizowanie. Masa powinna pojawić się
   dużą czcionką na górze ekranu, razem z impedancją.
4. Powtórz 2–3 razy, **znając swoją masę**.
5. **Kopiuj log** i wklej mi go.

Gdyby duża liczba się nie pojawiła, log i tak zawiera surowe bajty plus brute force
z `ScaleProtocol`, który pokaże, na którym offsecie faktycznie siedzi masa.
Przycisk połączenia zostaje na wypadek, gdyby ten egzemplarz wymagał jednak GATT.

### Dlaczego to zadziała nawet dla nieznanej wagi

W `ScaleProtocol.kt` jest `findWeightCandidates()` — brute force, który przegląda każdą
pozycję w ramce jako liczbę 16-bitową (little i big endian, dzielone przez 10 i 100)
i zgłasza te, które wyglądają jak masa ciała. Przy kilku pomiarach o znanej masie
od razu widać, który offset jest tym właściwym. To samo robi `findImpedanceCandidates()`
dla bioimpedancji (realny zakres 150–1500 Ω).

Dlatego nie musimy z góry wiedzieć, jaką masz wagę.

---

## Test 2 — zegarek

1. W aplikacji **Zepp**: Profil → Konta zewnętrzne → **Health Connect** → zezwól na wszystko.
2. Zsynchronizuj zegarek z Zepp (przeciągnij w dół na ekranie głównym).
3. W naszej aplikacji: zakładka **Zegarek (HC)** → **Przyznaj dostęp** → **Wczytaj dane**.
4. **Kopiuj** i wklej mi wynik.

### Na co patrzeć

Przy każdej pozycji wypisujemy **pakiet źródłowy**. To jest sedno testu: pokazuje,
co naprawdę przyszło z Zepp, a co z Google Fit albo z ręcznego wpisu.

Szukamy odpowiedzi na:
- Czy treningi siłowe w ogóle się pojawiają i z jaką szczegółowością?
- Czy jest **HRV (RMSSD)** i tętno spoczynkowe? To paliwo dla modułu gotowości.
- Czy sen ma rozbite fazy, czy tylko czas łączny?
- Czy biegi mają dystans, czy trzeba je dociągać ze Stravy?

---

## Co z tego wyjdzie

| Wynik | Co robimy dalej |
|---|---|
| Waga gada, HC ma komplet | Faza 1 pełną parą, integracje zamknięte w tydzień |
| Waga milczy | Plan B: Fitdays → Health Connect → my. Działa, ale dochodzi chmura producenta |
| HC ubogie w dane biegowe | Dokładamy Stravę (OAuth) jako drugie źródło — i tak było w planie |
| HC bez HRV | Moduł gotowości jedzie na tętnie spoczynkowym i śnie, trochę słabszy |

---

## Struktura

```
app/src/main/java/pl/gymbro/
├── MainActivity.kt           ekran z dwiema zakładkami
├── ble/
│   ├── ScaleScanner.kt       skan BLE, połączenie GATT, nasłuch wszystkich notify
│   └── ScaleProtocol.kt      dekodery znanych wag + brute force dla nieznanych
└── health/
    └── HealthReader.kt       odczyt Health Connect + raport źródeł danych
```

Nic tu nie trafia do sieci. Cały log zostaje na telefonie, dopóki sam go nie skopiujesz.
