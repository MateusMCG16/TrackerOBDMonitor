'use strict';

/* Painel ao vivo para ELM327 pela Web Serial API (Chrome ou Edge).
 * Só leitura: todo comando passa pela lista de permissão abaixo antes de ir para a porta.
 * Não há modo 04 (apagar códigos), troca de sessão, escrita, atuador nem ATSH. */

const ALLOW = [
  /^AT(Z|E0|L0|S0|H1|SP0|DPN|RV)$/,   // configuração e tensão, só no adaptador
  /^01([0-9A-F]{2}){1,3}$/,           // modo 01: dados atuais (até 3 PIDs por pedido)
  /^(03|07|0A)$/,                     // códigos confirmados / pendentes / permanentes
  /^09(02|0A)$/                       // VIN e nome das centrais
];

const sleep = ms => new Promise(r => setTimeout(r, ms));
const hex2 = n => n.toString(16).toUpperCase().padStart(2, '0');
const u16 = (d, i = 0) => d[i] * 256 + d[i + 1];
const pct = b => b * 100 / 255;
const $ = id => document.getElementById(id);

/* ---------- PIDs: tamanho dos dados e decodificação ---------- */

const FUEL_SYS = { 1: 'Malha aberta (motor frio)', 2: 'Malha fechada', 4: 'Malha aberta (carga ou corte)', 8: 'Malha aberta (falha)', 16: 'Malha fechada (falha na sonda)' };

// dec(d, ecu) devolve { sinal: valor }. O ecu é o endereço de quem respondeu ("11" motor, "18" câmbio).
const PID = {
  0x01: { len: 4, dec: (d, e) => ({ ['mil_' + e]: (d[0] & 0x80) ? 1 : 0, ['dtcCount_' + e]: d[0] & 0x7F }) },
  0x03: { len: 2, dec: d => ({ fuelSys: FUEL_SYS[d[0]] || ('Estado ' + d[0]) }) },
  0x04: { len: 1, dec: d => ({ load: pct(d[0]) }) },
  0x05: { len: 1, dec: d => ({ coolant: d[0] - 40 }) },
  0x06: { len: 1, dec: d => ({ stft: (d[0] - 128) * 100 / 128 }) },
  0x07: { len: 1, dec: d => ({ ltft: (d[0] - 128) * 100 / 128 }) },
  0x0A: { len: 1, dec: d => ({ fuelPress: d[0] * 3 }) },
  0x0C: { len: 2, dec: d => ({ rpm: u16(d) / 4 }) },
  0x0D: { len: 1, dec: d => ({ speed: d[0] }) },
  0x0E: { len: 1, dec: d => ({ timing: d[0] / 2 - 64 }) },
  0x0F: { len: 1, dec: d => ({ iat: d[0] - 40 }) },
  0x10: { len: 2, dec: d => ({ maf: u16(d) / 100 }) },
  0x11: { len: 1, dec: d => ({ throttle: pct(d[0]) }) },
  0x14: { len: 2, dec: d => ({ o2s1: d[0] * 0.005 }) },
  0x15: { len: 2, dec: d => ({ o2s2: d[0] * 0.005 }) },
  0x1F: { len: 2, dec: d => ({ runtime: u16(d) }) },
  0x21: { len: 2, dec: d => ({ distMil: u16(d) }) },
  0x2E: { len: 1, dec: d => ({ purge: pct(d[0]) }) },
  0x2F: { len: 1, dec: d => ({ fuelLevel: pct(d[0]) }) },
  0x30: { len: 1, dec: d => ({ warmups: d[0] }) },
  0x31: { len: 2, dec: d => ({ distSinceClear: u16(d) }) },
  0x33: { len: 1, dec: d => ({ baro: d[0] }) },
  0x3C: { len: 2, dec: d => ({ catTemp: u16(d) / 10 - 40 }) },
  0x42: { len: 2, dec: (d, e) => ({ [e === '18' ? 'vTcm' : 'vEcm']: u16(d) / 1000 }) },
  0x43: { len: 2, dec: d => ({ absLoad: u16(d) * 100 / 255 }) },
  0x44: { len: 2, dec: d => ({ lambda: u16(d) * 2 / 65536 }) },
  0x45: { len: 1, dec: d => ({ relThrottle: pct(d[0]) }) },
  0x46: { len: 1, dec: d => ({ ambient: d[0] - 40 }) },
  0x47: { len: 1, dec: d => ({ throttleB: pct(d[0]) }) },
  0x49: { len: 1, dec: d => ({ pedalD: pct(d[0]) }) },
  0x4A: { len: 1, dec: d => ({ pedalE: pct(d[0]) }) },
  0x4C: { len: 1, dec: d => ({ throttleCmd: pct(d[0]) }) },
  0x52: { len: 1, dec: d => ({ ethanol: pct(d[0]) }) },
  0x68: { len: 7, dec: d => (d[0] & 2) ? { iat2: d[2] - 40 } : {} },
  0x87: { len: 5, dec: d => (d[0] & 1) ? { map: u16(d, 1) * 0.03125 } : {} }
};
for (const m of [0x00, 0x20, 0x40, 0x60, 0x80, 0xA0, 0xC0]) PID[m] = { len: 4 };

