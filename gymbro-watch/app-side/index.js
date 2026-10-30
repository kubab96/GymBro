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

  // Zly token znaczy, ze telefon slucha, ale nas nie zna - zwykle po
  // przeinstalowaniu aplikacji, bo token rodzi sie na nowo. To zupelnie co
  // innego niz brak polaczenia i wymaga czego innego, wiec mowimy to wprost.
  if (response && response.status === 401) {
    return { error: 'Zly token - sprawdz go w Profilu' }
  }

  // Odpowiedź bywa już obiektem, a bywa tekstem - zależnie od wersji systemu.
  const body = response && response.body
  if (typeof body === 'string') {
    try {
      return JSON.parse(body)
    } catch (e) {
      return { error: 'zla odpowiedz' }
    }
  }
  return body || {}
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
      case 'RIR':
        data = await call('/rir', 'POST', params)
        break
      case 'DELETE':
        data = await call('/delete', 'POST', params)
        break
      case 'UNDO':
        data = await call('/undo', 'POST', request.params)
        break
      case 'EDIT':
        data = await call('/edit', 'POST', request.params)
        break
      case 'WEIGHT':
        data = await call('/weight', 'POST', request.params)
        break
      case 'UNITS':
        data = await call('/units', 'GET')
        break
      case 'START':
        data = await call('/start', 'POST', request.params)
        break
      case 'PAUSE':
        data = await call('/pause', 'POST', {})
        break
      case 'RESUME':
        data = await call('/resume', 'POST', {})
        break
      case 'FINISH':
        data = await call('/finish', 'POST', {})
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
    ctx.response({ data: { error: 'Otwórz GymBro na telefonie' } })
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
