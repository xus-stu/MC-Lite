/**
 * 画境 · Host 半边
 *
 * 只做三件事：
 *   1. 把配置持久化到 DSH profile 的 data 目录（删除对话、重启 DSH 都不会丢）；
 *   2. 给浏览器提供同源的配置读写与图片/视频上传、播放接口；
 *   3. 在 index.html 里注入一份启动配置，让 Client 半边在首屏就拿到上次的画风。
 *
 * 配置不写进 session，也不依赖当前对话，所以删掉对话后依然有效。
 */

import { randomUUID } from 'node:crypto';
import {
  createReadStream,
  createWriteStream,
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  renameSync,
  statSync,
  unlinkSync,
  writeFileSync,
} from 'node:fs';
import { homedir } from 'node:os';
import path from 'node:path';

export const inject = ['webServer'];

const PREFIX = '/huanjing-api';
const MAX_STATE_BYTES = 1024 * 1024;
const MAX_MEDIA_BYTES = 1024 * 1024 * 1024;

const THEME_IDS = new Set([
  'original',
  'fresh-green',
  'ink-bamboo',
  'sakura',
  'sea-salt',
  'warm-sun',
  'mist-violet',
]);

const FONT_IDS = new Set(['system', 'kai', 'hand-kai', 'song', 'round', 'lxgw', 'mono']);
const MODEL_IDS = new Set(['deepseek-flash', 'deepseek-v4-pro']);
const PERIOD_MODES = new Set(['auto', 'peak', 'off']);

const FONT_STACKS = {
  kai: '"Kaiti SC", "STKaiti", "KaiTi", "楷体", "BiauKai", serif',
  'hand-kai': '"Xingkai SC", "STXingkai", "华文行楷", "Kaiti SC", "KaiTi", "楷体", cursive, serif',
  song: '"Songti SC", "STSong", "SimSun", "Noto Serif CJK SC", serif',
  round: '"Yuanti SC", "YouYuan", "PingFang SC", "Microsoft YaHei", sans-serif',
  lxgw: '"LXGW WenKai", "霞鹜文楷", "Kaiti SC", "KaiTi", serif',
  mono: 'ui-monospace, "SF Mono", Menlo, Consolas, monospace',
};

const EXT_BY_MIME = {
  'image/png': 'png',
  'image/jpeg': 'jpg',
  'image/webp': 'webp',
  'image/gif': 'gif',
  'image/avif': 'avif',
  'video/mp4': 'mp4',
  'video/webm': 'webm',
  'video/quicktime': 'mov',
  'video/x-m4v': 'm4v',
  'audio/mpeg': 'mp3',
  'audio/mp3': 'mp3',
  'audio/wav': 'wav',
  'audio/x-wav': 'wav',
  'audio/ogg': 'ogg',
  'audio/mp4': 'm4a',
  'audio/x-m4a': 'm4a',
  'audio/aac': 'aac',
  'audio/flac': 'flac',
  'audio/x-flac': 'flac',
};

const MIME_BY_EXT = {
  png: 'image/png',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  webp: 'image/webp',
  gif: 'image/gif',
  avif: 'image/avif',
  mp4: 'video/mp4',
  webm: 'video/webm',
  mov: 'video/quicktime',
  m4v: 'video/x-m4v',
  mp3: 'audio/mpeg',
  wav: 'audio/wav',
  ogg: 'audio/ogg',
  m4a: 'audio/mp4',
  aac: 'audio/aac',
  flac: 'audio/flac',
};

const SAFE_MEDIA_NAME = /^[0-9a-f-]{20,}\.(?:png|jpe?g|webp|gif|avif|mp4|webm|mov|m4v|mp3|wav|ogg|m4a|aac|flac)$/i;

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function defaultState() {
  return {
    version: 1,
    theme: 'fresh-green',
    font: 'kai',
    splash: {
      enabled: false,
      media: null,
      duration: 2200,
      fade: 520,
      fit: 'cover',
      music: {
        enabled: false,
        media: null,
        tracks: [],
        currentId: '',
        volume: 0.6,
        loop: true,
        mode: 'splash',
        dock: null,
      },
    },
    cost: {
      show: true,
      showPeriod: true,
      model: 'deepseek-flash',
      period: 'auto',
      writeAsMiss: true,
    },
    updatedAt: 0,
  };
}

function text(value, max) {
  return typeof value === 'string' ? value.slice(0, max) : '';
}