// Grupos de leitura. Rápido a cada volta; médio ~1 vez por segundo; lento em rodízio.
// Limite medido no carro com o clone v2.1: pedidos de 2 e 3 PIDs cuja resposta cabe num quadro CAN
// (até 6 bytes depois do 41) funcionam; um pedido de 4 PIDs reinicia o adaptador.
const FAST = [0x0C, 0x0D];
const MEDIUM = [[0x04, 0x11, 0x0E], [0x10, 0x49], [0x87], [0x06, 0x07, 0x4C], [0x14, 0x15], [0x44]];
const SLOW = [[0x05, 0x0F, 0x46], [0x3C, 0x2F], [0x68], [0x42, 0x52], [0x33, 0x0A, 0x2E], [0x43, 0x45], [0x47, 0x4A, 0x30], [0x1F, 0x03], [0x31, 0x21], [0x01]];
for (const g of [FAST, ...MEDIUM, ...SLOW]) {
  const size = g.reduce((a, p) => a + 1 + PID[p].len, 0);
  if (g.length > 3 || (g.length > 1 && size > 6)) throw new Error('Grupo de PIDs acima do limite do adaptador: ' + g.map(hex2).join(' '));
}

/* ---------- o que aparece na tela ---------- */

// [chave, rótulo, unidade, casas, PID que traz o valor (null = adaptador), complemento do rótulo]
const GROUPS = [
  ['Motor', [
    ['load', 'Carga calculada', '%', 0, 0x04], ['absLoad', 'Carga absoluta', '%', 0, 0x43], ['timing', 'Avanço de ignição', '°', 1, 0x0E],
    ['throttle', 'Borboleta', '%', 0, 0x11], ['throttleCmd', 'Borboleta', '%', 0, 0x4C, 'comandada'], ['relThrottle', 'Borboleta', '%', 0, 0x45, 'relativa'],
    ['pedalD', 'Pedal', '%', 0, 0x49, 'sensor D'], ['pedalE', 'Pedal', '%', 0, 0x4A, 'sensor E'], ['runtime', 'Desde a partida', 'tempo', 0, 0x1F]
  ]],
  ['Ar e combustível', [
    ['map', 'Pressão do coletor', 'kPa', 1, 0x87], ['baro', 'Pressão barométrica', 'kPa', 0, 0x33], ['maf', 'Fluxo de ar', 'g/s', 2, 0x10],
    ['stft', 'Trim curto', '%', 1, 0x06], ['ltft', 'Trim longo', '%', 1, 0x07], ['lambda', 'Lambda', '', 3, 0x44, 'comandada'],
    ['fuelPress', 'Pressão de combustível', 'kPa', 0, 0x0A], ['fuelLevel', 'Tanque', '%', 0, 0x2F], ['ethanol', 'Etanol', '%', 0, 0x52],
    ['purge', 'Purga do cânister', '%', 0, 0x2E], ['fuelSys', 'Sistema de combustível', 'texto', 0, 0x03]
  ]],
  ['Temperaturas', [
    ['coolant', 'Arrefecimento', '°C', 0, 0x05], ['iat', 'Ar de admissão', '°C', 0, 0x0F], ['iat2', 'Ar de admissão', '°C', 0, 0x68, 'sensor 2'],
    ['ambient', 'Ar ambiente', '°C', 0, 0x46], ['catTemp', 'Catalisador', '°C', 0, 0x3C]
  ]],
  ['Sondas e elétrica', [
    ['o2s1', 'Sonda 1', 'V', 2, 0x14, 'pré-catalisador'], ['o2s2', 'Sonda 2', 'V', 2, 0x15, 'pós-catalisador'],
    ['vEcm', 'Tensão', 'V', 2, 0x42, 'motor (ECM)'], ['vTcm', 'Tensão', 'V', 2, 0x42, 'câmbio (TCM)'], ['vAdapter', 'Tensão', 'V', 1, null, 'adaptador']
  ]],
  ['Desde a última limpeza de códigos', [
    ['distSinceClear', 'Distância percorrida', 'km', 0, 0x31], ['warmups', 'Aquecimentos', '', 0, 0x30], ['distMil', 'Com a luz acesa', 'km', 0, 0x21]
  ]]
];

// Faixas que pedem atenção. Só mudam a cor; nenhuma decisão é tomada a partir delas.
const volts = v => v < 11.5 || v > 15.5 ? 'danger' : v < 12.2 || v > 15 ? 'attention' : '';
const LEVEL = {
  coolant: v => v >= 115 ? 'danger' : v >= 105 ? 'attention' : '',
  catTemp: v => v >= 950 ? 'danger' : v >= 850 ? 'attention' : '',
  stft: v => Math.abs(v) >= 20 ? 'danger' : Math.abs(v) >= 10 ? 'attention' : '',
  ltft: v => Math.abs(v) >= 20 ? 'danger' : Math.abs(v) >= 10 ? 'attention' : '',
  fuelLevel: v => v < 8 ? 'danger' : v < 15 ? 'attention' : '',
  distMil: v => v > 0 ? 'attention' : '',
  vEcm: volts, vTcm: volts, vAdapter: volts
};
const NO_BAR = new Set(['stft', 'ltft']);

/* ---------- estado ---------- */

const state = {
  running: false, demo: false, transport: null,
  signals: {},            // chave -> { v, t }
  supported: new Set(),
  singles: false, multiFail: 0,
  rpmTimes: [], lastLatency: null, txCount: 0, errCount: 0,
  log: [], wantDtc: false, dtc: null,
  info: {}
};

function setSignal(key, v) { state.signals[key] = { v, t: performance.now() }; }

