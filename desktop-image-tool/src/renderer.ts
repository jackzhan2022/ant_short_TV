import type {
  Creation,
  Draft,
  Format,
  Quality,
  Reference,
  Result,
  Settings,
  StudioAPI,
} from './shared';

declare global {
  interface Window {
    studio: StudioAPI;
  }
}
const api = window.studio;
function get<T extends HTMLElement = HTMLElement>(id: string): T {
  const element = document.getElementById(id);
  if (!element) throw new Error(`Missing element: ${id}`);
  return element as T;
}
function unwrap<T>(result: Result<T>): T {
  if (!result.ok) throw new Error(result.error);
  return result.value;
}
function node<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  className = '',
  text?: string,
) {
  const element = document.createElement(tag);
  element.className = className;
  if (text !== undefined) element.textContent = text;
  return element;
}
function action(
  label: string,
  handler: () => void | Promise<void>,
  className = 'button',
) {
  const button = node('button', className, label);
  button.type = 'button';
  button.onclick = () => {
    void perform(handler);
  };
  return button;
}
async function perform(work: () => void | Promise<void>) {
  try {
    await work();
  } catch (error) {
    notify(error instanceof Error ? error.message : '操作失败，请重试。');
  }
}
let toastTimer: ReturnType<typeof setTimeout>;
function notify(message: string) {
  clearTimeout(toastTimer);
  get('toast').textContent = message;
  get('toast').hidden = false;
  toastTimer = setTimeout(() => {
    get('toast').hidden = true;
  }, 5000);
}
function showError(id: string, message: string) {
  get(id).textContent = message;
  get(id).hidden = !message;
}
let settings: Settings = {
  outputDirectory: '',
  hasKey: false,
  baseUrl: '',
  responseModel: 'gpt-6-astra',
};
const records = new Map<string, Creation>();
let references: Reference[] = [];
let size = '1024x1024';
let submitting = false;
let loaded = false;
let referenceReads = 0;
let selectedImage: { id: string; index: number } | undefined;
const examples: Record<string, string> = {
  portrait:
    '电影感人像写真，一位穿着米色针织衫的年轻女性站在窗边，午后阳光透过薄纱窗帘，背景简洁，皮肤质感自然，浅景深，暖色调，细腻写实。',
  product:
    '商业产品摄影，一只哑光白色陶瓷杯放在浅色石材台面上，旁边有一枝绿叶，柔和的侧光，干净的米白色背景，真实材质，留白构图，无文字。',
  landscape:
    '日落时分的静谧山谷，层叠的青绿色山峦，一轮暖金色的太阳，极简插画，柔和光线，画面干净，没有文字。',
};
function draft(): Draft {
  return {
    outputName: get<HTMLInputElement>('output-name').value,
    prompt: get<HTMLTextAreaElement>('prompt').value,
    model: get<HTMLInputElement>('model').value.trim(),
    size,
    quality: get<HTMLSelectElement>('quality').value as Quality,
    format: get<HTMLSelectElement>('format').value as Format,
    references: [...references],
  };
}
function saveDraft() {
  const { references: _references, ...value } = draft();
  try {
    localStorage.setItem('antv-studio-draft-v1', JSON.stringify(value));
  } catch {
    /* Drafts remain usable when local storage is full. */
  }
}
function updatePrompt() {
  get('prompt-count').textContent =
    `${get<HTMLTextAreaElement>('prompt').value.length} / 2000`;
  saveDraft();
}
function updateSize(value: string) {
  size = value;
  for (const button of document.querySelectorAll<HTMLButtonElement>(
    '[data-size]',
  ))
    button.setAttribute('aria-pressed', String(button.dataset.size === value));
  get('size-label').textContent =
    value === 'auto' ? '模型自动选择' : value.replace('x', ' × ');
}
function updateGenerate() {
  const running = [...records.values()].some(
    (record) => record.status === 'running',
  );
  get<HTMLButtonElement>('generate').disabled =
    !loaded || submitting || running || referenceReads > 0;
  get('generate-text').textContent = referenceReads
    ? '正在读取参考图…'
    : submitting
      ? '正在提交…'
      : running
        ? '图片生成中…'
        : settings.hasKey
          ? '✦ 生成图片'
          : '⚿ 配置 Key 后开始创作';
}
function renderSettings() {
  renderDirectory();
  get('key-status').textContent = settings.hasKey
    ? '我的 Key · 已配置'
    : '配置我的 Key';
  get('server-address').textContent =
    settings.baseUrl || 'http://89.58.47.162:8080';
  get<HTMLInputElement>('response-model').value = settings.responseModel;
  get<HTMLInputElement>('api-key').placeholder = settings.hasKey
    ? '已保存 · 留空保留原 Key，输入可替换'
    : '粘贴你的 API Key';
  get('clear-key').hidden = !settings.hasKey;
  updateGenerate();
}
function renderDirectory() {
  get<HTMLInputElement>('output-directory').value = settings.outputDirectory;
  get('save-path').textContent = `保存至：${settings.outputDirectory}`;
  get('save-path').title = settings.outputDirectory;
}
function openSettings() {
  showError('settings-error', '');
  get<HTMLDialogElement>('settings-dialog').showModal();
}
get('settings-open').onclick = openSettings;
get('save-path').onclick = openSettings;
get('choose-directory').onclick = () => {
  void perform(async () => {
    settings = unwrap(await api.chooseDirectory());
    renderDirectory();
  });
};
get('settings-close').onclick = () =>
  get<HTMLDialogElement>('settings-dialog').close();
