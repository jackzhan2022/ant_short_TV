import { Popover } from 'antd';
import {
  type ReactNode,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import type { StoryboardPromptDocument, StoryboardPromptNode } from './service';

type PromptReferenceOption = Extract<StoryboardPromptNode, { type: 'mention' }>;

const plainTextFromDocument = (document?: StoryboardPromptDocument | null) =>
  document?.nodes
    .map((node) => (node.type === 'text' ? node.text : node.displayName))
    .join('') || '';

const createPromptMention = (node: PromptReferenceOption) => {
  const mention = window.document.createElement('span');
  mention.contentEditable = 'false';
  mention.dataset.mediaType = node.mediaType;
  mention.dataset.sourceType = node.sourceType;
  mention.dataset.sourceId = String(node.sourceId);
  if (node.assetType) mention.dataset.assetType = node.assetType;
  if (node.assetId) mention.dataset.assetId = String(node.assetId);
  if (node.variantId) mention.dataset.variantId = String(node.variantId);
  mention.dataset.displayName = node.displayName;
  mention.className = 'storyboard-prompt-reference';
  mention.title = node.displayName;
  const name = window.document.createElement('span');
  name.className = 'storyboard-prompt-reference-name';
  name.textContent = node.displayName;
  mention.append(name);
  return mention;
};

const MaterialPromptEditor = ({
  label,
  value,
  document,
  referenceThumbnails,
  renderReferencePicker,
  onChange,
  onCommit,
  onFocus,
  onBlur,
}: {
  label: string;
  value: string;
  document?: StoryboardPromptDocument | null;
  referenceThumbnails: ReadonlyMap<string, string>;
  renderReferencePicker: (
    onSelect: (reference: PromptReferenceOption) => void,
  ) => ReactNode;
  onChange: (plainText: string, nextDocument: StoryboardPromptDocument) => void;
  onCommit: (plainText: string, nextDocument: StoryboardPromptDocument) => void;
  onFocus: () => void;
  onBlur: () => void;
}) => {
  const editorRef = useRef<HTMLDivElement>(null);
  const initializedKey = useRef('');
  const composing = useRef(false);
  const [mentionTrigger, setMentionTrigger] = useState<{
    range: Range;
    left: number;
    top: number;
  }>();
  const renderedNodes = useMemo<StoryboardPromptNode[]>(
    () =>
      document?.nodes?.length
        ? document.nodes
        : [{ type: 'text', text: value }],
    [document?.nodes, value],
  );
  const renderedKey = JSON.stringify(renderedNodes);

  useLayoutEffect(() => {
    const editor = editorRef.current;
    if (!editor || initializedKey.current === renderedKey) return;
    const fragment = window.document.createDocumentFragment();
    const fullText = renderedNodes
      .map((node) => (node.type === 'text' ? node.text : node.displayName))
      .join('');
    const referenceStart = fullText.indexOf('### 素材引用');
    const referenceEnd = fullText.indexOf('### 画面描写');
    let offset = 0;
    renderedNodes.forEach((node) => {
      if (node.type === 'text') {
        let cursor = 0;
        for (const match of node.text.matchAll(/<([^<>\n]+)>/g)) {
          const position = offset + match.index;
          if (
            referenceStart >= 0 &&
            (position < referenceStart ||
              (referenceEnd >= 0 && position >= referenceEnd))
          )
            continue;
          if (match.index > cursor)
            fragment.append(
              window.document.createTextNode(
                node.text.slice(cursor, match.index),
              ),
            );
          const unbound = window.document.createElement('span');
          unbound.contentEditable = 'false';
          unbound.dataset.unboundReference = match[1].trim();
          unbound.title = '尚未绑定可用的参考图片';
          unbound.textContent = match[0];
          unbound.style.cssText =
            'display:inline-block;padding:0 4px;border-radius:4px;background:#fff3db;color:#9a5800;border:1px solid #f2ce89';
          fragment.append(unbound);
          cursor = match.index + match[0].length;
        }
        if (cursor < node.text.length)
          fragment.append(
            window.document.createTextNode(node.text.slice(cursor)),
          );
        offset += node.text.length;
        return;
      }
      fragment.append(createPromptMention(node));
      offset += node.displayName.length;
    });
    editor.replaceChildren(fragment);
    initializedKey.current = renderedKey;
  }, [renderedKey, renderedNodes]);

  useLayoutEffect(() => {
    // Update images separately so arriving metadata does not reset the editing caret.
    editorRef.current
      ?.querySelectorAll<HTMLElement>('[data-media-type="IMAGE"]')
      .forEach((mention) => {
        const thumbnail = referenceThumbnails.get(
          `${mention.dataset.sourceType}:${mention.dataset.sourceId}`,
        );
        let image = mention.querySelector('img');
        if (!thumbnail) {
          image?.remove();
          return;
        }
        if (image?.getAttribute('src') === thumbnail) return;
        if (!image) {
          image = window.document.createElement('img');
          image.className = 'storyboard-prompt-reference-image';
          image.alt = `${mention.dataset.displayName}参考图`;
          image.width = 22;
          image.height = 22;
          image.draggable = false;
          image.loading = 'lazy';
          image.decoding = 'async';
          image.onerror = () => image?.remove();
          mention.prepend(image);
        }
        image.src = thumbnail;
      });
  }, [renderedKey, referenceThumbnails]);

  const readDocument = () => {
    const nodes: StoryboardPromptNode[] = [];
    const appendText = (text: string) => {
      if (!text) return;
      const previous = nodes.at(-1);
      if (previous?.type === 'text') previous.text += text;
      else nodes.push({ type: 'text', text });
    };
    const walk = (node: Node) => {
      if (node.nodeType === Node.TEXT_NODE) {
        appendText(node.textContent || '');
        return;
      }
      if (!(node instanceof HTMLElement)) return;
      if (
        node.dataset.mediaType &&
        node.dataset.sourceType &&
        node.dataset.sourceId
      ) {
        nodes.push({
          type: 'mention',
          mediaType: node.dataset.mediaType as 'IMAGE' | 'VIDEO' | 'AUDIO',
          sourceType: node.dataset.sourceType,
          sourceId: Number(node.dataset.sourceId),
          assetType: node.dataset.assetType as
            | 'CHARACTER'
            | 'SCENE'
            | 'PROP'
            | undefined,
          assetId: node.dataset.assetId
            ? Number(node.dataset.assetId)
            : undefined,
          variantId: node.dataset.variantId
            ? Number(node.dataset.variantId)
            : undefined,
          displayName: node.dataset.displayName || node.textContent || '',
        });
        return;
      }
      Array.from(node.childNodes).forEach(walk);
      if (node.tagName === 'DIV') appendText('\n');
      if (node.tagName === 'BR') appendText('\n');
    };
    Array.from(editorRef.current?.childNodes || []).forEach(walk);
    const nextDocument: StoryboardPromptDocument = { version: 2, nodes };
    initializedKey.current = JSON.stringify(nextDocument.nodes);
    onChange(plainTextFromDocument(nextDocument), nextDocument);
    return nextDocument;
  };

  const detectMentionTrigger = () => {
    const selection = window.getSelection();
    const editor = editorRef.current;
    if (!editor || !selection?.isCollapsed || !selection.rangeCount) {
      setMentionTrigger(undefined);
      return;
    }
    const caret = selection.getRangeAt(0);
    const text = caret.startContainer;
    const offset = caret.startOffset;
    if (
      text.nodeType !== Node.TEXT_NODE ||
      !editor.contains(text) ||
      text.parentElement?.closest('[contenteditable="false"]') ||
      !['@', '＠'].includes(text.textContent?.[offset - 1] || '')
    ) {
      setMentionTrigger(undefined);
      return;
    }
    const range = caret.cloneRange();
    range.setStart(text, offset - 1);
    const rect = range.getBoundingClientRect();
    setMentionTrigger({ range, left: rect.left, top: rect.bottom });
  };
  const insertMentionAtTrigger = (reference: PromptReferenceOption) => {
    const editor = editorRef.current;
    const range = mentionTrigger?.range;
    if (
      !editor ||
      !range ||
      !editor.contains(range.commonAncestorContainer) ||
      !['@', '＠'].includes(range.toString())
    ) {
      setMentionTrigger(undefined);
      return;
    }
    range.deleteContents();
    const mention = createPromptMention(reference);
    range.insertNode(mention);
    range.setStartAfter(mention);
    range.collapse(true);
    setMentionTrigger(undefined);
    editor.focus();
    window.getSelection()?.removeAllRanges();
    window.getSelection()?.addRange(range);
    const nextDocument = readDocument();
    onCommit(plainTextFromDocument(nextDocument), nextDocument);
  };
  const dismissMentionPicker = () => {
    setMentionTrigger(undefined);
    onBlur();
  };

  return (
    <>
      {/* biome-ignore lint/a11y/useSemanticElements: Rich-text material mentions require a contenteditable container. */}
      <div
        ref={editorRef}
        role="textbox"
        tabIndex={0}
        aria-label={label}
        aria-multiline="true"
        data-storyboard-scroll-region
        contentEditable
        suppressContentEditableWarning
        onInput={(event) => {
          readDocument();
          if (
            !composing.current &&
            !(event.nativeEvent as InputEvent).isComposing
          )
            detectMentionTrigger();
        }}
        onCompositionStart={() => {
          composing.current = true;
        }}
        onCompositionEnd={() => {
          composing.current = false;
          readDocument();
          detectMentionTrigger();
        }}
        onKeyDown={(event) => {
          if (
            mentionTrigger &&
            [
              'Escape',
              'ArrowLeft',
              'ArrowRight',
              'ArrowUp',
              'ArrowDown',
            ].includes(event.key)
          ) {
            setMentionTrigger(undefined);
          }
        }}
        onClick={() => setMentionTrigger(undefined)}
        onScroll={() => setMentionTrigger(undefined)}
        onFocus={onFocus}
        onBlur={() => {
          if (!mentionTrigger) onBlur();
        }}
        className="storyboard-prompt-editor"
        style={{
          whiteSpace: 'pre-wrap',
          outline: 'none',
          fontSize: 14,
          fontWeight: 400,
          lineHeight: 1.7,
        }}
      />
      {mentionTrigger ? (
        <Popover
          open
          trigger="click"
          placement="bottomLeft"
          onOpenChange={(open) => {
            if (!open) dismissMentionPicker();
          }}
          content={
            <div
              role="dialog"
              aria-label={`${label}引用素材`}
              onKeyDown={(event) => {
                if (event.key === 'Escape') {
                  event.stopPropagation();
                  dismissMentionPicker();
                  editorRef.current?.focus();
                }
              }}
            >
              {renderReferencePicker(insertMentionAtTrigger)}
            </div>
          }
        >
          <span
            aria-hidden="true"
            style={{
              position: 'fixed',
              left: mentionTrigger.left,
              top: mentionTrigger.top,
              width: 1,
              height: 1,
              pointerEvents: 'none',
            }}
          />
        </Popover>
      ) : null}
    </>
  );
};

export default MaterialPromptEditor;