function setStatus(text, cls) {
  const el = $('status');
  el.textContent = text;
  el.className = 'state-label' + ({ ok: ' success', erro: ' danger' }[cls] || '');
}

function warn(text, kind = 'warning') {
  const el = $('aviso');
  el.hidden = !text;
  el.className = 'banner banner-' + kind;
  el.querySelector('use').setAttribute('href', kind === 'info' ? '#i-info' : '#i-alert');
  $('avisoText').textContent = text || '';
}

// Mantém a tela do notebook acesa durante a leitura (quando o navegador permite).
let wakeLock = null;
async function keepAwake(on) {
  try {
    if (on && 'wakeLock' in navigator && !wakeLock) wakeLock = await navigator.wakeLock.request('screen');
    if (!on && wakeLock) { await wakeLock.release(); wakeLock = null; }
  } catch (e) { wakeLock = null; }
}

/* ---------- interpretação das respostas ---------- */

// Texto do ELM (com ATH1) -> mensagens ISO-TP remontadas, uma por central: [{ ecu, data:[bytes] }]
function parseFrames(text) {
  const msgs = new Map();
  const flags = [];
  for (const raw of text.split(/[\r\n]+/)) {
    const line = raw.trim();
    if (!line) continue;
    const s = line.replace(/\s+/g, '').toUpperCase();
    let m = /^18DAF1([0-9A-F]{2})((?:[0-9A-F]{2})+)$/.exec(s);          // CAN 29 bits
    if (!m) { const n = /^(7E[89A-F])((?:[0-9A-F]{2})+)$/.exec(s); if (n) m = n; }  // CAN 11 bits
    if (!m) { if (/[A-Z]{3}/.test(line) && !/^(AT|0[0-9A])/.test(s)) flags.push(line); continue; }
    const ecu = m[1];
    const b = m[2].match(/../g).map(h => parseInt(h, 16));
    const type = b[0] >> 4;
    if (type === 0) {                              // quadro único: o tamanho vem no primeiro byte
      msgs.set(ecu + ':' + msgs.size, { ecu, len: b[0] & 0x0F, data: b.slice(1, 1 + (b[0] & 0x0F)), done: true });
    } else if (type === 1) {                       // primeiro quadro de uma resposta longa
      msgs.set(ecu, { ecu, len: ((b[0] & 0x0F) << 8) | b[1], data: b.slice(2), done: false });
    } else if (type === 2) {                       // continuação
      const cur = msgs.get(ecu);
      if (cur && !cur.done) cur.data.push(...b.slice(1));
    }
  }
  const out = [];
  for (const m of msgs.values()) {
    if (m.data.length < m.len) continue;           // resposta incompleta: descarta
    out.push({ ecu: m.ecu, data: m.data.slice(0, m.len) });
  }
  return { msgs: out, flags };
}

// Carga útil do modo 01 -> lista [pid, bytes]. Um pedido pode trazer vários PIDs.
function mode01(data) {
  const out = [];
  if (data[0] !== 0x41) return out;
  let i = 1;
  while (i < data.length) {
    const pid = data[i], def = PID[pid];
    if (!def || i + 1 + def.len > data.length) break;
    out.push([pid, data.slice(i + 1, i + 1 + def.len)]);
    i += 1 + def.len;
  }
  return out;
}

function dtcCode(b0, b1) {
  return 'PCBU'[b0 >> 6] + ((b0 >> 4) & 3) + (b0 & 0x0F).toString(16).toUpperCase() + hex2(b1);
}

/* ---------- transporte: porta serial real ---------- */

class SerialTransport {
  constructor() { this.buf = ''; this.waiter = null; }

  async open() {
    this.port = await navigator.serial.requestPort();
    await this.port.open({ baudRate: 38400 });
    this.writer = this.port.writable.getWriter();
    this.reader = this.port.readable.getReader();
    this.pump();
    await sleep(300);
    this.buf = '';
  }

  async pump() {
    const dec = new TextDecoder();
    let failure = new Error('A porta serial foi fechada.');
    try {
      for (;;) {
        const { value, done } = await this.reader.read();
        if (done) break;
        this.buf += dec.decode(value, { stream: true }).replace(/\0/g, '');   // o ELM pode inserir bytes NUL
        if (this.waiter && this.buf.includes('>')) { const w = this.waiter; this.waiter = null; w.resolve(); }
      }
    } catch (e) { failure = e; }
    this.dead = failure;
    if (this.waiter) { const w = this.waiter; this.waiter = null; w.reject(failure); }
  }

  async transact(cmd, timeout) {
    if (this.dead) throw this.dead;
    this.buf = '';
    await this.writer.write(new TextEncoder().encode(cmd + '\r'));
    if (!this.buf.includes('>')) {
      await new Promise((resolve, reject) => {
        const timer = setTimeout(() => { this.waiter = null; reject(new Error('Sem resposta do adaptador em ' + timeout + ' ms (' + cmd + ').')); }, timeout);
        this.waiter = { resolve: () => { clearTimeout(timer); resolve(); }, reject: e => { clearTimeout(timer); reject(e); } };
      });
    }
    const out = this.buf.replace(/>/g, '');
    this.buf = '';
    return out;
  }

  async close() {
    try { await this.reader.cancel(); } catch (e) { /* já fechada */ }
    try { this.reader.releaseLock(); } catch (e) { /* idem */ }
    try { this.writer.releaseLock(); } catch (e) { /* idem */ }
    try { await this.port.close(); } catch (e) { /* idem */ }
  }
}