get<HTMLDialogElement>('settings-dialog').addEventListener('close', () => {
  get<HTMLInputElement>('api-key').value = '';
  get<HTMLInputElement>('api-key').type = 'password';
  get('show-key').textContent = '显示';
  get('show-key').setAttribute('aria-pressed', 'false');
});
get('show-key').onclick = () => {
  const input = get<HTMLInputElement>('api-key');
  input.type = input.type === 'password' ? 'text' : 'password';
  get('show-key').textContent = input.type === 'text' ? '隐藏' : '显示';
  get('show-key').setAttribute('aria-pressed', String(input.type === 'text'));
};
get<HTMLFormElement>('settings-form').onsubmit = async (event) => {
  event.preventDefault();
  const button = get<HTMLButtonElement>('save-settings');
  if (button.disabled) return;
  button.disabled = true;
  try {
    const key = get<HTMLInputElement>('api-key').value.trim();
    settings = unwrap(
      await api.settings({
        key,
        responseModel: get<HTMLInputElement>('response-model').value.trim(),
      }),
    );
    renderSettings();
    get<HTMLDialogElement>('settings-dialog').close();
    notify(key ? 'Key 已在本机加密保存。' : '个人设置已保存。');
  } catch (error) {
    showError(
      'settings-error',
      error instanceof Error ? error.message : '配置保存失败。',
    );
  } finally {
    button.disabled = false;
  }
};
get('clear-key').onclick = () => {
  void perform(async () => {
    if (!confirm('移除本机保存的 Key？创作记录和图片会保留。')) return;
    settings = unwrap(
      await api.settings({
        responseModel: settings.responseModel,
        clearKey: true,
      }),
    );
    renderSettings();
    get<HTMLInputElement>('api-key').value = '';
    notify('已移除本机保存的 Key。');
  });
};
get('prompt').addEventListener('input', updatePrompt);
for (const id of ['model', 'quality', 'format'])
  get(id).addEventListener('change', saveDraft);
get('output-name').addEventListener('input', saveDraft);
get('example').onclick = () => {
  get<HTMLTextAreaElement>('prompt').value = examples.landscape;
  updatePrompt();
  get('prompt').focus();
};
for (const button of document.querySelectorAll<HTMLButtonElement>(
  '[data-example]',
))
  button.onclick = () => {
    get<HTMLTextAreaElement>('prompt').value =
      examples[button.dataset.example || 'landscape'];
    updatePrompt();
    get('prompt').focus();
  };
for (const button of document.querySelectorAll<HTMLButtonElement>(
  '[data-size]',
))
  button.onclick = () => {
    updateSize(button.dataset.size || 'auto');
    saveDraft();
  };

