import { MessageBuilder } from '../shared/message-side'
import { PHONE_URL, TOKEN } from '../utils/config/link'

const messageBuilder = new MessageBuilder()

/**
 * Usługa towarzysząca: jedyny most między zegarkiem a telefonem.
 *
 * Mini-aplikacja Zepp OS nie umie sięgnąć do innej aplikacji na telefonie.
 * Umie za to oddać robotę tutaj, a ten kod - działający już na telefonie,
 * wewnątrz aplikacji Zepp - potrafi wykonać zwykłe żądanie HTTP. Strzelamy
 * więc pod pętlę zwrotną telefonu, gdzie nasłuchuje GymBro.
 *
 * Zegarek niczego nie liczy. Przesyła tylko intencję („zapisz serię", „ciężar
 * w górę") i rysuje to, co wróci. Ciężary, talerze i progresję liczy telefon,
 * więc zegarek nie może się z nim rozjechać.
 */
const call = async (path, method, payload) => {
  const options = {
    url: PHONE_URL + path,
    method: method,
    headers: {
      'Content-Type': 'application/json',
      'X-GymBro-Token': TOKEN
    }
  }
  if (payload) {
    options.body = JSON.stringify(payload)
  }

  const response = await fetch(options)
  const status = (response && response.status) || 0

  // Bez odpowiedzi albo bez kodu nie wiadomo, czy cokolwiek sie wykonalo.
  // Puste cialo szlo dalej jako sukces i zegarek skreslal polecenie z kolejki.
  if (!status) {
    return { error: 'Otwórz GymBro na telefonie', retry: true }
  }

  // Zly token znaczy, ze telefon slucha, ale nas nie zna - zwykle po
  // przeinstalowaniu aplikacji, bo token rodzi sie na nowo. To zupelnie co
  // innego niz brak polaczenia i wymaga czego innego, wiec mowimy to wprost.
  if (status === 401) {
    return { error: 'Zły token - sprawdź go w Profilu GymBro', retry: true }
  }

  // Odpowiedź bywa już obiektem, a bywa tekstem - zależnie od wersji systemu.
  let body = response && response.body
  if (typeof body === 'string') {
    try {
      body = JSON.parse(body)
    } catch (e) {
      return { error: 'zla odpowiedz', retry: true }
    }
  }
  body = body || {}
  // Dwa rodzaje bledu i zegarek musi je odroznic. Odmowa telefonu (4xx)
  // dotyczy tego jednego polecenia - nie ma po co go ponawiac. Wszystko
  // inne to awaria po drodze i polecenie ma czekac w kolejce. Wczesniej
  // obie wygladaly tak samo, wiec zegarek wyrzucal z kolejki serie za
  // kazdym razem, gdy GymBro na telefonie akurat nie sluchal.
  if (status >= 500) return Object.assign({}, body, { error: body.error || 'blad telefonu', retry: true })
  if (status >= 400 && body.error) body.reject = true
  return body
}

const handle = async (ctx, request) => {
  try {
    let data
    switch (request.method) {
      case 'STATE':
        data = await call('/state', 'GET')
        break
      case 'SET':
        data = await call('/set', 'POST', request.params)
        break
      // `request.params`, nie `params`. Goła nazwa nie istniała w tym
      // zakresie, a moduł chodzi w trybie ścisłym, więc odwołanie do niej
      // rzucało wyjątek - prosto w `catch` niżej. Zegarek dostawał za to
      // „Otwórz GymBro na telefonie" i kasował polecenie z kolejki, więc
      // podany zapas znikał bez śladu, a seria wracała z „nie podano".
      // Tą samą drogą ginęło kasowanie serii z ekranu poprawki.
      case 'RIR':
        data = await call('/rir', 'POST', request.params)
        break
      case 'DELETE':
        data = await call('/delete', 'POST', request.params)
        break
      case 'UNDO':
        data = await call('/undo', 'POST', request.params)
        break
      case 'EDIT':
        data = await call('/edit', 'POST', request.params)
        break
      // Skrot listy, ktora zegarek juz ma - przy zgodnosci telefon odpowiada
      // samym "bez zmian" zamiast kilkunastu kilobajtow. GET nie niesie ciala,
      // wiec skrot jedzie w adresie.
      case 'UNITS': {
        const v = request.params && request.params.v
        data = await call('/units' + (v ? '?v=' + encodeURIComponent(v) : ''), 'GET')
        break
      }
      case 'START':
        data = await call('/start', 'POST', request.params)
        break
      // Te trzy szly dotad z pustym cialem. Telefon nie dostawal wiec ani
      // znacznika polecenia, ani sesji, ani godziny zakonczenia - trening
      // zamkniety bez lacznosci konczyl sie o godzinie synchronizacji,
      // a ponowione zakonczenie moglo zamknac cudzy trening.
      case 'PAUSE':
        data = await call('/pause', 'POST', request.params)
        break
      case 'RESUME':
        data = await call('/resume', 'POST', request.params)
        break
      case 'FINISH':
        data = await call('/finish', 'POST', request.params)
        break
      case 'BATCH':
        data = await call('/batch', 'POST', request.params)
        break
      default:
        data = { error: 'nieznane zadanie' }
    }
    ctx.response({ data: data })
  } catch (error) {
    // Najczęstszy powód: nikt nie nasłuchuje pod pętlą zwrotną, bo usługa
    // pilota nie stoi - po restarcie telefonu albo gdy system ubił proces.
    // Zegarek musi dostać odpowiedź, inaczej wisi na obietnicy, a sam napis
    // "brak połączenia" niczego nie podpowiada, więc mówimy co zrobić.
    // Z flaga ponowienia: polecenie zostaje w kolejce zegarka.
    ctx.response({ data: { error: 'Otwórz GymBro na telefonie', retry: true } })
  }
}

AppSideService({
  onInit() {
    messageBuilder.listen(() => {})
    messageBuilder.on('request', (ctx) => {
      const request = messageBuilder.buf2Json(ctx.request.payload)
      return handle(ctx, request)
    })
  },
  onRun() {},
  onDestroy() {}
})