/* ---------- transporte: carro simulado (mesmo formato de resposta do clone real) ---------- */

class DemoTransport {
  async open() { this.t0 = performance.now(); }
  async close() {}

  sim() {
    const t = (performance.now() - this.t0) / 1000;
    const ph = (t % 40) / 40;
    let speed = 0, accel = false;
    if (ph >= 0.1 && ph < 0.5) { speed = (ph - 0.1) / 0.4 * 100; accel = true; }
    else if (ph >= 0.5 && ph < 0.6) speed = 100;
    else if (ph >= 0.6 && ph < 0.9) speed = (0.9 - ph) / 0.3 * 100;
    const gear = Math.min(4, Math.floor(speed / 22));
    const frac = (speed - gear * 22) / 22;
    const rpm = speed < 1 ? 900 + Math.sin(t * 3) * 12 : 1300 + frac * (accel ? 2400 : 1200);
    const throttle = accel ? 38 : speed > 1 ? 20 : 16;
    return { t, speed, rpm, throttle, load: accel ? 62 : 24, map: accel ? 88 : 42, maf: rpm / 900 * (accel ? 6 : 2.2), pedal: accel ? 45 : 19.6 };
  }

  ecmBytes(pid, s) {
    const w = v => [(v >> 8) & 0xFF, v & 0xFF];
    switch (pid) {
      case 0x00: return [0xBE, 0x5F, 0xB8, 0x13];
      case 0x20: return [0x80, 0x07, 0xA0, 0x11];
      case 0x40: return [0xFE, 0xD0, 0xC0, 0x01];
      case 0x60: return [0x03, 0x00, 0x00, 0x01];
      case 0x80: return [0x02, 0x00, 0x20, 0x00];
      case 0x01: return [0x00, 0x07, 0xE1, 0x00];
      case 0x03: return [0x02, 0x00];
      case 0x04: return [Math.round(s.load * 2.55)];
      case 0x05: return [93 + 40];
      case 0x06: return [128 + Math.round(Math.sin(s.t) * 3)];
      case 0x07: return [128];
      case 0x0A: return [89];
      case 0x0C: return w(Math.round(s.rpm * 4));
      case 0x0D: return [Math.round(s.speed)];
      case 0x0E: return [Math.round((s.speed > 1 ? 18 : -1.5) + 64) * 2];
      case 0x0F: return [51 + 40];
      case 0x10: return w(Math.round(s.maf * 100));
      case 0x11: return [Math.round(s.throttle * 2.55)];
      case 0x14: return [Math.round((0.45 + Math.sin(s.t * 4) * 0.35) / 0.005), 0x80];
      case 0x15: return [128, 0xFF];
      case 0x1F: return w(Math.round(283 + s.t));
      case 0x21: return [0, 0];
      case 0x2E: return [0];
      case 0x2F: return [141];
      case 0x30: return [190];
      case 0x31: return [0x42, 0x2B];
      case 0x33: return [93];
      case 0x3C: return w((365 + 40) * 10);
      case 0x42: return w(13840);
      case 0x43: return w(Math.round(s.load * 2.55));
      case 0x44: return [0x80, 0x00];
      case 0x45: return [30];
      case 0x46: return [69];
      case 0x47: return [Math.round(s.throttle * 2.55)];
      case 0x49: return [Math.round(s.pedal * 2.55)];
      case 0x4A: return [Math.round(s.pedal * 1.275)];
      case 0x4C: return [Math.round(s.throttle * 2.55)];
      case 0x52: return [92];
      case 0x68: return [0x07, 0x5B, 0x64, 0x5B, 0x28, 0x28, 0x28];
      case 0x87: return [0x01, ...w(Math.round(s.map / 0.03125)), 0, 0];
      default: return null;
    }
  }

  tcmBytes(pid) {
    switch (pid) {
      case 0x00: return [0x80, 0x00, 0x00, 0x01];
      case 0x20: return [0x00, 0x00, 0x00, 0x01];
      case 0x40: return [0x40, 0x00, 0x00, 0x00];
      case 0x01: return [0x00, 0x04, 0x00, 0x00];
      case 0x42: return [0x35, 0x10];
      default: return null;
    }
  }

  // Monta os quadros CAN como o clone mostra: cabeçalho, PCI ISO-TP e preenchimento AA.
  frames(ecu, payload) {
    const head = '18DAF1' + ecu;
    const pad = arr => { while (arr.length < 8) arr.push(0xAA); return head + arr.map(hex2).join(''); };
    if (payload.length <= 7) return [pad([payload.length, ...payload])];
    const out = [pad([0x10 | (payload.length >> 8), payload.length & 0xFF, ...payload.slice(0, 6)])];
    for (let i = 6, n = 1; i < payload.length; i += 7, n++) out.push(pad([0x20 | (n & 0x0F), ...payload.slice(i, i + 7)]));
    return out;
  }