function renderReferences() {
  get('references').replaceChildren(
    ...references.map((ref, index) => {
      const tile = node('div', 'reference-tile');
      const image = node('img');
      image.src = ref.dataUrl;
      image.alt = ref.name;
      const remove = action(
        '×',
        () => {
          references.splice(index, 1);
          renderReferences();
        },
        'reference-remove',
      );
      remove.setAttribute('aria-label', `移除参考图 ${index + 1}：${ref.name}`);
      tile.append(image, remove, node('span', '', `参考 ${index + 1}`));
      return tile;
    }),
  );
  get('reference-count').textContent = `可选 · ${references.length} / 8`;
  get('mode-label').textContent = references.length ? '参考图编辑' : '文字生图';
}
function appendReferences(items: Reference[]): number {
  const unique = items.filter(
    (item, index) =>
      !references.some((ref) => ref.dataUrl === item.dataUrl) &&
      !items.slice(0, index).some((ref) => ref.dataUrl === item.dataUrl),
  );
  if (references.length + unique.length > 8)
    throw new Error('最多添加 8 张参考图，请先移除不需要的图片。');
  if (
    [...references, ...unique].reduce(
      (total, ref) => total + ref.dataUrl.split(',')[1].length * 0.75,
      0,
    ) >
    30 * 1024 * 1024
  )
    throw new Error('参考图总大小不能超过 30 MB。');
  references.push(...unique);
  renderReferences();
  return unique.length;
}
async function addFiles(files: File[]) {
  if (!files.length) return;
  referenceReads++;
  updateGenerate();
  try {
    if (files.length + references.length > 8)
      throw new Error('最多添加 8 张参考图。');
    const items = await Promise.all(
      files.map(async (file) => {
        if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type))
          throw new Error(`${file.name}：只支持 PNG、JPEG、WEBP。`);
        if (!file.size || file.size > 10 * 1024 * 1024)
          throw new Error(`${file.name}：每张图片需大于 0 且不超过 10 MB。`);
        const dataUrl = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = () => resolve(String(reader.result));
          reader.onerror = () => reject(new Error('图片读取失败。'));
          reader.readAsDataURL(file);
        });
        const image = new Image();
        image.src = dataUrl;
        await image.decode().catch(() => {
          throw new Error(`${file.name}：图片内容无法解码。`);
        });
        return { name: file.name || '粘贴的图片.png', dataUrl };
      }),
    );
    const added = appendReferences(items);
    notify(added ? `已添加 ${added} 张参考图。` : '这张图片已在参考图列表中。');
  } finally {
    referenceReads--;
    updateGenerate();
  }
}
get('choose-reference').onclick = () =>
  get<HTMLInputElement>('reference-files').click();
get<HTMLInputElement>('reference-files').onchange = (event) => {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  input.value = '';
  void perform(() => addFiles(files));
};
document.addEventListener('dragover', (event) => {
  event.preventDefault();
});
document.addEventListener('drop', (event) => {
  event.preventDefault();
});
get('drop-area').addEventListener('dragover', (event) => {
  event.preventDefault();
  get('drop-area').classList.add('dragging');
});
get('drop-area').addEventListener('dragleave', (event) => {
  if (!get('drop-area').contains(event.relatedTarget as Node))
    get('drop-area').classList.remove('dragging');
});
get('drop-area').addEventListener('drop', (event) => {
  event.preventDefault();
  get('drop-area').classList.remove('dragging');
  const files = Array.from(event.dataTransfer?.files || []);
  void perform(() => addFiles(files));
});
document.addEventListener('paste', (event) => {
  if (document.querySelector('dialog[open]')) return;
  const files = Array.from(event.clipboardData?.files || []);
  if (files.length) {
    event.preventDefault();
    void perform(() => addFiles(files));
  }
});

