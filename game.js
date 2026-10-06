'use strict';

/* =========================================================================
   Wuselburg – Wo steckt Fips?
   Prototyp eines Wimmelbild-Suchspiels (Handy & Tablet, läuft im Browser).
   Keine Abhängigkeiten: Szenen werden pro Level aus einem Seed als SVG erzeugt.
   ========================================================================= */

// ---------------------------------------------------------------- Welt-Layout
const ROAD = 100;               // Straßenbreite
const BW = 420, BH = 380;       // Blockgröße
const COLS = 4, ROWS = 3;
const W = ROAD + COLS * (BW + ROAD);   // 2180
const H = ROAD + ROWS * (BH + ROAD);   // 1540

// ---------------------------------------------------------------- Level
const LEVELS = [
  {
    id: 'fips1', kind: 'fips', title: 'Marktplatz', sub: 'Leicht · Fips ist gut zu sehen',
    seed: 11, crowd: 230, foxes: 8, decoys: ['noScarf'], hidden: false,
    intro: 'Fips, der kleine Fuchs, ist beim Markttrubel verloren gegangen. Findest du ihn?'
  },
  {
    id: 'fips2', kind: 'fips', title: 'Park-Wirbel', sub: 'Mittel · Fips versteckt sich',
    seed: 23, crowd: 380, foxes: 26, decoys: ['noScarf', 'redScarf', 'plainScarf'], hidden: true, hideOffset: 36,
    intro: 'Heute ist der Park voll! Fips spielt Verstecken – und es laufen viele andere Füchse herum.'
  },
  {
    id: 'fips3', kind: 'fips', title: 'Rushhour', sub: 'Schwer · Doppelgänger überall',
    seed: 37, crowd: 560, foxes: 56, decoys: ['noScarf', 'redScarf', 'plainScarf', 'noTip', 'grey'], hidden: true, hideOffset: 30,
    intro: 'Feierabend in Wuselburg! Achte genau auf Schal, Punkte und Schwanzspitze – es gibt Doppelgänger.'
  },
  {
    id: 'case1', kind: 'case', title: 'Der Kuchen-Fall', sub: 'Detektiv · Folge der Spur',
    seed: 5, crowd: 380, foxes: 6, decoys: ['noScarf', 'redScarf'], raccoons: 5,
    intro: 'In der Bäckerei ist der Geburtstagskuchen verschwunden! Finde zuerst die Bäckerei und folge dann der Krümelspur bis zum Dieb. Pass auf: Nicht jede Spur führt zum Ziel.'
  }
];

