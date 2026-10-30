# GymBro

Dziennik treningu siłowego na Androida, sterowany z zegarka Amazfit Balance.

Trening prowadzi się **klikając tylko na zegarku**: seria, przerwa, zapas
powtórzeń, zmiana ciężaru. Telefon może zostać w szatni. Po treningu aplikacja
proponuje progresję - ciężar, powtórzenia albo serię - liczoną z tego, co
faktycznie poszło, i z tego, co da się złożyć z posiadanych talerzy.

## Co jest w tym repozytorium

| Katalog | Co to | Czym budować |
|---|---|---|
| [`GymBro/`](GymBro) | aplikacja na Androida: Kotlin, Jetpack Compose, SQLite bez ORM | Android Studio albo `gradlew :app:assembleDebug` |
| [`gymbro-watch/`](gymbro-watch) | mini-aplikacja na Zepp OS 3.0 dla Amazfit Balance | `zeus build` / `zeus preview` |

Obie strony rozmawiają po lokalnym HTTP przez Side Service Zepp OS. Protokół
jest jeden i wspólny, więc zmiana w `GymBro/app/src/main/java/pl/gymbro/net/WatchApi.kt`
prawie zawsze pociąga zmianę w `gymbro-watch/page/index.js` - dlatego oba
projekty leżą w jednym repozytorium.

## Testy

```
cd GymBro && gradlew :app:testDebugUnitTest
cd gymbro-watch && node tools/check.js
```

Ten drugi ładuje prawdziwy ekran zegarka z atrapami `@zos/*` i atrapą telefonu,
więc sprawdza zachowanie, nie tylko składnię.

## Dokumentacja

[`GymBro/docs/progresja.md`](GymBro/docs/progresja.md) - skąd się biorą
propozycje progresji: źródła z literatury, próg 3%, reguła dwóch ruchów przy
doborze talerzy, i wyraźnie oznaczone miejsca, gdzie liczba jest moją
konwencją, a nie wynikiem badania.

`GymBro/README.md` opisuje jeszcze Fazę 0 i jest nieaktualny.