function mediaURL(id: string, index: number, thumb = false) {
  return `antv-media://image/${id}/${index}${thumb ? '?thumb=1' : ''}`;
}
async function addHistoryReference(id: string, index: number) {
  referenceReads++;
  updateGenerate();
  try {
    const ref = unwrap(await api.reference({ id, index }));
    const added = appendReferences([ref]);
    notify(
      added
        ? '已添加为参考，当前提示词和其他参考图已保留。'
        : '这张作品已在参考图列表中。',
    );
    get<HTMLDialogElement>('image-dialog').close();
    get('references').scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  } finally {
    referenceReads--;
    updateGenerate();
  }
}
async function reuse(id: string) {
  const value = unwrap(await api.reuse(id));
  if (
    get<HTMLTextAreaElement>('prompt').value.trim() &&
    !confirm('用这条记录的提示词、参数和参考图替换当前草稿？')
  )
    return;
  get<HTMLTextAreaElement>('prompt').value = value.prompt;
  get<HTMLInputElement>('model').value = value.model;
  get<HTMLSelectElement>('quality').value = value.quality;
  get<HTMLSelectElement>('format').value = value.format;
  get<HTMLInputElement>('output-name').value = value.outputName || '';
  updateSize(value.size);
  references = value.references;
  renderReferences();
  updatePrompt();
  get<HTMLDialogElement>('image-dialog').close();
  get('prompt').focus();
  notify('已回填提示词、参数和参考图，请检查后点击生成。');
}
async function saveImage(id: string, index: number) {
  if (unwrap(await api.save({ id, index }))) notify('原图已保存到所选位置。');
}
function openImage(id: string, index: number) {
  selectedImage = { id, index };
  get<HTMLImageElement>('full-image').src = mediaURL(id, index);
  get('image-prompt').textContent = records.get(id)?.prompt || '';
  get<HTMLDialogElement>('image-dialog').showModal();
}
get('image-close').onclick = () =>
  get<HTMLDialogElement>('image-dialog').close();
get('image-reference').onclick = () => {
  if (selectedImage)
    void perform(() =>
      addHistoryReference(selectedImage?.id || '', selectedImage?.index || 0),
    );
};
get('image-reuse').onclick = () => {
  if (selectedImage) void perform(() => reuse(selectedImage?.id || ''));
};
get('image-save').onclick = () => {
  if (selectedImage)
    void perform(() =>
      saveImage(selectedImage?.id || '', selectedImage?.index || 0),
    );
};
get('open-folder').onclick = () => {
  void perform(async () => {
    unwrap(await api.openFolder());
  });
};