  async transact(cmd) {
    await sleep(115 + Math.random() * 40);
    if (cmd === 'ATZ') return '\r\rELM327 v2.1 (simulado)\r\r';
    if (cmd === 'ATDPN') return 'A7\r\r';
    if (cmd === 'ATRV') return '13.2V\r\r';
    if (cmd.startsWith('AT')) return 'OK\r\r';
    const ascii = str => [...str].map(c => c.charCodeAt(0));
    const replies = [];   // [ecu, payload]
    if (cmd.startsWith('01')) {
      const pids = cmd.slice(2).match(/../g).map(h => parseInt(h, 16));
      const s = this.sim();
      const build = fn => { const p = [0x41]; for (const pid of pids) { const d = fn(pid); if (d) p.push(pid, ...d); } return p.length > 1 ? p : null; };
      const e = build(pid => this.ecmBytes(pid, s)), t = build(pid => this.tcmBytes(pid));
      if (e) replies.push(['11', e]);
      if (t) replies.push(['18', t]);
    } else if (cmd === '03' || cmd === '07' || cmd === '0A') {
      const sid = 0x40 + parseInt(cmd, 16);
      replies.push(['11', [sid, 0]], ['18', [sid, 0]]);
    } else if (cmd === '0902') {
      replies.push(['11', [0x49, 0x02, 0x01, ...ascii('DEMO0000000000000')]]);
    } else if (cmd === '090A') {
      replies.push(['11', [0x49, 0x0A, 0x01, ...ascii('ECM\0-EngineControl\0\0')]], ['18', [0x49, 0x0A, 0x01, ...ascii('TCM\0-TransmisCtrl\0\0\0')]]);
    }
    if (!replies.length) return 'NO DATA\r\r';
    const sets = replies.map(([ecu, p]) => this.frames(ecu, p));
    const lines = [];
    for (let i = 0; sets.some(f => i < f.length); i++) for (const f of sets) if (i < f.length) lines.push(f[i]);
    return lines.join('\r') + '\r\r';
  }
}

/* ---------- sessão ---------- */

async function tx(cmd, timeout) {
  if (!ALLOW.some(r => r.test(cmd))) throw new Error('Comando fora da lista de permissão: ' + cmd);
  const t0 = performance.now();
  const resp = await state.transport.transact(cmd, timeout);
  const ms = Math.round(performance.now() - t0);
  state.txCount++;
  state.lastLatency = ms;
  state.log.push(`${cmd.padEnd(14)} ${String(ms).padStart(5)} ms  ${resp.split(/[\r\n]+/).map(l => l.trim()).filter(Boolean).join(' | ')}`);
  if (state.log.length > 150) state.log.shift();
  return resp;
}

async function initAdapter() {
  const z = await tx('ATZ', 6000);
  state.info.adapter = (z.split(/[\r\n]+/).map(l => l.trim()).find(l => /ELM/i.test(l)) || 'ELM327 (sem identificação)');
  for (const c of ['ATE0', 'ATL0', 'ATS0', 'ATH1', 'ATSP0']) await tx(c, 3000);
}

async function readSupport() {
  state.supported = new Set();
  for (let base = 0; base <= 0xC0; base += 0x20) {
    const { msgs, flags } = parseFrames(await tx('01' + hex2(base), 15000));
    let any = false, more = false;
    for (const m of msgs) for (const [pid, d] of mode01(m.data)) {
      if (pid !== base) continue;
      any = true;
      for (let i = 0; i < 32; i++) if (d[i >> 3] & (0x80 >> (i & 7))) { state.supported.add(base + i + 1); if (i === 31) more = true; }
    }
    if (base === 0 && !any) throw new Error('O carro não respondeu ao pedido 0100' + (flags.length ? ' (' + flags.join(', ') + ')' : '') + '. A ignição está ligada?');
    if (!more) break;
  }
}

async function readIdentity() {
  const vin = parseFrames(await tx('0902', 15000)).msgs.find(x => x.data[0] === 0x49 && x.data[1] === 0x02);
  if (vin) state.info.vin = String.fromCharCode(...vin.data.slice(3).filter(c => c >= 0x20 && c < 0x7F));
  const { msgs } = parseFrames(await tx('090A', 15000));
  const names = msgs.filter(x => x.data[0] === 0x49 && x.data[1] === 0x0A)
    .map(x => x.ecu + ' ' + String.fromCharCode(...x.data.slice(3).filter(c => c >= 0x20 && c < 0x7F)));
  if (names.length) state.info.modules = names.join(' · ');
}

async function readDtcs() {
  const result = [];
  for (const [cmd, kind] of [['03', 'confirmado'], ['07', 'pendente'], ['0A', 'permanente']]) {
    const sid = 0x40 + parseInt(cmd, 16);
    const { msgs } = parseFrames(await tx(cmd, 15000));
    for (const m of msgs) {
      if (m.data[0] !== sid) continue;
      for (let i = 2; i + 1 < m.data.length; i += 2) {
        if (m.data[i] === 0 && m.data[i + 1] === 0) continue;
        result.push({ code: dtcCode(m.data[i], m.data[i + 1]), kind, ecu: m.ecu });
      }
    }
  }
  state.dtc = { list: result, t: Date.now() };
}

// Um pedido do modo 01 com um ou mais PIDs; devolve quantos PIDs vieram decodificados.
async function poll(pids) {
  const { msgs } = parseFrames(await tx('01' + pids.map(hex2).join(''), 6000));
  let count = 0;
  for (const m of msgs) for (const [pid, d] of mode01(m.data)) {
    const def = PID[pid];
    if (!def.dec) continue;
    const values = def.dec(d, m.ecu);
    for (const k in values) setSignal(k, values[k]);
    if (pid === 0x0C) { const now = performance.now(); state.rpmTimes.push(now); while (state.rpmTimes[0] < now - 2000) state.rpmTimes.shift(); }
    count++;
  }
  if (!count) state.errCount++;
  return count;
}

