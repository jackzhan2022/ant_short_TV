import {
  AuditOutlined,
  DownOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import { Button, Checkbox, Input } from 'antd';
import PointEstimateButton from '@/components/PointEstimateButton';
import {
  DEFAULT_REVIEW_DIMENSIONS,
  REVIEW_DIMENSIONS,
} from '../script-review/dimensions';
import ScriptContentImport from '../short-drama-creation/ScriptContentImport';
import styles from './index.module.css';

export const MAX_CONTENT_LENGTH = 200000;
const groups = [
  { title: '人物与台词', options: REVIEW_DIMENSIONS.slice(0, 4) },
  { title: '连续性', options: REVIEW_DIMENSIONS.slice(4, 8) },
  { title: '叙事与执行', options: REVIEW_DIMENSIONS.slice(8) },
];

export default function ReviewComposer({
  content,
  dimensions,
  busy,
  tenantId,
  onContentChange,
  onDimensionsChange,
  onImport,
  onSubmit,
}: {
  content: string;
  dimensions: string[];
  busy: boolean;
  tenantId: number;
  onContentChange: (value: string) => void;
  onDimensionsChange: (value: string[]) => void;
  onImport: (value: string) => void;
  onSubmit: () => void;
}) {
  const ready = Boolean(content.trim() && dimensions.length);
  return (
    <div className={styles.composeGrid}>
      <section className={styles.editor} aria-label="剧本编辑区">
        <header className={styles.panelHeading}>
          <div className={styles.panelTitle}>
            <span className={styles.documentIcon}>
              <FileTextOutlined />
            </span>
            <div>
              <h2>
                <label htmlFor="review-script-content">剧本内容</label>
              </h2>
              <small>粘贴全文，或导入已有剧本</small>
            </div>
          </div>
          <ScriptContentImport
            buttonLabel="导入文件"
            localOnly
            disabled={busy}
            currentContent={content}
            onImport={onImport}
          />
        </header>
        <Input.TextArea
          id="review-script-content"
          aria-label="剧本内容"
          className={styles.textarea}
          value={content}
          maxLength={MAX_CONTENT_LENGTH}
          disabled={busy}
          placeholder="在这里输入或粘贴剧本内容…"
          onChange={(event) => onContentChange(event.target.value)}
        />
        <footer className={styles.editorFoot}>
          <span>支持 TXT、MD、DOCX</span>
          <span>{content.length.toLocaleString('zh-CN')} / 200,000 字</span>
        </footer>
      </section>
      <section className={styles.selectionPanel} aria-label="审核维度">
        <header className={styles.panelHeading}>
          <div className={styles.selectionTitle}>
            <h2>审核维度</h2>
            <span className={styles.selectedCount}>{dimensions.length} 项</span>
          </div>
          <Button
            type="text"
            size="small"
            disabled={busy}
            onClick={() => onDimensionsChange([...REVIEW_DIMENSIONS])}
          >
            全选
          </Button>
        </header>
        <div className={styles.dimensionsScroll}>
          {groups.map((group, index) => (
            <details
              className={styles.dimensionGroup}
              key={group.title}
              open={index < 2}
            >
              <summary>
                {group.title}
                <DownOutlined />
              </summary>
              {group.options.map((dimension) => (
                <div className={styles.dimensionOption} key={dimension}>
                  <Checkbox
                    checked={dimensions.includes(dimension)}
                    disabled={busy}
                    onChange={(event) =>
                      onDimensionsChange(
                        event.target.checked
                          ? [...dimensions, dimension]
                          : dimensions.filter((value) => value !== dimension),
                      )
                    }
                  >
                    {dimension}
                  </Checkbox>
                </div>
              ))}
            </details>
          ))}
        </div>
        <footer className={styles.configurationFoot}>
          <div className={styles.scopeLine}>
            <span>审核全部输入内容</span>
            <Button
              type="text"
              size="small"
              disabled={busy}
              onClick={() => onDimensionsChange([...DEFAULT_REVIEW_DIMENSIONS])}
            >
              恢复默认
            </Button>
          </div>
          <PointEstimateButton
            block
            compact
            className={styles.submit}
            type="primary"
            icon={<AuditOutlined />}
            loading={busy}
            disabled={!ready}
            tenantId={tenantId}
            operations={
              ready
                ? [
                    {
                      url: '/api/script-review/submissions',
                      data: {
                        content: content.trim(),
                        selectedDimensions: dimensions,
                      },
                    },
                  ]
                : undefined
            }
            onClick={onSubmit}
          >
            开始审核
          </PointEstimateButton>
        </footer>
      </section>
    </div>
  );
}