function safeMediaUrl(value) {
  if (typeof value !== 'string') return null;
  const raw = value.trim();
  if (raw.length === 0 || raw.length > 1200) return null;
  if (raw.startsWith(`${PREFIX}/media/`)) return raw;
  if (raw.startsWith('/api/huanjing/media/')) return `${PREFIX}/media/${raw.slice('/api/huanjing/media/'.length)}`;
  try {
    const url = new URL(raw);
    if (url.protocol === 'http:' || url.protocol === 'https:') return raw;
  } catch {
    // 不是绝对 URL，拒绝。
  }
  return null;
}

function normalizeState(input) {
  const base = defaultState();
  const source = input && typeof input === 'object' && !Array.isArray(input) ? input : {};
  const splash = source.splash && typeof source.splash === 'object' ? source.splash : {};
  const cost = source.cost && typeof source.cost === 'object' ? source.cost : {};

  let media = null;
  if (splash.media && typeof splash.media === 'object') {
    const url = safeMediaUrl(splash.media.url);
    if (url) {
      const kind = splash.media.kind === 'video' ? 'video' : 'image';
      media = {
        kind,
        url,
        name: text(splash.media.name, 180) || (kind === 'video' ? '启动视频' : '启动图片'),
        mime: text(splash.media.mime, 80),
      };
    }
  }

  const duration = Number(splash.duration);
  const fade = Number(splash.fade);
  const musicSource = splash.music && typeof splash.music === 'object' ? splash.music : {};
  let musicMedia = null;
  if (musicSource.media && typeof musicSource.media === 'object') {
    const url = safeMediaUrl(musicSource.media.url);
    if (url) {
      musicMedia = {
        kind: 'audio',
        url,
        name: text(musicSource.media.name, 180) || '启动音乐',
        mime: text(musicSource.media.mime, 80),
      };
    }
  }
  const rawTracks = Array.isArray(musicSource.tracks) ? musicSource.tracks.slice(0, 100) : [];
  const tracks = [];
  const usedIds = new Set();
  rawTracks.forEach((track, index) => {
    if (!track || typeof track !== 'object') return;
    const url = safeMediaUrl(track.url);
    if (!url) return;
    let id = text(track.id, 80) || `track-${index + 1}`;
    while (usedIds.has(id)) id = `${id}-${index + 1}`;
    usedIds.add(id);
    tracks.push({
      id,
      name: text(track.name, 180) || `音乐 ${index + 1}`,
      url,
      mime: text(track.mime, 80),
      lyrics: text(track.lyrics, 100000),
    });
  });
  if (tracks.length === 0 && musicMedia) {
    tracks.push({
      id: 'legacy-1',
      name: musicMedia.name || '启动音乐',
      url: musicMedia.url,
      mime: musicMedia.mime || '',
      lyrics: '',
    });
  }
  const requestedCurrent = text(musicSource.currentId, 80);
  const currentTrack = tracks.find((track) => track.id === requestedCurrent) || tracks[0] || null;
  const currentId = currentTrack ? currentTrack.id : '';
  const musicMediaMirror = currentTrack
    ? { kind: 'audio', url: currentTrack.url, name: currentTrack.name, mime: currentTrack.mime || '' }
    : null;
  const dockSource = musicSource.dock && typeof musicSource.dock === 'object' ? musicSource.dock : null;
  const dock = dockSource && Number.isFinite(Number(dockSource.x)) && Number.isFinite(Number(dockSource.y))
    ? { x: Math.min(100000, Math.max(0, Number(dockSource.x))), y: Math.min(100000, Math.max(0, Number(dockSource.y))) }
    : null;
  const volume = Number(musicSource.volume);

  return {
    version: 1,
    theme: THEME_IDS.has(source.theme) ? source.theme : base.theme,
    font: FONT_IDS.has(source.font) ? source.font : base.font,
    splash: {
      enabled: splash.enabled === true,
      media,
      duration: Number.isFinite(duration) ? Math.min(12000, Math.max(800, Math.round(duration))) : base.splash.duration,
      fade: Number.isFinite(fade) ? Math.min(1500, Math.max(120, Math.round(fade))) : base.splash.fade,
      fit: splash.fit === 'contain' ? 'contain' : (splash.fit === 'auto' ? 'auto' : (splash.fit === 'window' ? 'window' : 'cover')),
      music: {
        enabled: musicSource.enabled === true,
        media: musicMediaMirror,
        tracks,
        currentId,
        volume: Number.isFinite(volume) ? Math.min(1, Math.max(0, volume)) : base.splash.music.volume,
        loop: musicSource.loop !== false,
        mode: musicSource.mode === 'always' ? 'always' : 'splash',
        dock,
      },
    },
    cost: {
      show: cost.show !== false,
      showPeriod: cost.showPeriod !== false,
      model: MODEL_IDS.has(cost.model) ? cost.model : base.cost.model,
      period: PERIOD_MODES.has(cost.period) ? cost.period : base.cost.period,
      writeAsMiss: cost.writeAsMiss !== false,
    },
    updatedAt: Number.isFinite(Number(source.updatedAt)) ? Number(source.updatedAt) : Date.now(),
  };
}

