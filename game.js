'use strict';

/* =========================================================================
   Wuselburg – Wo steckt Fips?  Spiel-Logik und UI (Browser).
   Welt, Level, Figuren und Szenengenerator liegen in scene.js.
   ========================================================================= */

const $ = id => document.getElementById(id);

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