async function pollAdapterVoltage() {
  const m = /(\d+\.\d)V/.exec(await tx('ATRV', 3000));
  if (m) setSignal('vAdapter', parseFloat(m[1]));
}

async function run(transport, demo) {
  if (state.running) return;
  Object.assign(state, { running: true, demo, transport, signals: {}, singles: false, multiFail: 0, rpmTimes: [], txCount: 0, errCount: 0, log: [], dtc: null, info: {}, wantDtc: true });
  $('demoBanner').hidden = !demo;
  warn('');
  $('btnConnect').hidden = $('btnDemo').hidden = true;
  $('btnStop').hidden = false;
  keepAwake(true);
  let finalStatus = ['Desconectado', ''];
  try {
    setStatus(demo ? 'Iniciando simulação…' : 'Escolha a porta do adaptador…');
    await transport.open();

    setStatus('Configurando o adaptador…');
    await initAdapter();

    setStatus('Procurando o protocolo do carro…');
    await readSupport();
    const proto = (await tx('ATDPN', 3000)).replace(/[^0-9A-F]/gi, '').toUpperCase();
    const num = proto.replace(/^A/, '');
    state.info.proto = ({ 6: 'CAN 11 bits, 500 kbps', 7: 'CAN 29 bits, 500 kbps', 8: 'CAN 11 bits, 250 kbps', 9: 'CAN 29 bits, 250 kbps' })[num] || ('Protocolo ' + proto);
    if (!'6789'.includes(num) || !num) warn('Esta página só entende respostas CAN; neste protocolo as leituras podem não aparecer.');
    await readIdentity();

    const keep = g => g.filter(p => state.supported.has(p));
    const fast = keep(FAST);
    const medium = MEDIUM.map(keep).filter(g => g.length);
    const slow = SLOW.map(keep).filter(g => g.length);
    const others = [...medium.flat(), ...slow.flat()];
    if (!fast.length && !others.length) throw new Error('O carro não anuncia nenhum dos PIDs que esta página lê.');

    setStatus(demo ? 'Simulação em andamento' : 'Conectado, lendo o carro', 'ok');
    $('btnDtc').disabled = false;

    // Pedidos combinados: o resultado decide se continuamos combinando ou voltamos a um PID por vez.
    const multi = async pids => {
      if (!pids.length) return;
      let n;
      try { n = await poll(pids); }
      catch (e) {
        // Sem resposta num pedido combinado: o clone costuma ter reiniciado. Reconfigura e segue um PID por vez.
        if (pids.length < 2 || state.singles || state.transport.dead || !state.running) throw e;
        state.singles = true;
        state.errCount++;
        warn('O adaptador travou num pedido combinado; foi reiniciado e a leitura segue um PID por vez (mais lento).');
        await sleep(500);
        await initAdapter();
        await tx('0100', 15000);
        return;
      }
      if (pids.length > 1) {
        if (n === 0) {
          if (++state.multiFail >= 3) { state.singles = true; warn('O adaptador não respondeu aos pedidos combinados; lendo um PID por vez (mais lento).'); }
        } else state.multiFail = 0;
      }
    };

    let mi = 0, si = 0, oi = 0, fi = 0;
    while (state.running) {
      if (state.wantDtc) { state.wantDtc = false; await readDtcs(); }
      if (state.singles) {
        for (const p of fast) { if (!state.running) break; await poll([p]); }
        if (others.length && state.running) await poll([others[oi++ % others.length]]);
        if (++fi % 40 === 0) await pollAdapterVoltage();
        continue;
      }
      await multi(fast);
      if (medium.length) await multi(medium[mi++ % medium.length]);
      await multi(fast);
      if (medium.length > 1) await multi(medium[mi++ % medium.length]);
      await multi(fast);
      const turn = si++ % (slow.length + 1);
      if (turn === slow.length) await pollAdapterVoltage(); else await multi(slow[turn]);
      if (!fast.length && !medium.length) await sleep(200);
    }
  } catch (e) {
    if (e && e.name === 'NotFoundError') finalStatus = ['Nenhuma porta escolhida', ''];
    else finalStatus = ['Conexão encerrada: ' + (e && e.message ? e.message : e), 'erro'];
  } finally {
    state.running = false;
    keepAwake(false);
    await transport.close();
    state.transport = null;
    $('btnConnect').hidden = $('btnDemo').hidden = false;
    $('btnStop').hidden = true;
    $('btnDtc').disabled = true;
    setStatus(...finalStatus);
  }
}

/* ---------- tela ---------- */

const esc = t => String(t).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]);
const NS = 'http://www.w3.org/2000/svg';

function svgEl(parent, tag, attrs, text) {
  const el = document.createElementNS(NS, tag);
  for (const k in attrs) el.setAttribute(k, attrs[k]);
  if (text != null) el.textContent = text;
  parent.appendChild(el);
  return el;
}

// Mostrador de 270°: começa embaixo à esquerda e termina embaixo à direita.
const polar = (f, r) => { const a = (135 + 270 * f) * Math.PI / 180; return [100 + r * Math.cos(a), 100 + r * Math.sin(a)]; };
const arcPath = (f0, f1, r) => {
  const [x0, y0] = polar(f0, r), [x1, y1] = polar(f1, r);
  return `M${x0.toFixed(2)} ${y0.toFixed(2)} A${r} ${r} 0 ${(f1 - f0) * 270 > 180 ? 1 : 0} 1 ${x1.toFixed(2)} ${y1.toFixed(2)}`;
};