function readState(file) {
  try {
    return normalizeState(JSON.parse(readFileSync(file, 'utf8')));
  } catch {
    return { ...defaultState(), updatedAt: Date.now() };
  }
}

function writeState(file, state) {
  const temp = `${file}.${process.pid}.${Date.now()}.tmp`;
  writeFileSync(temp, JSON.stringify(state, null, 2), 'utf8');
  renameSync(temp, file);
}

function pruneMedia(dir, state) {
  const keep = new Set();
  const media = state && state.splash ? state.splash.media : null;
  if (media && typeof media.url === 'string' && media.url.startsWith(`${PREFIX}/media/`)) {
    keep.add(path.basename(media.url));
  }
  const music = state && state.splash && state.splash.music ? state.splash.music : null;
  if (music) {
    const urls = [];
    if (music.media && typeof music.media.url === 'string') urls.push(music.media.url);
    if (Array.isArray(music.tracks)) {
      for (const track of music.tracks) {
        if (track && typeof track.url === 'string') urls.push(track.url);
      }
    }
    for (const url of urls) {
      if (url.startsWith(`${PREFIX}/media/`)) keep.add(path.basename(url));
    }
  }
  let names = [];
  try {
    names = readdirSync(dir);
  } catch {
    return;
  }
  const day = 24 * 60 * 60 * 1000;
  for (const name of names) {
    if (keep.has(name) || name.startsWith('.')) continue;
    const file = path.join(dir, name);
    try {
      const info = statSync(file);
      if (info.isFile() && Date.now() - info.mtimeMs > day) unlinkSync(file);
    } catch {
      // 清理失败不影响主流程。
    }
  }
}

function isLoopbackAddress(address) {
  if (typeof address !== 'string') return false;
  return address === '127.0.0.1' || address === '::1' || address.startsWith('::ffff:127.');
}

function isSameOrigin(req) {
  const origin = req.headers.origin;
  if (!origin) return true;
  try {
    return new URL(origin).host === req.headers.host;
  } catch {
    return false;
  }
}

function sendJson(res, status, value) {
  const body = Buffer.from(JSON.stringify(value), 'utf8');
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': body.length,
    'cache-control': 'no-store',
    'x-content-type-options': 'nosniff',
  });
  res.end(body);
}

async function readBody(req, maxBytes) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > maxBytes) throw new HttpError(413, '请求体过大');
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString('utf8');
}

