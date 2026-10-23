import { BulbOutlined } from '@ant-design/icons';
import { Input } from 'antd';
import type { CreationParameters } from './api';
import { type CreationDraft, imageCountOptions, newDraft } from './draft';
import styles from './index.module.css';
import ReferenceFiles, { type ReferenceDraft } from './ReferenceFiles';
import type { CreationMode, CreationModel } from './service';

const qualityLabels: Record<string, string> = {
  auto: '自动',
  low: '低',
  medium: '中',
  high: '高质量',
  xhigh: '超高',
  max: '最高',
};
export default function Composer({
  mode,
  models,
  loading,
  draft,
  onChange,
  example,
  disabled,
}: {
  mode: CreationMode;
  models: CreationModel[];
  loading: boolean;
  draft: CreationDraft;
  onChange: (
    draft: CreationDraft | ((current: CreationDraft) => CreationDraft),
  ) => void;
  example: string;
  disabled?: boolean;
}) {
  const selected = models.find((model) => model.id === draft.modelId);
  const setParameters = (parameters: Partial<CreationParameters>) =>
    onChange({ ...draft, parameters: { ...draft.parameters, ...parameters } });
  const setReferences = (
    kind: ReferenceDraft['kind'],
    values: ReferenceDraft[],
  ) =>
    onChange((current) => ({
      ...current,
      references: [
        ...current.references.filter((ref) => ref.kind !== kind),
        ...values,
      ],
    }));
  return (
    <fieldset className={styles.composerFields} disabled={disabled}>
      <div className={styles.field}>
        <label
          className={styles.fieldLabel}
          htmlFor={`free-creation-model-${mode}`}
        >
          生成模型
          {selected?.isDefault ? (
            <span className={styles.modelDefault}>系统默认</span>
          ) : null}
        </label>
        <select
          id={`free-creation-model-${mode}`}
          aria-label="生成模型"
          disabled={loading || !models.length}
          value={draft.modelId ?? ''}
          onChange={(event) => {
            const model = models.find(
              (item) => item.id === Number(event.target.value),
            );
            onChange({ ...newDraft(model), prompt: draft.prompt });
          }}
        >
          {!models.length ? (
            <option value="">
              {loading
                ? '正在读取模型…'
                : `暂无已启用的${mode === 'IMAGE' ? '图片' : '视频'}模型`}
            </option>
          ) : (
            models.map((model) => (
              <option key={model.id} value={model.id}>
                {model.name}
              </option>
            ))
          )}
        </select>
        {selected?.description ? (
          <p className={styles.modelDescription}>{selected.description}</p>
        ) : null}
      </div>
      <div className={styles.field}>
        <label
          className={styles.fieldLabel}
          htmlFor={`free-creation-prompt-${mode}`}
        >
          创意描述<span className={styles.muted}>提示词</span>
        </label>
        <div className={styles.promptBox}>
          <Input.TextArea
            id={`free-creation-prompt-${mode}`}
            aria-label="创意描述"
            value={draft.prompt}
            maxLength={2000}
            onChange={(event) =>
              onChange({ ...draft, prompt: event.target.value })
            }
            placeholder={
              mode === 'IMAGE'
                ? '描述你想创作的画面：主体、场景、风格与光线…'
                : '描述画面中的动作、镜头运动与节奏…'
            }
            autoSize={{ minRows: 4, maxRows: 7 }}
            variant="borderless"
          />
          <div className={styles.promptFooter}>
            <button
              type="button"
              onClick={() => onChange({ ...draft, prompt: example })}
            >
              <BulbOutlined aria-hidden="true" />
              试试示例
            </button>
            <span>{draft.prompt.length} / 2000</span>
          </div>
        </div>
      </div>
      {selected?.configStatus === 'invalid' ? (
        <p role="alert" className={styles.error}>
          模型扩展配置有误，请在管理后台检查。
        </p>
      ) : null}
      {selected ? (
        <div key={selected.id}>
          {(['image', 'video', 'audio'] as const).map((kind) =>
            (mode === 'IMAGE' && kind === 'image') ||
            (selected.references[kind] &&
              selected.references[kind]?.maxCount !== 0) ? (
              <ReferenceFiles
                key={kind}
                kind={kind}
                constraints={selected.references[kind]}
                initialReferences={draft.references.filter(
                  (ref) => ref.kind === kind,
                )}
                onChange={(refs) => setReferences(kind, refs)}
                disabled={disabled}
              />
            ) : null,
          )}
          {mode === 'VIDEO' && !Object.keys(selected.references).length ? (
            <p className={styles.help}>
              模型未配置参考素材规则，使用文本生成视频。
            </p>
          ) : null}
          {mode === 'IMAGE' ? (
            <>
              <div className={styles.field}>
                <div className={styles.fieldLabel}>画面比例</div>
                <div className={styles.ratios}>
                  {['', '1:1', '16:9', '9:16', '3:4'].map((value) => (
                    <button
                      type="button"
                      key={value}
                      aria-pressed={
                        (draft.parameters.aspectRatio || '') === value
                      }
                      onClick={() =>
                        setParameters({ aspectRatio: value || undefined })
                      }
                    >
                      {value || '默认'}
                    </button>
                  ))}
                </div>
              </div>
              <div className={styles.twoColumns}>
                <div className={styles.field}>
                  <div className={styles.fieldLabel}>图片质量</div>
                  <div className={styles.readonly}>
                    {selected.quality
                      ? qualityLabels[selected.quality] || selected.quality
                      : '模型默认'}
                  </div>
                </div>
                <div className={styles.field}>
                  <div className={styles.fieldLabel}>生成数量</div>
                  <div className={styles.choices}>
                    {imageCountOptions(selected).map((value) => (
                      <button
                        key={value}
                        type="button"
                        aria-label={`生成${value}张`}
                        aria-pressed={draft.parameters.count === value}
                        onClick={() => setParameters({ count: value })}
                      >
                        {value}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            </>
          ) : (
            <div className={styles.twoColumns}>
              <div className={styles.field}>
                <label
                  className={styles.fieldLabel}
                  htmlFor={`duration-${mode}`}
                >
                  视频时长
                </label>
                {selected.duration ? (
                  <>
                    <div className={styles.numberField}>
                      <input
                        id={`duration-${mode}`}
                        type="number"
                        aria-label="视频时长"
                        min={selected.duration.min}
                        max={selected.duration.max}
                        step={1}
                        disabled={draft.parameters.intelligentDuration}
                        value={
                          draft.parameters.durationSeconds ??
                          selected.duration.min
                        }
                        onChange={(event) =>
                          setParameters({
                            durationSeconds: Number(event.target.value),
                          })
                        }
                      />
                      <span>秒</span>
                    </div>
                    {selected.duration.intelligent ? (
                      <label className={styles.intelligent}>
                        <input
                          type="checkbox"
                          checked={Boolean(
                            draft.parameters.intelligentDuration,
                          )}
                          onChange={(event) =>
                            setParameters({
                              intelligentDuration: event.target.checked,
                            })
                          }
                        />
                        智能时长
                      </label>
                    ) : null}
                  </>
                ) : (
                  <div className={styles.readonly}>模型默认</div>
                )}
              </div>
              <div className={styles.field}>
                <label
                  className={styles.fieldLabel}
                  htmlFor={`resolution-${mode}`}
                >
                  分辨率
                </label>
                {selected.resolutions.length ? (
                  <select
                    id={`resolution-${mode}`}
                    aria-label="分辨率"
                    value={draft.parameters.resolution || ''}
                    onChange={(event) =>
                      setParameters({ resolution: event.target.value })
                    }
                  >
                    {selected.resolutions.map((value) => (
                      <option key={value} value={value}>
                        {value.toUpperCase()}
                      </option>
                    ))}
                  </select>
                ) : (
                  <div className={styles.readonly}>模型默认</div>
                )}
              </div>
            </div>
          )}
          <details className={styles.advanced}>
            <summary>模型配置说明</summary>
            <p>参数来自管理后台，未配置的选项使用模型默认值。</p>
            {selected.providerName ? (
              <p>模型服务商：{selected.providerName}</p>
            ) : null}
            {selected.outputFormat ? (
              <p>输出格式：{selected.outputFormat.toUpperCase()}</p>
            ) : null}
            <p>参考素材完成校验后提交；生成前会确认本次预计消耗积分。</p>
          </details>
        </div>
      ) : null}
    </fieldset>
  );
}
