// Zamienia kod QR z terminala (pol-bloki) na PNG. Znak bloku = jasny modul.
const fs = require('fs')
const zlib = require('zlib')
const [, , wej, wyj] = process.argv
const linie = fs.readFileSync(wej, 'utf8').split(/\r?\n/).filter((l) => /^[█▀▄ ]+$/.test(l) && /[█▀▄]/.test(l))
const szer = Math.max(...linie.map((l) => [...l].length))
const rzedy = []
for (const l of linie) {
  const z = [...l]
  const gora = [], dol = []
  for (let i = 0; i < szer; i++) {
    const c = z[i] || ' '
    gora.push(c === '█' || c === '▀')
    dol.push(c === '█' || c === '▄')
  }
  rzedy.push(gora, dol)
}
// Pusty pol-wiersz na brzegu to tlo terminala, nie modul - wycinamy.
while (rzedy.length && !rzedy[0].some(Boolean)) rzedy.shift()
while (rzedy.length && !rzedy[rzedy.length - 1].some(Boolean)) rzedy.pop()
const SKALA = 12, MARG = 4
const W = (szer + 2 * MARG) * SKALA, H = (rzedy.length + 2 * MARG) * SKALA
const surowe = Buffer.alloc((W + 1) * H)
for (let y = 0; y < H; y++) {
  surowe[y * (W + 1)] = 0
  const my = Math.floor(y / SKALA) - MARG
  for (let x = 0; x < W; x++) {
    const mx = Math.floor(x / SKALA) - MARG
    const jasny = my < 0 || mx < 0 || my >= rzedy.length || mx >= szer || rzedy[my][mx]
    surowe[y * (W + 1) + 1 + x] = jasny ? 255 : 0
  }
}
const crcT = []
for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; crcT[n] = c >>> 0 }
const crc = (b) => { let c = 0xffffffff; for (const x of b) c = crcT[(c ^ x) & 255] ^ (c >>> 8); return (c ^ 0xffffffff) >>> 0 }
const kawalek = (typ, dane) => {
  const d = Buffer.concat([Buffer.from(typ), dane])
  const len = Buffer.alloc(4); len.writeUInt32BE(dane.length)
  const c = Buffer.alloc(4); c.writeUInt32BE(crc(d))
  return Buffer.concat([len, d, c])
}
const ihdr = Buffer.alloc(13)
ihdr.writeUInt32BE(W, 0); ihdr.writeUInt32BE(H, 4); ihdr[8] = 8; ihdr[9] = 0
fs.writeFileSync(wyj, Buffer.concat([
  Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
  kawalek('IHDR', ihdr), kawalek('IDAT', zlib.deflateSync(surowe)), kawalek('IEND', Buffer.alloc(0))
]))
console.log(szer + 'x' + rzedy.length + ' modulow -> ' + wyj)