async function receiveMedia(req, mediaDir) {
  const mime = String(req.headers['content-type'] || '').split(';')[0].trim().toLowerCase();
  const ext = EXT_BY_MIME[mime];
  if (!ext) {
    throw new HttpError(415, '仅支持图片、视频或音频文件（PNG/JPG/WebP/GIF/AVIF、MP4/WebM/MOV/M4V、MP3/WAV/OGG/M4A/AAC/FLAC）');
  }

  const declared = Number(req.headers['content-length']);
  if (Number.isFinite(declared) && declared > MAX_MEDIA_BYTES) {
    throw new HttpError(413, '文件超过 1 GB');
  }

  mkdirSync(mediaDir, { recursive: true });
  const id = randomUUID();
  const temp = path.join(mediaDir, `.${id}.upload`);
  const finalName = `${id}.${ext}`;
  const finalPath = path.join(mediaDir, finalName);

  let size = 0;
  try {
    await new Promise((resolve, reject) => {
      let settled = false;
      const finish = (fn, value) => {
        if (settled) return;
        settled = true;
        fn(value);
      };
      const stream = createWriteStream(temp);
      req.on('data', (chunk) => {
        size += chunk.length;
        if (size > MAX_MEDIA_BYTES) {
          finish(reject, new HttpError(413, '文件超过 1 GB'));
          req.destroy();
          stream.destroy();
          return;
        }
        if (!stream.write(chunk)) {
          req.pause();
          stream.once('drain', () => req.resume());
        }
      });
      req.on('end', () => {
        stream.end(() => finish(resolve));
      });
      req.on('error', (error) => finish(reject, error));
      stream.on('error', (error) => finish(reject, error));
    });
    if (size === 0) throw new HttpError(400, '上传内容为空');
    renameSync(temp, finalPath);
  } catch (error) {
    try {
      if (existsSync(temp)) unlinkSync(temp);
    } catch {
      // 忽略清理错误。
    }
    throw error;
  }

  const incomingName = (() => {
    try {
      return decodeURIComponent(String(req.headers['x-file-name'] || ''));
    } catch {
      return '';
    }
  })();
  const fallbackName = `${mime.startsWith('video/') ? '启动视频' : '启动图片'}.${ext}`;
  const media = {
    kind: mime.startsWith('video/') ? 'video' : (mime.startsWith('audio/') ? 'audio' : 'image'),
    url: `${PREFIX}/media/${finalName}`,
    name: (incomingName || fallbackName).slice(0, 180),
    mime,
  };
  return { media, size };
}

function parseRange(header, size) {
  const match = /^bytes=(\d*)-(\d*)$/.exec(String(header || '').trim());
  if (!match) return null;
  let start = match[1] === '' ? null : Number(match[1]);
  let end = match[2] === '' ? null : Number(match[2]);
  if (start === null && end === null) return null;
  if (start === null) {
    start = Math.max(0, size - end);
    end = size - 1;
  } else if (end === null || end >= size) {
    end = size - 1;
  }
  if (!Number.isFinite(start) || !Number.isFinite(end) || start > end || start >= size) {
    return { invalid: true };
  }
  return { start, end };
}

function serveMedia(req, res, mediaDir, name) {
  if (!SAFE_MEDIA_NAME.test(name)) {
    sendJson(res, 404, { ok: false, error: '媒体不存在' });
    return;
  }
  const file = path.join(mediaDir, name);
  if (!existsSync(file)) {
    sendJson(res, 404, { ok: false, error: '媒体不存在' });
    return;
  }
  let info;
  try {
    info = statSync(file);
  } catch {
    sendJson(res, 404, { ok: false, error: '媒体不存在' });
    return;
  }
  if (!info.isFile()) {
    sendJson(res, 404, { ok: false, error: '媒体不存在' });
    return;
  }

  const ext = name.split('.').pop().toLowerCase();
  const common = {
    'content-type': MIME_BY_EXT[ext] || 'application/octet-stream',
    'accept-ranges': 'bytes',
    'cache-control': 'public, max-age=31536000, immutable',
    'x-content-type-options': 'nosniff',
    'content-disposition': 'inline',
  };

  if (req.method === 'HEAD') {
    res.writeHead(200, { ...common, 'content-length': info.size });
    res.end();
    return;
  }

  const range = req.headers.range ? parseRange(req.headers.range, info.size) : null;
  if (range && range.invalid) {
    res.writeHead(416, {
      ...common,
      'content-range': `bytes */${info.size}`,
      'content-length': 0,
    });
    res.end();
    return;
  }

  if (range) {
    const length = range.end - range.start + 1;
    res.writeHead(206, {
      ...common,
      'content-range': `bytes ${range.start}-${range.end}/${info.size}`,
      'content-length': length,
    });
    const stream = createReadStream(file, { start: range.start, end: range.end });
    stream.on('error', () => res.destroy());
    stream.pipe(res);
    return;
  }

  res.writeHead(200, { ...common, 'content-length': info.size });
  const stream = createReadStream(file);
  stream.on('error', () => res.destroy());
  stream.pipe(res);
}

function escapeForInlineJson(value) {
  return JSON.stringify(value)
    .replace(/</g, '\\u003c')
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029');
}

