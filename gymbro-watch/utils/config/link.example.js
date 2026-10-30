// Wzór pliku `link.js`, którego nie ma w repozytorium.
//
// Skopiuj go obok pod nazwą `link.js` i wklej swój token. Bez tego paczka
// zegarka zbuduje się, ale żadne żądanie nie dojdzie do telefonu - usługa
// na 127.0.0.1:48627 odrzuca wszystko bez pasującego nagłówka.
//
//     cp utils/config/link.example.js utils/config/link.js
//
// Token pokazuje GymBro na telefonie: Profil > Pilot na zegarku. Jest
// właściwie hasłem do tej usługi, dlatego `link.js` jest w .gitignore -
// jedna para zegarek-telefon ma jeden token i nie wychodzi on z dysku.
//
// Adres jest pętlą zwrotną TELEFONU, nie zegarka: żądanie wykonuje usługa
// towarzysząca działająca w aplikacji Zepp, a ona siedzi na telefonie. Dzięki
// temu nic nie wychodzi do internetu i działa to na siłowni bez zasięgu.
export const PHONE_URL = 'http://127.0.0.1:48627'
export const TOKEN = 'tutaj-token-z-Profil-Pilot-na-zegarku'