const gauges = {};
function buildGauge(id, { key, max, minor, major, fmt, unit, cls, red }) {
  const svg = $(id);
  svgEl(svg, 'path', { class: 'track', d: arcPath(0, 1, 80) });
  if (red) svgEl(svg, 'path', { class: 'red', d: arcPath(red / max, 1, 93) });
  for (let v = 0; v <= max + 1e-9; v += minor) {
    const f = v / max, isMajor = Math.abs(v / major - Math.round(v / major)) < 1e-9;
    const [x0, y0] = polar(f, isMajor ? 61 : 64), [x1, y1] = polar(f, 69);
    svgEl(svg, 'line', { class: 'tick' + (isMajor ? ' major' : ''), x1: x0.toFixed(2), y1: y0.toFixed(2), x2: x1.toFixed(2), y2: y1.toFixed(2) });
    if (isMajor) { const [lx, ly] = polar(f, 51); svgEl(svg, 'text', { class: 'tick-label', x: lx.toFixed(2), y: ly.toFixed(2) }, fmt(v)); }
  }
  const fill = svgEl(svg, 'path', { class: 'fill ' + cls, d: arcPath(0, 1, 80), pathLength: 100, 'stroke-dasharray': '0 100', visibility: 'hidden' });
  const value = svgEl(svg, 'text', { class: 'value waiting', x: 100, y: 126 }, 'sem leitura');
  svgEl(svg, 'text', { class: 'unit', x: 100, y: 146 }, unit);
  gauges[id] = { svg, fill, value, key, max, cls, red };
}

function renderGauge(id, now) {
  const g = gauges[id], sig = state.signals[g.key];
  if (!sig) {
    g.fill.setAttribute('visibility', 'hidden');
    g.value.setAttribute('class', 'value waiting');
    g.value.textContent = state.running ? 'aguardando…' : 'sem leitura';
    g.svg.classList.remove('stale');
    return;
  }
  const f = Math.max(0, Math.min(1, sig.v / g.max));
  g.fill.setAttribute('visibility', f > 0.004 ? 'visible' : 'hidden');
  g.fill.setAttribute('stroke-dasharray', `${(f * 100).toFixed(1)} 100`);
  g.fill.setAttribute('class', 'fill ' + (g.red && sig.v >= g.red ? 'redzone' : g.cls));
  g.value.setAttribute('class', 'value');
  g.value.textContent = Math.round(sig.v);
  g.svg.classList.toggle('stale', now - sig.t > 3000);
}

const META = Object.fromEntries(GROUPS.flatMap(g => g[1]).map(([key, label, unit, dp, pid, sub]) => [key, { label, unit, dp, pid, sub }]));
const STALE_MS = 15000;

function buildGroups() {
  const root = $('grupos');
  for (const [title, items] of GROUPS) {
    const box = document.createElement('section');
    box.className = 'box';
    box.innerHTML = `<div class="box-header"><h2>${esc(title)}</h2><span class="counter"></span></div><div class="tiles"></div><div class="box-footer missing" hidden></div>`;
    const grid = box.querySelector('.tiles');
    for (const [key, label, unit, , , sub] of items) {
      const tile = document.createElement('div');
      tile.className = 'tile' + (unit === 'texto' ? ' text wide' : '');
      tile.dataset.key = key;
      const bar = unit === '%' && !NO_BAR.has(key) ? '<div class="bar"><span></span></div>' : '';
      tile.innerHTML = `<div class="name">${esc(label)}${sub ? ` <small>${esc(sub)}</small>` : ''}</div><div class="val waiting">sem leitura</div>${bar}`;
      grid.appendChild(tile);
    }
    root.appendChild(box);
  }
}

function formatValue(key, v) {
  const { unit, dp } = META[key];
  if (unit === 'texto') return esc(v);
  if (unit === 'tempo') { const m = Math.floor(v / 60), s = Math.floor(v % 60); return `${m}:${String(s).padStart(2, '0')}<small>min</small>`; }
  let t = v.toLocaleString('pt-BR', { minimumFractionDigits: dp, maximumFractionDigits: dp });
  if (/^-0(,0*)?$/.test(t)) t = t.slice(1);
  return t + (unit ? `<small>${unit}</small>` : '');
}

// Um sinal some da tela quando o carro já disse quais PIDs suporta e o dele não está na lista.
const unsupported = key => { const p = META[key].pid; return state.supported.size > 0 && p != null && !state.supported.has(p); };

let showVin = false;

