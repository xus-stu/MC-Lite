/**
 * 画境 · Client 半边
 *
 * - 整体画风：ctx.theme.overrideTokens 叠加一整套设计 token，不抢 DSH 自己的明暗偏好。
 * - 整体字体：注入 --dsw-font-family 与通用 font-family，含楷体/手写楷体。
 * - 启动动画：图片或视频全屏播放一次，支持上传、网络地址与预览。
 * - 会话花费：读取 tokenUsage 投影，按官方人民币价与峰谷时段计价。
 * - 时段显示：梁文峰（高峰）/ 梁文谷（空闲），带倒计时。
 * - 配置入口：头像打开的设置面板里新增「画境」一页；配置由 Host 持久化。
 */
window.__ModuleLoader__.load({
  id: '@local/dsh-plugin-huanjing',
  factory(require) {
    const React = require('react');
    const h = React.createElement;

    const PREFIX = '/huanjing-api';
    const LEGACY_PREFIX = '/api/huanjing';
    let apiBase = PREFIX;
    const CONFIG_KEY = 'huanjing.config.v1';
    const PLUGIN_VERSION = '1.0.0';

    const BOOT = (() => {
      try {
        return window.__HUANJING_BOOT__ || null;
      } catch {
        return null;
      }
    })();

    /* ============================== 文案 ============================== */

    const ZH = {
      nav: '画境',
      'settings.title': '画境',
      'settings.subtitle': '整体画风、字体、启动动画、花费与时段。配置保存在本机，删除对话或重启 DSH 后仍然有效。',
      'save.saving': '正在保存…',
      'save.saved': '已保存到本机',
      'save.failed': '保存失败',
      'section.theme': '整体画风',
      'section.theme.desc': '只叠加色彩 token，不改变你原来的明暗模式。',
      'section.font': '整体字体',
      'section.font.desc': '正文、标题、按钮与输入框一起换；代码块保持等宽。',
      'section.splash': '启动动画',
      'section.splash.desc': '每次打开或刷新页面时播放一次，支持图片与视频。',
      'section.cost': '花费与时段',
      'section.cost.desc': '在会话标题栏显示本会话花费，以及梁文峰/梁文谷时段。',
      'section.about': '关于',
      'section.about.desc': '配置与媒体都在 DSH 配置目录里，不属于任何一个对话。',
      'splash.enable': '启用启动动画',
      'splash.enable.desc': '没有素材时播放画境默认动画。',
      'splash.upload': '上传图片/视频',
      'splash.preview': '预览',
      'splash.remove': '清除素材',
      'splash.url': '网络地址',
      'splash.url.placeholder': '粘贴 https:// 开头的图片或视频地址',
      'splash.useUrl': '使用地址',
      'splash.urlKind': '素材类型',
      'splash.duration': '图片停留时长',
      'splash.fit': '画面填充',
      'splash.fit.cover': '铺满屏幕',
      'splash.fit.contain': '完整显示',
      'splash.fit.auto': '自适应比例（视频/图片）',
      'splash.fit.window': 'DSH窗口自适应（画面铺满）',
      'splash.fit.windowDesc': '仅桌面窗口可用：按视频/图片比例临时调整 DSH 窗口，动画结束后恢复原窗口大小；极端竖屏/超宽比例会受窗口最小尺寸限制。',
      'splash.current': '当前素材',
      'splash.none': '还没有素材',
      'splash.uploading': '正在上传…',
      'splash.saved': '启动素材已保存',
      'splash.uploadFailed': '上传失败',
      'splash.tooLarge': '文件超过 1 GB，请换一个小一点的',
      'splash.invalidUrl': '请输入 http(s) 地址，或使用上传按钮',
      'splash.uploadHint': '支持 PNG / JPG / WebP / GIF / AVIF 与 MP4 / WebM / MOV / M4V，最大 1 GB。',
      'splash.pickFile': '选择文件',
      'section.music': '音乐播放',
      'section.music.desc': '给启动动画或日常使用配音乐；可上传音频或填网络地址。',
      'music.enable': '启用音乐',
      'music.enable.desc': '音乐总开关；播放方式决定只在启动动画还是使用 DSH 时常驻。',
      'music.upload': '上传音频',
      'music.replace': '更换音频',
      'music.preview': '试听',
      'music.pause': '暂停',
      'music.remove': '清除音乐',
      'music.current': '当前音乐',
      'music.none': '还没有音乐',
      'music.url.placeholder': '粘贴 https:// 开头的音频地址',
      'music.useUrl': '使用地址',
      'music.volume': '音量',
      'music.loop': '循环播放',
      'music.loop.desc': '常驻模式循环整个播放列表；启动动画模式循环当前音乐。',
      'music.mode': '播放方式',
      'music.mode.desc': '常驻模式会在右下角显示迷你播放器，使用 DSH 时持续播放。',
      'music.mode.splash': '仅启动动画',
      'music.mode.always': '使用 DSH 时常驻播放',
      'music.dock.title': '画境音乐',
      'music.dock.play': '播放',
      'music.dock.pause': '暂停',
      'music.dock.volume': '音量',
      'music.dock.loop': '循环',
      'music.dock.close': '收起播放器',
      'music.dock.blocked': '点击开始播放',
      'music.playlist': '播放列表',
      'music.playlist.desc': '列表会按顺序播放；可在右下角播放器里切换。',
      'music.playlist.empty': '还没有歌曲',
      'music.track.remove': '移除',
      'music.track.lyrics': '歌词',
      'music.lyrics': '歌词注入',
      'music.lyrics.desc': '粘贴 LRC 歌词或导入 .lrc 文件；播放器会按时间同步高亮。',
      'music.lyrics.placeholder': '[00:12.00]第一句歌词\n[00:16.50]第二句歌词',
      'music.lyrics.save': '保存歌词',
      'music.lyrics.import': '导入 LRC',
      'music.lyrics.saved': '歌词已保存',
      'music.lyrics.none': '暂无歌词',
      'music.dock.player': '播放',
      'music.dock.list': '列表',
      'music.dock.lyrics': '歌词',
      'music.dock.prev': '上一首',
      'music.dock.next': '下一首',
      'music.dock.loop': '循环列表',
      'music.dock.progress': '播放进度',
      'music.dock.noLyrics': '暂无歌词；在设置里粘贴或导入 LRC。',
      'music.dock.empty': '播放列表为空',
      'music.uploadHint': '支持 MP3 / WAV / OGG / M4A / AAC / FLAC，最大 1 GB。',
      'music.autoplayHint': '浏览器可能拦截自动播放；启动画面或右下角播放器上的按钮可手动开始。',
      'music.uploadFailed': '音乐上传失败',
      'music.saved': '音乐已保存',
      'music.invalidUrl': '请输入 http(s) 音频地址',
      'music.play': '播放音乐',
      'music.playing': '音乐播放中',
      'font.sample': '永远相信美好的事情即将发生。The quick brown fox jumps over the lazy dog.',
      'theme.original': '原版',
      'cost.show': '显示会话花费',
      'cost.show.desc': '在会话标题栏显示本会话累计人民币金额。',
      'cost.period.show': '显示时段',
      'cost.period.show.desc': '显示梁文峰（高峰）/ 梁文谷（空闲）与下次切换倒计时。',
      'cost.model': '计价模型',
      'cost.period': '时段判断',
      'cost.auto': '自动（北京时间）',
      'cost.peak': '手动锁定：高峰',
      'cost.off': '手动锁定：空闲',
      'cost.writeAsMiss': '缓存写入按未命中价计',
      'cost.priceNote': '单位：元 / 百万 token。高峰为周一至周五 09:00–12:00、14:00–18:00；其余时间与周末按空闲价。法定节假日无法离线判断，可手动锁定时段。',
      'cost.current': '当前',
      'cost.reset': '恢复默认配置',
      'cost.resetConfirm': '确定恢复画境的默认画风、字体、启动动画和花费设置吗？',
      'about.body': '画境把配置写在 DSH profile 的 data/huanjing 下，不写入任何会话日志。删除对话、切换会话、重启 DSH 后都会恢复上次的选择。',
      'about.version': '版本',
      'about.dataDir': '配置目录',
      'cost.title': '会话花费',
      'cost.sessionTotal': '本会话累计',
      'cost.periodNow': '当前时段',
      'cost.next': '距离换段',
      'cost.hit': '输入 · 缓存命中',
      'cost.miss': '输入 · 缓存未命中',
      'cost.write': '缓存写入',
      'cost.out': '输出',
      'cost.totalTokens': '总 token',
      'cost.hitRate': '缓存命中率',
      'cost.total': '合计',
      'cost.empty': '本次会话还没有用量',
      'cost.note': '按官方人民币价计算，无汇率换算',
      'period.peak': '梁文峰',
      'period.off': '梁文谷',
      'period.peakFull': '梁文峰时段',
      'period.offFull': '梁文谷时段',
      'splash.skip': '跳过',
      'status.preview': '正在预览启动动画',
    };

    const EN = {
      nav: 'Huajing',
      'settings.title': 'Huajing',
      'settings.subtitle': 'Whole-app theme, fonts, startup animation, conversation cost and time slot. Saved on this machine, surviving conversation deletion and restarts.',
      'save.saving': 'Saving…',
      'save.saved': 'Saved on this machine',
      'save.failed': 'Save failed',
      'section.theme': 'Whole-app theme',
      'section.theme.desc': 'Only stacks color tokens; your light or dark choice stays untouched.',
      'section.font': 'Whole-app font',
      'section.font.desc': 'Text, headings, buttons and inputs; code stays monospaced.',
      'section.splash': 'Startup animation',
      'section.splash.desc': 'Plays once whenever the page opens or refreshes. Image or video.',
      'section.cost': 'Cost and time slot',
      'section.cost.desc': 'Shows this conversation’s cost and the Liang Wenfeng / Liang Wengu slot in the session header.',
      'section.about': 'About',
      'section.about.desc': 'Configuration and media live in the DSH profile, not in a conversation.',
      'splash.enable': 'Enable startup animation',
      'splash.enable.desc': 'Plays Huajing’s default animation when no media is set.',
      'splash.upload': 'Upload image/video',
      'splash.preview': 'Preview',
      'splash.remove': 'Remove media',
      'splash.url': 'Media URL',
      'splash.url.placeholder': 'Paste an https:// image or video URL',
      'splash.useUrl': 'Use URL',
      'splash.urlKind': 'Media kind',
      'splash.duration': 'Image duration',
      'splash.fit': 'Frame fit',
      'splash.fit.cover': 'Cover',
      'splash.fit.contain': 'Contain',
      'splash.fit.auto': 'Adaptive media ratio',
      'splash.fit.window': 'Resize DSH window (fill)',
      'splash.fit.windowDesc': 'Desktop windows only: temporarily resizes the DSH window to the media ratio, then restores it. Extreme portrait or ultra-wide ratios are limited by the minimum window size.',
      'splash.current': 'Current media',
      'splash.none': 'No media yet',
      'splash.uploading': 'Uploading…',
      'splash.saved': 'Startup media saved',
      'splash.uploadFailed': 'Upload failed',
      'splash.tooLarge': 'The file is larger than 1 GB',
      'splash.invalidUrl': 'Enter an http(s) URL or use the upload button',
      'splash.uploadHint': 'PNG / JPG / WebP / GIF / AVIF and MP4 / WebM / MOV / M4V, up to 1 GB.',
      'splash.pickFile': 'Choose file',
      'section.music': 'Music',
      'section.music.desc': 'Background music for the startup animation or everyday use; upload audio or use a URL.',
      'music.enable': 'Enable music',
      'music.enable.desc': 'Master switch; the playback mode decides whether it is only for startup or persists while using DSH.',
      'music.upload': 'Upload audio',
      'music.replace': 'Replace audio',
      'music.preview': 'Preview',
      'music.pause': 'Pause',
      'music.remove': 'Remove music',
      'music.current': 'Current track',
      'music.none': 'No music yet',
      'music.url.placeholder': 'Paste an https:// audio URL',
      'music.useUrl': 'Use URL',
      'music.volume': 'Volume',
      'music.loop': 'Loop',
      'music.loop.desc': 'In persistent mode loops the playlist; in startup mode loops the current track.',
      'music.mode': 'Playback mode',
      'music.mode.desc': 'Persistent mode shows a mini player at the bottom-right and keeps playing while you use DSH.',
      'music.mode.splash': 'Startup animation only',
      'music.mode.always': 'While using DSH',
      'music.dock.title': 'Huajing music',
      'music.dock.play': 'Play',
      'music.dock.pause': 'Pause',
      'music.dock.volume': 'Volume',
      'music.dock.loop': 'Loop',
      'music.dock.close': 'Collapse player',
      'music.dock.blocked': 'Click to start',
      'music.playlist': 'Playlist',
      'music.playlist.desc': 'Tracks play in order; switch from the bottom-right player.',
      'music.playlist.empty': 'No tracks yet',
      'music.track.remove': 'Remove',
      'music.track.lyrics': 'Lyrics',
      'music.lyrics': 'Lyrics injection',
      'music.lyrics.desc': 'Paste LRC lyrics or import a .lrc file; the player highlights lines by time.',
      'music.lyrics.placeholder': '[00:12.00]First line\n[00:16.50]Second line',
      'music.lyrics.save': 'Save lyrics',
      'music.lyrics.import': 'Import LRC',
      'music.lyrics.saved': 'Lyrics saved',
      'music.lyrics.none': 'No lyrics yet',
      'music.dock.player': 'Player',
      'music.dock.list': 'Playlist',
      'music.dock.lyrics': 'Lyrics',
      'music.dock.prev': 'Previous',
      'music.dock.next': 'Next',
      'music.dock.loop': 'Loop playlist',
      'music.dock.progress': 'Playback progress',
      'music.dock.noLyrics': 'No lyrics yet; paste or import LRC in Settings.',
      'music.dock.empty': 'Playlist is empty',
      'music.uploadHint': 'MP3 / WAV / OGG / M4A / AAC / FLAC, up to 1 GB.',
      'music.autoplayHint': 'Browsers may block autoplay; use the button on the splash or the bottom-right player to start.',
      'music.uploadFailed': 'Music upload failed',
      'music.saved': 'Music saved',
      'music.invalidUrl': 'Enter an http(s) audio URL',
      'music.play': 'Play music',
      'music.playing': 'Music playing',
      'font.sample': 'The quick brown fox jumps over the lazy dog. 永远相信美好的事情即将发生。',
      'theme.original': 'Original',
      'cost.show': 'Show conversation cost',
      'cost.show.desc': 'Show the accumulated CNY amount in the session header.',
      'cost.period.show': 'Show time slot',
      'cost.period.show.desc': 'Show Liang Wenfeng (peak) / Liang Wengu (off-peak) and the countdown.',
      'cost.model': 'Pricing model',
      'cost.period': 'Time-slot mode',
      'cost.auto': 'Auto (Beijing time)',
      'cost.peak': 'Force peak',
      'cost.off': 'Force off-peak',
      'cost.writeAsMiss': 'Bill cache writes at the miss price',
      'cost.priceNote': 'CNY per million tokens. Peak: Mon–Fri 09:00–12:00 and 14:00–18:00 Beijing time; weekends and other hours are off-peak. Public holidays cannot be detected offline; lock the slot manually if needed.',
      'cost.current': 'Current',
      'cost.reset': 'Reset to defaults',
      'cost.resetConfirm': 'Reset Huajing theme, font, startup animation and cost settings?',
      'about.body': 'Huajing stores its configuration in the DSH profile at data/huanjing, never in a session log. Deleting conversations, switching sessions or restarting DSH restores your last choices.',
      'about.version': 'Version',
      'about.dataDir': 'Data directory',
      'cost.title': 'Conversation cost',
      'cost.sessionTotal': 'This session',
      'cost.periodNow': 'Current slot',
      'cost.next': 'Next switch in',
      'cost.hit': 'Input · cache hit',
      'cost.miss': 'Input · cache miss',
      'cost.write': 'Cache write',
      'cost.out': 'Output',
      'cost.totalTokens': 'Total tokens',
      'cost.hitRate': 'Cache hit rate',
      'cost.total': 'Total',
      'cost.empty': 'No usage in this session yet',
      'cost.note': 'Official CNY prices, no exchange-rate conversion',
      'period.peak': 'Wenfeng',
      'period.off': 'Wengu',
      'period.peakFull': 'Liang Wenfeng slot',
      'period.offFull': 'Liang Wengu slot',
      'splash.skip': 'Skip',
      'status.preview': 'Previewing startup animation',
    };

    /* ============================== 配置 ============================== */

    const THEMES = [
      { id: 'fresh-green', label: '清新绿', labelEn: 'Fresh Green', desc: '青草与晨露', descEn: 'Grass and morning dew', nHue: 142, nSat: 15, aHue: 152, aSat: 52, lift: 0 },
      { id: 'ink-bamboo', label: '墨竹', labelEn: 'Ink Bamboo', desc: '深林夜色', descEn: 'Deep bamboo night', nHue: 150, nSat: 10, aHue: 150, aSat: 55, lift: 0 },
      { id: 'sakura', label: '樱花粉', labelEn: 'Sakura', desc: '薄樱与暖白', descEn: 'Soft blossom and warm white', nHue: 340, nSat: 10, aHue: 340, aSat: 52, lift: -1 },
      { id: 'sea-salt', label: '海盐蓝', labelEn: 'Sea Salt', desc: '海风与晴空', descEn: 'Sea breeze and clear sky', nHue: 205, nSat: 13, aHue: 210, aSat: 66, lift: 0 },
      { id: 'warm-sun', label: '暖阳橙', labelEn: 'Warm Sun', desc: '午后与麦田', descEn: 'Afternoon and wheat', nHue: 32, nSat: 16, aHue: 28, aSat: 62, lift: 0 },
      { id: 'mist-violet', label: '雾紫', labelEn: 'Mist Violet', desc: '薄雾与鸢尾', descEn: 'Mist and iris', nHue: 265, nSat: 9, aHue: 270, aSat: 45, lift: 0 },
      { id: 'original', label: '原版', labelEn: 'Original', desc: '还原 DSH 默认', descEn: 'Restore DSH defaults', original: true },
    ];

    const FONTS = [
      { id: 'system', label: '系统默认', labelEn: 'System', stack: '' },
      { id: 'kai', label: '楷体', labelEn: 'KaiTi', stack: '"Kaiti SC", "STKaiti", "KaiTi", "楷体", "BiauKai", serif' },
      { id: 'hand-kai', label: '手写楷体', labelEn: 'Handwritten Kai', stack: '"Xingkai SC", "STXingkai", "华文行楷", "Kaiti SC", "KaiTi", "楷体", cursive, serif' },
      { id: 'lxgw', label: '霞鹜文楷', labelEn: 'LXGW WenKai', stack: '"LXGW WenKai", "霞鹜文楷", "Kaiti SC", "KaiTi", serif' },
      { id: 'song', label: '宋体', labelEn: 'Songti', stack: '"Songti SC", "STSong", "SimSun", "Noto Serif CJK SC", serif' },
      { id: 'round', label: '圆体', labelEn: 'Rounded', stack: '"Yuanti SC", "YouYuan", "PingFang SC", "Microsoft YaHei", sans-serif' },
      { id: 'mono', label: '等宽', labelEn: 'Monospace', stack: 'ui-monospace, "SF Mono", Menlo, Consolas, monospace' },
    ];

    const DEFAULT_CONFIG = {
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
    };

    const THEME_IDS = new Set(THEMES.map((item) => item.id));
    const FONT_IDS = new Set(FONTS.map((item) => item.id));
    const MODEL_IDS = new Set(['deepseek-flash', 'deepseek-v4-pro']);
    const PERIOD_MODES = new Set(['auto', 'peak', 'off']);
    const MAX_MEDIA_BYTES = 1024 * 1024 * 1024;

    function clone(value) {
      return JSON.parse(JSON.stringify(value));
    }

    function safeMediaUrl(value) {
      if (typeof value !== 'string') return null;
      const raw = value.trim();
      if (raw.length === 0 || raw.length > 1200) return null;
      if (raw.startsWith(`${PREFIX}/media/`) || raw.startsWith(`${LEGACY_PREFIX}/media/`)) return raw;
      try {
        const url = new URL(raw);
        if (url.protocol === 'http:' || url.protocol === 'https:') return raw;
      } catch {
        // 不是绝对 URL。
      }
      return null;
    }

    function normalizeConfig(input) {
      const base = clone(DEFAULT_CONFIG);
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
            name: typeof splash.media.name === 'string' ? splash.media.name.slice(0, 180) : (kind === 'video' ? '启动视频' : '启动图片'),
            mime: typeof splash.media.mime === 'string' ? splash.media.mime.slice(0, 80) : '',
          };
        }
      }
      const duration = Number(splash.duration);
      const fade = Number(splash.fade);
      const music = splash.music && typeof splash.music === 'object' ? splash.music : {};
      let legacyMedia = null;
      if (music.media && typeof music.media === 'object') {
        const url = safeMediaUrl(music.media.url);
        if (url) {
          legacyMedia = {
            kind: 'audio',
            url,
            name: typeof music.media.name === 'string' ? music.media.name.slice(0, 180) : '启动音乐',
            mime: typeof music.media.mime === 'string' ? music.media.mime.slice(0, 80) : '',
          };
        }
      }
      const rawTracks = Array.isArray(music.tracks) ? music.tracks.slice(0, 100) : [];
      const tracks = [];
      const usedTrackIds = new Set();
      rawTracks.forEach((track, index) => {
        if (!track || typeof track !== 'object') return;
        const url = safeMediaUrl(track.url);
        if (!url) return;
        let id = typeof track.id === 'string' && track.id ? track.id.slice(0, 80) : `track-${index + 1}`;
        while (usedTrackIds.has(id)) id = `${id}-${index + 1}`;
        usedTrackIds.add(id);
        tracks.push({
          id,
          name: typeof track.name === 'string' && track.name ? track.name.slice(0, 180) : `音乐 ${index + 1}`,
          url,
          mime: typeof track.mime === 'string' ? track.mime.slice(0, 80) : '',
          lyrics: typeof track.lyrics === 'string' ? track.lyrics.slice(0, 100000) : '',
        });
      });
      if (tracks.length === 0 && legacyMedia) {
        tracks.push({
          id: 'legacy-1',
          name: legacyMedia.name || '启动音乐',
          url: legacyMedia.url,
          mime: legacyMedia.mime || '',
          lyrics: '',
        });
      }
      const requestedCurrentId = typeof music.currentId === 'string' ? music.currentId.slice(0, 80) : '';
      const currentTrack = tracks.find((track) => track.id === requestedCurrentId) || tracks[0] || null;
      const currentId = currentTrack ? currentTrack.id : '';
      const musicMedia = currentTrack
        ? { kind: 'audio', url: currentTrack.url, name: currentTrack.name, mime: currentTrack.mime || '' }
        : null;
      const dockSource = music.dock && typeof music.dock === 'object' ? music.dock : null;
      const dock = dockSource && Number.isFinite(Number(dockSource.x)) && Number.isFinite(Number(dockSource.y))
        ? { x: Math.min(100000, Math.max(0, Number(dockSource.x))), y: Math.min(100000, Math.max(0, Number(dockSource.y))) }
        : null;
      const volume = Number(music.volume);
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
            enabled: music.enabled === true,
            media: musicMedia,
            tracks,
            currentId,
            volume: Number.isFinite(volume) ? Math.min(1, Math.max(0, volume)) : base.splash.music.volume,
            loop: music.loop !== false,
            mode: music.mode === 'always' ? 'always' : 'splash',
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
      };
    }

    function readLocalConfig() {
      try {
        return normalizeConfig(JSON.parse(localStorage.getItem(CONFIG_KEY) || 'null'));
      } catch {
        return clone(DEFAULT_CONFIG);
      }
    }

    function writeLocalConfig(value) {
      try {
        localStorage.setItem(CONFIG_KEY, JSON.stringify(value));
      } catch {
        // localStorage 只做首屏兜底，失败无妨。
      }
    }

    function makeTrackId() {
      try {
        if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID();
      } catch {
        // 忽略。
      }
      return `track-${Date.now()}-${Math.random().toString(16).slice(2)}`;
    }

    function currentTrackOf(music) {
      if (!music || !Array.isArray(music.tracks) || music.tracks.length === 0) return null;
      return music.tracks.find((track) => track.id === music.currentId) || music.tracks[0];
    }

    function parseLrc(text) {
      const source = typeof text === 'string' ? text.replace(/^\uFEFF/, '') : '';
      const offsetMatch = source.match(/\[offset:([+-]?\d+)\]/i);
      const offset = offsetMatch ? Number(offsetMatch[1]) / 1000 : 0;
      const entries = [];
      for (const rawLine of source.split(/\r?\n/)) {
        const tags = [...rawLine.matchAll(/\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]/g)];
        if (tags.length === 0) continue;
        const content = rawLine.replace(/\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]/g, '').trim();
        if (!content) continue;
        for (const tag of tags) {
          const fraction = tag[3] ? Number(`0.${tag[3]}`) : 0;
          entries.push({
            time: Math.max(0, Number(tag[1]) * 60 + Number(tag[2]) + fraction + offset),
            text: content,
          });
        }
      }
      entries.sort((a, b) => a.time - b.time);
      return entries;
    }

    function formatClock(seconds) {
      const total = Number.isFinite(seconds) && seconds > 0 ? Math.floor(seconds) : 0;
      const minutes = Math.floor(total / 60);
      const rest = total % 60;
      return `${minutes}:${String(rest).padStart(2, '0')}`;
    }

    let config = normalizeConfig(BOOT && BOOT.state ? BOOT.state : readLocalConfig());
    let configTouched = false;
    let hostDataDir = '';
    const configListeners = new Set();

    function notifyConfig() {
      for (const listener of configListeners) {
        try {
          listener(config);
        } catch {
          // 单个订阅者失败不影响其他人。
        }
      }
    }

    function setLocalConfig(next) {
      config = normalizeConfig(next);
      configTouched = true;
      writeLocalConfig(config);
      notifyConfig();
      applyVisuals();
    }

    function useConfig() {
      const [value, setValue] = React.useState(config);
      React.useEffect(() => {
        const listener = () => setValue(config);
        configListeners.add(listener);
        return () => configListeners.delete(listener);
      }, []);
      return value;
    }

    let saveChain = Promise.resolve();
    let saveSeq = 0;

    function persistConfig(next) {
      const snapshot = normalizeConfig(next);
      const run = saveChain.then(async () => {
        const response = await fetch(`${apiBase}/state`, {
          method: 'POST',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify(snapshot),
        });
        let data = null;
        try {
          data = await response.json();
        } catch {
          data = null;
        }
        if (!response.ok || !data || data.ok !== true) {
          throw new Error((data && data.error) || `HTTP ${response.status}`);
        }
        return { ok: true, state: data.state, dataDir: data.dataDir || hostDataDir };
      });
      saveChain = run.catch(() => {});
      return run;
    }

    async function commitConfig(next, okText) {
      const id = ++saveSeq;
      setLocalConfig(next);
      try {
        const result = await persistConfig(config);
        if (id === saveSeq && result.dataDir) hostDataDir = result.dataDir;
        return { ok: true, message: okText || '' };
      } catch (error) {
        return { ok: false, error: String((error && error.message) || error) };
      }
    }

    async function loadRemoteConfig() {
      for (const base of [PREFIX, LEGACY_PREFIX]) {
        try {
          const response = await fetch(`${base}/state`, { headers: { accept: 'application/json' } });
          if (!response.ok) continue;
          const data = await response.json();
          apiBase = base;
          if (data && data.dataDir) hostDataDir = data.dataDir;
          if (data && data.state && !configTouched) {
            config = normalizeConfig(data.state);
            writeLocalConfig(config);
            notifyConfig();
            applyVisuals();
          }
          return;
        } catch {
          // 尝试下一个地址。
        }
      }
    }

    /* ============================== 主题 ============================== */

    const REF_NEUTRAL = {
      '00': '#ffffff',
      '50': '#f9fafb',
      '60': '#f5f6f7',
      '75': '#f1f3f5',
      '100': '#ebeef2',
      '150': '#e9ecf2',
      '200': '#e1e5ee',
      '300': '#cfd3d6',
      '400': '#adb2b8',
      '500': '#979da6',
      '600': '#81858c',
      '700': '#61666b',
      '750': '#43454a',
      '800': '#353638',
      '850': '#2c2c2e',
      '875': '#232324',
      '900': '#1b1b1c',
      '950': '#151517',
      '1000': '#0f1115',
    };

    const REF_BRAND = {
      '50': '#edf3fe',
      '100': '#e4edfd',
      '200': '#d3e2ff',
      '300': '#b7c8fe',
      '400': '#7aaaff',
      '450': '#5686fe',
      '500': '#4176e6',
      '600': '#4868b2',
      '800': '#34415b',
      '900': '#283142',
    };

    function hsl(hue, sat, light) {
      const s = sat / 100;
      const l = light / 100;
      const c = (1 - Math.abs(2 * l - 1)) * s;
      const hp = (((hue % 360) + 360) % 360) / 60;
      const x = c * (1 - Math.abs((hp % 2) - 1));
      let r = 0;
      let g = 0;
      let b = 0;
      if (hp < 1) { r = c; g = x; }
      else if (hp < 2) { r = x; g = c; }
      else if (hp < 3) { g = c; b = x; }
      else if (hp < 4) { g = x; b = c; }
      else if (hp < 5) { r = x; b = c; }
      else { r = c; b = x; }
      const m = l - c / 2;
      const to = (value) => Math.round(Math.min(255, Math.max(0, (value + m) * 255))).toString(16).padStart(2, '0');
      return `#${to(r)}${to(g)}${to(b)}`;
    }

    function hexToHsl(hex) {
      const value = String(hex).replace('#', '');
      const full = value.length === 3 ? value.split('').map((part) => part + part).join('') : value;
      const r = parseInt(full.slice(0, 2), 16) / 255;
      const g = parseInt(full.slice(2, 4), 16) / 255;
      const b = parseInt(full.slice(4, 6), 16) / 255;
      const max = Math.max(r, g, b);
      const min = Math.min(r, g, b);
      const l = (max + min) / 2;
      let hue = 0;
      let sat = 0;
      if (max !== min) {
        const d = max - min;
        sat = l > 0.5 ? d / (2 - max - min) : d / (max + min);
        if (max === r) hue = ((g - b) / d + (g < b ? 6 : 0)) / 6;
        else if (max === g) hue = ((b - r) / d + 2) / 6;
        else hue = ((r - g) / d + 4) / 6;
      }
      return { h: hue * 360, s: sat * 100, l: l * 100 };
    }

    function retint(reference, hue, sat) {
      const out = {};
      for (const key of Object.keys(reference)) {
        const color = hexToHsl(reference[key]);
        out[key] = hsl(hue, sat, color.l);
      }
      return out;
    }

    function neutralRamp(hue, sat) {
      const bluish = retint(REF_NEUTRAL, hue, sat);
      const neutral = retint(REF_NEUTRAL, hue, sat);
      const out = {};
      for (const key of Object.keys(bluish)) {
        out[`--dsw-static-neutral-bluish-${key}`] = bluish[key];
        if (key !== '00') out[`--dsw-static-neutral-${key}`] = neutral[key];
      }
      return out;
    }

    function brandRamp(hue, sat) {
      const tinted = retint(REF_BRAND, hue, sat);
      const out = {};
      for (const key of Object.keys(tinted)) out[`--dsw-static-deepseek-${key}`] = tinted[key];
      return out;
    }

    function themeTokens(preset, scheme) {
      const dark = scheme === 'dark';
      const tokens = Object.assign({}, neutralRamp(preset.nHue, preset.nSat), brandRamp(preset.aHue, preset.aSat));
      const lift = Number(preset.lift) || 0;
      const n = (shade) => {
        const base = tokens[`--dsw-static-neutral-bluish-${shade}`];
        if (!lift || !base) return base;
        const color = hexToHsl(base);
        return hsl(color.h, color.s, Math.min(100, Math.max(0, color.l + lift)));
      };
      if (dark) {
        tokens['--dsw-alias-bg-base'] = n('950');
        tokens['--dsw-alias-bg-layer-1'] = n('900');
        tokens['--dsw-alias-bg-layer-2'] = n('875');
        tokens['--dsw-alias-bg-layer-3'] = n('850');
        tokens['--dsw-alias-bg-l1'] = n('900');
        tokens['--dsw-alias-bg-l2'] = n('875');
        tokens['--dsw-alias-bg-overlay'] = n('875');
        tokens['--dsw-specific-sidebar-fill'] = n('950');
        tokens['--dsw-alias-label-primary'] = n('50');
        tokens['--dsw-alias-label-primary-inverted'] = n('950');
        tokens['--dsw-alias-label-secondary'] = n('300');
        tokens['--dsw-alias-label-tertiary'] = n('400');
        tokens['--dsw-alias-label-quaternary'] = n('500');
        tokens['--dsw-alias-label-caption'] = n('600');
      } else {
        tokens['--dsw-alias-bg-base'] = n('50');
        tokens['--dsw-alias-bg-layer-1'] = n('75');
        tokens['--dsw-alias-bg-layer-2'] = n('60');
        tokens['--dsw-alias-bg-layer-3'] = n('100');
        tokens['--dsw-alias-bg-l1'] = n('75');
        tokens['--dsw-alias-bg-l2'] = n('60');
        tokens['--dsw-alias-bg-overlay'] = n('60');
        tokens['--dsw-specific-sidebar-fill'] = n('75');
        tokens['--dsw-alias-label-primary'] = n('900');
        tokens['--dsw-alias-label-primary-inverted'] = n('50');
        tokens['--dsw-alias-label-secondary'] = n('700');
        tokens['--dsw-alias-label-tertiary'] = n('700');
        tokens['--dsw-alias-label-quaternary'] = n('600');
        tokens['--dsw-alias-label-caption'] = n('600');
      }
      const accent = hsl(preset.aHue, preset.aSat, dark ? 58 : 46);
      tokens['--dsw-alias-brand-primary'] = accent;
      tokens['--dsw-alias-brand-text'] = accent;
      tokens['--dsw-alias-button-primary-fill'] = accent;
      tokens['--dsw-alias-button-primary-hover'] = hsl(preset.aHue, preset.aSat, dark ? 46 : 54);
      tokens['--dsw-alias-button-primary-dimmed'] = hsl(preset.aHue, preset.aSat, dark ? 32 : 66);
      tokens['--dsw-alias-link'] = dark ? hsl(preset.aHue, preset.aSat, 68) : hsl(preset.aHue, preset.aSat, 40);
      tokens['--dsw-alias-state-idle-primary'] = n('400');
      if (dark) {
        tokens['--dsw-alias-border-l1'] = 'rgba(255,255,255,.07)';
        tokens['--dsw-alias-border-l2'] = 'rgba(255,255,255,.12)';
        tokens['--dsw-alias-border-l3'] = 'rgba(255,255,255,.18)';
        tokens['--dsw-alias-border-l4'] = 'rgba(255,255,255,.26)';
        tokens['--dsw-alias-interactive-bg-hover'] = 'rgba(255,255,255,.07)';
        tokens['--dsw-alias-interactive-bg-active'] = 'rgba(255,255,255,.11)';
      } else {
        tokens['--dsw-alias-border-l1'] = 'rgba(0,0,0,.07)';
        tokens['--dsw-alias-border-l2'] = 'rgba(0,0,0,.11)';
        tokens['--dsw-alias-border-l3'] = 'rgba(0,0,0,.16)';
        tokens['--dsw-alias-border-l4'] = 'rgba(0,0,0,.22)';
        tokens['--dsw-alias-interactive-bg-hover'] = 'rgba(0,0,0,.05)';
        tokens['--dsw-alias-interactive-bg-active'] = 'rgba(0,0,0,.09)';
      }
      return tokens;
    }

    function buildThemeLayer(preset) {
      const light = themeTokens(preset, 'light');
      const dark = themeTokens(preset, 'dark');
      const keys = new Set([...Object.keys(light), ...Object.keys(dark)]);
      const layer = {};
      for (const key of keys) {
        layer[key] = { light: light[key] || dark[key], dark: dark[key] || light[key] };
      }
      return layer;
    }

    function themeSwatches(preset) {
      if (preset.original) {
        return ['#f4f6f8', '#ffffff', '#4176e6'];
      }
      const light = themeTokens(preset, 'light');
      const dark = themeTokens(preset, 'dark');
      return [
        light['--dsw-alias-bg-base'],
        dark['--dsw-alias-bg-base'],
        light['--dsw-alias-brand-primary'],
      ];
    }

    /* ============================== 花费与时段 ============================== */

    const PRICES = {
      'deepseek-flash': {
        label: 'DeepSeek Flash',
        peak: { hit: 0.04, miss: 2, out: 8 },
        off: { hit: 0.02, miss: 1, out: 4 },
      },
      'deepseek-v4-pro': {
        label: 'DeepSeek V4 Pro',
        peak: { hit: 0.30, miss: 9, out: 27 },
        off: { hit: 0.15, miss: 4.5, out: 13.5 },
      },
    };

    const PEAK_WINDOWS = [[540, 720], [840, 1080]];

    function beijingDate(timestamp) {
      const date = new Date(timestamp);
      return new Date(date.getTime() + (date.getTimezoneOffset() + 480) * 60000);
    }

    function isPeak(timestamp, mode) {
      if (mode === 'peak') return true;
      if (mode === 'off') return false;
      const date = beijingDate(timestamp);
      const day = date.getDay();
      if (day === 0 || day === 6) return false;
      const minutes = date.getHours() * 60 + date.getMinutes();
      return PEAK_WINDOWS.some((window) => minutes >= window[0] && minutes < window[1]);
    }

    function periodName(peak) {
      return peak ? '梁文峰' : '梁文谷';
    }

    function periodFullName(peak) {
      return peak ? '梁文峰时段' : '梁文谷时段';
    }

    function fmtClock(minutes) {
      return `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;
    }

    function nextBoundary(timestamp) {
      const date = beijingDate(timestamp);
      const day = date.getDay();
      const minutes = date.getHours() * 60 + date.getMinutes();
      const seconds = date.getSeconds();
      const marks = [...new Set(PEAK_WINDOWS.flat())].sort((a, b) => a - b);
      if (day !== 0 && day !== 6) {
        const next = marks.find((mark) => mark > minutes);
        if (next !== undefined) {
          return {
            ms: ((next - minutes) * 60 - seconds) * 1000,
            label: `今天 ${fmtClock(next)}`,
            nextMinute: next,
          };
        }
      }
      let daysAhead = 1;
      while (true) {
        const target = (day + daysAhead) % 7;
        if (target !== 0 && target !== 6) break;
        daysAhead += 1;
      }
      const msToday = (24 * 60 - minutes) * 60 * 1000 - seconds * 1000;
      return {
        ms: msToday + (daysAhead - 1) * 86400000 + marks[0] * 60000,
        label: `${daysAhead === 1 ? '明天' : '下个工作日'} ${fmtClock(marks[0])}`,
        nextMinute: marks[0],
      };
    }

    function fmtLeft(ms) {
      if (!(ms > 0)) return '0 秒';
      const total = Math.floor(ms / 1000);
      const days = Math.floor(total / 86400);
      const hours = Math.floor((total % 86400) / 3600);
      const minutes = Math.floor((total % 3600) / 60);
      const seconds = total % 60;
      if (days > 0) return `${days} 天 ${hours} 小时`;
      if (hours > 0) return `${hours} 小时 ${minutes} 分 ${String(seconds).padStart(2, '0')} 秒`;
      if (minutes > 0) return `${minutes} 分 ${String(seconds).padStart(2, '0')} 秒`;
      return `${seconds} 秒`;
    }

    function number(value) {
      const n = Number(value);
      return Number.isFinite(n) && n > 0 ? n : 0;
    }

    function usageNumbers(usage) {
      const source = usage && typeof usage === 'object' ? usage : {};
      const hit = number(source.cacheReadTokens ?? source.cachedInputTokens ?? source.cacheHitTokens);
      const write = number(source.cacheWriteTokens ?? source.cacheCreationTokens);
      let miss = number(source.uncachedInputTokens ?? source.missTokens);
      if (!miss) {
        const totalInput = number(source.inputTokens ?? source.promptTokens);
        if (totalInput) miss = Math.max(0, totalInput - hit - write);
      }
      const out = number(source.outputTokens ?? source.completionTokens);
      return {
        hit,
        miss,
        write,
        out,
        model: typeof source.modelId === 'string' ? source.modelId : (typeof source.model === 'string' ? source.model : ''),
      };
    }

    function pricing(modelId, peak) {
      const table = PRICES[modelId] || PRICES['deepseek-flash'];
      return peak ? table.peak : table.off;
    }

    function calculateCost(usage, costConfig, now) {
      const numbers = usageNumbers(usage);
      const peak = isPeak(now, costConfig.period);
      const price = pricing(numbers.model || costConfig.model, peak);
      const writePrice = costConfig.writeAsMiss === false ? price.hit : price.miss;
      const million = 1e6;
      const items = [
        { key: 'hit', nameKey: 'cost.hit', tokens: numbers.hit, price: price.hit, cny: numbers.hit / million * price.hit },
        { key: 'miss', nameKey: 'cost.miss', tokens: numbers.miss, price: price.miss, cny: numbers.miss / million * price.miss },
        { key: 'write', nameKey: 'cost.write', tokens: numbers.write, price: writePrice, cny: numbers.write / million * writePrice },
        { key: 'out', nameKey: 'cost.out', tokens: numbers.out, price: price.out, cny: numbers.out / million * price.out },
      ];
      const cny = items.reduce((sum, item) => sum + item.cny, 0);
      const tokens = numbers.hit + numbers.miss + numbers.write + numbers.out;
      const billed = numbers.hit + numbers.miss + numbers.write;
      return {
        peak,
        periodName: periodName(peak),
        periodFullName: periodFullName(peak),
        model: numbers.model || costConfig.model,
        items,
        cny,
        tokens,
        cacheHit: billed > 0 ? numbers.hit / billed : 0,
        next: nextBoundary(now),
      };
    }

    function fmtCny(value) {
      const n = Number.isFinite(value) ? value : 0;
      return `¥${n.toFixed(4)}`;
    }

    function fmtTokens(value) {
      const n = Number(value) || 0;
      if (n >= 1e6) return `${(n / 1e6).toFixed(2)}M`;
      if (n >= 1e3) return `${(n / 1e3).toFixed(1)}k`;
      return String(n);
    }

    /* ============================== 样式 ============================== */

    const CSS = `
.hj-page{display:flex;flex-direction:column;gap:16px;padding:2px 2px 36px;max-width:820px;color:var(--dsw-alias-label-primary)}
.hj-hero{position:relative;overflow:hidden;border:1px solid var(--dsw-alias-border-l1);border-radius:16px;padding:20px 22px;background:var(--dsw-alias-bg-layer-1)}
.hj-hero:before{content:"";position:absolute;inset:-40% -10% auto auto;width:260px;height:260px;border-radius:50%;background:radial-gradient(circle,color-mix(in srgb,var(--dsw-alias-brand-primary) 24%,transparent),transparent 68%);pointer-events:none}
.hj-hero h2{margin:0 0 6px;font-size:20px;letter-spacing:.5px}
.hj-hero p{margin:0;max-width:640px;color:var(--dsw-alias-label-secondary);font-size:12.5px;line-height:1.7}
.hj-badge{display:inline-flex;align-items:center;gap:6px;margin-bottom:10px;padding:3px 9px;border-radius:999px;border:1px solid color-mix(in srgb,var(--dsw-alias-brand-primary) 35%,var(--dsw-alias-border-l1));color:var(--dsw-alias-brand-text);font-size:11px;font-weight:600}
.hj-section{border:1px solid var(--dsw-alias-border-l1);border-radius:14px;background:var(--dsw-alias-bg-layer-1);padding:16px 17px}
.hj-section-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px;margin-bottom:13px}
.hj-section-head h3{margin:0 0 3px;font-size:13.5px;letter-spacing:.2px}
.hj-section-head p{margin:0;color:var(--dsw-alias-label-tertiary);font-size:11.5px;line-height:1.6}
.hj-theme-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(154px,1fr));gap:9px}
.hj-theme-card{position:relative;text-align:left;padding:11px 11px 10px;border-radius:11px;border:1px solid var(--dsw-alias-border-l1);background:var(--dsw-alias-bg-layer-2);color:var(--dsw-alias-label-primary);cursor:pointer;transition:border-color .15s,transform .12s,box-shadow .15s}
.hj-theme-card:hover{border-color:color-mix(in srgb,var(--dsw-alias-brand-primary) 55%,var(--dsw-alias-border-l1));transform:translateY(-1px)}
.hj-theme-card[data-on="1"]{border-color:var(--dsw-alias-brand-primary);box-shadow:0 0 0 1px color-mix(in srgb,var(--dsw-alias-brand-primary) 55%,transparent) inset}
.hj-theme-card[data-on="1"]:after{content:"✓";position:absolute;top:8px;right:9px;color:var(--dsw-alias-brand-primary);font-size:11px;font-weight:700}
.hj-swatches{display:flex;gap:4px;margin-bottom:8px}
.hj-swatches i{display:block;width:20px;height:20px;border-radius:6px;border:1px solid rgba(127,127,127,.18)}
.hj-theme-card b{display:block;font-size:12px;margin-bottom:1px}
.hj-theme-card small{display:block;color:var(--dsw-alias-label-tertiary);font-size:10.5px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.hj-field{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:10px 0;border-top:1px solid var(--dsw-alias-border-l1)}
.hj-field:first-child{border-top:0;padding-top:0}
.hj-field-copy{min-width:0}
.hj-field-copy b{display:block;font-size:12.5px;font-weight:600}
.hj-field-copy small{display:block;margin-top:2px;color:var(--dsw-alias-label-tertiary);font-size:11px;line-height:1.55}
.hj-select,.hj-input{min-width:120px;max-width:100%;padding:6px 9px;border-radius:8px;border:1px solid var(--dsw-alias-border-l2);background:var(--dsw-alias-bg-layer-2);color:var(--dsw-alias-label-primary);font-size:12px;outline:none}
.hj-select:focus,.hj-input:focus{border-color:var(--dsw-alias-brand-primary);box-shadow:0 0 0 2px color-mix(in srgb,var(--dsw-alias-brand-primary) 18%,transparent)}
.hj-input{flex:1}
.hj-switch{position:relative;flex:0 0 auto;width:40px;height:23px;border-radius:999px;border:1px solid var(--dsw-alias-border-l2);background:var(--dsw-alias-bg-layer-2);cursor:pointer;padding:0;transition:background .16s,border-color .16s}
.hj-switch span{position:absolute;top:3px;left:3px;width:15px;height:15px;border-radius:50%;background:var(--dsw-alias-label-tertiary);transition:transform .16s,background .16s}
.hj-switch[aria-checked="true"]{background:color-mix(in srgb,var(--dsw-alias-brand-primary) 22%,var(--dsw-alias-bg-layer-2));border-color:color-mix(in srgb,var(--dsw-alias-brand-primary) 60%,var(--dsw-alias-border-l2))}
.hj-switch[aria-checked="true"] span{transform:translateX(17px);background:var(--dsw-alias-brand-primary)}
.hj-btn{display:inline-flex;align-items:center;justify-content:center;gap:6px;padding:7px 12px;border-radius:8px;border:1px solid var(--dsw-alias-border-l2);background:var(--dsw-alias-bg-layer-2);color:var(--dsw-alias-label-primary);font-size:12px;cursor:pointer;transition:background .15s,border-color .15s,transform .12s;text-decoration:none}
.hj-btn:hover{background:var(--dsw-alias-interactive-bg-hover);border-color:color-mix(in srgb,var(--dsw-alias-brand-primary) 45%,var(--dsw-alias-border-l2))}
.hj-btn:active{transform:scale(.98)}
.hj-btn.primary{background:var(--dsw-alias-button-primary-fill);border-color:var(--dsw-alias-button-primary-fill);color:var(--dsw-alias-label-primary-inverted)}
.hj-btn.primary:hover{background:var(--dsw-alias-button-primary-hover)}
.hj-btn.danger{color:var(--dsw-alias-state-error-primary);border-color:color-mix(in srgb,var(--dsw-alias-state-error-primary) 35%,var(--dsw-alias-border-l2))}
.hj-btn[disabled]{opacity:.55;cursor:not-allowed;transform:none}
.hj-row{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.hj-note{color:var(--dsw-alias-label-tertiary);font-size:11px;line-height:1.65}
.hj-sample{margin-top:10px;padding:13px 14px;border-radius:10px;border:1px dashed var(--dsw-alias-border-l2);background:var(--dsw-alias-bg-layer-2);font-size:15px;line-height:1.8;color:var(--dsw-alias-label-primary);word-break:break-word}
.hj-media-preview{display:flex;align-items:center;gap:12px;padding:10px;border-radius:10px;border:1px solid var(--dsw-alias-border-l1);background:var(--dsw-alias-bg-layer-2);margin-bottom:10px}
.hj-media-thumb{width:92px;height:58px;border-radius:7px;overflow:hidden;flex:0 0 auto;background:#000;display:flex;align-items:center;justify-content:center;color:#fff;font-size:10px}
.hj-media-thumb img,.hj-media-thumb video{width:100%;height:100%;object-fit:cover;display:block}
.hj-media-meta{min-width:0}
.hj-media-meta b{display:block;font-size:12px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.hj-media-meta small{display:block;color:var(--dsw-alias-label-tertiary);font-size:10.5px;margin-top:2px}
.hj-save{font-size:11px;color:var(--dsw-alias-label-tertiary);white-space:nowrap}
.hj-save[data-state="saved"]{color:var(--dsw-alias-state-success-primary)}
.hj-save[data-state="error"]{color:var(--dsw-alias-state-error-primary)}
.hj-range{width:130px;accent-color:var(--dsw-alias-brand-primary)}
.hj-about-row{display:flex;justify-content:space-between;gap:16px;padding:5px 0;font-size:11.5px;color:var(--dsw-alias-label-secondary)}
.hj-about-row code{font-family:ui-monospace,"SF Mono",Menlo,monospace;font-size:10.5px;color:var(--dsw-alias-label-tertiary);word-break:break-all;text-align:right}
.hj-cost-root{position:relative;display:inline-flex;align-items:center;gap:5px}
.hj-pill{display:inline-flex;align-items:center;gap:5px;min-height:26px;padding:2px 8px;border-radius:7px;border:1px solid color-mix(in srgb,var(--dsw-alias-brand-primary) 32%,var(--dsw-alias-border-l1));background:color-mix(in srgb,var(--dsw-alias-brand-primary) 10%,transparent);color:var(--dsw-alias-brand-text);font:600 11.5px/18px ui-monospace,"SF Mono",Menlo,monospace;font-variant-numeric:tabular-nums;cursor:pointer;white-space:nowrap}
.hj-pill:hover{background:color-mix(in srgb,var(--dsw-alias-brand-primary) 17%,transparent)}
.hj-pill[data-empty="1"]{color:var(--dsw-alias-label-tertiary);border-color:var(--dsw-alias-border-l1);background:transparent}
.hj-period-pill{font-family:inherit;font-weight:600}
.hj-period-pill.peak{color:var(--dsw-alias-state-error-primary);border-color:color-mix(in srgb,var(--dsw-alias-state-error-primary) 38%,var(--dsw-alias-border-l1));background:color-mix(in srgb,var(--dsw-alias-state-error-primary) 9%,transparent)}
.hj-period-pill.off{color:var(--dsw-alias-state-success-primary);border-color:color-mix(in srgb,var(--dsw-alias-state-success-primary) 38%,var(--dsw-alias-border-l1));background:color-mix(in srgb,var(--dsw-alias-state-success-primary) 9%,transparent)}
.hj-dot{display:block;width:6px;height:6px;border-radius:50%;background:currentColor;flex:0 0 auto}
.hj-popover{position:fixed;z-index:120;width:340px;max-width:calc(100vw - 24px);padding:13px 14px 12px;border-radius:12px;border:1px solid var(--dsw-alias-border-l2);background:var(--dsw-alias-bg-overlay);color:var(--dsw-alias-label-primary);box-shadow:0 18px 48px rgba(0,0,0,.34);font-size:12px;line-height:1.6}
.hj-pop-head{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:10px}
.hj-pop-head b{font-size:13px;letter-spacing:.2px}
.hj-pop-close{border:0;background:none;color:var(--dsw-alias-label-tertiary);cursor:pointer;font-size:16px;line-height:1;padding:2px 4px;border-radius:4px}
.hj-pop-close:hover{color:var(--dsw-alias-label-primary);background:var(--dsw-alias-interactive-bg-hover)}
.hj-pop-row{display:flex;align-items:baseline;justify-content:space-between;gap:10px;padding:2px 0;color:var(--dsw-alias-label-secondary)}
.hj-pop-row span:last-child{font-family:ui-monospace,"SF Mono",Menlo,monospace;font-variant-numeric:tabular-nums;color:var(--dsw-alias-label-primary)}
.hj-pop-hr{height:1px;background:var(--dsw-alias-border-l1);margin:8px 0}
.hj-pop-total{display:flex;align-items:baseline;justify-content:space-between;gap:10px;padding-top:2px}
.hj-pop-total b{font:700 17px/1.3 ui-monospace,"SF Mono",Menlo,monospace;color:var(--dsw-alias-brand-primary);font-variant-numeric:tabular-nums}
.hj-pop-badge{display:inline-flex;align-items:center;gap:4px;padding:1px 7px;border-radius:999px;font-size:10.5px;font-weight:600}
.hj-pop-badge.peak{color:var(--dsw-alias-state-error-primary);background:color-mix(in srgb,var(--dsw-alias-state-error-primary) 13%,transparent)}
.hj-pop-badge.off{color:var(--dsw-alias-state-success-primary);background:color-mix(in srgb,var(--dsw-alias-state-success-primary) 13%,transparent)}
.hj-pop-note{color:var(--dsw-alias-label-tertiary);font-size:10.5px;line-height:1.55;margin-top:7px}
.hj-splash{position:fixed;inset:0;z-index:2147483000;pointer-events:auto;display:flex;align-items:center;justify-content:center;background:var(--dsw-alias-bg-base);overflow:hidden;opacity:1;transition:opacity var(--hj-fade,520ms) ease}
.hj-splash.hj-leave{opacity:0;pointer-events:none}
.hj-splash > img,.hj-splash > video{position:absolute;inset:0;width:100%;height:100%;max-width:none;max-height:none;display:block}
.hj-splash.hj-fit-auto{background:radial-gradient(circle at 50% 42%,color-mix(in srgb,var(--dsw-alias-brand-primary) 18%,#0b100d),#080b09 74%)}
.hj-splash-auto-bg{position:absolute;inset:-6%;background:radial-gradient(circle at 22% 28%,color-mix(in srgb,var(--dsw-alias-brand-primary) 32%,transparent),transparent 42%),radial-gradient(circle at 78% 74%,color-mix(in srgb,var(--dsw-alias-brand-primary) 22%,transparent),transparent 46%);filter:blur(18px);opacity:.7;pointer-events:none}
.hj-splash-auto-frame{position:relative;z-index:1;display:inline-block;max-width:92vw;max-height:88vh;border-radius:16px;overflow:hidden;border:1px solid rgba(255,255,255,.14);background:#000;box-shadow:0 26px 80px rgba(0,0,0,.55);line-height:0}
.hj-splash-auto-frame img,.hj-splash-auto-frame video{position:relative;inset:auto;display:block;width:auto;height:auto;max-width:92vw;max-height:88vh;object-fit:contain}
.hj-splash-vignette{position:absolute;inset:0;pointer-events:none;background:linear-gradient(180deg,rgba(0,0,0,.16),transparent 22%,transparent 72%,rgba(0,0,0,.42))}
.hj-splash-brand{position:absolute;z-index:3;left:26px;bottom:22px;display:flex;align-items:center;gap:8px;color:#fff;text-shadow:0 1px 12px rgba(0,0,0,.45);font-size:13px;letter-spacing:3px;opacity:.9}
.hj-splash-brand i{display:block;width:9px;height:9px;border-radius:50%;background:#8ef0b7;box-shadow:0 0 18px #8ef0b7}
.hj-splash-music{position:absolute;z-index:3;right:24px;bottom:62px;display:inline-flex;align-items:center;gap:6px;padding:6px 12px;border-radius:999px;border:1px solid rgba(255,255,255,.28);background:rgba(15,20,17,.30);color:#fff;font-size:11.5px;cursor:pointer;backdrop-filter:blur(8px)}
.hj-splash-music:hover{background:rgba(15,20,17,.52)}
.hj-splash-music b{font-size:12px;line-height:1}
.hj-splash-skip{position:absolute;z-index:3;right:24px;bottom:20px;padding:7px 13px;border-radius:999px;border:1px solid rgba(255,255,255,.36);background:rgba(15,20,17,.34);color:#fff;font-size:12px;cursor:pointer;backdrop-filter:blur(8px)}
.hj-splash-skip:hover{background:rgba(15,20,17,.56)}
.hj-splash-default{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;background:radial-gradient(circle at 50% 38%,color-mix(in srgb,var(--dsw-alias-brand-primary) 34%,#111),#0d1511 72%)}
.hj-splash-default:before{content:"";position:absolute;width:42vmin;height:42vmin;border-radius:50%;border:1px solid rgba(180,255,214,.22);box-shadow:0 0 80px rgba(80,220,150,.18) inset;animation:hj-breathe 3.2s ease-in-out infinite}
.hj-splash-default:after{content:"";position:absolute;width:28vmin;height:28vmin;border-radius:42% 58% 54% 46%/48% 42% 58% 52%;background:linear-gradient(145deg,#a8f3c6,#2fa86f);filter:blur(.4px);animation:hj-float 4.8s ease-in-out infinite;box-shadow:0 0 80px rgba(80,220,150,.38)}
.hj-splash-word{position:relative;z-index:2;color:#f2fff7;font-size:clamp(30px,6vw,62px);letter-spacing:.35em;text-indent:.35em;font-weight:600;text-shadow:0 2px 30px rgba(0,0,0,.35);animation:hj-fadein 1.1s ease both}
@keyframes hj-breathe{0%,100%{transform:scale(.96);opacity:.55}50%{transform:scale(1.04);opacity:.9}}
@keyframes hj-float{0%,100%{transform:translateY(-6px) rotate(-2deg)}50%{transform:translateY(8px) rotate(2deg)}}
@keyframes hj-fadein{from{opacity:0;transform:translateY(8px)}to{opacity:1;transform:none}}
.hj-music-dock{pointer-events:auto}
.hj-music-mini-progress{position:fixed;z-index:2147483001;right:22px;bottom:62px;width:42px;height:3px;border-radius:999px;background:color-mix(in srgb,var(--dsw-alias-label-tertiary) 28%,transparent);overflow:hidden;pointer-events:none}
.hj-music-mini-progress i{display:block;height:100%;border-radius:999px;background:var(--dsw-alias-brand-primary);transition:width .2s linear}
.hj-music-fab{position:fixed;z-index:2147483001;right:22px;bottom:70px;width:42px;height:42px;border-radius:50%;border:1px solid color-mix(in srgb,var(--dsw-alias-brand-primary) 55%,var(--dsw-alias-border-l2));background:var(--dsw-alias-bg-overlay);color:var(--dsw-alias-brand-primary);box-shadow:0 8px 24px rgba(0,0,0,.28);font-size:19px;line-height:1;cursor:pointer;display:flex;align-items:center;justify-content:center;transition:transform .15s,background .15s}
.hj-music-fab:hover{transform:scale(1.06);background:var(--dsw-alias-bg-layer-2)}
.hj-music-fab[data-playing="1"]{background:var(--dsw-alias-brand-primary);color:var(--dsw-alias-label-primary-inverted);animation:hj-music-pulse 2.4s ease-in-out infinite}
.hj-music-fab[data-blocked="1"]{border-color:var(--dsw-alias-state-warn-primary);color:var(--dsw-alias-state-warn-primary)}
.hj-music-panel{position:fixed;z-index:2147483002;right:20px;bottom:122px;width:286px;max-width:calc(100vw - 24px);padding:12px 13px;border-radius:13px;border:1px solid var(--dsw-alias-border-l2);background:var(--dsw-alias-bg-overlay);color:var(--dsw-alias-label-primary);box-shadow:0 18px 48px rgba(0,0,0,.34);font-size:12px;line-height:1.6}
.hj-music-head{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:8px}
.hj-music-head b{font-size:12.5px;letter-spacing:.2px}
.hj-music-close{border:0;background:none;color:var(--dsw-alias-label-tertiary);cursor:pointer;font-size:15px;line-height:1;padding:2px 4px;border-radius:4px}
.hj-music-close:hover{color:var(--dsw-alias-label-primary);background:var(--dsw-alias-interactive-bg-hover)}
.hj-music-track{color:var(--dsw-alias-label-secondary);font-size:11px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;margin-bottom:8px}
.hj-music-controls{display:flex;align-items:center;gap:8px}
.hj-music-play{flex:0 0 auto;padding:6px 12px;border-radius:8px;border:1px solid var(--dsw-alias-brand-primary);background:var(--dsw-alias-brand-primary);color:var(--dsw-alias-label-primary-inverted);font-size:12px;cursor:pointer}
.hj-music-play:hover{background:var(--dsw-alias-button-primary-hover)}
.hj-music-panel .hj-range{width:100%}
.hj-music-row{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-top:8px;color:var(--dsw-alias-label-secondary)}
.hj-music-note{color:var(--dsw-alias-label-tertiary);font-size:10.5px;line-height:1.5;margin-top:6px}
@keyframes hj-music-pulse{0%,100%{box-shadow:0 8px 24px rgba(0,0,0,.28),0 0 0 0 color-mix(in srgb,var(--dsw-alias-brand-primary) 38%,transparent)}50%{box-shadow:0 8px 24px rgba(0,0,0,.28),0 0 0 7px transparent}}
.hj-music-tabs{display:flex;gap:4px;padding:3px;margin-bottom:8px;border-radius:8px;background:var(--dsw-alias-bg-layer-2)}
.hj-music-tab{flex:1;padding:5px 6px;border:0;border-radius:6px;background:transparent;color:var(--dsw-alias-label-tertiary);font-size:11px;cursor:pointer}
.hj-music-tab:hover{color:var(--dsw-alias-label-primary)}
.hj-music-tab.active{background:var(--dsw-alias-bg-overlay);color:var(--dsw-alias-label-primary);box-shadow:0 1px 3px rgba(0,0,0,.14)}
.hj-music-body{display:flex;flex-direction:column;gap:6px}
.hj-music-progress-wrap{padding:2px 0 4px}
.hj-music-progress{width:100%;height:4px;accent-color:var(--dsw-alias-brand-primary);cursor:pointer}
.hj-music-time{display:flex;justify-content:space-between;margin-top:2px;color:var(--dsw-alias-label-tertiary);font:10.5px/1.4 ui-monospace,"SF Mono",Menlo,monospace;font-variant-numeric:tabular-nums}
.hj-music-controls{display:flex;align-items:center;justify-content:center;gap:8px;margin:3px 0}
.hj-music-skip{flex:0 0 auto;padding:5px 8px;border-radius:8px;border:1px solid var(--dsw-alias-border-l2);background:var(--dsw-alias-bg-layer-2);color:var(--dsw-alias-label-secondary);font-size:12px;cursor:pointer}
.hj-music-skip:hover{background:var(--dsw-alias-interactive-bg-hover);color:var(--dsw-alias-label-primary)}
.hj-music-list{display:flex;flex-direction:column;gap:2px;max-height:238px;overflow:auto;padding-right:2px}
.hj-music-item{display:flex;align-items:center;gap:7px;width:100%;padding:6px 7px;border:0;border-radius:7px;background:transparent;color:var(--dsw-alias-label-secondary);text-align:left;cursor:pointer;font-size:11.5px}
.hj-music-item:hover{background:var(--dsw-alias-interactive-bg-hover);color:var(--dsw-alias-label-primary)}
.hj-music-item.active{background:var(--dsw-alias-bg-layer-2);color:var(--dsw-alias-brand-primary);font-weight:600}
.hj-music-item-index{color:var(--dsw-alias-label-tertiary);font:10px/1 ui-monospace,"SF Mono",Menlo,monospace;font-variant-numeric:tabular-nums}
.hj-music-item-name{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.hj-music-item-lyric{flex:0 0 auto;padding:0 4px;border-radius:4px;border:1px solid var(--dsw-alias-border-l1);color:var(--dsw-alias-label-tertiary);font-size:9px}
.hj-music-lyrics{max-height:238px;overflow:auto;padding:4px 3px;scroll-behavior:smooth}
.hj-music-lyric{padding:4px 2px;color:var(--dsw-alias-label-tertiary);font-size:11.5px;line-height:1.6;transition:color .2s,transform .2s}
.hj-music-lyric.active{color:var(--dsw-alias-brand-primary);font-weight:600;transform:translateX(2px)}
.hj-music-manage{margin:2px 0 12px;padding:8px 9px;border-radius:10px;border:1px solid var(--dsw-alias-border-l1);background:var(--dsw-alias-bg-layer-2)}
.hj-music-manage-title{margin-bottom:5px;color:var(--dsw-alias-label-tertiary);font-size:11px;letter-spacing:.3px}
.hj-music-manage-item{display:flex;align-items:center;gap:5px;padding:3px 0;border-top:1px solid var(--dsw-alias-border-l1)}
.hj-music-manage-item:first-of-type{border-top:0}
.hj-music-manage-item.active .hj-music-manage-name{color:var(--dsw-alias-brand-primary);font-weight:600}
.hj-music-manage-name{flex:1;min-width:0;padding:2px 0;border:0;background:none;color:var(--dsw-alias-label-secondary);text-align:left;font-size:11.5px;cursor:pointer;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.hj-music-manage-name:hover{color:var(--dsw-alias-label-primary)}
.hj-music-manage-tags{color:var(--dsw-alias-label-tertiary);font-size:9.5px;white-space:nowrap}
.hj-music-manage-item .hj-btn{padding:4px 7px;font-size:10.5px}
.hj-lyrics-editor{margin:0 0 12px;padding:9px;border-radius:10px;border:1px solid var(--dsw-alias-border-l1);background:var(--dsw-alias-bg-layer-2)}
.hj-lyrics-input{width:100%;min-height:132px;resize:vertical;padding:8px 9px;border-radius:8px;border:1px solid var(--dsw-alias-border-l2);background:var(--dsw-alias-bg-layer-1);color:var(--dsw-alias-label-primary);font:11px/1.6 ui-monospace,"SF Mono",Menlo,monospace;outline:none}
.hj-lyrics-input:focus{border-color:var(--dsw-alias-brand-primary)}
.hj-music-fab{ touch-action:none; user-select:none; cursor:grab; }
.hj-music-fab:active{cursor:grabbing}
.hj-music-panel{max-height:min(520px,calc(100vh - 24px));overflow:hidden}

.hj-header-music{display:inline-flex;align-items:center;gap:4px;min-width:0;max-width:300px}
.hj-header-music-toggle{border:0;background:none;color:var(--dsw-alias-brand-primary);cursor:pointer;font-size:10px;line-height:1;padding:2px 3px;border-radius:4px;flex:0 0 auto}
.hj-header-music-toggle:hover{background:var(--dsw-alias-interactive-bg-hover)}
.hj-header-music-range{width:64px;height:3px;accent-color:var(--dsw-alias-brand-primary);cursor:pointer;flex:0 0 auto}
.hj-header-lyric{max-width:150px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;border:0;background:none;color:var(--dsw-alias-label-secondary);font-size:10.5px;line-height:1.5;padding:2px 4px;border-radius:4px;text-align:left;cursor:pointer;min-width:0}
.hj-header-lyric:hover{color:var(--dsw-alias-brand-primary);background:var(--dsw-alias-interactive-bg-hover)}
@media (max-width:900px){.hj-header-music-range{width:44px}.hj-header-lyric{max-width:86px}}
@media (max-width:640px){.hj-page{gap:12px}.hj-hero{padding:16px}.hj-section{padding:13px}.hj-field{align-items:flex-start;flex-direction:column;gap:7px}.hj-select{width:100%}.hj-popover{width:calc(100vw - 20px)}}
`;

    let globalStyleElement = null;
    let fontStyleElement = null;
    let themeService = null;
    let themeLayerDispose = null;
    let appliedThemeId = null;
    let localeService = null;
    let translateFn = null;

    function tr(key, fallback) {
      if (!translateFn) return fallback || key;
      try {
        const value = translateFn(key);
        return typeof value === 'string' && value.length > 0 && value !== key ? value : (fallback || key);
      } catch {
        return fallback || key;
      }
    }

    function useLocaleTick() {
      const [, setTick] = React.useState(0);
      React.useEffect(() => {
        if (!localeService || typeof localeService.subscribe !== 'function') return undefined;
        return localeService.subscribe(() => setTick((value) => value + 1));
      }, []);
    }

    function installStyles() {
      if (typeof document === 'undefined') return () => {};
      const existing = document.getElementById('huanjing-style');
      if (existing) {
        globalStyleElement = existing;
      } else {
        globalStyleElement = document.createElement('style');
        globalStyleElement.id = 'huanjing-style';
        document.head.appendChild(globalStyleElement);
      }
      globalStyleElement.textContent = CSS;

      const existingFont = document.getElementById('huanjing-font');
      if (existingFont) {
        fontStyleElement = existingFont;
      } else {
        fontStyleElement = document.createElement('style');
        fontStyleElement.id = 'huanjing-font';
        document.head.appendChild(fontStyleElement);
      }
      return () => {
        try {
          if (globalStyleElement && globalStyleElement.parentNode) globalStyleElement.remove();
          if (fontStyleElement && fontStyleElement.parentNode) fontStyleElement.remove();
        } finally {
          globalStyleElement = null;
          fontStyleElement = null;
        }
      };
    }

    function applyFont(fontId) {
      if (!fontStyleElement) return;
      const font = FONTS.find((item) => item.id === fontId) || FONTS[0];
      if (!font.stack) {
        fontStyleElement.textContent = '';
        return;
      }
      fontStyleElement.textContent = `
:root, body {
  --dsw-font-family: ${font.stack} !important;
  --dsw-font-family-brand: ${font.stack} !important;
  --dsh-font-family-text: ${font.stack} !important;
}
body, body :not(code):not(pre):not(kbd):not(samp):not(pre *):not(code *):not(kbd *):not(samp *):not([class*="monaco"]):not([class*="monaco"] *):not([class*="cm-"]):not([class*="cm-"] *):not([data-language] *) {
  font-family: ${font.stack} !important;
}
body :is(code, pre, kbd, samp, [class*="monaco"], [class*="cm-"], [data-language]) {
  font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace !important;
}
`;
    }

    function applyTheme(themeId) {
      if (themeId === appliedThemeId && (themeId === 'original' || themeLayerDispose)) return;
      if (themeLayerDispose) {
        try {
          themeLayerDispose();
        } catch {
          // 旧层可能已被同一 source 覆盖。
        }
        themeLayerDispose = null;
      }
      appliedThemeId = themeId;
      if (themeId === 'original' || !themeService) return;
      const preset = THEMES.find((item) => item.id === themeId);
      if (!preset || preset.original) return;
      try {
        themeLayerDispose = themeService.overrideTokens('huanjing', buildThemeLayer(preset));
      } catch (error) {
        console.warn('[画境] 应用画风失败：', error);
      }
    }

    function applyVisuals() {
      applyTheme(config.theme);
      applyFont(config.font);
    }

    /* ============================== 启动动画 ============================== */

    const musicProgressState = {
      active: false,
      playing: false,
      blocked: false,
      currentTime: 0,
      duration: 0,
      trackName: '',
      canPrev: false,
      canNext: false,
    };
    const musicProgressListeners = new Set();
    const musicPanelRequests = new Set();
    let musicProgressController = null;

    function requestMusicPanel(tab = 'lyrics') {
      for (const listener of musicPanelRequests) {
        try {
          listener(tab);
        } catch {
          // 忽略。
        }
      }
    }

    function publishMusicProgress(patch) {
      let changed = false;
      for (const key of Object.keys(patch)) {
        if (musicProgressState[key] !== patch[key]) {
          musicProgressState[key] = patch[key];
          changed = true;
        }
      }
      if (!changed) return;
      for (const listener of musicProgressListeners) {
        try {
          listener();
        } catch {
          // 忽略。
        }
      }
    }

    function useMusicProgress() {
      const [snapshot, setSnapshot] = React.useState({ ...musicProgressState });
      React.useEffect(() => {
        const listener = () => setSnapshot({ ...musicProgressState });
        musicProgressListeners.add(listener);
        listener();
        return () => musicProgressListeners.delete(listener);
      }, []);
      return snapshot;
    }

    const AUDIO_FADE_MS = 900;
    const audioFadeFrames = new WeakMap();

    function cancelAudioFade(audio) {
      if (!audio) return;
      const frame = audioFadeFrames.get(audio);
      if (frame !== undefined) {
        try {
          window.cancelAnimationFrame(frame);
        } catch {
          // 忽略。
        }
        audioFadeFrames.delete(audio);
      }
    }

    function fadeAudio(audio, target, duration = AUDIO_FADE_MS) {
      if (!audio) return;
      cancelAudioFade(audio);
      const to = Math.min(1, Math.max(0, Number(target) || 0));
      const from = Number(audio.volume) || 0;
      if (duration <= 0 || Math.abs(to - from) < 0.001) {
        audio.volume = to;
        return;
      }
      const startedAt = performance.now();
      const step = (now) => {
        const progress = Math.min(1, (now - startedAt) / duration);
        audio.volume = from + (to - from) * progress;
        if (progress < 1) {
          audioFadeFrames.set(audio, window.requestAnimationFrame(step));
        } else {
          audio.volume = to;
          audioFadeFrames.delete(audio);
        }
      };
      audioFadeFrames.set(audio, window.requestAnimationFrame(step));
    }

    let splashClaimed = false;
    let splashSequence = 0;
    const splashListeners = new Set();

    function requestSplash(preview) {
      splashSequence += 1;
      const request = { id: splashSequence, preview: preview === true };
      for (const listener of splashListeners) {
        try {
          listener(request);
        } catch {
          // 忽略。
        }
      }
    }

    const WINDOW_FIT_MIN_WIDTH = 520;
    const WINDOW_FIT_MIN_HEIGHT = 600;

    function restoreWindowBounds(ref) {
      const saved = ref.current;
      if (!saved) return;
      ref.current = null;
      try {
        window.resizeTo(saved.width, saved.height);
      } catch {
        // 浏览器不允许调整主窗口时忽略。
      }
      try {
        window.moveTo(saved.x, saved.y);
      } catch {
        // 忽略。
      }
    }

    function applyWindowFit(ratio, restoreRef) {
      if (!Number.isFinite(ratio) || ratio <= 0) return;
      if (document.fullscreenElement) return;
      const outerWidth = Number(window.outerWidth) || Number(window.innerWidth) || 0;
      const outerHeight = Number(window.outerHeight) || Number(window.innerHeight) || 0;
      const innerWidth = Number(window.innerWidth) || outerWidth;
      const innerHeight = Number(window.innerHeight) || outerHeight;
      if (outerWidth <= 0 || outerHeight <= 0) return;

      if (!restoreRef.current) {
        restoreRef.current = {
          width: outerWidth,
          height: outerHeight,
          x: Number(window.screenX) || 0,
          y: Number(window.screenY) || 0,
        };
      }

      const screenInfo = window.screen || {};
      const availWidth = Math.max(360, Number(screenInfo.availWidth) || innerWidth);
      const availHeight = Math.max(360, Number(screenInfo.availHeight) || innerHeight);
      const availLeft = Number.isFinite(Number(screenInfo.availLeft)) ? Number(screenInfo.availLeft) : 0;
      const availTop = Number.isFinite(Number(screenInfo.availTop)) ? Number(screenInfo.availTop) : 0;
      const chromeWidth = Math.max(0, outerWidth - innerWidth);
      const chromeHeight = Math.max(0, outerHeight - innerHeight);
      const maxOuterWidth = Math.max(WINDOW_FIT_MIN_WIDTH, availWidth - 24);
      const maxOuterHeight = Math.max(WINDOW_FIT_MIN_HEIGHT, availHeight - 24);
      const maxContentWidth = Math.max(320, maxOuterWidth - chromeWidth);
      const maxContentHeight = Math.max(320, maxOuterHeight - chromeHeight);

      let contentWidth = Math.min(innerWidth, maxContentWidth);
      let contentHeight = Math.min(innerHeight, maxContentHeight);
      if (ratio >= 1) {
        contentWidth = Math.max(contentWidth, Math.min(720, maxContentWidth));
        contentHeight = contentWidth / ratio;
        if (contentHeight > maxContentHeight) {
          contentHeight = maxContentHeight;
          contentWidth = contentHeight * ratio;
        }
      } else {
        contentHeight = Math.max(contentHeight, Math.min(720, maxContentHeight));
        contentWidth = contentHeight * ratio;
        if (contentWidth > maxContentWidth) {
          contentWidth = maxContentWidth;
          contentHeight = contentWidth / ratio;
        }
      }
      contentWidth = Math.max(320, Math.min(contentWidth, maxContentWidth));
      contentHeight = Math.max(320, Math.min(contentHeight, maxContentHeight));

      let targetWidth = Math.max(WINDOW_FIT_MIN_WIDTH, Math.min(Math.round(contentWidth + chromeWidth), maxOuterWidth));
      let targetHeight = Math.max(WINDOW_FIT_MIN_HEIGHT, Math.min(Math.round(contentHeight + chromeHeight), maxOuterHeight));
      const before = restoreRef.current;
      const targetX = Math.max(availLeft + 8, Math.min(availLeft + availWidth - targetWidth - 8, Math.round(before.x + (before.width - targetWidth) / 2)));
      const targetY = Math.max(availTop + 8, Math.min(availTop + availHeight - targetHeight - 8, Math.round(before.y + (before.height - targetHeight) / 2)));

      try {
        window.resizeTo(targetWidth, targetHeight);
      } catch {
        return;
      }
      try {
        window.moveTo(targetX, targetY);
      } catch {
        // 位置调整失败时保留系统默认位置。
      }
    }

    function SplashOverlay() {
      const cfg = useConfig();
      useLocaleTick();
      const [session, setSession] = React.useState(() => {
        if (splashClaimed) return null;
        if (!config.splash.enabled) return null;
        splashClaimed = true;
        return { id: 0, preview: false };
      });
      const [leaving, setLeaving] = React.useState(false);
      const closing = React.useRef(false);
      const closeTimer = React.useRef(0);
      const windowRestoreRef = React.useRef(null);
      const windowResizeKey = React.useRef('');
      const audioRef = React.useRef(null);
      const musicPauseTimer = React.useRef(0);
      const musicVolumeInitialized = React.useRef(false);
      const [musicPlaying, setMusicPlaying] = React.useState(false);
      const [musicBlocked, setMusicBlocked] = React.useState(false);

      React.useEffect(() => {
        const listener = (request) => {
          if (closeTimer.current) {
            window.clearTimeout(closeTimer.current);
            closeTimer.current = 0;
          }
          restoreWindowBounds(windowRestoreRef);
          windowResizeKey.current = '';
          if (musicPauseTimer.current) {
            window.clearTimeout(musicPauseTimer.current);
            musicPauseTimer.current = 0;
          }
          if (audioRef.current) cancelAudioFade(audioRef.current);
          closing.current = false;
          setLeaving(false);
          setSession(request);
        };
        splashListeners.add(listener);
        return () => splashListeners.delete(listener);
      }, []);

      const close = React.useCallback(() => {
        if (closing.current) return;
        closing.current = true;
        if (closeTimer.current) {
          window.clearTimeout(closeTimer.current);
          closeTimer.current = 0;
        }
        restoreWindowBounds(windowRestoreRef);
        windowResizeKey.current = '';
        const audio = audioRef.current;
        const willFade = Boolean(cfg.splash.music.enabled && cfg.splash.music.mode !== 'always' && audio && !audio.paused);
        if (musicPauseTimer.current) {
          window.clearTimeout(musicPauseTimer.current);
          musicPauseTimer.current = 0;
        }
        if (audio) {
          if (willFade) {
            fadeAudio(audio, 0, AUDIO_FADE_MS);
            musicPauseTimer.current = window.setTimeout(() => {
              try {
                audio.pause();
              } catch {
                // 忽略。
              }
            }, AUDIO_FADE_MS);
          } else {
            try {
              audio.pause();
            } catch {
              // 忽略。
            }
          }
        }
        setMusicPlaying(false);
        setLeaving(true);
        closeTimer.current = window.setTimeout(() => {
          setSession(null);
          setLeaving(false);
          closing.current = false;
        }, Math.max(120, cfg.splash.fade, willFade ? AUDIO_FADE_MS : 0));
      }, [cfg.splash.fade, cfg.splash.music.enabled, cfg.splash.music.mode]);

      React.useEffect(() => {
        if (!session) return undefined;
        closing.current = false;
        const media = cfg.splash.media;
        const delay = media && media.kind === 'video' ? 20000 : cfg.splash.duration;
        const timer = window.setTimeout(close, delay);
        return () => window.clearTimeout(timer);
      }, [session, cfg.splash.media, cfg.splash.duration, close]);

      React.useEffect(() => {
        if (!session) return undefined;
        const onKey = (event) => {
          if (event.key === 'Escape') close();
        };
        document.addEventListener('keydown', onKey);
        return () => document.removeEventListener('keydown', onKey);
      }, [session, close]);

      React.useEffect(() => {
        if (cfg.splash.fit !== 'window') {
          restoreWindowBounds(windowRestoreRef);
          windowResizeKey.current = '';
        }
      }, [cfg.splash.fit]);

      const musicMedia = cfg.splash.music && cfg.splash.music.media;
      const musicEnabled = Boolean(cfg.splash.music && cfg.splash.music.enabled && cfg.splash.music.mode !== 'always' && musicMedia);
      const musicUrl = musicMedia ? musicMedia.url : '';

      React.useEffect(() => {
        const audio = audioRef.current;
        if (!audio) {
          setMusicPlaying(false);
          return undefined;
        }
        if (!session || !musicEnabled) {
          audio.pause();
          setMusicPlaying(false);
          setMusicBlocked(false);
          return undefined;
        }
        audio.currentTime = 0;
        audio.volume = 0;
        audio.loop = cfg.splash.music.loop;
        let cancelled = false;
        const attempt = audio.play();
        if (attempt && typeof attempt.then === 'function') {
          attempt.then(() => {
            if (!cancelled) {
              setMusicPlaying(true);
              setMusicBlocked(false);
              fadeAudio(audio, cfg.splash.music.volume, AUDIO_FADE_MS);
            }
          }).catch(() => {
            if (!cancelled) {
              setMusicPlaying(false);
              setMusicBlocked(true);
            }
          });
        }
        return () => {
          cancelled = true;
          cancelAudioFade(audio);
          try {
            audio.pause();
          } catch {
            // 忽略。
          }
        };
      }, [session, musicEnabled, musicUrl]);

      React.useEffect(() => {
        const audio = audioRef.current;
        if (!audio) return;
        audio.loop = cfg.splash.music.loop;
        if (!musicVolumeInitialized.current) {
          musicVolumeInitialized.current = true;
          return;
        }
        cancelAudioFade(audio);
        audio.volume = cfg.splash.music.volume;
      }, [cfg.splash.music.volume, cfg.splash.music.loop]);

      React.useEffect(() => {
        const restore = () => restoreWindowBounds(windowRestoreRef);
        window.addEventListener('beforeunload', restore);
        return () => {
          window.removeEventListener('beforeunload', restore);
          restore();
        };
      }, []);

      React.useEffect(() => () => {
        if (closeTimer.current) window.clearTimeout(closeTimer.current);
        if (musicPauseTimer.current) window.clearTimeout(musicPauseTimer.current);
        if (audioRef.current) {
          cancelAudioFade(audioRef.current);
          try {
            audioRef.current.pause();
          } catch {
            // 忽略。
          }
        }
      }, []);

      const toggleMusic = () => {
        const audio = audioRef.current;
        if (!audio) return;
        if (musicPauseTimer.current) {
          window.clearTimeout(musicPauseTimer.current);
          musicPauseTimer.current = 0;
        }
        if (!audio.paused) {
          fadeAudio(audio, 0, AUDIO_FADE_MS);
          setMusicPlaying(false);
          musicPauseTimer.current = window.setTimeout(() => {
            try {
              audio.pause();
            } catch {
              // 忽略。
            }
          }, AUDIO_FADE_MS);
          return;
        }
        audio.volume = 0;
        const attempt = audio.play();
        if (attempt && typeof attempt.then === 'function') {
          attempt.then(() => {
            setMusicPlaying(true);
            setMusicBlocked(false);
            fadeAudio(audio, cfg.splash.music.volume, AUDIO_FADE_MS);
          }).catch(() => {
            setMusicPlaying(false);
            setMusicBlocked(true);
          });
        }
      };

      const fitWindowToMedia = (width, height) => {
        if (!session || closing.current || cfg.splash.fit !== 'window' || !width || !height) return;
        const media = cfg.splash.media;
        const key = `${session.id}:${media ? media.url : ''}:${(width / height).toFixed(4)}`;
        if (windowResizeKey.current === key) return;
        windowResizeKey.current = key;
        applyWindowFit(width / height, windowRestoreRef);
      };

      if (!session) return null;
      const media = cfg.splash.media;
      const mediaElement = media
        ? media.kind === 'video'
          ? h('video', {
            src: media.url,
            autoPlay: true,
            muted: true,
            playsInline: true,
            preload: 'auto',
            style: { objectFit: cfg.splash.fit === 'auto' ? 'contain' : (cfg.splash.fit === 'window' ? 'cover' : cfg.splash.fit) },
            onLoadedMetadata: (event) => fitWindowToMedia(event.currentTarget.videoWidth, event.currentTarget.videoHeight),
            onEnded: close,
            onError: close,
          })
          : h('img', {
            src: media.url,
            alt: media.name || '启动动画',
            style: { objectFit: cfg.splash.fit === 'auto' ? 'contain' : (cfg.splash.fit === 'window' ? 'cover' : cfg.splash.fit) },
            onLoad: (event) => fitWindowToMedia(event.currentTarget.naturalWidth, event.currentTarget.naturalHeight),
            onError: close,
          })
        : h('div', { className: 'hj-splash-default' }, h('div', { className: 'hj-splash-word' }, '画境'));
      const mediaNode = media && cfg.splash.fit === 'auto'
        ? h('div', { className: 'hj-splash-auto-frame' }, mediaElement)
        : mediaElement;

      return h('div', {
        className: `hj-splash hj-fit-${cfg.splash.fit}${leaving ? ' hj-leave' : ''}`,
        style: { '--hj-fade': `${cfg.splash.fade}ms` },
        role: 'dialog',
        'aria-modal': 'true',
        'aria-label': tr('section.splash', '启动动画'),
        onClick: close,
      },
        cfg.splash.fit === 'auto' ? h('div', { className: 'hj-splash-auto-bg' }) : null,
        musicEnabled
          ? h('audio', {
            ref: audioRef,
            src: musicUrl,
            loop: cfg.splash.music.loop,
            preload: 'auto',
            style: { display: 'none' },
            onPlay: () => setMusicPlaying(true),
            onPause: () => setMusicPlaying(false),
            onEnded: () => setMusicPlaying(false),
            onError: () => {
              setMusicPlaying(false);
              setMusicBlocked(true);
            },
          })
          : null,
        mediaNode,
        h('div', { className: 'hj-splash-vignette' }),
        h('div', { className: 'hj-splash-brand' }, h('i'), h('span', null, '画境')),
        musicEnabled
          ? h('button', {
            type: 'button',
            className: 'hj-splash-music',
            title: musicBlocked ? tr('music.autoplayHint', '浏览器可能拦截自动播放；点击开始播放音乐。') : undefined,
            onClick: (event) => {
              event.stopPropagation();
              toggleMusic();
            },
          },
            h('b', null, musicPlaying ? '❚❚' : '♪'),
            h('span', null, musicPlaying ? tr('music.playing', '音乐播放中') : tr('music.play', '播放音乐')))
          : null,
        h('button', {
          type: 'button',
          className: 'hj-splash-skip',
          onClick: (event) => {
            event.stopPropagation();
            close();
          },
        }, tr('splash.skip', '跳过')),
      );
    }

    /* ============================== 常驻音乐播放器 ============================== */

    function MusicDock() {
      const cfg = useConfig();
      useLocaleTick();
      const music = cfg.splash.music;
      const tracks = Array.isArray(music.tracks) ? music.tracks : [];
      const currentTrack = currentTrackOf(music);
      const active = Boolean(music.mode === 'always' && music.enabled && currentTrack);
      const currentUrl = currentTrack ? currentTrack.url : '';
      const [playing, setPlaying] = React.useState(false);
      const [blocked, setBlocked] = React.useState(false);
      const [open, setOpen] = React.useState(false);
      const [tab, setTab] = React.useState('player');
      const [currentTime, setCurrentTime] = React.useState(0);
      const [duration, setDuration] = React.useState(0);
      const [volumeDraft, setVolumeDraft] = React.useState(Math.round(music.volume * 100));
      const [dockPos, setDockPos] = React.useState(() => (music.dock ? { ...music.dock } : null));
      const audioRef = React.useRef(null);
      const rootRef = React.useRef(null);
      const dragRef = React.useRef(null);
      const activeLyricRef = React.useRef(null);
      const pauseTimerRef = React.useRef(0);
      const volumeInitialized = React.useRef(false);
      const latestControls = React.useRef({});

      const clampDock = (x, y) => ({
        x: Math.max(8, Math.min(Math.max(8, window.innerWidth - 50), Math.round(x))),
        y: Math.max(8, Math.min(Math.max(8, window.innerHeight - 50), Math.round(y))),
      });

      React.useEffect(() => {
        setVolumeDraft(Math.round(music.volume * 100));
      }, [music.volume]);

      React.useEffect(() => {
        if (music.dock) setDockPos(clampDock(music.dock.x, music.dock.y));
      }, [music.dock && music.dock.x, music.dock && music.dock.y]);

      React.useEffect(() => {
        const onResize = () => setDockPos((value) => (value ? clampDock(value.x, value.y) : value));
        window.addEventListener('resize', onResize);
        return () => window.removeEventListener('resize', onResize);
      }, []);

      React.useEffect(() => {
        publishMusicProgress({
          active,
          playing: false,
          blocked: false,
          currentTime: 0,
          duration: 0,
          trackName: currentTrack ? currentTrack.name : '',
          canPrev: tracks.length > 1,
          canNext: tracks.length > 1,
        });
      }, [active, currentTrack ? currentTrack.id : '', tracks.length]);

      React.useEffect(() => {
        publishMusicProgress({ playing, blocked });
      }, [playing, blocked]);

      const attemptPlay = () => {
        const audio = audioRef.current;
        if (!audio) return;
        if (pauseTimerRef.current) {
          window.clearTimeout(pauseTimerRef.current);
          pauseTimerRef.current = 0;
        }
        audio.volume = 0;
        const attempt = audio.play();
        if (attempt && typeof attempt.then === 'function') {
          attempt.then(() => {
            setPlaying(true);
            setBlocked(false);
            fadeAudio(audio, config.splash.music.volume, AUDIO_FADE_MS);
          }).catch(() => {
            setPlaying(false);
            setBlocked(true);
          });
        }
      };

      React.useEffect(() => {
        const audio = audioRef.current;
        if (!active || !audio) {
          if (audio) audio.pause();
          setPlaying(false);
          setOpen(false);
          return undefined;
        }
        audio.volume = 0;
        setCurrentTime(0);
        setDuration(0);
        let cancelled = false;
        const attempt = audio.play();
        if (attempt && typeof attempt.then === 'function') {
          attempt.then(() => {
            if (!cancelled) {
              setPlaying(true);
              setBlocked(false);
              fadeAudio(audio, music.volume, AUDIO_FADE_MS);
            }
          }).catch(() => {
            if (!cancelled) {
              setPlaying(false);
              setBlocked(true);
            }
          });
        }
        return () => {
          cancelled = true;
          cancelAudioFade(audio);
          try {
            audio.pause();
          } catch {
            // 忽略。
          }
        };
      }, [active, currentUrl]);

      React.useEffect(() => {
        if (!volumeInitialized.current) {
          volumeInitialized.current = true;
          return;
        }
        const audio = audioRef.current;
        if (!audio) return;
        cancelAudioFade(audio);
        audio.volume = music.volume;
      }, [music.volume]);

      React.useEffect(() => {
        if (!open) return undefined;
        const onDown = (event) => {
          if (rootRef.current && !rootRef.current.contains(event.target)) setOpen(false);
        };
        document.addEventListener('mousedown', onDown);
        return () => document.removeEventListener('mousedown', onDown);
      }, [open]);

      React.useEffect(() => {
        const listener = (tab) => {
          setOpen(true);
          setTab(tab || 'lyrics');
        };
        musicPanelRequests.add(listener);
        return () => musicPanelRequests.delete(listener);
      }, []);

      const lyrics = React.useMemo(
        () => parseLrc(currentTrack ? currentTrack.lyrics : ''),
        [currentTrack ? currentTrack.lyrics : ''],
      );
      let activeLyricIndex = -1;
      for (let index = 0; index < lyrics.length; index += 1) {
        if (lyrics[index].time <= currentTime + 0.15) activeLyricIndex = index;
        else break;
      }

      React.useEffect(() => {
        if (tab !== 'lyrics' || !activeLyricRef.current) return;
        try {
          activeLyricRef.current.scrollIntoView({ block: 'center', behavior: 'smooth' });
        } catch {
          // 忽略。
        }
      }, [activeLyricIndex, tab]);

      const playOrPause = () => {
        const audio = audioRef.current;
        if (!audio) return;
        if (pauseTimerRef.current) {
          window.clearTimeout(pauseTimerRef.current);
          pauseTimerRef.current = 0;
        }
        if (audio.paused) {
          attemptPlay();
          return;
        }
        fadeAudio(audio, 0, AUDIO_FADE_MS);
        setPlaying(false);
        pauseTimerRef.current = window.setTimeout(() => {
          try {
            audio.pause();
          } catch {
            // 忽略。
          }
        }, AUDIO_FADE_MS);
      };

      const selectTrack = (id, showPlayer = true) => {
        const latest = config.splash.music;
        if (!Array.isArray(latest.tracks) || !latest.tracks.some((track) => track.id === id)) return;
        setBlocked(false);
        if (showPlayer) setTab('player');
        void commitConfig({
          ...config,
          splash: {
            ...config.splash,
            music: { ...latest, currentId: id },
          },
        }, '');
      };

      const trackIndex = tracks.findIndex((track) => track.id === (currentTrack ? currentTrack.id : ''));
      const nextTrack = (auto = false) => {
        if (tracks.length === 0) return;
        if (trackIndex < tracks.length - 1) {
          selectTrack(tracks[trackIndex + 1].id, !auto);
          return;
        }
        if (music.loop || !auto) {
          selectTrack(tracks[0].id, !auto);
          return;
        }
        setPlaying(false);
      };

      const previousTrack = () => {
        if (tracks.length === 0) return;
        const previous = trackIndex > 0 ? trackIndex - 1 : tracks.length - 1;
        selectTrack(tracks[previous].id, true);
      };

      const handleEnded = () => {
        setPlaying(false);
        publishMusicProgress({ playing: false });
        nextTrack(true);
      };

      const seek = (value) => {
        const audio = audioRef.current;
        if (!audio) return;
        const target = Math.max(0, Number(value) || 0);
        try {
          audio.currentTime = target;
        } catch {
          // 忽略。
        }
        setCurrentTime(target);
      };

      const commitVolume = (value) => {
        const volume = Math.min(1, Math.max(0, Number(value) / 100));
        void commitConfig({
          ...config,
          splash: { ...config.splash, music: { ...config.splash.music, volume } },
        }, '');
      };

      const commitLoop = (value) => {
        void commitConfig({
          ...config,
          splash: { ...config.splash, music: { ...config.splash.music, loop: value } },
        }, '');
      };

      const commitDock = (position) => {
        void commitConfig({
          ...config,
          splash: { ...config.splash, music: { ...config.splash.music, dock: position } },
        }, '');
      };

      const activateDock = () => {
        if (open) {
          setOpen(false);
          return;
        }
        setOpen(true);
        if (!playing) attemptPlay();
      };

      const onPointerDown = (event) => {
        if (event.button !== undefined && event.button !== 0) return;
        const rect = event.currentTarget.getBoundingClientRect();
        dragRef.current = {
          pointerId: event.pointerId,
          startX: event.clientX,
          startY: event.clientY,
          baseX: rect.left,
          baseY: rect.top,
          moved: false,
          x: rect.left,
          y: rect.top,
        };
        try {
          event.currentTarget.setPointerCapture(event.pointerId);
        } catch {
          // 忽略。
        }
      };

      const onPointerMove = (event) => {
        const drag = dragRef.current;
        if (!drag || drag.pointerId !== event.pointerId) return;
        const dx = event.clientX - drag.startX;
        const dy = event.clientY - drag.startY;
        if (Math.abs(dx) > 3 || Math.abs(dy) > 3) drag.moved = true;
        if (!drag.moved) return;
        event.preventDefault();
        const next = clampDock(drag.baseX + dx, drag.baseY + dy);
        drag.x = next.x;
        drag.y = next.y;
        setDockPos(next);
      };

      const onPointerUp = (event) => {
        const drag = dragRef.current;
        dragRef.current = null;
        try {
          event.currentTarget.releasePointerCapture(event.pointerId);
        } catch {
          // 忽略。
        }
        if (!drag || drag.pointerId !== event.pointerId) return;
        if (drag.moved) commitDock({ x: drag.x, y: drag.y });
        else activateDock();
      };

      latestControls.current = {
        toggle: () => {
          const audio = audioRef.current;
          if (!audio) return;
          if (audio.paused) attemptPlay();
          else playOrPause();
        },
        next: () => nextTrack(false),
        prev: previousTrack,
        seek,
      };

      React.useEffect(() => {
        const controller = {
          toggle: () => latestControls.current.toggle(),
          next: () => latestControls.current.next(),
          prev: () => latestControls.current.prev(),
          seek: (value) => latestControls.current.seek(value),
        };
        musicProgressController = controller;
        return () => {
          if (musicProgressController === controller) musicProgressController = null;
          publishMusicProgress({ active: false, playing: false });
        };
      }, []);

      React.useEffect(() => () => {
        if (pauseTimerRef.current) window.clearTimeout(pauseTimerRef.current);
        const audio = audioRef.current;
        if (audio) {
          cancelAudioFade(audio);
          try {
            audio.pause();
          } catch {
            // 忽略。
          }
        }
      }, []);

      if (!active) return null;

      const progressPercent = duration > 0 ? Math.min(100, Math.max(0, (currentTime / duration) * 100)) : 0;
      const fabStyle = dockPos
        ? { left: `${dockPos.x}px`, top: `${dockPos.y}px`, right: 'auto', bottom: 'auto' }
        : undefined;
      const miniProgressStyle = dockPos
        ? { left: `${dockPos.x}px`, top: `${dockPos.y + 46}px`, right: 'auto', bottom: 'auto' }
        : undefined;
      const panelStyle = dockPos
        ? (() => {
          const width = 300;
          const height = 378;
          let left = dockPos.x - width + 42;
          if (left < 8) left = 8;
          if (left + width > window.innerWidth - 8) left = window.innerWidth - width - 8;
          let top = dockPos.y - height - 10;
          if (top < 8) top = dockPos.y + 52;
          if (top + height > window.innerHeight - 8) top = Math.max(8, window.innerHeight - height - 8);
          return { left: `${Math.max(8, left)}px`, top: `${Math.max(8, top)}px`, right: 'auto', bottom: 'auto' };
        })()
        : undefined;

      const tabs = [
        { id: 'player', label: tr('music.dock.player', '播放') },
        { id: 'list', label: tr('music.dock.list', '列表') },
        { id: 'lyrics', label: tr('music.dock.lyrics', '歌词') },
      ];

      return h('div', { className: 'hj-music-dock', ref: rootRef },
        h('audio', {
          ref: audioRef,
          src: currentUrl,
          preload: 'auto',
          style: { display: 'none' },
          onPlay: () => setPlaying(true),
          onPause: () => setPlaying(false),
          onEnded: handleEnded,
          onLoadedMetadata: (event) => {
            const value = Number(event.currentTarget.duration) || 0;
            setDuration(value);
            publishMusicProgress({ duration: value });
          },
          onDurationChange: (event) => {
            const value = Number(event.currentTarget.duration) || 0;
            setDuration(value);
            publishMusicProgress({ duration: value });
          },
          onTimeUpdate: (event) => {
            const value = Number(event.currentTarget.currentTime) || 0;
            setCurrentTime(value);
            publishMusicProgress({ currentTime: value });
          },
          onError: () => {
            setPlaying(false);
            setBlocked(true);
          },
        }),
        h('button', {
          type: 'button',
          className: 'hj-music-fab',
          style: fabStyle,
          'data-playing': playing ? '1' : '0',
          'data-blocked': blocked ? '1' : '0',
          title: playing ? tr('music.dock.pause', '暂停') : (blocked ? tr('music.dock.blocked', '点击开始播放') : tr('music.dock.play', '播放')),
          'aria-label': tr('music.dock.title', '画境音乐'),
          onPointerDown,
          onPointerMove,
          onPointerUp,
          onPointerCancel: () => { dragRef.current = null; },
          onKeyDown: (event) => {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault();
              activateDock();
            }
          },
        }, h('span', null, '♪')),
        h('div', { className: 'hj-music-mini-progress', style: miniProgressStyle, 'aria-hidden': true },
          h('i', { style: { width: `${progressPercent}%` } })),
        open
          ? h('div', { className: 'hj-music-panel', style: panelStyle, role: 'dialog', 'aria-label': tr('music.dock.title', '画境音乐') },
            h('div', { className: 'hj-music-head' },
              h('b', null, tr('music.dock.title', '画境音乐')),
              h('button', {
                type: 'button',
                className: 'hj-music-close',
                'aria-label': tr('music.dock.close', '收起播放器'),
                onClick: () => setOpen(false),
              }, '×')),
            h('div', { className: 'hj-music-track' }, currentTrack ? (currentTrack.name || 'audio') : ''),
            h('div', { className: 'hj-music-tabs' },
              tabs.map((item) => h('button', {
                key: item.id,
                type: 'button',
                className: `hj-music-tab${tab === item.id ? ' active' : ''}`,
                onClick: () => setTab(item.id),
              }, item.label))),
            tab === 'player'
              ? h('div', { className: 'hj-music-body' },
                h('div', { className: 'hj-music-progress-wrap' },
                  h('input', {
                    className: 'hj-music-progress',
                    type: 'range',
                    min: 0,
                    max: duration > 0 ? duration : 0,
                    step: 0.1,
                    value: Math.min(currentTime, duration > 0 ? duration : 0),
                    'aria-label': tr('music.dock.progress', '播放进度'),
                    onChange: (event) => seek(event.target.value),
                  }),
                  h('div', { className: 'hj-music-time' },
                    h('span', null, formatClock(currentTime)),
                    h('span', null, formatClock(duration)))),
                h('div', { className: 'hj-music-controls' },
                  h('button', { type: 'button', className: 'hj-music-skip', onClick: previousTrack, title: tr('music.dock.prev', '上一首') }, '⏮'),
                  h('button', { type: 'button', className: 'hj-music-play', onClick: playOrPause }, playing ? tr('music.dock.pause', '暂停') : tr('music.dock.play', '播放')),
                  h('button', { type: 'button', className: 'hj-music-skip', onClick: () => nextTrack(false), title: tr('music.dock.next', '下一首') }, '⏭')),
                h('div', { className: 'hj-music-row' },
                  h('span', null, tr('music.dock.volume', '音量')),
                  h('span', { className: 'hj-music-note' }, `${volumeDraft}%`)),
                h('input', {
                  className: 'hj-range',
                  type: 'range',
                  min: 0,
                  max: 100,
                  step: 5,
                  value: volumeDraft,
                  'aria-label': tr('music.dock.volume', '音量'),
                  onChange: (event) => {
                    const value = Number(event.target.value);
                    setVolumeDraft(value);
                    if (audioRef.current) {
                      cancelAudioFade(audioRef.current);
                      audioRef.current.volume = value / 100;
                    }
                  },
                  onPointerUp: (event) => commitVolume(event.currentTarget.value),
                  onKeyUp: (event) => commitVolume(event.currentTarget.value),
                }),
                h('div', { className: 'hj-music-row' },
                  h('span', null, tr('music.dock.loop', '循环列表')),
                  h(Toggle, {
                    checked: music.loop,
                    label: tr('music.dock.loop', '循环列表'),
                    onChange: commitLoop,
                  })),
                blocked ? h('div', { className: 'hj-music-note' }, tr('music.dock.blocked', '点击开始播放')) : null)
              : null,
            tab === 'list'
              ? h('div', { className: 'hj-music-list' },
                tracks.length === 0
                  ? h('div', { className: 'hj-music-note' }, tr('music.dock.empty', '播放列表为空'))
                  : tracks.map((track, index) => h('button', {
                    key: track.id,
                    type: 'button',
                    className: `hj-music-item${currentTrack && track.id === currentTrack.id ? ' active' : ''}`,
                    onClick: () => selectTrack(track.id, false),
                  },
                    h('span', { className: 'hj-music-item-index' }, String(index + 1).padStart(2, '0')),
                    h('span', { className: 'hj-music-item-name' }, track.name || 'audio'),
                    track.lyrics ? h('span', { className: 'hj-music-item-lyric' }, '词') : null)))
              : null,
            tab === 'lyrics'
              ? h('div', { className: 'hj-music-lyrics' },
                lyrics.length === 0
                  ? h('div', { className: 'hj-music-note' }, tr('music.dock.noLyrics', '暂无歌词；在设置里粘贴或导入 LRC 即可注入。'))
                  : lyrics.map((line, index) => h('div', {
                    key: `${line.time}-${index}`,
                    ref: index === activeLyricIndex ? activeLyricRef : undefined,
                    className: `hj-music-lyric${index === activeLyricIndex ? ' active' : ''}`,
                  }, line.text)))
              : null,
          )
          : null,
      );
    }

    /* ============================== 花费挂件 ============================== */

    function useNow(active) {
      const [now, setNow] = React.useState(() => Date.now());
      React.useEffect(() => {
        if (!active) return undefined;
        setNow(Date.now());
        const timer = window.setInterval(() => setNow(Date.now()), 1000);
        return () => window.clearInterval(timer);
      }, [active]);
      return now;
    }

    function HeaderMusic() {
      const cfg = useConfig();
      useLocaleTick();
      const state = useMusicProgress();
      const music = cfg.splash.music;
      const currentTrack = currentTrackOf(music);
      const active = Boolean(state.active && music.mode === 'always' && music.enabled && currentTrack);
      const lyrics = React.useMemo(
        () => parseLrc(currentTrack ? currentTrack.lyrics : ''),
        [currentTrack ? currentTrack.lyrics : ''],
      );
      let activeLyricIndex = -1;
      for (let index = 0; index < lyrics.length; index += 1) {
        if (lyrics[index].time <= state.currentTime + 0.15) activeLyricIndex = index;
        else break;
      }
      const line = activeLyricIndex >= 0 ? lyrics[activeLyricIndex].text : '';
      if (!active) return null;
      const control = musicProgressController;
      return h('span', { className: 'hj-header-music' },
        h('button', {
          type: 'button',
          className: 'hj-header-music-toggle',
          title: state.playing ? tr('music.dock.pause', '暂停') : tr('music.dock.play', '播放'),
          onClick: () => { if (control) control.toggle(); },
        }, state.playing ? '❚❚' : '▶'),
        h('input', {
          className: 'hj-header-music-range',
          type: 'range',
          min: 0,
          max: state.duration > 0 ? state.duration : 0,
          step: 0.1,
          value: Math.min(state.currentTime, state.duration > 0 ? state.duration : 0),
          title: `${state.trackName || (currentTrack ? currentTrack.name : '')} · ${formatClock(state.currentTime)} / ${formatClock(state.duration)}`,
          'aria-label': tr('music.dock.progress', '播放进度'),
          onChange: (event) => { if (control) control.seek(event.target.value); },
        }),
        h('button', {
          type: 'button',
          className: 'hj-header-lyric',
          title: line || tr('music.lyrics.none', '暂无歌词'),
          onClick: () => requestMusicPanel('lyrics'),
        }, line || tr('music.lyrics.none', '暂无歌词')),
      );
    }

    function CostWidget(props) {
      const cfg = useConfig();
      useLocaleTick();
      let projection;
      try {
        projection = typeof props.useProjection === 'function' ? props.useProjection('tokenUsage') : undefined;
      } catch {
        projection = undefined;
      }
      const usage = projection && projection.value && typeof projection.value === 'object' ? projection.value : projection;
      const [open, setOpen] = React.useState(false);
      const [position, setPosition] = React.useState(null);
      const rootRef = React.useRef(null);
      const now = useNow(cfg.cost.showPeriod || open);

      const result = React.useMemo(
        () => calculateCost(usage, cfg.cost, now),
        [usage, cfg.cost, now],
      );

      const close = React.useCallback(() => setOpen(false), []);

      const toggle = React.useCallback(() => {
        if (!open && rootRef.current) {
          const anchor = rootRef.current.getBoundingClientRect();
          setPosition({
            top: Math.min(window.innerHeight - 80, anchor.bottom + 8),
            right: Math.max(10, window.innerWidth - anchor.right),
          });
        }
        setOpen((value) => !value);
      }, [open]);

      React.useEffect(() => {
        if (!open) return undefined;
        const onDown = (event) => {
          if (rootRef.current && !rootRef.current.contains(event.target)) setOpen(false);
        };
        const onScroll = () => setOpen(false);
        const onResize = () => setOpen(false);
        document.addEventListener('mousedown', onDown);
        window.addEventListener('scroll', onScroll, true);
        window.addEventListener('resize', onResize);
        return () => {
          document.removeEventListener('mousedown', onDown);
          window.removeEventListener('scroll', onScroll, true);
          window.removeEventListener('resize', onResize);
        };
      }, [open]);

      if (!cfg.cost.show && !cfg.cost.showPeriod) return null;

      const empty = result.tokens === 0;
      const slotName = tr(result.peak ? 'period.peak' : 'period.off', result.periodName);
      const slotFull = tr(result.peak ? 'period.peakFull' : 'period.offFull', result.periodFullName);
      const title = `${slotFull} · ${fmtCny(result.cny)}`;
      return h('span', { className: 'hj-cost-root', ref: rootRef },
        cfg.cost.showPeriod
          ? h('button', {
            type: 'button',
            className: `hj-pill hj-period-pill ${result.peak ? 'peak' : 'off'}`,
            title: `${slotFull}，${tr('cost.next', '距离换段')} ${fmtLeft(result.next.ms)}`,
            onClick: toggle,
          }, h('i', { className: 'hj-dot' }), slotName)
          : null,
        h(HeaderMusic),
        cfg.cost.show
          ? h('button', {
            type: 'button',
            className: 'hj-pill',
            'data-empty': empty ? '1' : '0',
            title,
            onClick: toggle,
          }, fmtCny(result.cny))
          : null,
        open ? h('div', {
          className: 'hj-popover',
          style: position ? { top: position.top, right: position.right } : { top: 56, right: 16 },
          role: 'dialog',
          'aria-label': tr('cost.title', '会话花费'),
        },
          h('div', { className: 'hj-pop-head' },
            h('b', null, tr('cost.title', '会话花费')),
            h('button', { type: 'button', className: 'hj-pop-close', onClick: close, 'aria-label': '关闭' }, '×')),
          h('div', { className: 'hj-pop-row' },
            h('span', null, tr('cost.periodNow', '当前时段')),
            h('span', null,
              h('span', { className: `hj-pop-badge ${result.peak ? 'peak' : 'off'}` }, slotFull),
              cfg.cost.period === 'auto' ? null : h('span', { className: 'hj-pop-note', style: { marginLeft: 6 } }, '手动')),
          ),
          h('div', { className: 'hj-pop-row' },
            h('span', null, tr('cost.next', '距离换段')),
            h('span', null, `${fmtLeft(result.next.ms)} · ${result.next.label}`)),
          h('div', { className: 'hj-pop-hr' }),
          result.items.map((item) => h('div', { className: 'hj-pop-row', key: item.key },
            h('span', null, tr(item.nameKey, item.nameKey)),
            h('span', null, `${fmtTokens(item.tokens)} × ¥${item.price}/M`))),
          h('div', { className: 'hj-pop-hr' }),
          h('div', { className: 'hj-pop-row' }, h('span', null, tr('cost.totalTokens', '总 token')), h('span', null, result.tokens.toLocaleString('zh-CN'))),
          h('div', { className: 'hj-pop-row' }, h('span', null, tr('cost.hitRate', '缓存命中率')), h('span', null, `${(result.cacheHit * 100).toFixed(1)}%`)),
          h('div', { className: 'hj-pop-total' },
            h('span', null, tr('cost.total', '合计')),
            h('b', null, fmtCny(result.cny))),
          h('div', { className: 'hj-pop-note' }, empty ? tr('cost.empty', '本次会话还没有用量') : tr('cost.note', '按官方人民币价计算，无汇率换算')),
          h('div', { className: 'hj-pop-note' }, `${PRICES[result.model]?.label || result.model} · ${cfg.cost.period === 'auto' ? tr('cost.auto', '自动（北京时间）') : (result.peak ? tr('cost.peak', '手动锁定：高峰') : tr('cost.off', '手动锁定：空闲'))}`),
        ) : null,
      );
    }

    /* ============================== 设置页 ============================== */

    function Section(props) {
      return h('section', { className: 'hj-section' },
        h('div', { className: 'hj-section-head' },
          h('div', null, h('h3', null, props.title), props.desc ? h('p', null, props.desc) : null),
          props.extra || null),
        props.children);
    }

    function Toggle(props) {
      return h('button', {
        type: 'button',
        className: 'hj-switch',
        role: 'switch',
        'aria-checked': props.checked ? 'true' : 'false',
        'aria-label': props.label,
        onClick: () => props.onChange(!props.checked),
      }, h('span'));
    }

    function Field(props) {
      return h('div', { className: 'hj-field' },
        h('div', { className: 'hj-field-copy' },
          h('b', null, props.title),
          props.desc ? h('small', null, props.desc) : null),
        h('div', { className: 'hj-row' }, props.children));
    }

    async function uploadMedia(file) {
      if (file.size > MAX_MEDIA_BYTES) throw new Error(tr('splash.tooLarge', '文件超过 1 GB，请换一个小一点的'));
      const type = file.type || (() => {
        const name = file.name.toLowerCase();
        if (/\.(mp4|m4v)$/.test(name)) return 'video/mp4';
        if (/\.webm$/.test(name)) return 'video/webm';
        if (/\.mov$/.test(name)) return 'video/quicktime';
        if (/\.mp3$/.test(name)) return 'audio/mpeg';
        if (/\.wav$/.test(name)) return 'audio/wav';
        if (/\.ogg$/.test(name)) return 'audio/ogg';
        if (/\.m4a$/.test(name)) return 'audio/mp4';
        if (/\.aac$/.test(name)) return 'audio/aac';
        if (/\.flac$/.test(name)) return 'audio/flac';
        if (/\.png$/.test(name)) return 'image/png';
        if (/\.webp$/.test(name)) return 'image/webp';
        if (/\.gif$/.test(name)) return 'image/gif';
        if (/\.avif$/.test(name)) return 'image/avif';
        return 'image/jpeg';
      })();
      let response;
      try {
        response = await fetch(`${apiBase}/media`, {
          method: 'POST',
          headers: {
            'content-type': type,
            'x-file-name': encodeURIComponent(file.name || 'splash'),
          },
          body: file,
        });
      } catch (error) {
        throw new Error(`${tr('splash.uploadFailed', '上传失败')}：${String((error && error.message) || error)}`);
      }
      let data = null;
      try {
        data = await response.json();
      } catch {
        data = null;
      }
      if (!response.ok || !data || !data.ok || !data.media) {
        throw new Error((data && data.error) || `${tr('splash.uploadFailed', '上传失败')}（HTTP ${response.status}）`);
      }
      return data.media;
    }

    function SettingsPage() {
      const cfg = useConfig();
      useLocaleTick();
      const [status, setStatus] = React.useState({ state: '', text: '' });
      const [uploading, setUploading] = React.useState(false);
      const [urlDraft, setUrlDraft] = React.useState('');
      const [urlKind, setUrlKind] = React.useState('image');
      const [durationDraft, setDurationDraft] = React.useState(cfg.splash.duration);
      const [musicUploading, setMusicUploading] = React.useState(false);
      const [musicUrlDraft, setMusicUrlDraft] = React.useState('');
      const [musicVolumeDraft, setMusicVolumeDraft] = React.useState(Math.round(cfg.splash.music.volume * 100));
      const [lyricsTrackId, setLyricsTrackId] = React.useState(cfg.splash.music.currentId || '');
      const [lyricsDraft, setLyricsDraft] = React.useState('');
      const fileRef = React.useRef(null);
      const musicFileRef = React.useRef(null);
      const lyricsFileRef = React.useRef(null);
      const lyricsDirtyRef = React.useRef(false);
      const musicPreviewRef = React.useRef(null);
      const statusSeq = React.useRef(0);

      React.useEffect(() => {
        setDurationDraft(cfg.splash.duration);
      }, [cfg.splash.duration]);

      React.useEffect(() => {
        setMusicVolumeDraft(Math.round(cfg.splash.music.volume * 100));
      }, [cfg.splash.music.volume]);

      React.useEffect(() => {
        const list = cfg.splash.music.tracks || [];
        let id = lyricsTrackId;
        if (!list.some((track) => track.id === id)) id = cfg.splash.music.currentId || (list[0] ? list[0].id : '');
        if (id !== lyricsTrackId) setLyricsTrackId(id);
        if (!lyricsDirtyRef.current) {
          const track = list.find((item) => item.id === id);
          setLyricsDraft(track ? track.lyrics || '' : '');
        }
      }, [cfg.splash.music.currentId, cfg.splash.music.tracks, lyricsTrackId]);

      const commitMusicVolume = (value) => {
        const volume = Math.min(1, Math.max(0, Number(value) / 100));
        update((current) => ({ ...current, splash: { ...current.splash, music: { ...current.splash.music, volume } } }), tr('save.saved', '已保存到本机'));
      };

      const commitDuration = (value) => {
        const duration = Number(value);
        update((current) => ({ ...current, splash: { ...current.splash, duration } }), tr('save.saved', '已保存到本机'));
      };

      const flash = (state, text) => {
        setStatus({ state, text });
      };

      const update = async (mutator, successText) => {
        const next = mutator(config);
        const id = ++statusSeq.current;
        flash('saving', tr('save.saving', '正在保存…'));
        const result = await commitConfig(next, successText);
        if (id !== statusSeq.current) return;
        if (result.ok) {
          flash('saved', successText || tr('save.saved', '已保存到本机'));
        } else {
          flash('error', `${tr('save.failed', '保存失败')}：${result.error}`);
        }
      };

      const pickFile = () => {
        if (fileRef.current) fileRef.current.click();
      };

      const onFile = async (event) => {
        const file = event.target.files && event.target.files[0];
        event.target.value = '';
        if (!file) return;
        setUploading(true);
        flash('saving', tr('splash.uploading', '正在上传…'));
        try {
          const media = await uploadMedia(file);
          if (media.kind !== 'image' && media.kind !== 'video') throw new Error('启动动画只支持图片或视频');
          await update((current) => ({
            ...current,
            splash: { ...current.splash, enabled: true, media },
          }), tr('splash.saved', '启动素材已保存'));
        } catch (error) {
          flash('error', String((error && error.message) || error));
        } finally {
          setUploading(false);
        }
      };

      const useUrl = async () => {
        const raw = urlDraft.trim();
        if (!/^https?:\/\//i.test(raw) && !raw.startsWith(`${PREFIX}/media/`) && !raw.startsWith(`${LEGACY_PREFIX}/media/`)) {
          flash('error', tr('splash.invalidUrl', '请输入 http(s) 地址，或使用上传按钮'));
          return;
        }
        if (raw.length > 1200) {
          flash('error', tr('splash.invalidUrl', '请输入 http(s) 地址，或使用上传按钮'));
          return;
        }
        await update((current) => ({
          ...current,
          splash: {
            ...current.splash,
            enabled: true,
            media: {
              kind: urlKind,
              url: raw,
              name: raw.split('/').pop() || (urlKind === 'video' ? '网络视频' : '网络图片'),
              mime: '',
            },
          },
        }), tr('splash.saved', '启动素材已保存'));
      };

      const pickMusicFile = () => {
        if (musicFileRef.current) musicFileRef.current.click();
      };

      const startSplashPreview = () => {
        try {
          if (musicPreviewRef.current) musicPreviewRef.current.pause();
        } catch {
          // 忽略。
        }
        requestSplash(true);
      };

      const onMusicFile = async (event) => {
        const files = event.target.files ? Array.from(event.target.files) : [];
        event.target.value = '';
        if (files.length === 0) return;
        setMusicUploading(true);
        flash('saving', tr('splash.uploading', '正在上传…'));
        try {
          const added = [];
          for (const file of files) {
            const media = await uploadMedia(file);
            if (media.kind !== 'audio') throw new Error(tr('music.uploadFailed', '音乐上传失败'));
            added.push({
              id: makeTrackId(),
              name: media.name || file.name || '音乐',
              url: media.url,
              mime: media.mime || '',
              lyrics: '',
            });
          }
          await update((current) => {
            const music = current.splash.music;
            const tracks = [...music.tracks, ...added];
            return {
              ...current,
              splash: {
                ...current.splash,
                music: { ...music, enabled: true, tracks, currentId: added[added.length - 1].id },
              },
            };
          }, tr('music.saved', '音乐已保存'));
        } catch (error) {
          flash('error', String((error && error.message) || error));
        } finally {
          setMusicUploading(false);
        }
      };

      const useMusicUrl = async () => {
        const raw = musicUrlDraft.trim();
        if (!/^https?:\/\//i.test(raw) && !raw.startsWith(`${PREFIX}/media/`) && !raw.startsWith(`${LEGACY_PREFIX}/media/`)) {
          flash('error', tr('music.invalidUrl', '请输入 http(s) 音频地址'));
          return;
        }
        if (raw.length > 1200) {
          flash('error', tr('music.invalidUrl', '请输入 http(s) 音频地址'));
          return;
        }
        const track = {
          id: makeTrackId(),
          name: raw.split('/').pop() || '网络音乐',
          url: raw,
          mime: '',
          lyrics: '',
        };
        await update((current) => {
          const music = current.splash.music;
          const tracks = [...music.tracks, track];
          return {
            ...current,
            splash: {
              ...current.splash,
              music: { ...music, enabled: true, tracks, currentId: track.id },
            },
          };
        }, tr('music.saved', '音乐已保存'));
        setMusicUrlDraft('');
      };

      const chooseTrack = async (id) => {
        await update((current) => ({
          ...current,
          splash: {
            ...current.splash,
            music: { ...current.splash.music, currentId: id },
          },
        }), tr('save.saved', '已保存到本机'));
      };

      const removeTrack = async (id) => {
        await update((current) => {
          const music = current.splash.music;
          const tracks = music.tracks.filter((track) => track.id !== id);
          const currentId = music.currentId === id ? (tracks[0] ? tracks[0].id : '') : music.currentId;
          return {
            ...current,
            splash: {
              ...current.splash,
              music: { ...music, tracks, currentId },
            },
          };
        }, tr('save.saved', '已保存到本机'));
      };

      const selectLyricsTrack = (id) => {
        lyricsDirtyRef.current = false;
        setLyricsTrackId(id);
        const track = (config.splash.music.tracks || []).find((item) => item.id === id);
        setLyricsDraft(track ? track.lyrics || '' : '');
      };

      const saveLyrics = async () => {
        if (!lyricsTrackId) return;
        lyricsDirtyRef.current = false;
        await update((current) => ({
          ...current,
          splash: {
            ...current.splash,
            music: {
              ...current.splash.music,
              tracks: current.splash.music.tracks.map((track) => (
                track.id === lyricsTrackId ? { ...track, lyrics: lyricsDraft } : track
              )),
            },
          },
        }), tr('music.lyrics.saved', '歌词已保存'));
      };

      const pickLyricsFile = () => {
        if (lyricsFileRef.current) lyricsFileRef.current.click();
      };

      const onLyricsFile = async (event) => {
        const file = event.target.files && event.target.files[0];
        event.target.value = '';
        if (!file) return;
        try {
          const text = await file.text();
          lyricsDirtyRef.current = true;
          setLyricsDraft(text.slice(0, 100000));
          flash('saved', '已导入，点「保存歌词」生效');
        } catch (error) {
          flash('error', String((error && error.message) || error));
        }
      };

      const reset = async () => {
        if (!window.confirm(tr('cost.resetConfirm', '确定恢复画境的默认画风、字体、启动动画和花费设置吗？'))) return;
        await update(() => clone(DEFAULT_CONFIG), tr('save.saved', '已保存到本机'));
      };

      const theme = THEMES.find((item) => item.id === cfg.theme) || THEMES[0];
      const font = FONTS.find((item) => item.id === cfg.font) || FONTS[0];
      const price = pricing(cfg.cost.model, isPeak(Date.now(), cfg.cost.period));
      const previewPeriod = periodFullName(isPeak(Date.now(), cfg.cost.period));

      return h('div', { className: 'hj-page' },
        h('div', { className: 'hj-hero' },
          h('div', { className: 'hj-badge' }, h('i', { className: 'hj-dot' }), 'HUAJING · 画境'),
          h('h2', null, tr('settings.title', '画境')),
          h('p', null, tr('settings.subtitle', '整体画风、字体、启动动画、花费与时段。配置保存在本机，删除对话或重启 DSH 后仍然有效。')),
          h('div', { className: 'hj-row', style: { marginTop: 12 } },
            h('span', { className: 'hj-save', 'data-state': status.state, 'aria-live': 'polite' }, status.text || ''),
          ),
        ),

        h(Section, { title: tr('section.theme', '整体画风'), desc: tr('section.theme.desc', '只叠加色彩 token，不改变你原来的明暗模式。') },
          h('div', { className: 'hj-theme-grid' },
            THEMES.map((item) => h('button', {
              key: item.id,
              type: 'button',
              className: 'hj-theme-card',
              'data-on': cfg.theme === item.id ? '1' : '0',
              'aria-pressed': cfg.theme === item.id,
              onClick: () => update((current) => ({ ...current, theme: item.id }), tr('save.saved', '已保存到本机')),
            },
              h('span', { className: 'hj-swatches' },
                themeSwatches(item).map((color, index) => h('i', { key: index, style: { background: color } }))),
              h('b', null, tr(`theme.${item.id}`, item.label)),
              h('small', null, tr(`theme.${item.id}.desc`, item.desc))))),
          h('div', { className: 'hj-note', style: { marginTop: 10 } },
            `${tr('cost.current', '当前')}：${theme.label} · ${theme.desc}`),
        ),

        h(Section, { title: tr('section.font', '整体字体'), desc: tr('section.font.desc', '正文、标题、按钮与输入框一起换；代码块保持等宽。') },
          h(Field, { title: tr('section.font', '整体字体'), desc: tr('font.sample', '永远相信美好的事情即将发生。The quick brown fox jumps over the lazy dog.') },
            h('select', {
              className: 'hj-select',
              value: cfg.font,
              'aria-label': tr('section.font', '整体字体'),
              onChange: (event) => update((current) => ({ ...current, font: event.target.value }), tr('save.saved', '已保存到本机')),
            }, FONTS.map((item) => h('option', { key: item.id, value: item.id }, item.label)))),
          h('div', {
            className: 'hj-sample',
            style: { fontFamily: font.stack || undefined },
          }, tr('font.sample', '永远相信美好的事情即将发生。The quick brown fox jumps over the lazy dog.')),
        ),

        h(Section, { title: tr('section.splash', '启动动画'), desc: tr('section.splash.desc', '每次打开或刷新页面时播放一次，支持图片与视频。') },
          h(Field, { title: tr('splash.enable', '启用启动动画'), desc: tr('splash.enable.desc', '没有素材时播放画境默认动画。') },
            h(Toggle, {
              checked: cfg.splash.enabled,
              label: tr('splash.enable', '启用启动动画'),
              onChange: (value) => update((current) => ({ ...current, splash: { ...current.splash, enabled: value } }), tr('save.saved', '已保存到本机')),
            })),
          cfg.splash.media
            ? h('div', { className: 'hj-media-preview' },
              h('div', { className: 'hj-media-thumb' },
                cfg.splash.media.kind === 'video'
                  ? h('video', { src: cfg.splash.media.url, muted: true, playsInline: true, preload: 'metadata' })
                  : h('img', { src: cfg.splash.media.url, alt: cfg.splash.media.name })),
              h('div', { className: 'hj-media-meta' },
                h('b', null, cfg.splash.media.name || tr('splash.current', '当前素材')),
                h('small', null, `${cfg.splash.media.kind === 'video' ? '视频' : '图片'} · ${cfg.splash.media.mime || '媒体'}`),
                h('div', { className: 'hj-row', style: { marginTop: 6 } },
                  h('button', { type: 'button', className: 'hj-btn', onClick: startSplashPreview }, tr('splash.preview', '预览')),
                  h('button', {
                    type: 'button',
                    className: 'hj-btn danger',
                    onClick: () => update((current) => ({ ...current, splash: { ...current.splash, media: null } }), tr('save.saved', '已保存到本机')),
                  }, tr('splash.remove', '清除素材')))))
            : h('div', { className: 'hj-note', style: { marginBottom: 9 } }, tr('splash.none', '还没有素材')),
          h('div', { className: 'hj-row', style: { marginBottom: 9 } },
            h('input', {
              ref: fileRef,
              type: 'file',
              accept: 'image/png,image/jpeg,image/webp,image/gif,image/avif,video/mp4,video/webm,video/quicktime,video/x-m4v',
              style: { display: 'none' },
              onChange: onFile,
            }),
            h('button', { type: 'button', className: 'hj-btn primary', disabled: uploading, onClick: pickFile },
              uploading ? tr('splash.uploading', '正在上传…') : tr('splash.upload', '上传图片/视频')),
            h('button', { type: 'button', className: 'hj-btn', onClick: startSplashPreview }, tr('splash.preview', '预览')),
          ),
          h('div', { className: 'hj-note', style: { marginBottom: 10 } }, tr('splash.uploadHint', '支持 PNG / JPG / WebP / GIF / AVIF 与 MP4 / WebM / MOV / M4V，最大 1 GB。')),
          h('div', { className: 'hj-row', style: { marginBottom: 10 } },
            h('select', { className: 'hj-select', value: urlKind, onChange: (event) => setUrlKind(event.target.value), 'aria-label': tr('splash.urlKind', '素材类型') },
              h('option', { value: 'image' }, '图片'),
              h('option', { value: 'video' }, '视频')),
            h('input', {
              className: 'hj-input',
              value: urlDraft,
              placeholder: tr('splash.url.placeholder', '粘贴 https:// 开头的图片或视频地址'),
              onChange: (event) => setUrlDraft(event.target.value),
            }),
            h('button', { type: 'button', className: 'hj-btn', onClick: useUrl }, tr('splash.useUrl', '使用地址')),
          ),
          h(Field, { title: tr('splash.duration', '图片停留时长'), desc: '0.8 – 12 秒' },
            h('input', {
              className: 'hj-range',
              type: 'range',
              min: 800,
              max: 12000,
              step: 200,
              value: durationDraft,
              'aria-label': tr('splash.duration', '图片停留时长'),
              onChange: (event) => setDurationDraft(Number(event.target.value)),
              onPointerUp: (event) => commitDuration(event.currentTarget.value),
              onKeyUp: (event) => commitDuration(event.currentTarget.value),
            }),
            h('span', { className: 'hj-note' }, `${(durationDraft / 1000).toFixed(1)}s`)),
          h(Field, { title: tr('splash.fit', '画面填充'), desc: tr('splash.fit.windowDesc', '仅桌面窗口可用：按视频/图片比例临时调整 DSH 窗口，动画结束后恢复原窗口大小。') },
            h('select', {
              className: 'hj-select',
              value: cfg.splash.fit,
              onChange: (event) => update((current) => ({ ...current, splash: { ...current.splash, fit: event.target.value } }), tr('save.saved', '已保存到本机')),
            },
              h('option', { value: 'cover' }, tr('splash.fit.cover', '铺满屏幕')),
              h('option', { value: 'contain' }, tr('splash.fit.contain', '完整显示')),
              h('option', { value: 'auto' }, tr('splash.fit.auto', '自适应比例（视频/图片）')),
              h('option', { value: 'window' }, tr('splash.fit.window', 'DSH窗口自适应（画面铺满）')))),
        ),

        h(Section, { title: tr('section.music', '音乐播放'), desc: tr('section.music.desc', '给启动动画或日常使用配音乐；可上传音频或填网络地址。') },
          h(Field, { title: tr('music.mode', '播放方式'), desc: tr('music.mode.desc', '常驻模式会在右下角显示迷你播放器，使用 DSH 时持续播放。') },
            h('select', {
              className: 'hj-select',
              value: cfg.splash.music.mode,
              onChange: (event) => update((current) => ({ ...current, splash: { ...current.splash, music: { ...current.splash.music, mode: event.target.value } } }), tr('save.saved', '已保存到本机')),
            },
              h('option', { value: 'splash' }, tr('music.mode.splash', '仅启动动画')),
              h('option', { value: 'always' }, tr('music.mode.always', '使用 DSH 时常驻播放')))),
          h(Field, { title: tr('music.enable', '启用音乐'), desc: tr('music.enable.desc', '音乐总开关；播放方式决定只在启动动画还是使用 DSH 时常驻。') },
            h(Toggle, {
              checked: cfg.splash.music.enabled,
              label: tr('music.enable', '启用启动音乐'),
              onChange: (value) => update((current) => ({ ...current, splash: { ...current.splash, music: { ...current.splash.music, enabled: value } } }), tr('save.saved', '已保存到本机')),
            })),
          cfg.splash.music.media
            ? h('div', { className: 'hj-media-preview', style: { flexDirection: 'column', alignItems: 'stretch' } },
              h('div', { className: 'hj-media-meta' },
                h('b', null, cfg.splash.music.media.name || tr('music.current', '当前音乐')),
                h('small', null, `音频 · ${cfg.splash.music.media.mime || 'audio'}`)),
              h('audio', {
                ref: musicPreviewRef,
                controls: true,
                src: cfg.splash.music.media.url,
                preload: 'metadata',
                style: { width: '100%', marginTop: 8 },
              }),
              h('div', { className: 'hj-row', style: { marginTop: 6 } },
                h('button', {
                  type: 'button',
                  className: 'hj-btn danger',
                  onClick: () => removeTrack(cfg.splash.music.currentId),
                }, tr('music.track.remove', '移除'))))
            : h('div', { className: 'hj-note', style: { marginBottom: 9 } }, tr('music.none', '还没有音乐')),
          h('div', { className: 'hj-row', style: { marginBottom: 9 } },
            h('input', {
              ref: musicFileRef,
              type: 'file',
              multiple: true,
              accept: 'audio/mpeg,audio/wav,audio/ogg,audio/mp4,audio/aac,audio/flac,audio/x-m4a',
              style: { display: 'none' },
              onChange: onMusicFile,
            }),
            h('button', { type: 'button', className: 'hj-btn primary', disabled: musicUploading, onClick: pickMusicFile },
              musicUploading ? tr('splash.uploading', '正在上传…') : tr('music.upload', '上传音频'))),
          h('div', { className: 'hj-note', style: { marginBottom: 10 } }, tr('music.uploadHint', '支持 MP3 / WAV / OGG / M4A / AAC / FLAC，最大 1 GB。')),
          h('div', { className: 'hj-row', style: { marginBottom: 10 } },
            h('input', {
              className: 'hj-input',
              value: musicUrlDraft,
              placeholder: tr('music.url.placeholder', '粘贴 https:// 开头的音频地址'),
              onChange: (event) => setMusicUrlDraft(event.target.value),
            }),
            h('button', { type: 'button', className: 'hj-btn', onClick: useMusicUrl }, tr('music.useUrl', '使用地址'))),
          h('div', { className: 'hj-music-manage' },
            h('div', { className: 'hj-music-manage-title' }, tr('music.playlist', '播放列表')),
            cfg.splash.music.tracks.length === 0
              ? h('div', { className: 'hj-note' }, tr('music.playlist.empty', '还没有歌曲'))
              : cfg.splash.music.tracks.map((track) => h('div', {
                key: track.id,
                className: `hj-music-manage-item${track.id === cfg.splash.music.currentId ? ' active' : ''}`,
              },
                h('button', {
                  type: 'button',
                  className: 'hj-music-manage-name',
                  onClick: () => chooseTrack(track.id),
                  title: tr('music.dock.play', '播放'),
                }, track.name || 'audio'),
                h('span', { className: 'hj-music-manage-tags' },
                  track.id === cfg.splash.music.currentId ? tr('music.current', '当前音乐') : ''),
                h('button', {
                  type: 'button',
                  className: 'hj-btn',
                  onClick: () => selectLyricsTrack(track.id),
                }, tr('music.track.lyrics', '歌词')),
                h('button', {
                  type: 'button',
                  className: 'hj-btn danger',
                  onClick: () => removeTrack(track.id),
                }, tr('music.track.remove', '移除'))))),
          cfg.splash.music.tracks.length > 0
            ? h('div', { className: 'hj-lyrics-editor' },
              h('div', { className: 'hj-row', style: { marginBottom: 7 } },
                h('select', {
                  className: 'hj-select',
                  value: lyricsTrackId,
                  'aria-label': tr('music.lyrics', '歌词注入'),
                  onChange: (event) => selectLyricsTrack(event.target.value),
                }, cfg.splash.music.tracks.map((track) => h('option', { key: track.id, value: track.id }, track.name || 'audio'))),
                h('button', { type: 'button', className: 'hj-btn', onClick: pickLyricsFile }, tr('music.lyrics.import', '导入 LRC')),
                h('input', {
                  ref: lyricsFileRef,
                  type: 'file',
                  accept: '.lrc,text/plain',
                  style: { display: 'none' },
                  onChange: onLyricsFile,
                })),
              h('textarea', {
                className: 'hj-lyrics-input',
                value: lyricsDraft,
                placeholder: tr('music.lyrics.placeholder', '[00:12.00]第一句歌词\n[00:16.50]第二句歌词'),
                onChange: (event) => {
                  lyricsDirtyRef.current = true;
                  setLyricsDraft(event.target.value);
                },
              }),
              h('div', { className: 'hj-row', style: { marginTop: 7, justifyContent: 'space-between' } },
                h('span', { className: 'hj-note' }, tr('music.lyrics.desc', '粘贴 LRC 歌词或导入 .lrc 文件；播放器会按时间同步高亮。')),
                h('button', { type: 'button', className: 'hj-btn primary', onClick: saveLyrics }, tr('music.lyrics.save', '保存歌词'))))
            : null,
          h(Field, { title: tr('music.volume', '音量'), desc: '0 – 100%' },
            h('input', {
              className: 'hj-range',
              type: 'range',
              min: 0,
              max: 100,
              step: 5,
              value: musicVolumeDraft,
              'aria-label': tr('music.volume', '音量'),
              onChange: (event) => setMusicVolumeDraft(Number(event.target.value)),
              onPointerUp: (event) => commitMusicVolume(event.currentTarget.value),
              onKeyUp: (event) => commitMusicVolume(event.currentTarget.value),
            }),
            h('span', { className: 'hj-note' }, `${musicVolumeDraft}%`)),
          h(Field, { title: tr('music.loop', '循环播放'), desc: tr('music.loop.desc', '开启后循环到动画结束；关闭则只播一遍。') },
            h(Toggle, {
              checked: cfg.splash.music.loop,
              label: tr('music.loop', '循环播放'),
              onChange: (value) => update((current) => ({ ...current, splash: { ...current.splash, music: { ...current.splash.music, loop: value } } }), tr('save.saved', '已保存到本机')),
            })),
          h('div', { className: 'hj-note' }, tr('music.autoplayHint', '浏览器可能拦截自动播放；启动画面的「播放音乐」按钮可手动开始。')),
        ),

        h(Section, { title: tr('section.cost', '花费与时段'), desc: tr('section.cost.desc', '在会话标题栏显示本会话花费，以及梁文峰/梁文谷时段。') },
          h(Field, { title: tr('cost.show', '显示会话花费'), desc: tr('cost.show.desc', '在会话标题栏显示本会话累计人民币金额。') },
            h(Toggle, {
              checked: cfg.cost.show,
              label: tr('cost.show', '显示会话花费'),
              onChange: (value) => update((current) => ({ ...current, cost: { ...current.cost, show: value } }), tr('save.saved', '已保存到本机')),
            })),
          h(Field, { title: tr('cost.period.show', '显示时段'), desc: tr('cost.period.show.desc', '显示梁文峰（高峰）/ 梁文谷（空闲）与下次切换倒计时。') },
            h(Toggle, {
              checked: cfg.cost.showPeriod,
              label: tr('cost.period.show', '显示时段'),
              onChange: (value) => update((current) => ({ ...current, cost: { ...current.cost, showPeriod: value } }), tr('save.saved', '已保存到本机')),
            })),
          h(Field, { title: tr('cost.model', '计价模型') },
            h('select', {
              className: 'hj-select',
              value: cfg.cost.model,
              onChange: (event) => update((current) => ({ ...current, cost: { ...current.cost, model: event.target.value } }), tr('save.saved', '已保存到本机')),
            }, Object.keys(PRICES).map((id) => h('option', { key: id, value: id }, PRICES[id].label)))),
          h(Field, { title: tr('cost.period', '时段判断') },
            h('select', {
              className: 'hj-select',
              value: cfg.cost.period,
              onChange: (event) => update((current) => ({ ...current, cost: { ...current.cost, period: event.target.value } }), tr('save.saved', '已保存到本机')),
            },
              h('option', { value: 'auto' }, tr('cost.auto', '自动（北京时间）')),
              h('option', { value: 'peak' }, tr('cost.peak', '手动锁定：高峰')),
              h('option', { value: 'off' }, tr('cost.off', '手动锁定：空闲')))),
          h(Field, { title: tr('cost.writeAsMiss', '缓存写入按未命中价计') },
            h(Toggle, {
              checked: cfg.cost.writeAsMiss,
              label: tr('cost.writeAsMiss', '缓存写入按未命中价计'),
              onChange: (value) => update((current) => ({ ...current, cost: { ...current.cost, writeAsMiss: value } }), tr('save.saved', '已保存到本机')),
            })),
          h('div', { className: 'hj-note' },
            `${tr('cost.current', '当前')}：${previewPeriod} · 命中 ¥${price.hit}/M · 未命中 ¥${price.miss}/M · 输出 ¥${price.out}/M`),
          h('div', { className: 'hj-note' }, tr('cost.priceNote', '单位：元 / 百万 token。高峰为周一至周五 09:00–12:00、14:00–18:00；其余时间与周末按空闲价。法定节假日无法离线判断，可手动锁定时段。')),
        ),

        h(Section, { title: tr('section.about', '关于'), desc: tr('section.about.desc', '配置与媒体都在 DSH 配置目录里，不属于任何一个对话。') },
          h('div', { className: 'hj-about-row' }, h('span', null, tr('about.version', '版本')), h('code', null, PLUGIN_VERSION)),
          h('div', { className: 'hj-about-row' }, h('span', null, tr('about.dataDir', '配置目录')), h('code', null, hostDataDir || 'DSH profile/data/huanjing')),
          h('div', { className: 'hj-note', style: { margin: '6px 0 10px' } }, tr('about.body', '画境把配置写在 DSH profile 的 data/huanjing 下，不写入任何会话日志。删除对话、切换会话、重启 DSH 后都会恢复上次的选择。')),
          h('div', { className: 'hj-row' }, h('button', { type: 'button', className: 'hj-btn danger', onClick: reset }, tr('cost.reset', '恢复默认配置')))),
      );
    }

    /* ============================== 挂载 ============================== */

    function SafeSettingsPage(props) {
      try {
        return SettingsPage(props);
      } catch (error) {
        try {
          console.warn('[画境] 设置页渲染失败：', error);
        } catch {
          // 忽略。
        }
        return h('div', { className: 'hj-note' }, '画境设置页加载失败，请刷新页面或查看控制台。');
      }
    }

    function SafeCostWidget(props) {
      try {
        return CostWidget(props);
      } catch (error) {
        try {
          console.warn('[画境] 花费挂件渲染失败：', error);
        } catch {
          // 忽略。
        }
        return null;
      }
    }

    function SafeMusicDock(props) {
      try {
        return MusicDock(props);
      } catch (error) {
        try {
          console.warn('[画境] 音乐播放器渲染失败：', error);
        } catch {
          // 忽略。
        }
        return null;
      }
    }

    function SafeSplashOverlay(props) {
      try {
        return SplashOverlay(props);
      } catch (error) {
        try {
          console.warn('[画境] 启动动画渲染失败：', error);
        } catch {
          // 忽略。
        }
        return null;
      }
    }

    function apply(ctx) {
      try {
        themeService = ctx.get ? ctx.get('theme') : ctx.theme;
        localeService = ctx.get ? ctx.get('locale') : null;

        if (localeService && typeof localeService.register === 'function') {
          const register = (id, dict, label) => {
            ctx.effect(() => {
              try {
                return localeService.register('huanjing', id, dict);
              } catch (error) {
                console.warn(`[画境] 注册${label}文案失败：`, error);
                return () => {};
              }
            }, `画境：${label}文案`);
          };
          register('zh', ZH, '中文');
          register('en', EN, '英文');
          try {
            translateFn = localeService.bind('huanjing');
          } catch {
            translateFn = null;
          }
        }

        ctx.effect(() => installStyles(), '画境：样式');
        ctx.effect(() => {
          applyVisuals();
          return () => {
            if (themeLayerDispose) {
              try {
                themeLayerDispose();
              } catch {
                // 忽略。
              }
              themeLayerDispose = null;
              appliedThemeId = null;
            }
          };
        }, '画境：画风与字体');

        if (!configTouched) void loadRemoteConfig();

        ctx.slots.inject('settings.section', () => ctx.slots.register({
          name: 'settings.section',
          id: 'huanjing',
          order: 8,
          label: () => tr('nav', '画境'),
        }, SafeSettingsPage));

        ctx.slots.inject('shell.overlay', () => ctx.slots.register({
          name: 'shell.overlay',
          id: 'huanjing-splash',
          order: -1000,
        }, SafeSplashOverlay));

        ctx.slots.inject('shell.overlay', () => ctx.slots.register({
          name: 'shell.overlay',
          id: 'huanjing-music',
          order: 100,
        }, SafeMusicDock));

        ctx.slots.inject('conversation.session.header.utilities', () => ctx.slots.register({
          name: 'conversation.session.header.utilities',
          id: 'huanjing-cost',
          order: -6,
        }, SafeCostWidget));

      } catch (error) {
        try {
          console.warn('[画境] 插件挂载失败：', error);
        } catch {
          // 忽略。
        }
      }
    }

    return {
      inject: ['slots', 'theme'],
      apply,
    };
  },
});