function injectBoot(html, state) {
  const stack = FONT_STACKS[state.font] || '';
  const fontStyle = stack
    ? `<style id="huanjing-preboot-font">:root,body{--dsw-font-family:${stack}!important;--dsw-font-family-brand:${stack}!important;--dsh-font-family-text:${stack}!important}body,body :not(code):not(pre):not(kbd):not(samp):not(pre *):not(code *):not(kbd *):not(samp *){font-family:${stack}!important}body :is(code,pre,kbd,samp){font-family:ui-monospace,"SF Mono",Menlo,Consolas,monospace!important}</style>`
    : '';
  const boot = {
    state,
    injectedAt: Date.now(),
  };
  const script = `<script id="huanjing-boot">window.__HUANJING_BOOT__=${escapeForInlineJson(boot)};</script>`;
  const block = `${fontStyle}${script}`;
  const index = html.search(/<\/head>/i);
  if (index >= 0) return `${html.slice(0, index)}${block}${html.slice(index)}`;
  return `${block}${html}`;
}

function resolveDataDir() {
  const profileDir = process.env.DSH_PROFILE_DIR
    ? process.env.DSH_PROFILE_DIR
    : path.join(homedir(), '.dsh', 'profiles', process.env.DSH_PROFILE || 'desktop');
  return path.join(profileDir, 'data', 'huanjing');
}

export function apply(ctx) {
  const dataDir = resolveDataDir();
  const mediaDir = path.join(dataDir, 'media');
  const stateFile = path.join(dataDir, 'state.json');

  mkdirSync(mediaDir, { recursive: true });
  let state = readState(stateFile);

  const handler = async (req, res) => {
    try {
      const remote = req.socket && req.socket.remoteAddress;
      if (!isLoopbackAddress(remote)) {
        sendJson(res, 403, { ok: false, error: '画境仅接受本机访问' });
        return;
      }
      if (req.method !== 'GET' && req.method !== 'HEAD' && !isSameOrigin(req)) {
        sendJson(res, 403, { ok: false, error: '请求来源不合法' });
        return;
      }
      const url = new URL(req.url || '/', 'http://127.0.0.1');
      const pathname = url.pathname;

      if (pathname === `${PREFIX}/state`) {
        if (req.method === 'GET') {
          sendJson(res, 200, { ok: true, state, dataDir });
          return;
        }
        if (req.method === 'POST') {
          let parsed;
          try {
            parsed = JSON.parse(await readBody(req, MAX_STATE_BYTES));
          } catch (error) {
            if (error instanceof HttpError) throw error;
            throw new HttpError(400, '配置不是合法的 JSON');
          }
          const next = normalizeState(parsed);
          next.updatedAt = Date.now();
          writeState(stateFile, next);
          state = next;
          pruneMedia(mediaDir, state);
          sendJson(res, 200, { ok: true, state });
          return;
        }
        sendJson(res, 405, { ok: false, error: 'Method Not Allowed' });
        return;
      }

      if (pathname === `${PREFIX}/media`) {
        if (req.method !== 'POST') {
          sendJson(res, 405, { ok: false, error: 'Method Not Allowed' });
          return;
        }
        const result = await receiveMedia(req, mediaDir);
        sendJson(res, 200, { ok: true, ...result });
        return;
      }

      const mediaPrefix = `${PREFIX}/media/`;
      if (pathname.startsWith(mediaPrefix) && (req.method === 'GET' || req.method === 'HEAD')) {
        let name;
        try {
          name = decodeURIComponent(pathname.slice(mediaPrefix.length));
        } catch {
          throw new HttpError(400, '媒体路径不合法');
        }
        serveMedia(req, res, mediaDir, name);
        return;
      }

      sendJson(res, 404, { ok: false, error: 'Not Found' });
    } catch (error) {
      if (res.headersSent) {
        try {
          res.destroy();
        } catch {
          // 忽略。
        }
        return;
      }
      if (error instanceof HttpError) {
        sendJson(res, error.status, { ok: false, error: error.message });
        return;
      }
      ctx.logger?.error?.('画境：处理请求失败', error);
      sendJson(res, 500, { ok: false, error: '画境内部错误' });
    }
  };

  // WebServer 的 prefix 匹配是 pathname.startsWith(prefix + '/')，所以这里不能带结尾斜杠。
  ctx.effect(
    () => ctx.webServer.register({ kind: 'prefix', path: PREFIX, handler }),
    '画境：配置与媒体路由',
  );

  ctx.effect(
    () => ctx.webServer.tapIndex((html) => injectBoot(html, state)),
    '画境：首屏配置注入',
  );

  ctx.logger?.info?.(`画境：配置目录 ${dataDir}`);
}