const statusLabels: Record<Creation['status'], string> = {
  running: '生成中',
  succeeded: '已完成',
  failed: '生成失败',
  stopped: '已停止等待',
  interrupted: '上次生成中断',
};
const qualityLabels: Record<Quality, string> = {
  auto: '自动质量',
  low: '低质量',
  medium: '中等质量',
  high: '高质量',
};
function elapsed(record: Creation): string {
  const seconds = Math.max(
    0,
    Math.floor(
      ((record.finishedAt ? Date.parse(record.finishedAt) : Date.now()) -
        Date.parse(record.createdAt)) /
        1000,
    ),
  );
  return seconds < 60
    ? `${seconds} 秒`
    : `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`;
}
function recordCard(record: Creation) {
  const card = node('article', 'record');
  card.dataset.id = record.id;
  const header = node('div', 'record-header');
  const date = new Date(record.createdAt).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
  header.append(
    node('span', `status ${record.status}`, statusLabels[record.status]),
    node('span', '', `${date} · ${record.model}`),
  );
  card.append(header, node('p', 'record-prompt', record.prompt));
  if (record.status === 'running') {
    const pending = node('div', 'pending');
    const text = node('div');
    text.append(
      node('p', '', '正在描绘你的想象'),
      node('small', '', '等待服务商返回 · '),
    );
    const time = node('small', '', elapsed(record));
    time.dataset.elapsed = record.id;
    text.append(time);
    pending.append(
      node('span', 'spinner'),
      text,
      action(
        '停止等待',
        async () => {
          if (confirm('停止本机等待？服务商可能仍会生成并计费。'))
            unwrap(await api.stop());
        },
        'text-button',
      ),
    );
    card.append(pending);
  }
  if (record.error) card.append(node('p', 'record-error', record.error));
  if (record.saveError)
    card.append(node('p', 'record-error', record.saveError));
  if (record.imageCount) {
    const pictures = node('div', 'record-pictures');
    for (let index = 0; index < record.imageCount; index++) {
      const output = node('figure', 'output');
      const imageButton = action(
        '',
        () => openImage(record.id, index),
        'thumbnail-button',
      );
      imageButton.setAttribute('aria-label', `查看原图 ${index + 1}`);
      const image = node('img');
      image.src = mediaURL(record.id, index, true);
      image.alt = record.prompt;
      image.loading = 'lazy';
      imageButton.append(image);
      const buttons = node('div', 'output-actions');
      buttons.append(
        action('＋ 添加为参考', () => addHistoryReference(record.id, index)),
        action('另存原图', () => saveImage(record.id, index)),
      );
      output.append(imageButton);
      const savedPath = record.savedFiles?.[index];
      if (savedPath) {
        const filename = node(
          'figcaption',
          'saved-filename',
          `已保存 · ${savedPath.split(/[\\/]/).pop()}`,
        );
        filename.title = savedPath;
        output.append(filename);
      }
      output.append(buttons);
      pictures.append(output);
    }
    card.append(pictures);
  }
  const footer = node('div', 'record-footer');
  footer.append(
    node(
      'span',
      'record-meta',
      `${record.size === 'auto' ? '自动尺寸' : record.size.replace('x', ' × ')} · ${qualityLabels[record.quality]} · ${record.format.toUpperCase()}${record.referenceCount ? ` · ${record.referenceCount} 张参考图` : ''}${record.finishedAt ? ` · ${elapsed(record)}` : ''}`,
    ),
    action('复用参数 ↗', () => reuse(record.id), 'text-button'),
  );
  card.append(footer);
  return card;
}
function renderRecords() {
  get('records').replaceChildren(
    ...[...records.values()]
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map(recordCard),
  );
  get('record-count').textContent = String(records.size);
  get('empty').hidden = records.size > 0;
  updateGenerate();
}
function mergeRecord(record: Creation) {
  if (records.get(record.id)?.finishedAt && record.status === 'running') return;
  records.set(record.id, record);
}
async function generate() {
  if (get<HTMLButtonElement>('generate').disabled) return;
  if (!settings.hasKey) {
    openSettings();
    return;
  }
  const value = draft();
  if (!value.prompt.trim()) {
    showError('editor-error', '请先描述你想生成的画面。');
    get('prompt').focus();
    return;
  }
  if (!value.model) {
    showError('editor-error', '请填写图片模型名称。');
    get('model').focus();
    return;
  }
  submitting = true;
  updateGenerate();
  showError('editor-error', '');
  try {
    const record = unwrap(await api.generate(value));
    mergeRecord(record);
    renderRecords();
    get('records').scrollTop = 0;
  } catch (error) {
    showError(
      'editor-error',
      error instanceof Error ? error.message : '提交失败。',
    );
  } finally {
    submitting = false;
    updateGenerate();
  }
}
get('generate').onclick = () => {
  void generate();
};
document.addEventListener('keydown', (event) => {
  if (
    (event.ctrlKey || event.metaKey) &&
    event.key === 'Enter' &&
    !document.querySelector('dialog[open]')
  ) {
    event.preventDefault();
    void generate();
  }
});
setInterval(() => {
  for (const label of document.querySelectorAll<HTMLElement>(
    '[data-elapsed]',
  )) {
    const record = records.get(label.dataset.elapsed || '');
    if (record) label.textContent = elapsed(record);
  }
}, 1000);
async function initialize() {
  try {
    const saved = JSON.parse(
      localStorage.getItem('antv-studio-draft-v1') || 'null',
    );
    if (saved && typeof saved.prompt === 'string') {
      get<HTMLTextAreaElement>('prompt').value = saved.prompt.slice(0, 2000);
      if (typeof saved.model === 'string')
        get<HTMLInputElement>('model').value = saved.model;
      if (typeof saved.outputName === 'string')
        get<HTMLInputElement>('output-name').value = saved.outputName.slice(
          0,
          85,
        );
      if (
        Array.from(document.querySelectorAll<HTMLElement>('[data-size]')).some(
          (item) => item.dataset.size === saved.size,
        )
      )
        updateSize(saved.size);
      if (['auto', 'low', 'medium', 'high'].includes(saved.quality))
        get<HTMLSelectElement>('quality').value = saved.quality;
      if (['png', 'jpeg', 'webp'].includes(saved.format))
        get<HTMLSelectElement>('format').value = saved.format;
    }
  } catch {
    /* Ignore an incompatible draft, never a saved credential. */
  }
  updatePrompt();
  renderReferences();
  api.onUpdate((record) => {
    mergeRecord(record);
    renderRecords();
    if (record.status === 'succeeded')
      notify(record.saveError || '图片已生成并自动保存到所选目录。');
  });
  const state = unwrap(await api.state());
  settings = state.settings;
  for (const record of state.records) mergeRecord(record);
  loaded = true;
  renderSettings();
  renderRecords();
}
void initialize().catch((error) =>
  showError(
    'editor-error',
    error instanceof Error ? error.message : '本地配置读取失败，请重启工具。',
  ),
);
