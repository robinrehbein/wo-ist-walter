'use strict';

/* =========================================================================
   Wuselburg – Welt, Level, Figuren und Szenengenerator.
   Keine DOM-Abhängigkeit: läuft im Browser und in Node (Paritätstests mit der
   Android-App, siehe android/core).

   Ablauf:  buildSceneData(level)  ->  reine Daten (Blöcke, Krümel, Figuren, Schritte)
            sceneToSvg(data)       ->  SVG-String für die Web-Version
   Die Reihenfolge ALLER r()-Aufrufe ist die Spezifikation für den Kotlin-Port.
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
    seed: 11, crowd: 230, foxes: 8, decoys: ['noScarf'], hidden: false, groups: 4, mood: 'day',
    intro: 'Fips, der kleine Fuchs, ist beim Markttrubel verloren gegangen. Findest du ihn?'
  },
  {
    id: 'fips2', kind: 'fips', title: 'Park-Wirbel', sub: 'Mittel · Fips versteckt sich',
    seed: 23, crowd: 380, foxes: 26, decoys: ['noScarf', 'redScarf', 'plainScarf'], hidden: true, hideOffset: 36, groups: 6, mood: 'day',
    intro: 'Heute ist der Park voll! Fips spielt Verstecken – und es laufen viele andere Füchse herum.'
  },
  {
    id: 'fips3', kind: 'fips', title: 'Rushhour', sub: 'Schwer · Doppelgänger überall',
    seed: 37, crowd: 560, foxes: 56, decoys: ['noScarf', 'redScarf', 'plainScarf', 'noTip', 'grey'], hidden: true, hideOffset: 30, groups: 8, mood: 'evening',
    intro: 'Feierabend in Wuselburg! Achte genau auf Schal, Punkte und Schwanzspitze – es gibt Doppelgänger.'
  },
  {
    id: 'case1', kind: 'case', title: 'Der Kuchen-Fall', sub: 'Detektiv · Folge der Spur',
    seed: 5, crowd: 380, foxes: 6, decoys: ['noScarf', 'redScarf'], raccoons: 5, groups: 5, mood: 'day',
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
const idx = (arr, v) => arr[Math.min(arr.length - 1, Math.floor(v * arr.length))];
function shuffle(arr, r) {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(r() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}
const f1 = n => Math.round(n * 10) / 10;

// ---------------------------------------------------------------- Paletten
const SHIRT = ['#e05a5a', '#4a7fd6', '#f2b84b', '#7bc47f', '#a56bd6', '#ef8fb4', '#5ec2c9', '#d9d9d9', '#ff9f55'];
const SKIN = ['#f6d2b0', '#e3a97b', '#b97a52', '#8a5a3b', '#fde0c8'];
const HAIR = ['#2d2118', '#6b4423', '#c9a24b', '#b5472b', '#555555', '#111111'];
const GREYS = ['#c9c9cf', '#e8e8ee', '#a8a8b3'];
const PANTS = ['#3b4a6b', '#5b4a3b', '#444444', '#2f5d62', '#7a5c8a'];
const SHOES = ['#3a2b22', '#222222', '#7a4e2d', '#e05a5a', '#ffffff'];
const PATTERN = ['#ffffff', '#2b2a33', '#ffd84a', '#e05a5a'];
const HATS = ['cap', 'beanie', 'top', 'sun', 'band'];
const HAIRSTYLES = ['short', 'long', 'bun', 'curly', 'bald', 'pony', 'short', 'long'];
const DOGC = ['#a0703f', '#6b4a2b', '#d9b98a', '#333333', '#c9884a'];
const CATC = ['#555555', '#d98c3f', '#cccccc', '#222222', '#e8d6b8'];
const GREENS = ['#4caf50', '#3f9f4a', '#5bbf5a', '#2f8f46'];
const PINKS = ['#f4a9c2', '#f7c1d3', '#e98fb2'];
const STALLC = ['#e45b4b', '#4a7fd6', '#7bc47f', '#f2b84b'];
const FLOWERC = ['#ffffff', '#ffd84a', '#ff9fc2', '#c9a0ff'];
const WALLS = ['#f6e0b5', '#f2c9c9', '#cfe3f0', '#e9ecc1', '#f7d9a8', '#e6d3f0'];
const ROOFS = ['#c8553d', '#7a5c8a', '#3f7cac', '#b5722a', '#4f8a5b'];
const SHUTTER = ['#4a7fd6', '#7bc47f', '#c8553d', '#f2b84b'];
const DOORC = ['#8a5a3b', '#7a4e2d', '#c8553d', '#3f7cac', '#5b8a4f'];
const BLANKET = [['#e05a5a', '#ffffff'], ['#4a7fd6', '#ffffff'], ['#f2b84b', '#fff6dc']];
const TEAL = '#1fa6a0';

// ---------------------------------------------------------------- Füchse & Waschbären
const FOXSTYLES = {
  fips:       { fur: '#e8802a', scarf: TEAL, dots: true, tip: true },
  noScarf:    { fur: '#e8802a', scarf: null, dots: false, tip: true },
  redScarf:   { fur: '#e8802a', scarf: '#d9433b', dots: true, tip: true },
  plainScarf: { fur: '#e8802a', scarf: TEAL, dots: false, tip: true },
  noTip:      { fur: '#e8802a', scarf: TEAL, dots: true, tip: false },
  grey:       { fur: '#9a9aa6', scarf: TEAL, dots: true, tip: true }
};
const FIPS = FOXSTYLES.fips;
// Stil: Papier-Optik (weiße Schnittkanten, Schlagschatten, Papierkörnung) oder klassisch (dunkler Umriss)
const STYLE = { paper: true };
const OL = () => STYLE.paper
  ? 'stroke="#fffaf0" stroke-width="1.3" stroke-linejoin="round"'
  : 'stroke="rgba(30,20,40,.32)" stroke-width=".7" stroke-linejoin="round"';
// Einteilige Silhouette: erst ein breiter Umriss, dann die Füllung ohne Innenlinien
const OUT = () => STYLE.paper
  ? 'stroke="#fffaf0" stroke-width="2.8" stroke-linejoin="round" stroke-linecap="round"'
  : 'stroke="rgba(30,20,40,.32)" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round"';
const sil = shapes => `<g ${OUT()}>${shapes}</g><g stroke="none">${shapes}</g>`;
const HOUSE_EDGE = () => STYLE.paper ? 'stroke="#fffaf0" stroke-width="1.6"' : 'stroke="rgba(30,20,40,.28)" stroke-width="1"';

function fox(o) {
  const fur = o.fur;
  const belly = '#fff4e0';
  let s = `<g ${OL()}>`;
  s += `<ellipse cx="16" cy="-24" rx="6" ry="14" transform="rotate(32 16 -24)" fill="${fur}"/>`;
  if (o.tip !== false) s += `<ellipse cx="22.5" cy="-35.5" rx="3.6" ry="4.6" transform="rotate(32 22.5 -35.5)" fill="#fff"/>`;
  s += `<rect x="-8" y="-9" width="5" height="9" rx="2" fill="#6b3a1d"/><rect x="3" y="-9" width="5" height="9" rx="2" fill="#6b3a1d"/>`;
  s += `<ellipse cx="0" cy="-20" rx="10" ry="13" fill="${fur}"/><ellipse cx="0" cy="-18" rx="6" ry="9" fill="${belly}" stroke="none"/>`;
  s += `<path d="M-9,-43 L-9,-57 L-1,-46Z" fill="${fur}"/><path d="M9,-43 L9,-57 L1,-46Z" fill="${fur}"/>`;
  s += `<path d="M-7.6,-46 L-7.6,-53 L-3.4,-47Z" fill="#5a3320" stroke="none"/><path d="M7.6,-46 L7.6,-53 L3.4,-47Z" fill="#5a3320" stroke="none"/>`;
  s += `<ellipse cx="0" cy="-39" rx="10.5" ry="8.5" fill="${fur}"/>`;
  s += `<path d="M-7,-38 Q0,-26 7,-38 Z" fill="${belly}" stroke="none"/><circle cx="0" cy="-32.5" r="1.9" fill="#222" stroke="none"/>`;
  s += `<circle cx="-4.2" cy="-40.5" r="1.5" fill="#222" stroke="none"/><circle cx="4.2" cy="-40.5" r="1.5" fill="#222" stroke="none"/>`;
  s += `<circle cx="-7" cy="-37" r="1.5" fill="rgba(255,120,120,.35)" stroke="none"/><circle cx="7" cy="-37" r="1.5" fill="rgba(255,120,120,.35)" stroke="none"/>`;
  if (o.scarf) {
    s += `<rect x="3" y="-30" width="5.5" height="12" rx="2.4" fill="${o.scarf}"/>`;
    s += `<rect x="-10.5" y="-33.5" width="21" height="6" rx="3" fill="${o.scarf}"/>`;
    if (o.dots) {
      s += `<g fill="#ffd84a" stroke="none"><circle cx="-6.5" cy="-30.5" r="1.3"/><circle cx="-1" cy="-30.5" r="1.3"/><circle cx="4.5" cy="-30.5" r="1.3"/>` +
           `<circle cx="5.7" cy="-25.5" r="1.2"/><circle cx="5.7" cy="-21" r="1.2"/></g>`;
    }
  }
  return `<ellipse cx="0" cy="1" rx="12" ry="3" fill="rgba(0,0,0,.13)"/>` + s + `</g>`;
}

function raccoon(cake) {
  const fur = '#8d8d99', dark = '#3a3a45';
  let s = `<ellipse cx="0" cy="1" rx="12" ry="3" fill="rgba(0,0,0,.13)"/><g ${OL()}>`;
  s += `<ellipse cx="16" cy="-22" rx="6" ry="14" transform="rotate(32 16 -22)" fill="${fur}"/>`;
  s += `<ellipse cx="13" cy="-17" rx="6" ry="2.4" transform="rotate(32 13 -17)" fill="${dark}" stroke="none"/>`;
  s += `<ellipse cx="18" cy="-27" rx="5.6" ry="2.4" transform="rotate(32 18 -27)" fill="${dark}" stroke="none"/>`;
  s += `<rect x="-8" y="-9" width="5" height="9" rx="2" fill="${dark}"/><rect x="3" y="-9" width="5" height="9" rx="2" fill="${dark}"/>`;
  s += `<ellipse cx="0" cy="-20" rx="10" ry="13" fill="${fur}"/><ellipse cx="0" cy="-18" rx="6" ry="9" fill="#cfcfd6" stroke="none"/>`;
  s += `<circle cx="-8" cy="-45" r="4" fill="${fur}"/><circle cx="8" cy="-45" r="4" fill="${fur}"/>`;
  s += `<ellipse cx="0" cy="-39" rx="10.5" ry="8.5" fill="#cfcfd6"/>`;
  s += `<path d="M-10.5,-42 Q0,-47 10.5,-42 L9,-37 Q0,-40 -9,-37 Z" fill="${dark}" stroke="none"/>`;
  s += `<circle cx="-4" cy="-40" r="1.6" fill="#fff" stroke="none"/><circle cx="4" cy="-40" r="1.6" fill="#fff" stroke="none"/>`;
  s += `<circle cx="-4" cy="-40" r=".8" fill="#000" stroke="none"/><circle cx="4" cy="-40" r=".8" fill="#000" stroke="none"/>`;
  s += `<ellipse cx="0" cy="-34" rx="2.4" ry="1.8" fill="${dark}" stroke="none"/>`;
  if (cake) {
    s += `<g transform="translate(-1 -17)"><rect x="-8" y="-6" width="16" height="9" rx="2" fill="#f7c6d9"/>` +
         `<rect x="-8" y="-9" width="16" height="4.5" rx="2.2" fill="#fff"/><circle cx="0" cy="-11.5" r="2.2" fill="#d9433b"/></g>`;
  }
  return s + `</g>`;
}

// ---------------------------------------------------------------- Menschen
// Alle 17 Zufallswerte werden IMMER in dieser Reihenfolge gezogen (auch wenn o sie überschreibt).
function makePerson(r, o) {
  o = o || {};
  const rb = r(), rBottom = r(), rShirt = r(), rSkin = r(), rHair = r(), rPants = r(), rShoes = r(),
        rStyle = r(), rHat = r(), rHatC = r(), rGl = r(), rBeard = r(), rPat = r(), rPatC = r(),
        rBag = r(), rProp = r(), rPropC = r();
  const body = o.body || (rb < 0.7 ? 'adult' : rb < 0.88 ? 'kid' : 'senior');
  const bottom = idx(['pants', 'pants', 'skirt', 'dress'], rBottom);
  let prop = rProp < 0.07 ? 'balloon' : rProp < 0.11 ? 'icecream' : rProp < 0.15 ? 'umbrella' : 'none';
  if (o.prop) prop = o.prop;
  return {
    body, bottom,
    shirt: idx(SHIRT, rShirt), skin: idx(SKIN, rSkin),
    hair: body === 'senior' ? idx(GREYS, rHair) : idx(HAIR, rHair),
    pants: idx(PANTS, rPants), shoes: idx(SHOES, rShoes),
    hairStyle: idx(HAIRSTYLES, rStyle),
    hat: rHat < 0.42 ? idx(HATS, rHat / 0.42) : 'none',
    hatColor: idx(SHIRT, rHatC),
    glasses: rGl < 0.14,
    beard: body === 'adult' && bottom === 'pants' && rBeard < 0.12,
    pattern: idx(['none', 'none', 'stripes', 'dots', 'jacket'], rPat),
    patternColor: idx(PATTERN, rPatC),
    bag: idx(['none', 'none', 'none', 'backpack', 'tote'], rBag),
    prop, propColor: idx(SHIRT, rPropC),
    pose: o.pose || 'stand', seat: o.seat || 'ground'
  };
}

function personSvg(d) {
  const k = d.body === 'kid' ? 0.74 : d.body === 'senior' ? 0.95 : 1;
  const sit = d.pose === 'sit';
  const seatLift = d.seat === 'bench' ? 9 : 0;
  const oy = sit ? 10 - seatLift : 0;
  const headR = d.body === 'kid' ? 8.4 : 7.5;
  const legCol = d.bottom === 'pants' ? d.pants : d.skin;
  let s = `<ellipse cx="0" cy="1" rx="11" ry="3" fill="rgba(0,0,0,.13)"/>`;
  s += `<g ${OL()} transform="scale(${k})">`;
  if (d.bag === 'backpack') s += `<g transform="translate(0 ${oy})"><rect x="3" y="-34" width="9" height="16" rx="3" fill="${d.propColor}"/></g>`;
  if (sit) {
    // Beine nach vorne ausgestreckt
    s += `<g transform="translate(0 ${-seatLift})"><rect x="-4" y="-7" width="18" height="5.6" rx="2.6" fill="${legCol}"/><ellipse cx="14.5" cy="-4.2" rx="2.6" ry="3.2" fill="${d.shoes}"/></g>`;
  } else if (d.bottom === 'pants') {
    s += `<rect x="-6.5" y="-12" width="5.5" height="12" rx="2" fill="${d.pants}"/><rect x="1" y="-12" width="5.5" height="12" rx="2" fill="${d.pants}"/>`;
    s += `<ellipse cx="-3.7" cy="-1" rx="3.4" ry="2" fill="${d.shoes}"/><ellipse cx="3.7" cy="-1" rx="3.4" ry="2" fill="${d.shoes}"/>`;
  } else {
    s += `<rect x="-5" y="-12" width="3" height="12" rx="1.4" fill="${d.skin}"/><rect x="2" y="-12" width="3" height="12" rx="1.4" fill="${d.skin}"/>`;
    s += `<ellipse cx="-3.5" cy="-1" rx="3.2" ry="2" fill="${d.shoes}"/><ellipse cx="3.5" cy="-1" rx="3.2" ry="2" fill="${d.shoes}"/>`;
  }
  s += `<g transform="translate(0 ${oy})">`;
  if (d.bottom === 'skirt' && !sit) s += `<path d="M-8,-22 L8,-22 L11,-9 L-11,-9Z" fill="${d.pants}"/>`;
  s += `<rect x="-11" y="-34" width="4.2" height="17" rx="2" fill="${d.shirt}"/><rect x="6.8" y="-34" width="4.2" height="17" rx="2" fill="${d.shirt}"/>`;
  if (d.bottom === 'dress') {
    s += `<path d="M-8,-34 Q-8,-36 -6,-36 L6,-36 Q8,-36 8,-34 L11,-9 L-11,-9Z" fill="${d.shirt}"/>`;
  } else {
    s += `<rect x="-8" y="-36" width="16" height="26" rx="6" fill="${d.shirt}"/>`;
  }
  if (d.bottom !== 'dress') {
    if (d.pattern === 'stripes') s += `<g fill="${d.patternColor}" stroke="none"><rect x="-7.6" y="-31" width="15.2" height="2.4"/><rect x="-7.6" y="-25" width="15.2" height="2.4"/><rect x="-7.6" y="-19" width="15.2" height="2.4"/></g>`;
    if (d.pattern === 'dots') s += `<g fill="${d.patternColor}" stroke="none"><circle cx="-4" cy="-30" r="1.5"/><circle cx="3" cy="-27" r="1.5"/><circle cx="-3" cy="-21" r="1.5"/><circle cx="4" cy="-17" r="1.5"/><circle cx="0" cy="-14" r="1.5"/></g>`;
    if (d.pattern === 'jacket') s += `<path d="M0,-36 V-10" stroke="${d.patternColor}" stroke-width="1.4" fill="none"/><path d="M-4.5,-36 L0,-29 L4.5,-36Z" fill="#ffffff" stroke="none"/>`;
  } else if (d.pattern === 'dots') {
    s += `<g fill="${d.patternColor}" stroke="none"><circle cx="-3" cy="-28" r="1.5"/><circle cx="3" cy="-22" r="1.5"/><circle cx="-4" cy="-15" r="1.5"/></g>`;
  }
  if (d.bag === 'tote') s += `<path d="M-7,-29 Q-13,-21 -10,-18" fill="none" stroke="#777777" stroke-width=".9"/><rect x="-15" y="-19" width="9" height="8" rx="1.5" fill="${d.propColor}"/>`;
  // Kopf
  s += `<circle cx="0" cy="-43.5" r="${headR}" fill="${d.skin}"/>`;
  const hs = d.hairStyle;
  const arc = `<path d="M${-headR},-43.5 A${headR},${headR} 0 0 1 ${headR},-43.5 Q0,-47 ${-headR},-43.5Z" fill="${d.hair}"/>`;
  if (hs === 'short') s += arc;
  if (hs === 'long') s += arc + `<rect x="${-headR - 1}" y="-44" width="3.4" height="14" rx="1.7" fill="${d.hair}"/><rect x="${headR - 2.4}" y="-44" width="3.4" height="14" rx="1.7" fill="${d.hair}"/>`;
  if (hs === 'bun') s += arc + `<circle cx="0" cy="-52.5" r="3.6" fill="${d.hair}"/>`;
  if (hs === 'curly') s += `<g fill="${d.hair}"><circle cx="-6" cy="-47" r="3.6"/><circle cx="-2.5" cy="-50.5" r="3.6"/><circle cx="2.5" cy="-50.5" r="3.6"/><circle cx="6" cy="-47" r="3.6"/><circle cx="0" cy="-47.5" r="4"/></g>`;
  if (hs === 'pony') s += arc + `<ellipse cx="9" cy="-41" rx="2.6" ry="6" transform="rotate(-15 9 -41)" fill="${d.hair}"/>`;
  s += `<circle cx="-2.6" cy="-42.5" r="1" fill="#222" stroke="none"/><circle cx="2.6" cy="-42.5" r="1" fill="#222" stroke="none"/>`;
  s += `<circle cx="-5" cy="-40" r="1.4" fill="rgba(255,110,110,.35)" stroke="none"/><circle cx="5" cy="-40" r="1.4" fill="rgba(255,110,110,.35)" stroke="none"/>`;
  s += `<path d="M-2.2,-39.8 Q0,-38 2.2,-39.8" fill="none" stroke="#7a3b2a" stroke-width=".7" stroke-linecap="round"/>`;
  if (d.beard) s += `<path d="M-6.4,-41 Q0,-30 6.4,-41 Q0,-36 -6.4,-41Z" fill="${d.hair}" stroke="none"/>`;
  if (d.glasses) s += `<g fill="none" stroke="#333333" stroke-width=".8"><circle cx="-2.9" cy="-42.5" r="2.3"/><circle cx="2.9" cy="-42.5" r="2.3"/><path d="M-0.6,-42.5 h1.2"/></g>`;
  const hc = d.hatColor;
  if (d.hat === 'cap') s += `<path d="M-8,-45.5 A8,8 0 0 1 8,-45.5Z" fill="${hc}"/><rect x="2" y="-47" width="9.5" height="2.6" rx="1.2" fill="${hc}"/>`;
  if (d.hat === 'beanie') s += `<path d="M-8,-45 A8,9.5 0 0 1 8,-45Z" fill="${hc}"/><circle cx="0" cy="-54" r="2.2" fill="#ffffff"/>`;
  if (d.hat === 'top') s += `<rect x="-9.5" y="-50" width="19" height="3" rx="1.5" fill="#222222"/><rect x="-6" y="-60" width="12" height="11" rx="1.5" fill="#222222"/><rect x="-6" y="-53" width="12" height="2.6" fill="${hc}" stroke="none"/>`;
  if (d.hat === 'sun') s += `<ellipse cx="0" cy="-47.5" rx="13" ry="3.2" fill="${hc}"/><path d="M-7,-48 A7,7 0 0 1 7,-48Z" fill="${hc}"/>`;
  if (d.hat === 'band') s += `<rect x="-7.6" y="-48.4" width="15.2" height="2.6" rx="1.3" fill="${hc}" stroke="none"/>`;
  // Requisiten
  const pc = d.propColor;
  if (d.prop === 'balloon') s += `<path d="M11,-27 Q18,-50 14,-72" stroke="#777777" stroke-width=".8" fill="none"/><ellipse cx="14" cy="-80" rx="7" ry="9" fill="${pc}"/>`;
  if (d.prop === 'icecream') s += `<path d="M10,-26 L13,-18 L16,-26Z" fill="#e8b46a"/><circle cx="13" cy="-29" r="3.6" fill="#f7c6d9"/><circle cx="13" cy="-33.2" r="3" fill="#fff4e0"/>`;
  if (d.prop === 'umbrella') s += `<line x1="12" y1="-34" x2="12" y2="-62" stroke="#555555" stroke-width="1"/><path d="M-6,-62 Q12,-84 30,-62 Q21,-66 12,-62 Q3,-66 -6,-62Z" fill="${pc}"/>`;
  if (d.prop === 'guitar') s += `<g transform="translate(9 -22) rotate(-30)"><ellipse cx="0" cy="0" rx="7" ry="5.5" fill="#b9792f"/><circle cx="-1" cy="0" r="2" fill="#4a2f17" stroke="none"/><rect x="5" y="-1.2" width="14" height="2.4" fill="#6b4423"/></g>`;
  if (d.prop === 'camera') s += `<rect x="6" y="-41" width="9" height="6.5" rx="1.4" fill="#444444"/><circle cx="10.5" cy="-37.8" r="2.2" fill="#99aadd"/>`;
  if (d.body === 'senior' && d.prop === 'none') s += `<path d="M12,-26 V-3 q0,-3 -3,-3" stroke="#6b4a2b" fill="none" stroke-width="1.6" stroke-linecap="round"/>`;
  s += `</g></g>`;
  return s;
}

// ---------------------------------------------------------------- Tiere
function makeDog(r) {
  const rb = r(), rc = r();
  return { breed: idx(['lab', 'dachs', 'spot', 'poodle'], rb), color: idx(DOGC, rc) };
}
function dogSvg(d) {
  const c = d.color;
  const sh = `<ellipse cx="0" cy="1" rx="15" ry="3" fill="rgba(0,0,0,.13)"/>`;
  const nose = (x, y) => `<ellipse cx="${x}" cy="${y}" rx="1.7" ry="1.3" fill="#1c1418" stroke="none"/>`;
  const eye = (x, y) => `<circle cx="${x}" cy="${y}" r="1.2" fill="#111" stroke="none"/>`;
  if (d.breed === 'lab') {
    const body = `<ellipse cx="19" cy="-24" rx="2.6" ry="7.5" transform="rotate(35 19 -24)" fill="${c}"/>` +
      `<rect x="-13" y="-10" width="5" height="10" rx="2.4" fill="${c}"/><rect x="-5" y="-10" width="5" height="10" rx="2.4" fill="${c}"/>` +
      `<rect x="6" y="-10" width="5" height="10" rx="2.4" fill="${c}"/><rect x="13" y="-10" width="5" height="10" rx="2.4" fill="${c}"/>` +
      `<ellipse cx="3" cy="-16" rx="16" ry="8" fill="${c}"/><ellipse cx="-11" cy="-21" rx="7" ry="8" transform="rotate(-25 -11 -21)" fill="${c}"/>` +
      `<ellipse cx="-15" cy="-26" rx="7.5" ry="6.5" fill="${c}"/><ellipse cx="-22" cy="-23.5" rx="5.5" ry="3.8" fill="${c}"/>`;
    return sh + sil(body) +
      `<ellipse cx="-22" cy="-22.6" rx="4.6" ry="2.8" fill="#f3e6d3" stroke="none"/>` + nose(-25.5, -24.2) + eye(-16.5, -27.5) +
      `<ellipse cx="-11" cy="-25" rx="3.2" ry="6" transform="rotate(14 -11 -25)" fill="rgba(40,20,5,.45)" stroke="none"/>`;
  }
  if (d.breed === 'dachs') {
    const body = `<ellipse cx="25" cy="-16" rx="2.4" ry="6.5" transform="rotate(40 25 -16)" fill="${c}"/>` +
      `<rect x="-18" y="-7" width="5" height="7" rx="2.4" fill="${c}"/><rect x="-9" y="-7" width="5" height="7" rx="2.4" fill="${c}"/>` +
      `<rect x="10" y="-7" width="5" height="7" rx="2.4" fill="${c}"/><rect x="18" y="-7" width="5" height="7" rx="2.4" fill="${c}"/>` +
      `<ellipse cx="3" cy="-12" rx="22" ry="6.5" fill="${c}"/><ellipse cx="-17" cy="-15" rx="6" ry="7" fill="${c}"/>` +
      `<ellipse cx="-20" cy="-18" rx="6.8" ry="6" fill="${c}"/><ellipse cx="-27" cy="-16" rx="6" ry="3.2" fill="${c}"/>`;
    return sh + sil(body) +
      nose(-32, -17.2) + eye(-21.5, -19.5) +
      `<ellipse cx="-17" cy="-15.5" rx="3" ry="6" transform="rotate(10 -17 -15.5)" fill="rgba(40,20,5,.5)" stroke="none"/>`;
  }
  if (d.breed === 'spot') {
    const w = '#f5f1e8';
    const body = `<ellipse cx="19" cy="-24" rx="2.6" ry="7.5" transform="rotate(35 19 -24)" fill="${w}"/>` +
      `<rect x="-13" y="-10" width="5" height="10" rx="2.4" fill="${w}"/><rect x="-5" y="-10" width="5" height="10" rx="2.4" fill="${w}"/>` +
      `<rect x="6" y="-10" width="5" height="10" rx="2.4" fill="${w}"/><rect x="13" y="-10" width="5" height="10" rx="2.4" fill="${w}"/>` +
      `<ellipse cx="3" cy="-16" rx="16" ry="8" fill="${w}"/><ellipse cx="-11" cy="-21" rx="7" ry="8" transform="rotate(-25 -11 -21)" fill="${w}"/>` +
      `<ellipse cx="-15" cy="-26" rx="7.5" ry="6.5" fill="${w}"/><ellipse cx="-22" cy="-23.5" rx="5.5" ry="3.8" fill="${w}"/>`;
    return sh + sil(body) +
      `<g fill="${c}" stroke="none"><ellipse cx="2" cy="-18" rx="5.5" ry="4"/><ellipse cx="11" cy="-14" rx="4" ry="3.2"/><ellipse cx="-17.5" cy="-28.5" rx="3.2" ry="3"/></g>` +
      nose(-25.5, -24.2) + eye(-16.5, -27.5) +
      `<ellipse cx="-11" cy="-25" rx="3.2" ry="6" transform="rotate(14 -11 -25)" fill="${c}" stroke="none"/>`;
  }
  // poodle: Locken-Puschel
  const body = `<rect x="-8" y="-10" width="2.8" height="10" fill="${c}"/><rect x="-2" y="-10" width="2.8" height="10" fill="${c}"/>` +
    `<rect x="5" y="-10" width="2.8" height="10" fill="${c}"/><rect x="11" y="-10" width="2.8" height="10" fill="${c}"/>` +
    `<circle cx="-7" cy="-5" r="3.8" fill="${c}"/><circle cx="12" cy="-5" r="3.8" fill="${c}"/>` +
    `<ellipse cx="3" cy="-16" rx="13" ry="7.5" fill="${c}"/><circle cx="-8" cy="-20" r="7" fill="${c}"/>` +
    `<circle cx="-13" cy="-26" r="6" fill="${c}"/><circle cx="-13" cy="-32.5" r="3.8" fill="${c}"/>` +
    `<circle cx="-7" cy="-26" r="3.8" fill="${c}"/><ellipse cx="-20" cy="-24" rx="4.4" ry="3" fill="${c}"/>` +
    `<rect x="14" y="-22" width="2" height="8" transform="rotate(25 14 -14)" fill="${c}"/><circle cx="19" cy="-24" r="4" fill="${c}"/>`;
  return sh + sil(body) + nose(-23.5, -24.4) + eye(-14.5, -26.5);
}

function makeCat(r) {
  const rb = r(), rc = r(), rs = r();
  return { pose: rb < 0.5 ? 'stand' : 'sit', color: idx(CATC, rc), stripes: rs < 0.4 };
}
function catSvg(d) {
  const c = d.color;
  const sh = `<ellipse cx="0" cy="1" rx="11" ry="3" fill="rgba(0,0,0,.13)"/>`;
  const eyes = (x1, x2, y) => `<g fill="#7fcf5a" stroke="none"><ellipse cx="${x1}" cy="${y}" rx="1.1" ry="1.3"/><ellipse cx="${x2}" cy="${y}" rx="1.1" ry="1.3"/></g>` +
    `<g fill="#111" stroke="none"><ellipse cx="${x1}" cy="${y}" rx=".4" ry="1"/><ellipse cx="${x2}" cy="${y}" rx=".4" ry="1"/></g>`;
  if (d.pose === 'stand') {
    const body = `<ellipse cx="19" cy="-19" rx="2.4" ry="10" transform="rotate(28 19 -19)" fill="${c}"/>` +
      `<rect x="-9" y="-8" width="4" height="8" rx="2" fill="${c}"/><rect x="-3" y="-8" width="4" height="8" rx="2" fill="${c}"/>` +
      `<rect x="6" y="-8" width="4" height="8" rx="2" fill="${c}"/><rect x="11" y="-8" width="4" height="8" rx="2" fill="${c}"/>` +
      `<ellipse cx="2" cy="-12" rx="13" ry="6.5" fill="${c}"/><ellipse cx="-9" cy="-16" rx="5" ry="6" fill="${c}"/>` +
      `<circle cx="-12" cy="-19" r="6" fill="${c}"/>` +
      `<path d="M-17,-22 L-16,-29 L-11,-24Z M-8,-24 L-6,-30 L-4,-22Z" fill="${c}"/>`;
    let s = sh + sil(body);
    if (d.stripes) s += `<path d="M-3,-17 v6 M2,-18 v7 M7,-17 v6" stroke="rgba(0,0,0,.3)" stroke-width="1.3" fill="none"/>`;
    return s + eyes(-14.4, -9.8, -19.4) + `<path d="M-12.8,-16.6 l1.4,1.2 l1.4,-1.2Z" fill="#e48a8a" stroke="none"/>`;
  }
  const body = `<ellipse cx="11" cy="-4" rx="8" ry="2.8" transform="rotate(-12 11 -4)" fill="${c}"/><circle cx="17" cy="-8" r="2.6" fill="${c}"/>` +
    `<ellipse cx="0" cy="-11" rx="8.8" ry="11" fill="${c}"/><circle cx="0" cy="-26" r="6.8" fill="${c}"/>` +
    `<path d="M-6.8,-29.5 L-5.8,-37 L-1.4,-31Z M6.8,-29.5 L5.8,-37 L1.4,-31Z" fill="${c}"/>` +
    `<ellipse cx="-3.8" cy="-1.5" rx="3.2" ry="2" fill="${c}"/><ellipse cx="3.8" cy="-1.5" rx="3.2" ry="2" fill="${c}"/>`;
  let s = sh + sil(body);
  if (d.stripes) s += `<path d="M-4,-17 v7 M0,-18 v8 M4,-17 v7" stroke="rgba(0,0,0,.3)" stroke-width="1.3" fill="none"/>`;
  return s + eyes(-2.7, 2.7, -26.4) + `<path d="M-1.2,-23.6 l1.2,1.1 l1.2,-1.1Z" fill="#e48a8a" stroke="none"/>`;
}

const pigeonSvg = () =>
  `<ellipse cx="0" cy="1" rx="8" ry="2" fill="rgba(0,0,0,.12)"/><g ${OL()}>` +
  `<ellipse cx="0" cy="-6" rx="7" ry="5" fill="#9aa0ad"/><circle cx="-6" cy="-10" r="3.2" fill="#7f8594"/>` +
  `<path d="M-9,-10 l-3,1 l3,1Z" fill="#e8a33c"/><path d="M5,-5 l8,2 l-8,2Z" fill="#7f8594"/>` +
  `<path d="M-2,0 v3 M2,0 v3" stroke="#e8a33c" stroke-width="1"/></g>`;

const duckSvg = () =>
  `<ellipse cx="0" cy="1" rx="11" ry="3" fill="rgba(255,255,255,.35)"/><g ${OL()}>` +
  `<ellipse cx="0" cy="-5" rx="9" ry="5.5" fill="#ffffff"/><path d="M6,-7 Q12,-12 10,-3Z" fill="#eeeeee"/>` +
  `<circle cx="-7" cy="-11" r="4" fill="#2f8f46"/><path d="M-11,-11 l-5,1.5 l5,1.5Z" fill="#f2a22e"/>` +
  `<circle cx="-8" cy="-12" r=".8" fill="#111" stroke="none"/></g>`;

// ---------------------------------------------------------------- Bäume, Stände, Häuser
function makeTree(r) {
  const rt = r(), rc = r();
  const type = idx(['round', 'round', 'pine', 'birch', 'blossom', 'apple'], rt);
  const color = type === 'blossom' ? idx(PINKS, rc) : idx(GREENS, rc);
  return { type, color, wide: type === 'round' || type === 'blossom' || type === 'apple' };
}
function treeSvg(d) {
  const c = d.color;
  let s = `<ellipse cx="0" cy="2" rx="20" ry="5" fill="rgba(0,0,0,.14)"/><g ${OL()}>`;
  if (d.type === 'pine') {
    s += `<rect x="-3.5" y="-22" width="7" height="22" rx="2" fill="#7a4e2d"/>` +
      `<polygon points="-22,-18 0,-52 22,-18" fill="#2f7d4a"/><polygon points="-18,-42 0,-76 18,-42" fill="#3b9158"/><polygon points="-13,-66 0,-96 13,-66" fill="#48a566"/>`;
    return s + `</g>`;
  }
  if (d.type === 'birch') {
    s += `<rect x="-3.5" y="-40" width="7" height="40" rx="2" fill="#f1efe8"/><g fill="#555555" stroke="none"><rect x="-3.5" y="-30" width="4" height="1.6"/><rect x="0" y="-20" width="3.5" height="1.6"/><rect x="-3.5" y="-10" width="3" height="1.6"/></g>` +
      `<ellipse cx="0" cy="-64" rx="20" ry="30" fill="#8fd16a"/><ellipse cx="-6" cy="-72" rx="7" ry="10" fill="rgba(255,255,255,.18)" stroke="none"/>`;
    return s + `</g>`;
  }
  s += `<rect x="-4" y="-34" width="8" height="34" rx="3" fill="#7a4e2d"/>` +
    `<circle cx="-20" cy="-48" r="18" fill="${c}"/><circle cx="20" cy="-48" r="18" fill="${c}"/>` +
    `<circle cx="0" cy="-62" r="26" fill="${c}"/><circle cx="-8" cy="-68" r="9" fill="rgba(255,255,255,.16)" stroke="none"/>` +
    `<ellipse cx="0" cy="-38" rx="30" ry="6" fill="rgba(0,0,0,.07)" stroke="none"/>`;
  if (d.type === 'apple') s += `<g fill="#e05a5a" stroke="none"><circle cx="-14" cy="-52" r="2.6"/><circle cx="10" cy="-70" r="2.6"/><circle cx="18" cy="-48" r="2.6"/><circle cx="-4" cy="-58" r="2.6"/><circle cx="-24" cy="-44" r="2.6"/></g>`;
  if (d.type === 'blossom') s += `<g fill="#ffffff" stroke="none" opacity=".75"><circle cx="-14" cy="-52" r="2"/><circle cx="8" cy="-72" r="2"/><circle cx="20" cy="-46" r="2"/><circle cx="-6" cy="-60" r="2"/></g>`;
  return s + `</g>`;
}

function makeStall(r) {
  const rc = r(), rg = r();
  return { color: idx(STALLC, rc), goods: idx(['fruit', 'veg', 'flowers', 'bread', 'fish'], rg) };
}
function stallSvg(d) {
  const col = d.color;
  let stripes = '';
  for (let i = 0; i < 6; i++) stripes += `<rect x="${-45 + i * 15}" y="-62" width="15" height="18" fill="${i % 2 ? '#ffffff' : col}" stroke="none"/>`;
  let goods = '';
  if (d.goods === 'fruit') goods = `<circle cx="-24" cy="-28" r="4.5" fill="#e45b4b"/><circle cx="-12" cy="-28" r="4.5" fill="#f2b84b"/><circle cx="2" cy="-28" r="4.5" fill="#7bc47f"/><circle cx="16" cy="-28" r="4.5" fill="#e45b4b"/><circle cx="29" cy="-28" r="4.5" fill="#f2b84b"/>`;
  if (d.goods === 'veg') goods = `<g stroke="none"><path d="M-30,-24 l4,-12 l4,12Z" fill="#f08a2c"/><path d="M-20,-24 l4,-12 l4,12Z" fill="#f08a2c"/><circle cx="-4" cy="-29" r="5" fill="#7bc47f"/><circle cx="8" cy="-29" r="5" fill="#a56bd6"/><circle cx="20" cy="-29" r="5" fill="#7bc47f"/><circle cx="31" cy="-28" r="4" fill="#e05a5a"/></g>`;
  if (d.goods === 'flowers') goods = `<g stroke="none"><rect x="-32" y="-30" width="14" height="8" rx="2" fill="#b9792f"/><rect x="-8" y="-30" width="14" height="8" rx="2" fill="#b9792f"/><rect x="16" y="-30" width="14" height="8" rx="2" fill="#b9792f"/>` +
    `<circle cx="-29" cy="-34" r="3" fill="#ff9fc2"/><circle cx="-23" cy="-35" r="3" fill="#ffd84a"/><circle cx="-5" cy="-34" r="3" fill="#ffffff"/><circle cx="1" cy="-35" r="3" fill="#c9a0ff"/><circle cx="19" cy="-34" r="3" fill="#e05a5a"/><circle cx="25" cy="-35" r="3" fill="#ff9fc2"/></g>`;
  if (d.goods === 'bread') goods = `<g stroke="none"><ellipse cx="-24" cy="-28" rx="7" ry="4" fill="#d79a4a"/><ellipse cx="-8" cy="-28" rx="7" ry="4" fill="#c98636"/><ellipse cx="8" cy="-28" rx="7" ry="4" fill="#d79a4a"/><ellipse cx="25" cy="-28" rx="7" ry="4" fill="#c98636"/></g>`;
  if (d.goods === 'fish') goods = `<g stroke="none" fill="#9fb4c7"><ellipse cx="-22" cy="-28" rx="8" ry="3.6"/><path d="M-14,-28 l5,-4 v8Z"/><ellipse cx="2" cy="-27" rx="8" ry="3.6"/><path d="M10,-27 l5,-4 v8Z"/><ellipse cx="25" cy="-28" rx="6" ry="3"/></g>`;
  return `<ellipse cx="0" cy="4" rx="52" ry="7" fill="rgba(0,0,0,.12)"/><g ${OL()}>` +
    `<rect x="-42" y="-44" width="5" height="44" fill="#8a6a45"/><rect x="37" y="-44" width="5" height="44" fill="#8a6a45"/>` +
    `<rect x="-42" y="-24" width="84" height="24" rx="3" fill="#c99a62"/>${stripes}` +
    `<path d="M-45,-44 q7.5,10 15,0 q7.5,10 15,0 q7.5,10 15,0 q7.5,10 15,0 q7.5,10 15,0 q7.5,10 15,0 Z" fill="${col}"/>${goods}</g>`;
}

function makeHouse(r, x, baseY, w) {
  const rh = r(), rw = r(), rr = r(), rt = r(), rch = r(), rsh = r(), rpb = r(), rshop = r(), rdoor = r(), rshc = r();
  const h = 150 + Math.floor(rh * 3) * 25;
  const floors = h > 150 ? 2 : 1;
  const curtains = [];
  for (let i = 0; i < floors * 2; i++) { const a = r(), b = r(); curtains.push(a < 0.4 ? idx(SHIRT, b) : ''); }
  return {
    x, baseY, w, h, wall: idx(WALLS, rw), roof: idx(ROOFS, rr),
    roofType: idx(['gable', 'gable', 'flat', 'steep'], rt),
    chimney: rch < 0.5, shutters: rsh < 0.4, plantBox: rpb < 0.35,
    shop: rshop < 0.3 ? idx(['cafe', 'flowers', 'books'], rshop / 0.3) : '',
    door: idx(DOORC, rdoor), shutterColor: idx(SHUTTER, rshc), curtains, bakery: false
  };
}
const SHOPS = {
  cafe:    { text: 'Café', color: '#4a7fd6' },
  flowers: { text: 'Blumen', color: '#7bc47f' },
  books:   { text: 'Bücher', color: '#a56bd6' },
  bakery:  { text: 'Bäckerei', color: '#e45b4b' }
};
function houseSvg(d, mood) {
  const { x, baseY: by, w, h } = d;
  const top = by - h;
  const floors = h > 150 ? 2 : 1;
  const winFill = mood === 'evening' ? '#ffe9a8' : '#bfe6f5';
  let s = `<g ${HOUSE_EDGE()} stroke-linejoin="round">`;
  s += `<rect x="${x}" y="${top}" width="${w}" height="${h}" fill="${d.wall}"/>`;
  s += `<rect x="${x + w - 16}" y="${top}" width="16" height="${h}" fill="rgba(0,0,0,.07)" stroke="none"/>`;
  if (d.roofType === 'flat') {
    s += `<rect x="${x - 5}" y="${top - 14}" width="${w + 10}" height="14" rx="2" fill="${d.roof}"/>`;
    s += `<rect x="${x + 10}" y="${top - 24}" width="18" height="10" fill="#cfd3d8"/>`;
  } else {
    const apex = d.roofType === 'steep' ? 66 : 46;
    const ov = d.roofType === 'steep' ? 2 : 7;
    s += `<polygon points="${x - ov},${top} ${x + w / 2},${top - apex} ${x + w + ov},${top}" fill="${d.roof}"/>`;
    for (const f of [0.28, 0.55, 0.8]) {
      const half = (w / 2 + ov) * (1 - f);
      s += `<path d="M${f1(x + w / 2 - half)},${f1(top - apex * f)} H${f1(x + w / 2 + half)}" stroke="rgba(0,0,0,.16)" fill="none"/>`;
    }
    if (d.chimney) s += `<rect x="${x + w * 0.68}" y="${top - 40}" width="12" height="22" fill="#9c5b45"/><rect x="${x + w * 0.68 - 2}" y="${top - 43}" width="16" height="4" fill="#7e4634"/>`;
  }
  const hasShop = d.bakery || d.shop;
  for (let f = 0; f < floors; f++) {
    if (hasShop && floors === 2 && f === 1) continue;   // Ladenschild statt Erdgeschoss-Fenster
    for (let c = 0; c < 2; c++) {
      const wx = x + 16 + c * (w - 32 - 24), wy = top + 16 + f * 56;
      if (d.shutters) s += `<rect x="${wx - 7}" y="${wy}" width="6" height="28" fill="${d.shutterColor}"/><rect x="${wx + 25}" y="${wy}" width="6" height="28" fill="${d.shutterColor}"/>`;
      s += `<rect x="${wx}" y="${wy}" width="24" height="28" rx="3" fill="${winFill}" stroke="#ffffff" stroke-width="3"/>` +
           `<path d="M${wx + 12},${wy} v28 M${wx},${wy + 14} h24" stroke="#ffffff" stroke-width="2" fill="none"/>`;
      const cur = d.curtains[f * 2 + c];
      if (cur) s += `<rect x="${wx + 2}" y="${wy + 2}" width="9" height="24" fill="${cur}" opacity=".8" stroke="none"/>`;
      if (d.plantBox && f === floors - 1 && !d.bakery && !d.shop) s += `<rect x="${wx - 2}" y="${wy + 28}" width="28" height="6" rx="1.5" fill="#8a5a3b"/><g stroke="none"><circle cx="${wx + 4}" cy="${wy + 27}" r="3.4" fill="#5bbf5a"/><circle cx="${wx + 12}" cy="${wy + 26}" r="3.4" fill="#e05a5a"/><circle cx="${wx + 20}" cy="${wy + 27}" r="3.4" fill="#5bbf5a"/></g>`;
    }
  }
  s += `<rect x="${x + w / 2 - 13}" y="${by - 42}" width="26" height="42" rx="3" fill="${d.bakery ? '#7a4e2d' : d.door}"/><circle cx="${x + w / 2 + 7}" cy="${by - 20}" r="2" fill="#ffd84a" stroke="none"/>`;
  const shop = d.bakery ? SHOPS.bakery : d.shop ? SHOPS[d.shop] : null;
  if (shop) {
    let aw = '';
    for (let i = 0; i < 8; i++) aw += `<rect x="${x + i * (w / 8)}" y="${by - 62}" width="${w / 8}" height="16" fill="${i % 2 ? '#ffffff' : shop.color}" stroke="none"/>`;
    s += aw + `<path d="M${x},${by - 46} q${w / 16},8 ${w / 8},0 q${w / 16},8 ${w / 8},0 q${w / 16},8 ${w / 8},0 q${w / 16},8 ${w / 8},0 q${w / 16},8 ${w / 8},0 q${w / 16},8 ${w / 8},0 q${w / 16},8 ${w / 8},0 q${w / 16},8 ${w / 8},0Z" fill="${shop.color}" stroke="none"/>`;
    s += `<rect x="${x + 6}" y="${top + 66}" width="${w - 12}" height="22" rx="4" fill="#fff6dc" stroke="#c99a62" stroke-width="2"/>` +
         `<text x="${x + w / 2}" y="${top + 82}" text-anchor="middle" font-size="14" font-weight="700" font-family="sans-serif" fill="#8a4b1c" stroke="none">${shop.text}</text>`;
    if (d.shop === 'flowers') s += `<g stroke="none"><rect x="${x + 6}" y="${by - 10}" width="12" height="10" fill="#b9792f"/><circle cx="${x + 12}" cy="${by - 14}" r="5" fill="#ff9fc2"/><rect x="${x + w - 18}" y="${by - 10}" width="12" height="10" fill="#b9792f"/><circle cx="${x + w - 12}" cy="${by - 14}" r="5" fill="#ffd84a"/></g>`;
    if (d.shop === 'cafe') s += `<g stroke="none"><circle cx="${x + 14}" cy="${by - 8}" r="7" fill="#ffffff"/><rect x="${x + 13}" y="${by - 8}" width="2" height="8" fill="#777777"/><circle cx="${x + w - 14}" cy="${by - 8}" r="7" fill="#ffffff"/><rect x="${x + w - 15}" y="${by - 8}" width="2" height="8" fill="#777777"/></g>`;
  }
  return s + `</g>`;
}

// ---------------------------------------------------------------- Straßenmöbel & Gruppen-Requisiten
// type: lamp, bench, bush, blanket, basket, guitarcase, cart, leash, easel, ball, rod, bucket, notes
function propSvg(d, mood) {
  const c = d.color, c2 = d.color2;
  let s = '';
  switch (d.type) {
    case 'lamp':
      s += `<ellipse cx="0" cy="1" rx="6" ry="2" fill="rgba(0,0,0,.15)"/>`;
      if (mood === 'evening') s += `<circle cx="0" cy="-60" r="22" fill="rgba(255,220,120,.30)"/>`;
      s += `<g ${OL()}><rect x="-2" y="-58" width="4" height="58" fill="#4a4f57"/><rect x="-5" y="-4" width="10" height="4" rx="1" fill="#3a3f47"/>` +
           `<path d="M-6,-58 h12 l-2,-8 h-8Z" fill="#3a3f47"/><circle cx="0" cy="-62" r="4" fill="${mood === 'evening' ? '#ffe9a8' : '#fff3c4'}"/></g>`;
      break;
    case 'bench':
      s += `<ellipse cx="0" cy="1" rx="24" ry="3" fill="rgba(0,0,0,.13)"/><g ${OL()}>` +
           `<rect x="-20" y="-22" width="40" height="5" rx="1.5" fill="#a9733a"/><rect x="-20" y="-16" width="40" height="3" fill="#8a5a2a"/>` +
           `<rect x="-21" y="-12" width="42" height="5" rx="1.5" fill="#b9823f"/><rect x="-18" y="-7" width="3" height="7" fill="#4a4f57"/><rect x="15" y="-7" width="3" height="7" fill="#4a4f57"/></g>`;
      break;
    case 'bush':
      s += `<ellipse cx="0" cy="2" rx="16" ry="4" fill="rgba(0,0,0,.13)"/><g ${OL()}><circle cx="-9" cy="-8" r="9" fill="${c}"/><circle cx="9" cy="-8" r="9" fill="${c}"/><circle cx="0" cy="-13" r="10" fill="${c}"/></g>` +
           `<g fill="${c2}" stroke="none"><circle cx="-6" cy="-10" r="1.8"/><circle cx="5" cy="-14" r="1.8"/><circle cx="10" cy="-7" r="1.8"/></g>`;
      break;
    case 'blanket': {
      let cells = '';
      for (let i = 0; i < 4; i++) for (let j = 0; j < 3; j++) if ((i + j) % 2 === 0) cells += `<rect x="${-48 + i * 24}" y="${-40 + j * 14.7}" width="24" height="14.7" fill="${c2}" stroke="none"/>`;
      s += `<g ${OL()}><rect x="-48" y="-40" width="96" height="44" rx="3" fill="${c}"/>${cells}</g>`;
      break;
    }
    case 'basket':
      s += `<g ${OL()}><path d="M-9,-9 h18 l-2,9 h-14Z" fill="#b9792f"/><path d="M-7,-9 q7,-13 14,0" stroke="#8a5a2a" fill="none" stroke-width="1.4"/><rect x="-8" y="-11" width="16" height="3" rx="1" fill="#e05a5a"/></g>`;
      break;
    case 'guitarcase':
      s += `<g ${OL()}><rect x="-14" y="-7" width="28" height="9" rx="3" fill="#3a3f47"/><rect x="-11" y="-5" width="22" height="5" rx="2" fill="#7a2f3a"/></g>` +
           `<g fill="#ffd84a" stroke="none"><circle cx="-5" cy="-2" r="1.6"/><circle cx="1" cy="-3" r="1.6"/><circle cx="6" cy="-2" r="1.6"/></g>`;
      break;
    case 'cart': {
      let can = '';
      for (let i = 0; i < 6; i++) can += `<path d="M${-34 + i * 11.3},-66 q${5.65},-16 ${11.3},0Z" fill="${i % 2 ? '#ffffff' : c}" stroke="none"/>`;
      s += `<ellipse cx="0" cy="3" rx="34" ry="5" fill="rgba(0,0,0,.13)"/><g ${OL()}>` +
           `<rect x="-1.5" y="-70" width="3" height="40" fill="#8a6a45"/>` + can +
           `<rect x="-28" y="-30" width="56" height="26" rx="4" fill="#fff4e0"/><rect x="-28" y="-30" width="56" height="8" rx="4" fill="${c}"/>` +
           `<circle cx="-18" cy="-2" r="5" fill="#555555"/><circle cx="18" cy="-2" r="5" fill="#555555"/>` +
           `<circle cx="-12" cy="-35" r="5" fill="#f7c6d9"/><circle cx="0" cy="-36" r="5" fill="#fff4e0"/><circle cx="12" cy="-35" r="5" fill="#a9733a"/></g>`;
      break;
    }
    case 'leash':
      s += `<path d="M0,0 Q${d.dx * 0.5},${d.dy + 8} ${d.dx},${d.dy}" stroke="#a02f3a" stroke-width="1.1" fill="none"/>`;
      break;
    case 'easel':
      s += `<ellipse cx="0" cy="2" rx="14" ry="3" fill="rgba(0,0,0,.12)"/><g ${OL()}><path d="M-10,0 L-3,-44 M10,0 L3,-44 M0,0 L0,-30" stroke="#8a6a45" stroke-width="2" fill="none"/>` +
           `<rect x="-13" y="-52" width="26" height="22" fill="#ffffff"/></g>` +
           `<g stroke="none"><circle cx="-6" cy="-45" r="3.4" fill="#e05a5a"/><circle cx="2" cy="-40" r="3.4" fill="#4a7fd6"/><circle cx="6" cy="-47" r="2.6" fill="#ffd84a"/><path d="M-10,-34 q8,-6 20,0" stroke="#7bc47f" stroke-width="2.2" fill="none"/></g>`;
      break;
    case 'ball':
      s += `<ellipse cx="0" cy="1" rx="6" ry="2" fill="rgba(0,0,0,.18)"/><g ${OL()}><circle cx="0" cy="${-d.lift - 5}" r="5" fill="#ffffff"/><path d="M-5,${-d.lift - 5} a5,5 0 0 1 10,0Z" fill="#e05a5a"/></g>`;
      break;
    case 'rod':
      s += `<path d="M0,0 L${d.dx * 0.45},-30" stroke="#6b4a2b" stroke-width="1.6" stroke-linecap="round" fill="none"/>` +
           `<path d="M${d.dx * 0.45},-30 L${d.dx},${d.dy}" stroke="#999999" stroke-width=".7" fill="none"/>` +
           `<g ${OL()}><circle cx="${d.dx}" cy="${d.dy}" r="2.6" fill="#e05a5a"/></g>`;
      break;
    case 'bucket':
      s += `<g ${OL()}><path d="M-6,-9 h12 l-1.5,9 h-9Z" fill="#4a7fd6"/><path d="M-5,-9 q5,-7 10,0" stroke="#777777" fill="none"/></g><ellipse cx="0" cy="-9" rx="6" ry="1.6" fill="#9fd8f0" stroke="none"/>`;
      break;
    case 'notes':
      s += `<g fill="#333333" stroke="none"><ellipse cx="0" cy="0" rx="3" ry="2.2"/><rect x="2" y="-12" width="1.2" height="12"/><path d="M3.2,-12 q5,1 3,5"/>` +
           `<ellipse cx="14" cy="-8" rx="3" ry="2.2"/><rect x="16" y="-20" width="1.2" height="12"/><path d="M17.2,-20 q5,1 3,5"/></g>`;
      break;
  }
  return s;
}

// ---------------------------------------------------------------- Zeichnen eines Objekts
function drawableSvg(d, mood) {
  let inner;
  switch (d.kind) {
    case 'person': inner = personSvg(d); break;
    case 'dog': inner = dogSvg(d); break;
    case 'cat': inner = catSvg(d); break;
    case 'pigeon': inner = pigeonSvg(); break;
    case 'duck': inner = duckSvg(); break;
    case 'fox': inner = fox(FOXSTYLES[d.style]); break;
    case 'raccoon': inner = raccoon(d.hasCake); break;
    case 'tree': inner = treeSvg(d); break;
    case 'stall': inner = stallSvg(d); break;
    case 'prop': inner = propSvg(d, mood); break;
    default: inner = '';
  }
  const sx = d.flip ? -d.scale : d.scale;
  return `<g transform="translate(${f1(d.x)} ${f1(d.y)}) scale(${f1(sx * 1000) / 1000} ${d.scale})">${inner}</g>`;
}

// ---------------------------------------------------------------- Szenen-Daten
function buildSceneData(cfg) {
  const r = mulberry32(cfg.seed);
  const mood = cfg.mood || 'day';
  const types = ['park', 'park', 'market', 'plaza', 'houses', 'houses', 'houses', 'houses', 'houses', 'houses', 'houses', 'houses'];
  shuffle(types, r);

  const blocks = [];
  for (let row = 0; row < ROWS; row++) {
    for (let col = 0; col < COLS; col++) {
      blocks.push({
        col, row, x: ROAD + col * (BW + ROAD), y: ROAD + row * (BH + ROAD), w: BW, h: BH,
        type: types[row * COLS + col], houses: [], pond: null, dots: []
      });
    }
  }
  let bakeryBlock = null;
  if (cfg.kind === 'case') bakeryBlock = pick(r, blocks.filter(b => b.type === 'houses'));

  const walk = [];       // begehbare Rechtecke {x,y,w,h,zone}
  const obstacles = [];  // {x,y,r}
  const drawables = [];
  const trees = [];
  const benches = [];
  const ponds = [];
  let bakeryHit = null;
  const D = (kind, x, y, scale, flip, extra) => Object.assign({ kind, x, y, scale, flip }, extra);
  const prop = (type, x, y, extra) => D('prop', x, y, 1, false, Object.assign({ type }, extra));

  for (let k = 0; k <= COLS; k++) walk.push({ x: k * (BW + ROAD) + 14, y: 14, w: ROAD - 28, h: H - 28, zone: 'road' });
  for (let j = 0; j <= ROWS; j++) walk.push({ x: 14, y: j * (BH + ROAD) + 14, w: W - 28, h: ROAD - 28, zone: 'road' });

  const addTree = (x, y) => {
    const t = makeTree(r);
    trees.push({ x, y, wide: t.wide });
    obstacles.push({ x, y, r: 22 });
    drawables.push(D('tree', x, y, 1, false, t));
  };
  const addBench = (x, y) => {
    benches.push({ x, y });
    obstacles.push({ x, y: y - 6, r: 24 });
    drawables.push(prop('bench', x, y, {}));
  };
  const addLamp = (x, y) => { obstacles.push({ x, y, r: 8 }); drawables.push(prop('lamp', x, y, {})); };
  const addBush = (x, y) => {
    const a = r(), b = r();
    obstacles.push({ x, y, r: 16 });
    drawables.push(prop('bush', x, y, { color: idx(GREENS, a), color2: idx(['#e05a5a', '#ffd84a', '#ff9fc2'], b) }));
  };

  for (const b of blocks) {
    const { x, y } = b;
    if (b.type === 'park') {
      const px = x + 120 + r() * 180, py = y + 120 + r() * 140;
      b.pond = { cx: px, cy: py, rx: 60, ry: 40 };
      ponds.push(b.pond);
      obstacles.push({ x: px, y: py, r: 55 });
      for (let i = 0; i < 40; i++) { const a = r(), c = r(), e = r(); b.dots.push({ x: x + 14 + a * (BW - 28), y: y + 14 + c * (BH - 28), r: 2.2, color: idx(FLOWERC, e), shape: 'circle' }); }
      for (let i = 0; i < 26; i++) { const a = r(), c = r(); b.dots.push({ x: x + 14 + a * (BW - 28), y: y + 14 + c * (BH - 28), r: 0, color: '#6fb85f', shape: 'tuft' }); }
      const dry = (tx, ty, m) => {      // Bäume und Büsche nicht ins Wasser stellen
        const dx = tx - px, dy = ty - py, q = Math.hypot(dx / (b.pond.rx + m), dy / (b.pond.ry + m));
        if (q >= 1) return [tx, ty];
        if (q < 1e-6) return [tx, py + b.pond.ry + m];
        return [px + dx / q * 1.02, py + dy / q * 1.02];
      };
      for (let i = 0; i < 7; i++) { const a = r(), c = r(); addTree(...dry(x + 40 + a * (BW - 80), y + 80 + c * (BH - 90), 16)); }
      for (let i = 0; i < 2; i++) { const a = r(); addBench(x + 70 + a * (BW - 140), y + BH - 26); }
      for (let i = 0; i < 3; i++) { const a = r(), c = r(); addBush(...dry(x + 30 + a * (BW - 60), y + 40 + c * (BH - 80), 12)); }
      for (let i = 0; i < 3; i++) { const a = r(), c = r(); drawables.push(D('duck', px - 40 + a * 80, py - 14 + c * 28, 0.9, a < 0.5, {})); }
      walk.push({ x: x + 20, y: y + 20, w: BW - 40, h: BH - 30, zone: 'park' });
    } else if (b.type === 'market') {
      for (let i = 0; i < 24; i++) { const a = r(), c = r(); b.dots.push({ x: x + 12 + a * (BW - 24), y: y + 12 + c * (BH - 24), r: 3, color: '#e3c88f', shape: 'circle' }); }
      [[x + 90, y + 120], [x + 210, y + 120], [x + 330, y + 120], [x + 130, y + 290], [x + 290, y + 290]].forEach(([sx, sy]) => {
        drawables.push(D('stall', sx, sy, 1, false, makeStall(r)));
        obstacles.push({ x: sx, y: sy - 10, r: 46 });
      });
      addTree(x + 30, y + 40); addTree(x + BW - 30, y + BH - 20);
      addLamp(x + BW / 2, y + 205);
      walk.push({ x: x + 14, y: y + 14, w: BW - 28, h: BH - 20, zone: 'market' });
    } else if (b.type === 'plaza') {
      const cx = x + BW / 2, cy = y + BH / 2;
      obstacles.push({ x: cx, y: cy, r: 66 });
      addTree(x + 36, y + 50); addTree(x + BW - 36, y + 50); addTree(x + 36, y + BH - 20); addTree(x + BW - 36, y + BH - 20);
      addBench(x + 90, y + 100); addBench(x + BW - 90, y + 100); addBench(x + BW / 2, y + BH - 22);
      addLamp(x + 60, y + 230); addLamp(x + BW - 60, y + 230);
      for (let f = 0; f < 2; f++) {                       // Taubenschwärme
        const a = r(), c = r(), n = 2 + Math.floor(r() * 3);
        const fx = x + 70 + a * (BW - 140), fy = y + 150 + c * 160;
        for (let i = 0; i < n; i++) { const e = r(), g = r(); drawables.push(D('pigeon', fx + (e - .5) * 40, fy + (g - .5) * 24, 1, e < .5, {})); }
      }
      walk.push({ x: x + 20, y: y + 20, w: BW - 40, h: BH - 28, zone: 'plaza' });
    } else {
      const baseY = y + BH - 85;
      for (let i = 0; i < 3; i++) {
        const hx = x + 20 + i * 140;
        const hd = makeHouse(r, hx, baseY, 120);
        if (b === bakeryBlock && i === 1) {
          Object.assign(hd, { wall: '#fbe9c8', roof: '#c8553d', roofType: 'gable', shop: '', plantBox: false, bakery: true, door: '#7a4e2d' });
          bakeryHit = { rect: { x: hx - 7, y: baseY - hd.h - 46, w: 134, h: hd.h + 46 }, center: { x: hx + 60, y: baseY - hd.h / 2 }, door: { x: hx + 60, y: baseY + 20 } };
        }
        b.houses.push(hd);
      }
      addTree(x + 140, baseY + 30); addTree(x + 280, baseY + 30);
      addLamp(x + 14, baseY + 40); addLamp(x + BW - 14, baseY + 40);
      walk.push({ x: x + 10, y: y + BH - 66, w: BW - 20, h: 50, zone: 'sidewalk' });
    }
  }

  // ---- Platzierungs-Helfer
  const steps = [];
  const crumbs = [];
  const placed = [];
  const free = (x, y, d) => !obstacles.some(o => (o.x - x) ** 2 + (o.y - y) ** 2 < (o.r + 8) ** 2) && !placed.some(p => (p.x - x) ** 2 + (p.y - y) ** 2 < d * d);
  const spotIn = (zones, filterFn, tries) => {
    const rects = walk.filter(w => zones.includes(w.zone));
    const area = rects.reduce((a, w) => a + w.w * w.h, 0);
    for (let n = 0; n < tries; n++) {
      let t = r() * area, rect = rects[0];
      for (const w of rects) { t -= w.w * w.h; if (t <= 0) { rect = w; break; } }
      const a = r(), c = r();
      const p = { x: rect.x + a * rect.w, y: rect.y + c * rect.h };
      if (filterFn(p.x, p.y)) return p;
    }
    return null;
  };
  const ALL = ['road', 'park', 'market', 'plaza', 'sidewalk'];
  const randomSpot = () => spotIn(ALL, () => true, 1);
  const addActor = (kind, x, y, scale, flip, extra) => {
    placed.push({ x, y });
    drawables.push(D(kind, x, y, scale, flip, extra));
  };

  // ---- Zielfiguren
  if (cfg.kind === 'fips') {
    let fx, fy;
    if (cfg.hidden) {
      const cand = trees.filter(t => t.wide && t.x > 60 && t.x < W - 60 && t.y > 120 && t.y < H - 60);
      const t = pick(r, cand);
      const side = r() < 0.5 ? -1 : 1;
      fx = t.x + side * cfg.hideOffset; fy = t.y - 4;
    } else {
      let s = randomSpot(), n = 0;
      while (!free(s.x, s.y, 40) && n++ < 80) s = randomSpot();
      fx = s.x; fy = s.y;
    }
    addActor('fox', fx, fy, 1, false, { style: 'fips' });
    steps.push({
      title: 'Finde Fips!', sub: 'Fuchs mit türkisem Schal, gelben Punkten und weißer Schwanzspitze',
      hit: { circle: { x: fx, y: fy - 28, r: 30 } }, center: { x: fx, y: fy - 28 }
    });
  } else {
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
    const dropCrumbs = pts => {
      for (let i = 0; i < pts.length - 1; i++) {
        const [ax, ay] = pts[i], [bx, by] = pts[i + 1];
        const len = Math.hypot(bx - ax, by - ay), n = Math.floor(len / 26);
        for (let m = 0; m < n; m++) {
          const t = m / n, p1 = r(), p2 = r();
          crumbs.push({ x: ax + (bx - ax) * t + (p1 - .5) * 10, y: ay + (by - ay) * t + (p2 - .5) * 10, big: m % 3 === 0 });
        }
      }
    };
    dropCrumbs(route);
    const fEnd = [Math.min(W - 60, Math.max(60, roadX(kv) + dir * 330)), yh];
    dropCrumbs([[roadX(kv), yh], fEnd]);
    for (let i = 0; i < 3; i++) { const a = r(), c = r(); addActor('pigeon', fEnd[0] + dir * (14 + i * 20), fEnd[1] + (a - .5) * 20, 1, c < .5, {}); }
    const thiefX = tx, thiefY = yt + 12;
    addActor('raccoon', thiefX, thiefY, 1.05, r() < .5, { hasCake: true });
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

  // ---- Sitzende auf Bänken
  for (const bn of benches) {
    const a = r(), c = r();
    if (a < 0.6) {
      const p = makePerson(r, { pose: 'sit', seat: 'bench' });
      addActor('person', bn.x - 6 + c * 12, bn.y + 1, 1, c < .5, p);
    }
  }

  // ---- Szenen-Gruppen (kleine Geschichten)
  const GROUPS = {
    picnic:   { zones: ['park'], R: 70 },
    musician: { zones: ['plaza', 'market', 'sidewalk'], R: 80 },
    icecream: { zones: ['plaza', 'park', 'market'], R: 80 },
    dogwalk:  { zones: ['road', 'sidewalk', 'park'], R: 55 },
    photo:    { zones: ['plaza', 'park'], R: 90 },
    ball:     { zones: ['park', 'plaza'], R: 60 },
    painter:  { zones: ['park', 'plaza', 'market'], R: 60 },
    angler:   { zones: [], R: 0 }
  };
  const person = (o) => makePerson(r, o);
  const place = {
    picnic(ax, ay) {
      const col = idx(BLANKET, r());
      const g = D('prop', ax, ay, 1, false, { type: 'blanket', color: col[0], color2: col[1], z: ay - 60 });
      drawables.push(g);
      drawables.push(prop('basket', ax - 2, ay - 22, {}));
      [[-34, -16], [8, -8], [38, -20]].forEach(([dx, dy], i) => { addActor('person', ax + dx, ay + dy, 1, i > 0, person({ pose: 'sit' })); });
      if (r() < 0.6) { const dg = makeDog(r); addActor('dog', ax + 64, ay + 6, 0.95, true, dg); }
    },
    musician(ax, ay) {
      const s = r() < 0.5 ? 1 : -1;
      addActor('person', ax, ay, 1, s < 0, person({ body: 'adult', prop: 'guitar' }));
      drawables.push(prop('guitarcase', ax - 30 * s, ay + 10, {}));
      drawables.push(prop('notes', ax + 14 * s, ay - 62, {}));
      const n = 2 + Math.floor(r() * 3);
      for (let i = 0; i < n; i++) { const a = r(); addActor('person', ax + (48 + i * 24) * s, ay + 4 - (i % 2) * 8 + a * 6, 1, s > 0, person({ prop: i === 0 ? 'none' : undefined })); }
    },
    icecream(ax, ay) {
      const s = r() < 0.5 ? 1 : -1;
      drawables.push(prop('cart', ax, ay, { color: idx(STALLC, r()) }));
      addActor('person', ax + 36 * s, ay - 4, 1, s > 0, person({ body: 'adult', prop: 'none' }));
      for (let i = 0; i < 3; i++) addActor('person', ax - (30 + i * 26) * s, ay + 8 + (i % 2) * 4, 1, s < 0, person({ body: i === 1 ? 'kid' : undefined, prop: i < 2 ? 'icecream' : undefined }));
    },
    dogwalk(ax, ay) {
      const s = r() < 0.5 ? 1 : -1;
      addActor('person', ax, ay, 1, s < 0, person({ body: 'adult', prop: 'none' }));
      const dg = makeDog(r);
      addActor('dog', ax + 40 * s, ay + 6, 0.95, s < 0, dg);
      drawables.push(prop('leash', ax + 10 * s, ay - 24, { dx: 28 * s, dy: 12, z: ay + 7 }));
    },
    photo(ax, ay) {
      const s = r() < 0.5 ? 1 : -1;
      addActor('person', ax, ay, 1, s < 0, person({ body: 'adult', prop: 'camera' }));
      [[56, -6], [80, 2], [104, -4]].forEach(([dx, dy], i) => addActor('person', ax + dx * s, ay + dy, 1, s > 0, person({ body: i === 1 ? 'kid' : undefined, prop: 'none' })));
    },
    ball(ax, ay) {
      [[-38, 4], [38, -2], [0, 24]].forEach(([dx, dy], i) => addActor('person', ax + dx, ay + dy, 1, dx > 0, person({ body: 'kid', prop: 'none' })));
      drawables.push(prop('ball', ax, ay + 10, { lift: 30 }));
    },
    painter(ax, ay) {
      const s = r() < 0.5 ? 1 : -1;
      addActor('person', ax, ay, 1, s < 0, person({ body: 'adult', prop: 'none' }));
      drawables.push(prop('easel', ax + 36 * s, ay + 2, {}));
      if (r() < 0.6) addActor('person', ax - 40 * s, ay + 10, 1, s < 0, person({ body: 'senior', prop: 'none' }));
    },
    angler(pond) {
      const ax = pond.cx - pond.rx - 12, ay = pond.cy + 8;
      addActor('person', ax, ay, 1, false, person({ pose: 'sit', prop: 'none' }));
      drawables.push(prop('rod', ax + 8, ay - 22, { dx: 44, dy: 22 }));
      drawables.push(prop('bucket', ax - 20, ay + 6, {}));
    }
  };
  const kinds = shuffle(['picnic', 'musician', 'icecream', 'dogwalk', 'photo', 'ball', 'painter', 'angler'], r);
  for (let gi = 0; gi < (cfg.groups || 0); gi++) {
    const kind = kinds[gi % kinds.length];
    if (kind === 'angler') {
      const pond = ponds.length ? pick(r, ponds) : null;
      const fipsNear = steps[0] && steps[0].center && Math.hypot(steps[0].center.x - (pond ? pond.cx - pond.rx : 0), steps[0].center.y - (pond ? pond.cy : 0)) < 90;
      if (pond && !fipsNear) { place.angler(pond); obstacles.push({ x: pond.cx - pond.rx - 12, y: pond.cy + 8, r: 40 }); }
      continue;
    }
    const gd = GROUPS[kind];
    const spot = spotIn(gd.zones, (x, y) =>
      !obstacles.some(o => (o.x - x) ** 2 + (o.y - y) ** 2 < (o.r + gd.R * 0.6) ** 2) &&
      !trees.some(t => Math.abs(t.x - x) < gd.R * 0.9 && t.y - y > -10 && t.y - y < 110) &&   // Baumkronen sollen die Gruppe nicht verdecken
      !placed.some(p => (p.x - x) ** 2 + (p.y - y) ** 2 < gd.R * gd.R), 80);
    if (!spot) continue;
    place[kind](spot.x, spot.y);
    obstacles.push({ x: spot.x, y: spot.y - 10, r: gd.R * 0.7 });
  }

  // ---- Doppelgänger, Waschbären, Bevölkerung
  const fill = (n, d, makeFn) => {
    for (let i = 0; i < n; i++) {
      let s = randomSpot(), t = 0;
      while (!free(s.x, s.y, d) && t++ < 40) s = randomSpot();
      makeFn(s, i);
    }
  };
  const kinds2 = cfg.decoys || [];
  fill(cfg.foxes || 0, 34, (s, i) => { const sc = 0.92 + r() * 0.16, fl = r() < .5; addActor('fox', s.x, s.y, sc, fl, { style: kinds2[i % kinds2.length] }); });
  fill(cfg.raccoons || 0, 34, (s) => { const sc = 0.95 + r() * 0.15, fl = r() < .5; addActor('raccoon', s.x, s.y, sc, fl, { hasCake: false }); });
  const rest = Math.max(0, cfg.crowd - (cfg.foxes || 0) - (cfg.raccoons || 0));
  fill(rest, 30, (s) => {
    const roll = r(), sc = 0.9 + r() * 0.2, fl = r() < .5;
    if (roll < 0.09) addActor('dog', s.x, s.y, sc, fl, makeDog(r));
    else if (roll < 0.16) addActor('cat', s.x, s.y, sc, fl, makeCat(r));
    else if (roll < 0.19) addActor('pigeon', s.x, s.y, 1, fl, {});
    else addActor('person', s.x, s.y, sc, fl, makePerson(r));
  });

  // stabil nach y (bzw. z) sortieren
  drawables.forEach((d, i) => { d._i = i; });
  drawables.sort((a, b) => ((a.z !== undefined ? a.z : a.y) - (b.z !== undefined ? b.z : b.y)) || (a._i - b._i));
  drawables.forEach(d => { delete d._i; });

  return { mood, blocks, crumbs, drawables, steps, bakery: bakeryHit };
}

// ---------------------------------------------------------------- SVG aus Daten
function sceneToSvg(data) {
  const { mood, blocks, crumbs, drawables } = data;
  const paper = STYLE.paper;
  const asphalt = '#dcd3c0';
  let bg = `<rect width="${W}" height="${H}" fill="${paper ? asphalt : '#bfe3a4'}"/>`;
  // Grasstruktur (klassisch; im Papier-Stil liegen die Blöcke als Schichten auf der Straße)
  if (!paper) for (const b of blocks) {
    if (b.type === 'houses') bg += `<rect x="${b.x}" y="${b.y}" width="${b.w}" height="${b.h}" rx="14" fill="#d3e7b9"/>`;
  }
  // Straßen
  if (!paper) for (let k = 0; k <= COLS; k++) {
    const x = k * (BW + ROAD);
    bg += `<rect x="${x}" y="0" width="${ROAD}" height="${H}" fill="${asphalt}"/>`;
    bg += `<line x1="${x + 6}" y1="0" x2="${x + 6}" y2="${H}" stroke="rgba(0,0,0,.07)" stroke-width="4"/><line x1="${x + ROAD - 6}" y1="0" x2="${x + ROAD - 6}" y2="${H}" stroke="rgba(0,0,0,.07)" stroke-width="4"/>`;
  }
  if (!paper) for (let j = 0; j <= ROWS; j++) {
    const y = j * (BH + ROAD);
    bg += `<rect x="0" y="${y}" width="${W}" height="${ROAD}" fill="${asphalt}"/>`;
    bg += `<line x1="0" y1="${y + 6}" x2="${W}" y2="${y + 6}" stroke="rgba(0,0,0,.07)" stroke-width="4"/><line x1="0" y1="${y + ROAD - 6}" x2="${W}" y2="${y + ROAD - 6}" stroke="rgba(0,0,0,.07)" stroke-width="4"/>`;
  }
  if (!paper) for (let k = 0; k <= COLS; k++) for (let j = 0; j <= ROWS; j++) {
    bg += `<rect x="${k * (BW + ROAD)}" y="${j * (BH + ROAD)}" width="${ROAD}" height="${ROAD}" fill="${asphalt}"/>`;
  }
  if (paper) {   // Randlinien der Straße wie gefaltetes Papier
    for (let k = 0; k <= COLS; k++) bg += `<rect x="${k * (BW + ROAD)}" y="0" width="${ROAD}" height="${H}" fill="rgba(255,250,240,.28)"/>`;
    for (let j = 0; j <= ROWS; j++) bg += `<rect x="0" y="${j * (BH + ROAD)}" width="${W}" height="${ROAD}" fill="rgba(255,250,240,.28)"/>`;
  }
  // Mittellinien
  for (let k = 0; k <= COLS; k++) bg += `<line x1="${k * (BW + ROAD) + ROAD / 2}" y1="0" x2="${k * (BW + ROAD) + ROAD / 2}" y2="${H}" stroke="#ffffff" stroke-width="3" stroke-dasharray="26 22" opacity=".7"/>`;
  for (let j = 0; j <= ROWS; j++) bg += `<line x1="0" y1="${j * (BH + ROAD) + ROAD / 2}" x2="${W}" y2="${j * (BH + ROAD) + ROAD / 2}" stroke="#ffffff" stroke-width="3" stroke-dasharray="26 22" opacity=".7"/>`;
  // Zebrastreifen an den Kreuzungen
  let zebra = '';
  for (let k = 0; k <= COLS; k++) for (let j = 0; j <= ROWS; j++) {
    const x0 = k * (BW + ROAD), y0 = j * (BH + ROAD);
    for (let i = 0; i < 5; i++) {
      if (j > 0) zebra += `<rect x="${x0 + 10 + i * 18}" y="${y0 - 24}" width="9" height="18"/>`;
      if (j < ROWS) zebra += `<rect x="${x0 + 10 + i * 18}" y="${y0 + ROAD + 6}" width="9" height="18"/>`;
      if (k > 0) zebra += `<rect x="${x0 - 24}" y="${y0 + 10 + i * 18}" width="18" height="9"/>`;
      if (k < COLS) zebra += `<rect x="${x0 + ROAD + 6}" y="${y0 + 10 + i * 18}" width="18" height="9"/>`;
    }
  }
  bg += `<g fill="#ffffff" opacity=".75">${zebra}</g>`;

  // Blöcke
  let houseSvgs = '';
  for (const b of blocks) {
    const { x, y } = b;
    if (paper) bg += `<rect x="${x + 3}" y="${y + 5}" width="${BW}" height="${BH}" rx="${b.type === 'park' ? 26 : 14}" fill="rgba(45,30,15,.30)"/>`;
    if (b.type === 'park') {
      bg += `<rect x="${x}" y="${y}" width="${BW}" height="${BH}" rx="26" fill="#8fcf7a"${paper ? ' stroke="#fffaf0" stroke-width="2"' : ''}/>`;
      let tufts = '';
      for (const d of b.dots) if (d.shape === 'tuft') tufts += `M${f1(d.x)},${f1(d.y)} l-2,-5 M${f1(d.x)},${f1(d.y)} l0,-6 M${f1(d.x)},${f1(d.y)} l2,-5 `;
      bg += `<path d="${tufts}" stroke="#6fb85f" stroke-width="1.2" stroke-linecap="round" fill="none"/>`;
      const p = b.pond;
      bg += `<ellipse cx="${f1(p.cx)}" cy="${f1(p.cy)}" rx="${p.rx + 5}" ry="${p.ry + 5}" fill="#e9e1c8"/>`;
      bg += `<ellipse cx="${f1(p.cx)}" cy="${f1(p.cy)}" rx="${p.rx}" ry="${p.ry}" fill="#9fd8f0" stroke="#ffffff" stroke-width="3"/>`;
      bg += `<path d="M${f1(p.cx - 40)},${f1(p.cy - 10)} q10,-5 20,0 M${f1(p.cx + 14)},${f1(p.cy + 16)} q10,-5 20,0 M${f1(p.cx + 20)},${f1(p.cy - 20)} q8,-4 16,0" stroke="#ffffff" stroke-width="1.6" fill="none" opacity=".8"/>`;
      bg += `<ellipse cx="${f1(p.cx - 20)}" cy="${f1(p.cy + 4)}" rx="10" ry="5" fill="#5fae5b"/><circle cx="${f1(p.cx - 22)}" cy="${f1(p.cy + 2)}" r="2" fill="#ff9fc2"/>`;
      for (const d of b.dots) if (d.shape === 'circle') bg += `<circle cx="${f1(d.x)}" cy="${f1(d.y)}" r="${d.r}" fill="${d.color}"/>`;
    } else if (b.type === 'market') {
      bg += `<rect x="${x}" y="${y}" width="${BW}" height="${BH}" rx="14" fill="#f0dcae"${paper ? ' stroke="#fffaf0" stroke-width="2"' : ''}/>`;
      for (const d of b.dots) bg += `<circle cx="${f1(d.x)}" cy="${f1(d.y)}" r="${d.r}" fill="${d.color}"/>`;
    } else if (b.type === 'plaza') {
      const cx = x + BW / 2, cy = y + BH / 2;
      bg += `<rect x="${x}" y="${y}" width="${BW}" height="${BH}" rx="14" fill="#e8e1d2"${paper ? ' stroke="#fffaf0" stroke-width="2"' : ''}/>`;
      let tiles = '';
      for (let i = 1; i < 8; i++) tiles += `M${x + i * BW / 8},${y} v${BH} `;
      for (let j = 1; j < 7; j++) tiles += `M${x},${y + j * BH / 7} h${BW} `;
      bg += `<path d="${tiles}" stroke="rgba(0,0,0,.05)" stroke-width="2" fill="none"/>`;
      bg += `<ellipse cx="${cx + 2}" cy="${cy + 6}" rx="58" ry="42" fill="rgba(45,30,15,.16)"/>` +
            `<ellipse cx="${cx}" cy="${cy}" rx="56" ry="40" fill="#f6f1e4" stroke="#ffffff" stroke-width="2"/>` +
            `<ellipse cx="${cx}" cy="${cy}" rx="47" ry="32" fill="#9fd8f0"/>` +
            `<path d="M${cx - 34},${cy + 8} q8,-4 16,0 M${cx + 14},${cy + 16} q8,-4 16,0 M${cx + 22},${cy - 4} q6,-3 12,0" stroke="#ffffff" stroke-width="1.6" fill="none" opacity=".8"/>` +
            `<ellipse cx="${cx}" cy="${cy + 2}" rx="15" ry="9" fill="#d9d2c0" stroke="#ffffff" stroke-width="1.6"/>` +
            `<rect x="${cx - 4}" y="${cy - 26}" width="8" height="26" fill="#cfc8b5" stroke="#ffffff" stroke-width="1.4"/>` +
            `<ellipse cx="${cx}" cy="${cy - 24}" rx="19" ry="7" fill="#f6f1e4" stroke="#ffffff" stroke-width="1.6"/>` +
            `<ellipse cx="${cx}" cy="${cy - 25}" rx="14" ry="4.6" fill="#b9e3f2"/>` +
            `<path d="M${cx},${cy - 25} q0,-14 0,-18 M${cx},${cy - 25} q-9,-12 -15,-4 M${cx},${cy - 25} q9,-12 15,-4" stroke="#e8f8ff" stroke-width="2.4" stroke-linecap="round" fill="none"/>` +
            `<g fill="#ffffff"><circle cx="${cx - 17}" cy="${cy - 22}" r="1.6"/><circle cx="${cx + 17}" cy="${cy - 22}" r="1.6"/><circle cx="${cx}" cy="${cy - 46}" r="1.8"/></g>`;
    } else {
      if (paper) bg += `<rect x="${x}" y="${y}" width="${BW}" height="${BH}" rx="14" fill="#d3e7b9" stroke="#fffaf0" stroke-width="2"/>`;
      bg += `<rect x="${x}" y="${y + BH - 78}" width="${BW}" height="78" rx="10" fill="#e6dfcf"/>`;
      let curb = '';
      for (let i = 0; i < 12; i++) curb += `M${x + 10 + i * 35},${y + BH - 78} v78 `;
      bg += `<path d="${curb}" stroke="rgba(0,0,0,.05)" stroke-width="1.5" fill="none"/>`;
      for (const hd of b.houses) houseSvgs += houseSvg(hd, mood);
    }
  }
  const cr = crumbs.map(c => `<circle cx="${f1(c.x)}" cy="${f1(c.y)}" r="${c.big ? 3.4 : 2.4}" fill="${c.big ? '#c98a2b' : '#e0a845'}"/>`).join('');
  const items = drawables.map(d => drawableSvg(d, mood)).join('');
  const tint = mood === 'evening' ? `<rect width="${W}" height="${H}" fill="rgba(255,140,60,.16)" pointer-events="none"/>` : '';
  const sceneDefs = paper
    ? `<defs><filter id="lift" x="-2%" y="-2%" width="104%" height="104%"><feDropShadow dx="1.8" dy="2.8" stdDeviation="1" flood-color="#2a1d10" flood-opacity=".34"/></filter>` +
      `<pattern id="grain" width="384" height="384" patternUnits="userSpaceOnUse"><image href="paper-grain.png" width="384" height="384"/></pattern></defs>`
    : '';
  const grain = paper ? `<rect width="${W}" height="${H}" fill="url(#grain)" pointer-events="none"/>` : '';
  return `${sceneDefs}<g>${bg}</g><g${paper ? ' filter="url(#lift)"' : ''}>${houseSvgs}</g><g>${cr}</g><g${paper ? ' filter="url(#lift)"' : ''}>${items}</g>${tint}${grain}`;
}

function buildScene(cfg) {
  const data = buildSceneData(cfg);
  return { data, svg: sceneToSvg(data), steps: data.steps };
}

if (typeof module !== 'undefined') module.exports = { STYLE, LEVELS, buildSceneData, buildScene, sceneToSvg, fox, raccoon, FOXSTYLES, FIPS, W, H };
