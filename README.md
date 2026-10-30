# GymBro

Dziennik treningu siłowego na Androida, sterowany z zegarka Amazfit Balance.

Trening prowadzi się **klikając tylko na zegarku**: seria, przerwa, zapas
powtórzeń, zmiana ciężaru. Telefon może zostać w szatni. Po treningu aplikacja
proponuje progresję - ciężar, powtórzenia albo serię - liczoną z tego, co
faktycznie poszło, i z tego, co da się złożyć z posiadanych talerzy.

## Co jest w tym repozytorium

| Katalog | Co to | Czym budować |
|---|---|---|
| [`GymBro/`](GymBro) | aplikacja na Androida: Kotlin, Jetpack Compose, SQLite bez ORM | Android Studio albo `GymBro/tools/gr.sh :app:assembleDebug` |
| [`gymbro-watch/`](gymbro-watch) | mini-aplikacja na Zepp OS 3.0 dla Amazfit Balance | `zeus build` / `zeus preview` |

Obie strony rozmawiają po lokalnym HTTP przez Side Service Zepp OS. Protokół
jest jeden i wspólny, więc zmiana w `GymBro/app/src/main/java/pl/gymbro/net/WatchApi.kt`
prawie zawsze pociąga zmianę w `gymbro-watch/page/index.js` - dlatego oba
projekty leżą w jednym repozytorium.

## Testy

```
GymBro/tools/gr.sh :app:testDebugUnitTest
cd gymbro-watch && node tools/check.js
```

Ten drugi ładuje prawdziwy ekran zegarka z atrapami `@zos/*` i atrapą telefonu,
więc sprawdza zachowanie, nie tylko składnię.

W repozytorium nie ma wrappera Gradle'a - `GymBro/tools/gr.sh` podstawia za
niego JDK 21 i dystrybucję Gradle 8.9 z cache Android Studio.

## Dokumentacja

[`GymBro/docs/progresja.md`](GymBro/docs/progresja.md) - skąd się biorą
propozycje progresji: źródła z literatury, próg 3%, reguła dwóch ruchów przy
doborze talerzy, i wyraźnie oznaczone miejsca, gdzie liczba jest moją
konwencją, a nie wynikiem badania.

[`GymBro/README.md`](GymBro/README.md) - aplikacja na Androida z bliska:
budowanie, układ pakietów, baza i migracje, waga po BLE, Health Connect.

[`CLAUDE.md`](CLAUDE.md) - to samo pod kątem pracy nad kodem: polecenia wraz
z pułapkami, architektura obu stron, konwencje i rzeczy, które łatwo zepsuć.