// ---------------------------------------------------------------- Hilfsfunktionen
function mulberry32(a) {
  return function () {
    a |= 0; a = a + 0x6D2B79F5 | 0;
    let t = Math.imul(a ^ a >>> 15, 1 | a);
    t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t;
    return ((t ^ t >>> 14) >>> 0) / 4294967296;
  };
}
const pick = (r, arr) => arr[Math.floor(r() * arr.length)];
function shuffle(arr, r) {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(r() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}
const f1 = n => Math.round(n * 10) / 10;
const $ = id => document.getElementById(id);

// ---------------------------------------------------------------- Figuren (SVG-Strings, Ursprung = Füße)
const SHIRT = ['#e05a5a', '#4a7fd6', '#f2b84b', '#7bc47f', '#a56bd6', '#ef8fb4', '#5ec2c9', '#d9d9d9', '#ff9f55'];
const SKIN = ['#f6d2b0', '#e3a97b', '#b97a52', '#8a5a3b', '#fde0c8'];
const HAIR = ['#2d2118', '#6b4423', '#c9a24b', '#b5472b', '#555', '#111'];
const PANTS = ['#3b4a6b', '#5b4a3b', '#444', '#2f5d62', '#7a5c8a'];
const TEAL = '#1fa6a0';

function fox(o) {
  const fur = o.fur || '#e8802a';
  const belly = '#fff4e0';
  let s = '';
  s += `<ellipse cx="16" cy="-24" rx="6" ry="14" transform="rotate(32 16 -24)" fill="${fur}"/>`;
  if (o.tip !== false) s += `<ellipse cx="22.5" cy="-35.5" rx="3.6" ry="4.6" transform="rotate(32 22.5 -35.5)" fill="#fff"/>`;
  s += `<rect x="-8" y="-9" width="5" height="9" rx="2" fill="#6b3a1d"/><rect x="3" y="-9" width="5" height="9" rx="2" fill="#6b3a1d"/>`;
  s += `<ellipse cx="0" cy="-20" rx="10" ry="13" fill="${fur}"/><ellipse cx="0" cy="-18" rx="6" ry="9" fill="${belly}"/>`;
  s += `<path d="M-9,-43 L-9,-57 L-1,-46Z" fill="${fur}"/><path d="M9,-43 L9,-57 L1,-46Z" fill="${fur}"/>`;
  s += `<path d="M-7.6,-46 L-7.6,-53 L-3.4,-47Z" fill="#5a3320"/><path d="M7.6,-46 L7.6,-53 L3.4,-47Z" fill="#5a3320"/>`;
  s += `<ellipse cx="0" cy="-39" rx="10.5" ry="8.5" fill="${fur}"/>`;
  s += `<path d="M-7,-38 Q0,-26 7,-38 Z" fill="${belly}"/><circle cx="0" cy="-32.5" r="1.9" fill="#222"/>`;
  s += `<circle cx="-4.2" cy="-40.5" r="1.5" fill="#222"/><circle cx="4.2" cy="-40.5" r="1.5" fill="#222"/>`;
  if (o.scarf) {
    s += `<rect x="3" y="-30" width="5.5" height="12" rx="2.4" fill="${o.scarf}"/>`;
    s += `<rect x="-10.5" y="-33.5" width="21" height="6" rx="3" fill="${o.scarf}"/>`;
    if (o.dots) {
      s += `<g fill="#ffd84a"><circle cx="-6.5" cy="-30.5" r="1.3"/><circle cx="-1" cy="-30.5" r="1.3"/><circle cx="4.5" cy="-30.5" r="1.3"/>` +
           `<circle cx="5.7" cy="-25.5" r="1.2"/><circle cx="5.7" cy="-21" r="1.2"/></g>`;
    }
  }
  return s;
}

const FIPS = { fur: '#e8802a', scarf: TEAL, dots: true, tip: true };
const DECOY = {
  noScarf:    { fur: '#e8802a', scarf: null, tip: true },
  redScarf:   { fur: '#e8802a', scarf: '#d9433b', dots: true, tip: true },
  plainScarf: { fur: '#e8802a', scarf: TEAL, dots: false, tip: true },
  noTip:      { fur: '#e8802a', scarf: TEAL, dots: true, tip: false },
  grey:       { fur: '#9a9aa6', scarf: TEAL, dots: true, tip: true }
};

function raccoon(cake) {
  const fur = '#8d8d99', dark = '#3a3a45';
  let s = '';
  s += `<ellipse cx="16" cy="-22" rx="6" ry="14" transform="rotate(32 16 -22)" fill="${fur}"/>`;
  s += `<ellipse cx="13" cy="-17" rx="6" ry="2.4" transform="rotate(32 13 -17)" fill="${dark}"/>`;
  s += `<ellipse cx="18" cy="-27" rx="5.6" ry="2.4" transform="rotate(32 18 -27)" fill="${dark}"/>`;
  s += `<rect x="-8" y="-9" width="5" height="9" rx="2" fill="${dark}"/><rect x="3" y="-9" width="5" height="9" rx="2" fill="${dark}"/>`;
  s += `<ellipse cx="0" cy="-20" rx="10" ry="13" fill="${fur}"/><ellipse cx="0" cy="-18" rx="6" ry="9" fill="#cfcfd6"/>`;
  s += `<circle cx="-8" cy="-45" r="4" fill="${fur}"/><circle cx="8" cy="-45" r="4" fill="${fur}"/>`;
  s += `<ellipse cx="0" cy="-39" rx="10.5" ry="8.5" fill="#cfcfd6"/>`;
  s += `<path d="M-10.5,-42 Q0,-47 10.5,-42 L9,-37 Q0,-40 -9,-37 Z" fill="${dark}"/>`;
  s += `<circle cx="-4" cy="-40" r="1.6" fill="#fff"/><circle cx="4" cy="-40" r="1.6" fill="#fff"/>`;
  s += `<circle cx="-4" cy="-40" r=".8" fill="#000"/><circle cx="4" cy="-40" r=".8" fill="#000"/>`;
  s += `<ellipse cx="0" cy="-34" rx="2.4" ry="1.8" fill="${dark}"/>`;
  if (cake) {
    s += `<g transform="translate(-1 -17)"><rect x="-8" y="-6" width="16" height="9" rx="2" fill="#f7c6d9"/>` +
         `<rect x="-8" y="-9" width="16" height="4.5" rx="2.2" fill="#fff"/><circle cx="0" cy="-11.5" r="2.2" fill="#d9433b"/></g>`;
  }
  return s;
}

function person(r) {
  const shirt = pick(r, SHIRT), skin = pick(r, SKIN), hair = pick(r, HAIR), pants = pick(r, PANTS);
  let s = '';
  s += `<rect x="-6.5" y="-12" width="5.5" height="12" rx="2" fill="${pants}"/><rect x="1" y="-12" width="5.5" height="12" rx="2" fill="${pants}"/>`;
  s += `<rect x="-11" y="-34" width="4.2" height="17" rx="2" fill="${shirt}"/><rect x="6.8" y="-34" width="4.2" height="17" rx="2" fill="${shirt}"/>`;
  s += `<rect x="-8" y="-36" width="16" height="26" rx="6" fill="${shirt}"/>`;
  s += `<circle cx="0" cy="-43.5" r="7.5" fill="${skin}"/>`;
  const hat = r() < 0.5 ? pick(r, ['cap', 'beanie', 'top', 'none']) : 'none';
  const hc = pick(r, SHIRT);
  if (hat === 'none') s += `<path d="M-7.5,-43.5 A7.5,7.5 0 0 1 7.5,-43.5 Q0,-47 -7.5,-43.5Z" fill="${hair}"/>`;
  s += `<circle cx="-2.6" cy="-42.5" r="1" fill="#222"/><circle cx="2.6" cy="-42.5" r="1" fill="#222"/>`;
  if (hat === 'cap') s += `<path d="M-8,-45.5 A8,8 0 0 1 8,-45.5Z" fill="${hc}"/><rect x="2" y="-47" width="9.5" height="2.6" rx="1.2" fill="${hc}"/>`;
  if (hat === 'beanie') s += `<path d="M-8,-45 A8,9.5 0 0 1 8,-45Z" fill="${hc}"/><circle cx="0" cy="-54" r="2.2" fill="#fff"/>`;
  if (hat === 'top') s += `<rect x="-9.5" y="-50" width="19" height="3" rx="1.5" fill="#222"/><rect x="-6" y="-60" width="12" height="11" rx="1.5" fill="#222"/><rect x="-6" y="-53" width="12" height="2.6" fill="${hc}"/>`;
  if (r() < 0.09) { // Luftballon
    const bc = pick(r, SHIRT);
    s += `<path d="M11,-27 Q18,-50 14,-72" stroke="#777" stroke-width=".8" fill="none"/><ellipse cx="14" cy="-80" rx="7" ry="9" fill="${bc}"/>`;
  }
  return s;
}

function dog(r) {
  const c = pick(r, ['#a0703f', '#6b4a2b', '#d9b98a', '#333']);
  return `<path d="M14,-14 Q22,-22 19,-26" stroke="${c}" stroke-width="3.5" stroke-linecap="round" fill="none"/>` +
    `<rect x="-12" y="-9" width="4" height="9" rx="2" fill="${c}"/><rect x="8" y="-9" width="4" height="9" rx="2" fill="${c}"/>` +
    `<ellipse cx="0" cy="-15" rx="15" ry="8" fill="${c}"/><circle cx="-14" cy="-22" r="7" fill="${c}"/>` +
    `<ellipse cx="-19.5" cy="-20" rx="4" ry="3" fill="#f3e6d3"/><circle cx="-12" cy="-23.5" r="1.1" fill="#111"/>` +
    `<path d="M-12,-27 q-3,6 -6,3" fill="#4a2f17"/>`;
}
function cat(r) {
  const c = pick(r, ['#555', '#d98c3f', '#ccc', '#222']);
  return `<path d="M12,-10 Q24,-16 18,-30" stroke="${c}" stroke-width="3" stroke-linecap="round" fill="none"/>` +
    `<ellipse cx="0" cy="-10" rx="12" ry="7.5" fill="${c}"/><circle cx="-11" cy="-17" r="6" fill="${c}"/>` +
    `<path d="M-16,-21 L-15,-27 L-11,-22Z M-7,-22 L-6,-27 L-3,-20Z" fill="${c}"/>` +
    `<circle cx="-13" cy="-17.5" r="1" fill="#9be07a"/><circle cx="-9" cy="-17.5" r="1" fill="#9be07a"/>`;
}
function pigeon() {
  return `<ellipse cx="0" cy="-6" rx="7" ry="5" fill="#9aa0ad"/><circle cx="-6" cy="-10" r="3.2" fill="#7f8594"/>` +
    `<path d="M-9,-10 l-3,1 l3,1Z" fill="#e8a33c"/><path d="M5,-5 l8,2 l-8,2Z" fill="#7f8594"/>` +
    `<path d="M-2,0 v3 M2,0 v3" stroke="#e8a33c" stroke-width="1"/>`;
}

function treeSvg(x, y, col) {
  return `<g transform="translate(${f1(x)} ${f1(y)})"><ellipse cx="0" cy="2" rx="20" ry="5" fill="rgba(0,0,0,.14)"/>` +
    `<rect x="-4" y="-34" width="8" height="34" rx="3" fill="#7a4e2d"/>` +
    `<circle cx="-20" cy="-48" r="18" fill="${col}"/><circle cx="20" cy="-48" r="18" fill="${col}"/>` +
    `<circle cx="0" cy="-62" r="26" fill="${col}"/><circle cx="-8" cy="-68" r="9" fill="rgba(255,255,255,.14)"/></g>`;
}

function stallSvg(x, y, col) {
  let stripes = '';
  for (let i = 0; i < 6; i++) stripes += `<rect x="${-45 + i * 15}" y="-62" width="15" height="18" fill="${i % 2 ? '#fff' : col}"/>`;
  return `<g transform="translate(${x} ${y})"><ellipse cx="0" cy="4" rx="52" ry="7" fill="rgba(0,0,0,.12)"/>` +
    `<rect x="-42" y="-44" width="5" height="44" fill="#8a6a45"/><rect x="37" y="-44" width="5" height="44" fill="#8a6a45"/>` +
    `<rect x="-42" y="-24" width="84" height="24" rx="3" fill="#c99a62"/>${stripes}` +
    `<path d="M-45,-44 q7.5,10 15,0 q7.5,10 15,0 q7.5,10 15,0 q7.5,10 15,0 q7.5,10 15,0 q7.5,10 15,0 Z" fill="${col}"/>` +
    `<circle cx="-24" cy="-28" r="4.5" fill="#e45b4b"/><circle cx="-12" cy="-28" r="4.5" fill="#f2b84b"/><circle cx="2" cy="-28" r="4.5" fill="#7bc47f"/>` +
    `<circle cx="16" cy="-28" r="4.5" fill="#e45b4b"/><circle cx="29" cy="-28" r="4.5" fill="#f2b84b"/></g>`;
}

function houseSvg(x, baseY, w, h, col, roof, r, bakery) {
  let s = `<g>`;
  s += `<rect x="${x}" y="${baseY - h}" width="${w}" height="${h}" fill="${col}"/>`;
  s += `<polygon points="${x - 7},${baseY - h} ${x + w / 2},${baseY - h - 46} ${x + w + 7},${baseY - h}" fill="${roof}"/>`;
  const floors = h > 150 ? 2 : 1;
  for (let f = 0; f < floors; f++) {
    for (let c = 0; c < 2; c++) {
      const wx = x + 16 + c * (w - 32 - 24), wy = baseY - h + 16 + f * 56;
      s += `<rect x="${wx}" y="${wy}" width="24" height="28" rx="3" fill="#bfe6f5" stroke="#fff" stroke-width="3"/>` +
           `<path d="M${wx + 12},${wy} v28 M${wx},${wy + 14} h24" stroke="#fff" stroke-width="2"/>`;
      if (r() < 0.4) s += `<rect x="${wx + 2}" y="${wy + 2}" width="9" height="24" fill="${pick(r, SHIRT)}" opacity=".8"/>`;
    }
  }
  s += `<rect x="${x + w / 2 - 13}" y="${baseY - 42}" width="26" height="42" rx="3" fill="${bakery ? '#7a4e2d' : '#8a5a3b'}"/>` +
       `<circle cx="${x + w / 2 + 7}" cy="${baseY - 20}" r="2" fill="#ffd84a"/>`;
  if (bakery) {
    let aw = '';
    for (let i = 0; i < 8; i++) aw += `<rect x="${x + i * (w / 8)}" y="${baseY - 62}" width="${w / 8}" height="16" fill="${i % 2 ? '#fff' : '#e45b4b'}"/>`;
    s += aw;
    s += `<rect x="${x + 6}" y="${baseY - h + 66}" width="${w - 12}" height="22" rx="4" fill="#fff6dc" stroke="#c99a62" stroke-width="2"/>` +
         `<text x="${x + w / 2}" y="${baseY - h + 82}" text-anchor="middle" font-size="14" font-weight="700" font-family="sans-serif" fill="#8a4b1c">Bäckerei</text>`;
  }
  return s + `</g>`;
}

// ---------------------------------------------------------------- Szene bauen
function buildScene(cfg) {
  const r = mulberry32(cfg.seed);
  const types = ['park', 'park', 'market', 'plaza', 'houses', 'houses', 'houses', 'houses', 'houses', 'houses', 'houses', 'houses'];
  shuffle(types, r);

  const blocks = [];
  for (let row = 0; row < ROWS; row++) {
    for (let col = 0; col < COLS; col++) {
      blocks.push({ col, row, x: ROAD + col * (BW + ROAD), y: ROAD + row * (BH + ROAD), type: types[row * COLS + col] });
    }
  }
  let bakeryBlock = null;
  if (cfg.kind === 'case') bakeryBlock = pick(r, blocks.filter(b => b.type === 'houses'));

  let bg = `<rect width="${W}" height="${H}" fill="#bfe3a4"/>`;
  const walk = [];       // begehbare Rechtecke
  const obstacles = [];  // {x,y,r}
  const items = [];      // sortierte Objekte {y, svg}
  const trees = [];
  let bakeryHit = null;

  // Straßen
  for (let k = 0; k <= COLS; k++) {
    const x = k * (BW + ROAD);
    bg += `<rect x="${x}" y="0" width="${ROAD}" height="${H}" fill="#dcd3c0"/>`;
    bg += `<line x1="${x + ROAD / 2}" y1="0" x2="${x + ROAD / 2}" y2="${H}" stroke="#fff" stroke-width="3" stroke-dasharray="26 22" opacity=".7"/>`;
    walk.push({ x: x + 14, y: 14, w: ROAD - 28, h: H - 28 });
  }
  for (let j = 0; j <= ROWS; j++) {
    const y = j * (BH + ROAD);
    bg += `<rect x="0" y="${y}" width="${W}" height="${ROAD}" fill="#dcd3c0"/>`;
    bg += `<line x1="0" y1="${y + ROAD / 2}" x2="${W}" y2="${y + ROAD / 2}" stroke="#fff" stroke-width="3" stroke-dasharray="26 22" opacity=".7"/>`;
    walk.push({ x: 14, y: y + 14, w: W - 28, h: ROAD - 28 });
  }
  // Kreuzungen glätten
  for (let k = 0; k <= COLS; k++) for (let j = 0; j <= ROWS; j++) {
    bg += `<rect x="${k * (BW + ROAD)}" y="${j * (BH + ROAD)}" width="${ROAD}" height="${ROAD}" fill="#dcd3c0"/>`;
  }

  const addTree = (x, y) => {
    trees.push({ x, y });
    obstacles.push({ x, y, r: 22 });
    items.push({ y, svg: treeSvg(x, y, pick(r, ['#4caf50', '#3f9f4a', '#5bbf5a', '#2f8f46'])) });
  };

  // Blöcke
  for (const b of blocks) {
    const { x, y } = b;
    if (b.type === 'park') {
      bg += `<rect x="${x}" y="${y}" width="${BW}" height="${BH}" rx="26" fill="#8fcf7a"/>`;
      const px = x + 120 + r() * 180, py = y + 120 + r() * 140;
      bg += `<ellipse cx="${f1(px)}" cy="${f1(py)}" rx="70" ry="44" fill="#9fd8f0" stroke="#fff" stroke-width="4"/>`;
      bg += `<ellipse cx="${f1(px - 20)}" cy="${f1(py + 4)}" rx="10" ry="5" fill="#5fae5b"/>`;
      obstacles.push({ x: px, y: py, r: 55 });
      for (let i = 0; i < 40; i++) bg += `<circle cx="${f1(x + 14 + r() * (BW - 28))}" cy="${f1(y + 14 + r() * (BH - 28))}" r="2.2" fill="${pick(r, ['#fff', '#ffd84a', '#ff9fc2'])}"/>`;
      for (let i = 0; i < 7; i++) addTree(x + 40 + r() * (BW - 80), y + 80 + r() * (BH - 90));
      walk.push({ x: x + 20, y: y + 20, w: BW - 40, h: BH - 30 });
    } else if (b.type === 'market') {
      bg += `<rect x="${x}" y="${y}" width="${BW}" height="${BH}" rx="14" fill="#f0dcae"/>`;
      for (let i = 0; i < 24; i++) bg += `<circle cx="${f1(x + 12 + r() * (BW - 24))}" cy="${f1(y + 12 + r() * (BH - 24))}" r="3" fill="#e3c88f"/>`;
      const sc = ['#e45b4b', '#4a7fd6', '#7bc47f', '#f2b84b'];
      [[x + 90, y + 120], [x + 210, y + 120], [x + 330, y + 120], [x + 130, y + 290], [x + 290, y + 290]].forEach(([sx, sy]) => {
        items.push({ y: sy, svg: stallSvg(sx, sy, pick(r, sc)) });
        obstacles.push({ x: sx, y: sy - 10, r: 46 });
      });
      addTree(x + 30, y + 40); addTree(x + BW - 30, y + BH - 20);
      walk.push({ x: x + 14, y: y + 14, w: BW - 28, h: BH - 20 });
    } else if (b.type === 'plaza') {
      bg += `<rect x="${x}" y="${y}" width="${BW}" height="${BH}" rx="14" fill="#e8e1d2"/>`;
      const cx = x + BW / 2, cy = y + BH / 2;
      bg += `<circle cx="${cx}" cy="${cy}" r="62" fill="#b9e3f2" stroke="#fff" stroke-width="8"/><circle cx="${cx}" cy="${cy}" r="14" fill="#8cc9e0"/>`;
      obstacles.push({ x: cx, y: cy, r: 66 });
      addTree(x + 36, y + 50); addTree(x + BW - 36, y + 50); addTree(x + 36, y + BH - 20); addTree(x + BW - 36, y + BH - 20);
      walk.push({ x: x + 20, y: y + 20, w: BW - 40, h: BH - 28 });
    } else {
      bg += `<rect x="${x}" y="${y}" width="${BW}" height="${BH}" rx="14" fill="#d3e7b9"/>`;
      bg += `<rect x="${x}" y="${y + BH - 78}" width="${BW}" height="78" rx="10" fill="#e6dfcf"/>`;
      const baseY = y + BH - 85;
      const roofs = ['#c8553d', '#7a5c8a', '#3f7cac', '#b5722a'];
      const walls = ['#f6e0b5', '#f2c9c9', '#cfe3f0', '#e9ecc1', '#f7d9a8'];
      for (let i = 0; i < 3; i++) {
        const hx = x + 20 + i * 140, hh = 150 + Math.floor(r() * 3) * 25;
        const isBakery = b === bakeryBlock && i === 1;
        bg += houseSvg(hx, baseY, 120, hh, isBakery ? '#fbe9c8' : pick(r, walls), isBakery ? '#c8553d' : pick(r, roofs), r, isBakery);
        if (isBakery) bakeryHit = { rect: { x: hx - 7, y: baseY - hh - 46, w: 134, h: hh + 46 }, center: { x: hx + 60, y: baseY - hh / 2 }, door: { x: hx + 60, y: baseY + 20 } };
      }
      addTree(x + 140, baseY + 30); addTree(x + 280, baseY + 30);
      walk.push({ x: x + 10, y: y + BH - 66, w: BW - 20, h: 50 });
    }
  }

  // Zielfiguren & Spuren
  const steps = [];
  let crumbs = '';
  const placed = [];   // {x,y} für Mindestabstand
  const free = (x, y, d) => !obstacles.some(o => (o.x - x) ** 2 + (o.y - y) ** 2 < (o.r + 8) ** 2) && !placed.some(p => (p.x - x) ** 2 + (p.y - y) ** 2 < d * d);
  const areaSum = walk.reduce((a, w) => a + w.w * w.h, 0);
  const randomSpot = () => {
    let t = r() * areaSum, rect = walk[0];
    for (const w of walk) { t -= w.w * w.h; if (t <= 0) { rect = w; break; } }
    return { x: rect.x + r() * rect.w, y: rect.y + r() * rect.h };
  };
  const addActor = (x, y, svgInner, scale, flip) => {
    placed.push({ x, y });
    items.push({ y, svg: `<g transform="translate(${f1(x)} ${f1(y)}) scale(${flip ? -scale : scale} ${scale})">${svgInner}</g>` });
  };

  if (cfg.kind === 'fips') {
    let fx, fy;
    if (cfg.hidden) {
      const t = pick(r, trees.filter(t => t.x > 60 && t.x < W - 60 && t.y > 120 && t.y < H - 60));
      const side = r() < 0.5 ? -1 : 1;
      fx = t.x + side * cfg.hideOffset; fy = t.y - 4;
    } else {
      let s = randomSpot(), n = 0;
      while (!free(s.x, s.y, 40) && n++ < 80) s = randomSpot();
      fx = s.x; fy = s.y;
    }
    addActor(fx, fy, fox(FIPS), 1, false);
    steps.push({
      title: 'Finde Fips!', sub: 'Fuchs mit türkisem Schal, gelben Punkten und weißer Schwanzspitze',
      hit: { circle: { x: fx, y: fy - 28, r: 30 } }, center: { x: fx, y: fy - 28 }
    });
  } else {
    // Detektivfall: Spur von der Bäckerei zum Dieb
    const b = bakeryBlock;
    const S = bakeryHit.door;
    const roadX = k => k * (BW + ROAD) + ROAD / 2;
    const roadY = j => j * (BH + ROAD) + ROAD / 2;
    const yh = roadY(b.row + 1);
    const cands = [0, 1, 2, 3, 4].filter(k => Math.abs(k - b.col) >= 2);
    const kv = pick(r, cands);
    const jt = b.row + 1 <= 1 ? 3 : 0;
    const yt = roadY(jt);
    const dir = roadX(kv) > S.x ? 1 : -1;
    const tx = Math.min(W - 80, Math.max(80, roadX(kv) + (r() < 0.5 ? -1 : 1) * (150 + r() * 150)));
    const route = [[S.x, S.y], [S.x, yh], [roadX(kv), yh], [roadX(kv), yt], [tx, yt]];
    const dropCrumbs = (pts, keepEnd) => {
      for (let i = 0; i < pts.length - 1; i++) {
        const [ax, ay] = pts[i], [bx, by] = pts[i + 1];
        const len = Math.hypot(bx - ax, by - ay), n = Math.floor(len / 26);
        for (let m = 0; m < n; m++) {
          const t = m / n, px = ax + (bx - ax) * t + (r() - .5) * 10, py = ay + (by - ay) * t + (r() - .5) * 10;
          crumbs += `<circle cx="${f1(px)}" cy="${f1(py)}" r="${m % 3 === 0 ? 3.4 : 2.4}" fill="${m % 3 === 0 ? '#c98a2b' : '#e0a845'}"/>`;
        }
      }
    };
    dropCrumbs(route);
    // falsche Spur: geht an der Kurve geradeaus weiter und endet bei Tauben
    const fEnd = [Math.min(W - 60, Math.max(60, roadX(kv) + dir * 330)), yh];
    dropCrumbs([[roadX(kv), yh], fEnd]);
    for (let i = 0; i < 3; i++) addActor(fEnd[0] + dir * (14 + i * 20), fEnd[1] + (r() - .5) * 20, pigeon(), 1, r() < .5);

    const thiefX = tx, thiefY = yt + 12;
    addActor(thiefX, thiefY, raccoon(true), 1.05, r() < .5);

    steps.push({
      title: 'Finde die Bäckerei!', sub: 'Dort fehlt der Geburtstagskuchen',
      hit: { rect: bakeryHit.rect }, center: bakeryHit.center,
      onFound: 'Hier stand der Kuchen! Auf dem Boden liegen Krümel …'
    });
    steps.push({
      title: 'Wer hat den Kuchen?', sub: 'Folge der Krümelspur bis zum Dieb',
      hit: { circle: { x: thiefX, y: thiefY - 28, r: 32 } }, center: { x: thiefX, y: thiefY - 28 },
      epilogue: 'Erwischt! Waschbär Rocky hatte großen Hunger – und hat den Kuchen mit allen Tauben der Stadt geteilt. Fast.'
    });
  }

  // Doppelgänger-Füchse
  const kinds = cfg.decoys || [];
  for (let i = 0; i < (cfg.foxes || 0); i++) {
    let s = randomSpot(), n = 0;
    while (!free(s.x, s.y, 34) && n++ < 40) s = randomSpot();
    addActor(s.x, s.y, fox(DECOY[kinds[i % kinds.length]]), 0.92 + r() * 0.16, r() < .5);
  }
  for (let i = 0; i < (cfg.raccoons || 0); i++) {
    let s = randomSpot(), n = 0;
    while (!free(s.x, s.y, 34) && n++ < 40) s = randomSpot();
    addActor(s.x, s.y, raccoon(false), 0.95 + r() * 0.15, r() < .5);
  }
  // Bevölkerung
  const rest = Math.max(0, cfg.crowd - (cfg.foxes || 0) - (cfg.raccoons || 0));
  for (let i = 0; i < rest; i++) {
    let s = randomSpot(), n = 0;
    while (!free(s.x, s.y, 30) && n++ < 40) s = randomSpot();
    const roll = r();
    const sc = 0.9 + r() * 0.2, flip = r() < .5;
    if (roll < 0.1) addActor(s.x, s.y, dog(r), sc, flip);
    else if (roll < 0.17) addActor(s.x, s.y, cat(r), sc, flip);
    else addActor(s.x, s.y, person(r), sc, flip);
  }

  items.sort((a, b) => a.y - b.y);
  const svg = `<g>${bg}</g><g>${crumbs}</g><g>${items.map(i => i.svg).join('')}</g>`;
  return { svg, steps };
}

// ---------------------------------------------------------------- Zustand
let level = null, scene = null, stepIdx = 0, misses = 0, hints = 0, elapsed = 0;
let running = false, timerHandle = null, lastTick = 0;
const cam = { x: 0, y: 0, k: 1 };
let minK = .2, maxK = 2;

const stage = $('stage'), world = $('world');
let fxLayer = null;

// ---------------------------------------------------------------- Fortschritt
const SAVE_KEY = 'wuselburg.v1';
function loadSave() { try { return JSON.parse(localStorage.getItem(SAVE_KEY)) || {}; } catch (e) { return {}; } }
function saveStars(id, n) {
  try { const s = loadSave(); s[id] = Math.max(s[id] || 0, n); localStorage.setItem(SAVE_KEY, JSON.stringify(s)); } catch (e) { /* privater Modus */ }
}
const starStr = n => [1, 2, 3].map(i => `<span class="${i <= n ? '' : 'off'}">★</span>`).join('');

// ---------------------------------------------------------------- Menü
function renderMenu() {
  const save = loadSave();
  $('menu-logo').innerHTML = fox(FIPS);
  $('level-list').innerHTML = LEVELS.map(l => `
    <button class="card" data-id="${l.id}">
      <div class="card-ico">${l.kind === 'case' ? '🥧' : '🦊'}</div>
      <div class="grow"><h3>${l.title}</h3><p>${l.sub}</p></div>
      <div class="stars">${starStr(save[l.id] || 0)}</div>
    </button>`).join('');
  document.querySelectorAll('#level-list .card').forEach(b => b.addEventListener('click', () => startLevel(b.dataset.id)));
}
function showMenu() {
  stopTimer();
  $('game').classList.add('hidden');
  $('menu').classList.remove('hidden');
  renderMenu();
}

// ---------------------------------------------------------------- Overlay
function showCard({ art, title, body, stars, buttons }) {
  $('card-art').innerHTML = art || '';
  $('card-art').style.display = art ? '' : 'none';
  $('card-title').textContent = title;
  $('card-body').textContent = body || '';
  $('card-stars').innerHTML = stars == null ? '' : starStr(stars);
  const box = $('card-buttons');
  box.innerHTML = '';
  buttons.forEach(b => {
    const el = document.createElement('button');
    el.className = 'btn' + (b.secondary ? ' secondary' : '');
    el.textContent = b.label;
    el.addEventListener('click', b.onClick);
    box.appendChild(el);
  });
  $('overlay').classList.remove('hidden');
  pauseTimer();
}
function hideCard() { $('overlay').classList.add('hidden'); }

// ---------------------------------------------------------------- Timer
function fmt(s) { s = Math.floor(s); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`; }
function startTimer() { running = true; lastTick = performance.now(); if (!timerHandle) timerHandle = setInterval(tick, 250); }
function pauseTimer() { tick(); running = false; }
function stopTimer() { running = false; clearInterval(timerHandle); timerHandle = null; }
function tick() {
  const now = performance.now();
  if (running) elapsed += (now - lastTick) / 1000;
  lastTick = now;
  $('timer').textContent = fmt(elapsed);
}

// ---------------------------------------------------------------- Level starten
function startLevel(id) {
  level = LEVELS.find(l => l.id === id);
  scene = buildScene(level);
  world.innerHTML = scene.svg + '<g id="fx"></g>';
  fxLayer = $('fx');
  stepIdx = 0; misses = 0; hints = 0; elapsed = 0;
  $('timer').textContent = '0:00';
  $('menu').classList.add('hidden');
  $('game').classList.remove('hidden');
  updateGoal();
  resetCamera();
  showCard({
    art: fox(FIPS),
    title: level.title,
    body: level.intro,
    buttons: [{ label: 'Los geht’s!', onClick: () => { hideCard(); startTimer(); } }]
  });
}

function updateGoal() {
  const st = scene.steps[stepIdx];
  $('goal-title').textContent = st.title;
  $('goal-sub').textContent = st.sub;
  $('goal-icon').innerHTML = level.kind === 'fips' ? fox(FIPS) : (stepIdx === 0 ? '<text x="0" y="-20" font-size="42" text-anchor="middle">🥧</text>' : raccoon(true));
}

// ---------------------------------------------------------------- Kamera
function viewSize() { return { vw: stage.clientWidth, vh: stage.clientHeight }; }
function clampCam() {
  const { vw, vh } = viewSize();
  const mw = W * cam.k, mh = H * cam.k;
  cam.x = mw <= vw ? (vw - mw) / 2 : Math.min(0, Math.max(vw - mw, cam.x));
  cam.y = mh <= vh ? (vh - mh) / 2 : Math.min(0, Math.max(vh - mh, cam.y));
}
function applyCam() { clampCam(); world.setAttribute('transform', `translate(${f1(cam.x)} ${f1(cam.y)}) scale(${cam.k})`); }
function limits() {
  const { vw, vh } = viewSize();
  minK = Math.min(vw / W, vh / H);
  maxK = Math.max(minK * 8, 1.4);
}
function resetCamera() {
  limits();
  const { vw, vh } = viewSize();
  cam.k = Math.min(maxK, minK * 2.2);
  cam.x = vw / 2 - (W / 2) * cam.k;
  cam.y = vh / 2 - (H / 2) * cam.k;
  applyCam();
}
function zoomAt(px, py, nk) {
  nk = Math.min(maxK, Math.max(minK, nk));
  const wx = (px - cam.x) / cam.k, wy = (py - cam.y) / cam.k;
  cam.k = nk; cam.x = px - wx * nk; cam.y = py - wy * nk;
  applyCam();
}
let flyHandle = 0;
function flyTo(wx, wy, k) {
  limits();
  k = Math.min(maxK, Math.max(minK, k));
  const { vw, vh } = viewSize();
  const from = { x: cam.x, y: cam.y, k: cam.k };
  const to = { k, x: vw / 2 - wx * k, y: vh / 2 - wy * k };
  const t0 = performance.now(), dur = 550;
  cancelAnimationFrame(flyHandle);
  const step = now => {
    const t = Math.min(1, (now - t0) / dur), e = t * t * (3 - 2 * t);
    cam.k = from.k + (to.k - from.k) * e; cam.x = from.x + (to.x - from.x) * e; cam.y = from.y + (to.y - from.y) * e;
    applyCam();
    if (t < 1) flyHandle = requestAnimationFrame(step);
  };
  flyHandle = requestAnimationFrame(step);
}

// ---------------------------------------------------------------- Eingabe (Ziehen, Pinch, Tippen)
const ptrs = new Map();
let gesture = { moved: false, multi: false, d0: 0, k0: 1, t0: 0 };

stage.addEventListener('pointerdown', e => {
  stage.setPointerCapture(e.pointerId);
  cancelAnimationFrame(flyHandle);
  ptrs.set(e.pointerId, { x: e.clientX, y: e.clientY, sx: e.clientX, sy: e.clientY });
  if (ptrs.size === 1) gesture = { moved: false, multi: false, d0: 0, k0: cam.k, t0: performance.now() };
  if (ptrs.size === 2) {
    const [a, b] = [...ptrs.values()];
    gesture.multi = true; gesture.d0 = Math.hypot(a.x - b.x, a.y - b.y) || 1; gesture.k0 = cam.k;
  }
});
stage.addEventListener('pointermove', e => {
  const p = ptrs.get(e.pointerId);
  if (!p) return;
  const dx = e.clientX - p.x, dy = e.clientY - p.y;
  if (ptrs.size === 1) {
    if (Math.hypot(e.clientX - p.sx, e.clientY - p.sy) > 8) gesture.moved = true;
    if (gesture.moved) { cam.x += dx; cam.y += dy; applyCam(); }
    p.x = e.clientX; p.y = e.clientY;
  } else if (ptrs.size === 2) {
    const before = [...ptrs.values()];
    const cx0 = (before[0].x + before[1].x) / 2, cy0 = (before[0].y + before[1].y) / 2;
    p.x = e.clientX; p.y = e.clientY;
    const [a, b] = [...ptrs.values()];
    const cx = (a.x + b.x) / 2, cy = (a.y + b.y) / 2, d = Math.hypot(a.x - b.x, a.y - b.y) || 1;
    gesture.moved = true;
    cam.x += cx - cx0; cam.y += cy - cy0;
    const rect = stage.getBoundingClientRect();
    zoomAt(cx - rect.left, cy - rect.top, gesture.k0 * d / gesture.d0);
  }
});
function endPointer(e) {
  const p = ptrs.get(e.pointerId);
  if (!p) return;
  ptrs.delete(e.pointerId);
  if (e.type === 'pointerup' && ptrs.size === 0 && !gesture.moved && !gesture.multi && performance.now() - gesture.t0 < 700) {
    const rect = stage.getBoundingClientRect();
    tapScreen(e.clientX - rect.left, e.clientY - rect.top);
  }
  if (ptrs.size === 1) { const q = [...ptrs.values()][0]; q.sx = q.x; q.sy = q.y; gesture.moved = true; }
}
stage.addEventListener('pointerup', endPointer);
stage.addEventListener('pointercancel', endPointer);
stage.addEventListener('wheel', e => {
  e.preventDefault();
  const rect = stage.getBoundingClientRect();
  zoomAt(e.clientX - rect.left, e.clientY - rect.top, cam.k * Math.exp(-e.deltaY * 0.0015));
}, { passive: false });
$('zoom-in').addEventListener('click', () => { const { vw, vh } = viewSize(); zoomAt(vw / 2, vh / 2, cam.k * 1.5); });
$('zoom-out').addEventListener('click', () => { const { vw, vh } = viewSize(); zoomAt(vw / 2, vh / 2, cam.k / 1.5); });
window.addEventListener('resize', () => { if (level) { limits(); zoomAt(0, 0, cam.k); } });

// ---------------------------------------------------------------- Treffer
function hitTest(hit, x, y, tol) {
  if (hit.circle) return Math.hypot(hit.circle.x - x, hit.circle.y - y) <= hit.circle.r + tol;
  const r = hit.rect;
  return x >= r.x - tol && x <= r.x + r.w + tol && y >= r.y - tol && y <= r.y + r.h + tol;
}
function tapScreen(px, py) { tapWorld((px - cam.x) / cam.k, (py - cam.y) / cam.k); }
function tapWorld(wx, wy) {
  if (!running || !scene) return;
  const st = scene.steps[stepIdx];
  const tol = 14 / cam.k;   // großzügig für Kinderfinger
  if (hitTest(st.hit, wx, wy, tol)) found(st);
  else miss(wx, wy);
}
function fx(svg) {
  const g = document.createElementNS('http://www.w3.org/2000/svg', 'g');
  g.innerHTML = svg;
  fxLayer.appendChild(g);
  return g;
}
function miss(wx, wy) {
  misses++;
  const s = 1 / Math.max(cam.k, .35);
  const g = fx(`<g transform="translate(${f1(wx)} ${f1(wy)}) scale(${f1(s)})"><g class="fx-miss"><circle r="16" fill="none" stroke="#fff" stroke-width="4"/>` +
    `<path d="M-6,-6 L6,6 M6,-6 L-6,6" stroke="#e05a5a" stroke-width="4" stroke-linecap="round"/></g></g>`);
  setTimeout(() => g.remove(), 750);
}
function found(st) {
  const c = st.center;
  const r = st.hit.circle ? st.hit.circle.r + 10 : Math.max(st.hit.rect.w, st.hit.rect.h) / 2 + 6;
  fx(`<g class="fx-found"><circle cx="${f1(c.x)}" cy="${f1(c.y)}" r="${f1(r)}" fill="rgba(255,255,255,.25)" stroke="#2fbf71" stroke-width="5"/>` +
     `<text x="${f1(c.x)}" y="${f1(c.y - r - 8)}" text-anchor="middle" font-size="26" font-weight="800" fill="#fff" stroke="#2fbf71" stroke-width="6" paint-order="stroke" font-family="sans-serif">Gefunden!</text></g>`);
  stepIdx++;
  pauseTimer();
  if (stepIdx < scene.steps.length) {
    setTimeout(() => {
      showCard({
        art: '<text x="0" y="-20" font-size="42" text-anchor="middle">🔎</text>',
        title: 'Gut gemacht!', body: st.onFound || '',
        buttons: [{ label: 'Weiter', onClick: () => { hideCard(); updateGoal(); startTimer(); } }]
      });
    }, 700);
  } else {
    setTimeout(() => finish(st), 900);
  }
}

// ---------------------------------------------------------------- Tipp
function useHint() {
  if (!running || !scene) return;
  hints++;
  const c = scene.steps[stepIdx].center;
  const a = Math.random() * Math.PI * 2, off = 50 + Math.random() * 90;
  const hx = c.x + Math.cos(a) * off, hy = c.y + Math.sin(a) * off;
  const g = fx(`<g class="fx-hint"><circle cx="${f1(hx)}" cy="${f1(hy)}" r="240" fill="rgba(255,226,120,.28)" stroke="#ffd84a" stroke-width="6" stroke-dasharray="14 10"/></g>`);
  setTimeout(() => g.remove(), 3700);
  flyTo(hx, hy, Math.max(cam.k, minK * 3.2));
}
$('btn-hint').addEventListener('click', useHint);
$('btn-menu').addEventListener('click', () => {
  showCard({
    title: 'Pause', body: 'Möchtest du zurück zum Menü?',
    buttons: [
      { label: 'Weiterspielen', onClick: () => { hideCard(); startTimer(); } },
      { label: 'Menü', secondary: true, onClick: () => { hideCard(); showMenu(); } }
    ]
  });
});

// ---------------------------------------------------------------- Ende
function finish(lastStep) {
  stopTimer();
  const stars = 1 + (hints === 0 ? 1 : 0) + (misses <= 2 ? 1 : 0);
  saveStars(level.id, stars);
  const idx = LEVELS.indexOf(level), next = LEVELS[idx + 1];
  const buttons = [{ label: 'Nochmal', secondary: true, onClick: () => { hideCard(); startLevel(level.id); } }];
  if (next) buttons.push({ label: 'Nächstes Level', onClick: () => { hideCard(); startLevel(next.id); } });
  buttons.push({ label: 'Menü', secondary: true, onClick: () => { hideCard(); showMenu(); } });
  showCard({
    art: level.kind === 'case' ? raccoon(true) : fox(FIPS),
    title: level.kind === 'case' ? 'Fall gelöst!' : 'Fips gefunden!',
    body: `${lastStep.epilogue || 'Super Spürnase!'}\nZeit ${fmt(elapsed)} · ${misses} ${misses === 1 ? 'Fehlversuch' : 'Fehlversuche'} · ${hints} ${hints === 1 ? 'Tipp' : 'Tipps'}`,
    stars, buttons
  });
  $('card-body').style.whiteSpace = 'pre-line';
  stopTimer();
}

// ---------------------------------------------------------------- Start
renderMenu();
// Für Tests / Debugging im Browser
window.wuselburg = { startLevel, tapWorld, steps: () => scene && scene.steps, cam, W, H };
