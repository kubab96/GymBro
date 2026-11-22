// Rysuje ikony ekranu czasu treningu do assets/ (PNG RGBA, z wygładzaniem).
//
// Ikony są obrazkami, bo zegarek nie obraca prostokątów: płomień złożony
// z koła i kwadratu obróconego o 45 stopni wyszedł na tarczy jako kłódka.
// Uruchom po zmianie kształtu: node tools/ikony.js
const fs = require('fs')
const path = require('path')
const zlib = require('zlib')

const S = 56 // bok ikony w pikselach
const SS = 4 // próbek na piksel w każdą stronę - wygładzone krawędzie

const rgb = (c) => [(c >> 16) & 255, (c >> 8) & 255, c & 255]

// Każda ikona to lista warstw [kolor, czy punkt (u, v) w środku], u i v od 0 do 1.
const kolo = (cx, cy, r) => (u, v) => (u - cx) ** 2 + (v - cy) ** 2 <= r * r
const pierscien = (cx, cy, r1, r2) => (u, v) => {
  const d = (u - cx) ** 2 + (v - cy) ** 2
  return d <= r2 * r2 && d >= r1 * r1
}
const pigulka = (x1, x2, y, h) => (u, v) => {
  const r = h / 2
  if (v < y - r || v > y + r) return false
  if (u >= x1 && u <= x2) return true
  return (u - x1) ** 2 + (v - y) ** 2 <= r * r || (u - x2) ** 2 + (v - y) ** 2 <= r * r
}
// Płomień: kropla z koła u dołu i zwężającego się, lekko wygiętego czubka.
const plomien = (cx, dol, r, wys) => (u, v) => {
  const sk = dol - r // środek koła
  if ((u - cx) ** 2 + (v - sk) ** 2 <= r * r) return true
  const gora = sk - wys
  if (v < gora || v > sk) return false
  const t = (v - gora) / wys // 0 na czubku, 1 przy kole
  const szer = r * Math.pow(t, 0.75)
  const wygiecie = 0.05 * (1 - t) * (1 - t)
  return Math.abs(u - cx - wygiecie) <= szer
}
const tlo = kolo(0.5, 0.5, 0.5)

const ikony = {
  'stat-kcal.png': [
    [0x3a2a12, tlo],
    [0xffb357, plomien(0.5, 0.8, 0.2, 0.42)],
    [0xffe2b8, plomien(0.5, 0.8, 0.09, 0.2)]
  ],
  'stat-serie.png': [
    [0x11385e, tlo],
    [0x6aa9ff, pigulka(0.32, 0.68, 0.36, 0.08)],
    [0x6aa9ff, pigulka(0.32, 0.68, 0.5, 0.08)],
    [0x6aa9ff, pigulka(0.32, 0.68, 0.64, 0.08)]
  ],
  'stat-ton.png': [
    [0x0b4a42, tlo],
    [0x37d6c0, pierscien(0.5, 0.5, 0.08, 0.3)],
    [0x0b4a42, pierscien(0.5, 0.5, 0.19, 0.215)]
  ]
}

function rysuj(warstwy) {
  const px = Buffer.alloc(S * S * 4)
  for (let y = 0; y < S; y++) {
    for (let x = 0; x < S; x++) {
      let r = 0, g = 0, b = 0, a = 0
      for (let sy = 0; sy < SS; sy++) {
        for (let sx = 0; sx < SS; sx++) {
          const u = (x + (sx + 0.5) / SS) / S
          const v = (y + (sy + 0.5) / SS) / S
          let kolor = null
          for (const [c, w] of warstwy) if (w(u, v)) kolor = c
          if (kolor === null) continue
          const [cr, cg, cb] = rgb(kolor)
          r += cr; g += cg; b += cb; a += 1
        }
      }
      const i = (y * S + x) * 4
      if (a) {
        px[i] = Math.round(r / a); px[i + 1] = Math.round(g / a); px[i + 2] = Math.round(b / a)
      }
      px[i + 3] = Math.round((a / (SS * SS)) * 255)
    }
  }
  return px
}

const crcT = []
for (let n = 0; n < 256; n++) {
  let c = n
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
  crcT[n] = c >>> 0
}
const crc = (b) => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0 }
const kawalek = (typ, dane) => {
  const d = Buffer.concat([Buffer.from(typ), dane])
  const len = Buffer.alloc(4); len.writeUInt32BE(dane.length)
  const c = Buffer.alloc(4); c.writeUInt32BE(crc(d))
  return Buffer.concat([len, d, c])
}
function png(rgba) {
  const surowe = Buffer.alloc((S * 4 + 1) * S)
  for (let y = 0; y < S; y++) {
    surowe[y * (S * 4 + 1)] = 0
    rgba.copy(surowe, y * (S * 4 + 1) + 1, y * S * 4, (y + 1) * S * 4)
  }
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(S, 0); ihdr.writeUInt32BE(S, 4); ihdr[8] = 8; ihdr[9] = 6
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    kawalek('IHDR', ihdr), kawalek('IDAT', zlib.deflateSync(surowe)), kawalek('IEND', Buffer.alloc(0))
  ])
}

const korzen = path.join(__dirname, '..', 'assets')
for (const [nazwa, warstwy] of Object.entries(ikony)) {
  const plik = png(rysuj(warstwy))
  for (const plat of ['common.r', 'common.s']) fs.writeFileSync(path.join(korzen, plat, nazwa), plik)
  console.log(nazwa)
}