function render() {
  const now = performance.now();
  renderGauge('gSpeed', now);
  renderGauge('gRpm', now);

  for (const box of $('grupos').children) {
    let shown = 0, live = 0;
    const missing = [];
    for (const tile of box.querySelectorAll('.tile')) {
      const key = tile.dataset.key, sig = state.signals[key], meta = META[key];
      const gone = !sig && unsupported(key);
      tile.hidden = gone;
      if (gone) { missing.push(meta.sub ? `${meta.label} (${meta.sub})` : meta.label); continue; }
      shown++;
      if (sig) live++;
      const level = sig && LEVEL[key] && typeof sig.v === 'number' ? LEVEL[key](sig.v) : '';
      tile.className = 'tile' + (meta.unit === 'texto' ? ' text wide' : '') + (level ? ' ' + level : '') + (sig && now - sig.t > STALE_MS ? ' stale' : '');
      const val = tile.querySelector('.val');
      const html = sig ? formatValue(key, sig.v) : state.running ? 'aguardando…' : 'sem leitura';
      if (val.innerHTML !== html) val.innerHTML = html;
      val.classList.toggle('waiting', !sig);
      const bar = tile.querySelector('.bar span');
      if (bar) bar.style.width = sig ? Math.max(0, Math.min(100, sig.v)).toFixed(1) + '%' : '0';
    }
    box.querySelector('.counter').textContent = state.running || live ? `${live} de ${shown}` : shown;
    const foot = box.querySelector('.missing');
    foot.hidden = !missing.length;
    if (missing.length) foot.textContent = 'O carro não informa: ' + missing.join(', ') + '.';
  }

  // Luz de injeção e contagem, somando as centrais que responderam ao 0101
  const mils = Object.keys(state.signals).filter(k => k.startsWith('mil_'));
  const mil = $('milLabel');
  if (!mils.length) { mil.className = 'state-label'; mil.textContent = 'Luz de injeção: --'; }
  else {
    const on = mils.some(k => state.signals[k].v);
    const count = Object.keys(state.signals).filter(k => k.startsWith('dtcCount_')).reduce((a, k) => a + state.signals[k].v, 0);
    mil.className = 'state-label ' + (on ? 'danger' : 'success');
    mil.textContent = on ? `Luz acesa · ${count} código${count === 1 ? '' : 's'}` : 'Luz apagada';
  }

  let html;
  if (!state.dtc) html = `<div class="blankslate"><svg class="octicon"><use href="#i-info"/></svg><b>Sem leitura ainda</b><span>${state.running ? 'Lendo os códigos…' : 'Os códigos são lidos ao conectar.'}</span></div>`;
  else if (!state.dtc.list.length) html = `<div class="blankslate ok"><svg class="octicon"><use href="#i-check"/></svg><b>Nenhum código</b><span>Confirmados, pendentes e permanentes · lido às ${new Date(state.dtc.t).toLocaleTimeString('pt-BR')}</span></div>`;
  else html = '<ul class="dtc-list">' + state.dtc.list.map(d =>
    `<li><code>${esc(d.code)}</code><span class="label ${({ confirmado: 'danger', pendente: 'attention' })[d.kind] || ''}">${esc(d.kind)}</span><span class="ecu">${({ 11: 'motor', 18: 'câmbio' })[d.ecu] || 'central ' + esc(d.ecu)}</span></li>`).join('') + '</ul>';
  const area = $('dtcArea');
  if (area.dataset.html !== html) { area.innerHTML = html; area.dataset.html = html; }

  const i = state.info;
  const vin = i.vin || '';
  $('btnVin').hidden = !vin;
  $('iVin').textContent = !vin ? '--' : showVin ? vin : vin.slice(0, 3) + '•'.repeat(Math.max(0, vin.length - 7)) + vin.slice(-4);
  $('btnVin').textContent = showVin ? 'ocultar' : 'mostrar';
  $('iModules').textContent = i.modules || '--';
  $('iProto').textContent = i.proto || '--';
  $('iAdapter').textContent = i.adapter || '--';
  $('iMode').textContent = !state.running ? '--' : state.singles ? 'Um PID por vez' : 'Pedidos combinados';
  const t = state.rpmTimes.filter(x => x > now - 2000);
  const hz = t.length >= 2 ? ((t.length - 1) / ((t[t.length - 1] - t[0]) / 1000)).toFixed(1).replace('.', ',') + ' Hz' : null;
  $('iHz').textContent = hz || '--';
  $('hzLabel').hidden = !(state.running && hz);
  $('hzLabel').textContent = hz || '';
  $('iLat').textContent = state.lastLatency == null ? '--' : state.lastLatency + ' ms';
  $('iCount').textContent = state.txCount ? `${state.txCount} / ${state.errCount}` : '--';
  if ($('logBox').open) $('log').textContent = state.log.slice(-60).join('\n');
}

/* ---------- início ---------- */

buildGauge('gSpeed', { key: 'speed', max: 220, minor: 10, major: 40, fmt: v => v, unit: 'km/h', cls: 'speed' });
buildGauge('gRpm', { key: 'rpm', max: 8000, minor: 500, major: 1000, fmt: v => v / 1000, unit: 'rpm × 1000', cls: 'rpm', red: 6500 });
buildGroups();
render();
setInterval(render, 200);

$('btnConnect').addEventListener('click', () => run(new SerialTransport(), false));
$('btnDemo').addEventListener('click', () => run(new DemoTransport(), true));
$('btnStop').addEventListener('click', () => { state.running = false; setStatus('Encerrando…'); });
$('btnDtc').addEventListener('click', () => { state.wantDtc = true; });
$('btnVin').addEventListener('click', () => { showVin = !showVin; render(); });
$('btnFull').addEventListener('click', () => {
  if (document.fullscreenElement) document.exitFullscreen(); else document.documentElement.requestFullscreen().catch(() => {});
});
document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible' && state.running) keepAwake(true); });

if (!('serial' in navigator)) {
  $('btnConnect').disabled = true;
  warn('Este navegador não tem a Web Serial API. Abra a página no Chrome ou no Edge do computador para conectar ao ELM327; o modo demo funciona em qualquer navegador.', 'info');
}

// Para conferência no console
window.obd = { parseFrames, mode01, dtcCode, state, PID };
