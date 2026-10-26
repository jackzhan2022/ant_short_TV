import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { Alert, App, Button, Form, Input, Modal, Select } from 'antd';
import { useEffect, useRef, useState } from 'react';
import styles from './ManualAssetCreate.module.css';
import {
  type AssetDefinition,
  buildManualAssetPrompt,
  type CreatedManualAsset,
  createManualAsset,
  fieldIssue,
  type ManualAssetOptions,
  type ManualAssetType,
  normalizeAssetName,
  queryManualAssetOptions,
} from './manual-assets';

const labels = { CHARACTER: '角色', SCENE: '场景', PROP: '道具' };
const hints: Record<string, string> = {
  背景设定: '按剧本或已确认设定填写，没有依据可明确选择“未明确”。',
  身份与年龄: '年龄可填写年龄段，不根据姓名或职业推断性别、年龄。',
  稳定外貌: '填写具体制作设定；耳饰可填“无”，不要用“待补充”代替。',
  主体服装: '系统补齐自然站立、中性表情、双手下垂，不填写单场动作和手持道具。',
  场景设定: '描述同一地点与同一状态，不写人物、动物、运动、特效或冲突机位。',
  道具本体:
    '仅描述物件本体，不写人物、手部或剧情环境；文字标识必须有可靠依据。',
};
type Values = {
  name: string;
  aliases: string[];
  configuration: Record<string, string>;
  ownerCharacterId?: number;
};
type Props = {
  projectId: number;
  type: ManualAssetType;
  characters: { id: number; name: string }[];
  blocked: boolean;
  onCreated: (created: CreatedManualAsset, configure: boolean) => void;
};
function nameIssue(value?: string) {
  return !value?.trim() ||
    value.trim().length > 100 ||
    !normalizeAssetName(value)
    ? '需包含文字或数字，且不超过 100 字符。'
    : undefined;
}
function errorText(error: unknown) {
  const failure = error as {
    message?: string;
    response?: { data?: { errorMessage?: string } };
    data?: { errorMessage?: string };
  };
  return (
    failure?.data?.errorMessage ||
    failure?.response?.data?.errorMessage ||
    failure?.message ||
    '保存失败，请重试。'
  );
}

function ConfigurationModal({
  projectId,
  type,
  characters,
  blocked,
  options,
  onClose,
  onCreated,
}: Props & { options: ManualAssetOptions; onClose: () => void }) {
  const { message, modal } = App.useApp();
  const [form] = Form.useForm<Values>();
  const values = Form.useWatch([], form) as Partial<Values> | undefined;
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');
  const pending = useRef(false);
  const dirty = useRef(false);
  const alive = useRef(true);
  const attempted = useRef<{ payload: string; requestId: string } | undefined>(
    undefined,
  );
  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);
  const definition: AssetDefinition = options.definitions[type];
  const prompt = buildManualAssetPrompt(
    type,
    values?.name || '',
    definition,
    values?.configuration || {},
  );
  const groups = [...new Set(definition.fields.map((field) => field.group))];
  const close = () => {
    if (pending.current) return;
    if (!dirty.current && !form.isFieldsTouched()) return onClose();
    modal.confirm({
      title: '放弃本次新增？',
      content: '已填写的资产设定尚未保存，关闭后将丢失。',
      okText: '放弃修改',
      cancelText: '继续填写',
      onOk: onClose,
    });
  };
  const save = async (configure: boolean) => {
    if (pending.current || blocked || !options.canCreate || !options.scriptId)
      return;
    pending.current = true;
    let data: Values;
    try {
      data = await form.validateFields();
    } catch {
      pending.current = false;
      return;
    }
    setSaving(true);
    setSaveError('');
    const payload = {
      scriptId: options.scriptId,
      assetType: type,
      name: data.name.trim(),
      aliases: data.aliases || [],
      configuration: data.configuration,
      ownerCharacterId: data.ownerCharacterId,
    };
    const signature = JSON.stringify(payload);
    if (!attempted.current || attempted.current.payload !== signature)
      attempted.current = {
        payload: signature,
        requestId: crypto.randomUUID(),
      };
    let created: CreatedManualAsset;
    try {
      const response = await createManualAsset(projectId, {
        ...payload,
        requestId: attempted.current.requestId,
      });
      if (!response.success || !response.data)
        throw new Error(response.errorMessage || '保存失败，请重试。');
      created = response.data;
    } catch (error) {
      if (alive.current) {
        setSaveError(errorText(error));
        setSaving(false);
      }
      pending.current = false;
      return;
    }
    // A successful mutation must never become a retryable form because list refresh failed.
    if (!alive.current) return;
    pending.current = false;
    onClose();
    message.success(`${labels[type]}已添加，主形象待配置`);
    onCreated(created, configure);
  };
  return (
    <Modal
      open
      title={`新增${labels[type]}`}
      width={1080}
      centered
      onCancel={close}
      closable={!saving}
      keyboard={!saving}
      mask={{ closable: false }}
      className={styles.modal}
      footer={
        <div className={styles.footer}>
          <span>保存后创建主形象，可继续上传或生成图片</span>
          <div>
            <Button disabled={saving} onClick={close}>
              取消
            </Button>
            <Button
              loading={saving}
              disabled={blocked}
              onClick={() => void save(false)}
            >
              保存
            </Button>
            <Button
              type="primary"
              loading={saving}
              disabled={blocked}
              onClick={() => void save(true)}
            >
              保存并配置形象
            </Button>
          </div>
        </div>
      }
    >
      <p className={styles.subtitle}>
        填写与 AI 生成一致的{labels[type]}设定，自动整理形象提示词。
      </p>
      {blocked ? (
        <Alert
          type="warning"
          title="资产提取或归一尚未完成，请等待完成后保存。"
          showIcon
        />
      ) : null}
      {saveError ? (
        <Alert
          type="error"
          title={saveError}
          showIcon
          className={styles.error}
        />
      ) : null}
      <div className={styles.layout}>
        <Form
          form={form}
          layout="vertical"
          disabled={saving}
          initialValues={{ name: '', aliases: [], configuration: {} }}
          className={styles.form}
          onValuesChange={() => {
            dirty.current = true;
            setSaveError('');
          }}
        >
          <section className={styles.section}>
            <h3>基本信息</h3>
            <Form.Item
              name="name"
              label={`${labels[type]}名称`}
              required
              rules={[
                {
                  validator: (_, value) =>
                    nameIssue(value)
                      ? Promise.reject(new Error(nameIssue(value)))
                      : Promise.resolve(),
                },
              ]}
            >
              <Input
                maxLength={100}
                showCount
                placeholder={
                  {
                    CHARACTER: '例如：苏禾',
                    SCENE: '例如：天台花园',
                    PROP: '例如：苏禾的怀表',
                  }[type]
                }
              />
            </Form.Item>
            <Form.Item
              name="aliases"
              label="别名（选填）"
              dependencies={['name']}
              rules={[
                {
                  validator: (_, aliases: string[] = []) => {
                    const seen = new Set([
                      normalizeAssetName(form.getFieldValue('name') || ''),
                    ]);
                    if (aliases.length > 30)
                      return Promise.reject(new Error('最多添加 30 个别名。'));
                    for (const alias of aliases) {
                      const normalized = normalizeAssetName(alias);
                      if (nameIssue(alias) || seen.has(normalized))
                        return Promise.reject(
                          new Error(
                            '别名需包含文字或数字、最多 100 字符，且不能与名称或其他别名重复。',
                          ),
                        );
                      seen.add(normalized);
                    }
                    return Promise.resolve();
                  },
                },
              ]}
            >
              <Select
                mode="tags"
                tokenSeparators={['，', ',', '；', ';']}
                maxCount={30}
                placeholder="输入别名后按回车，最多 30 个"
                open={false}
              />
            </Form.Item>
          </section>
          {groups.map((group) => (
            <section className={styles.section} key={group}>
              <h3>{group}</h3>
              <p className={styles.hint}>{hints[group]}</p>
              <div className={type === 'CHARACTER' ? styles.fields : undefined}>
                {definition.fields
                  .filter((field) => field.group === group)
                  .map((field) => (
                    <div className={styles.field} key={field.key}>
                      {field.unknown ? (
                        <Button
                          type="link"
                          size="small"
                          disabled={saving}
                          className={styles.unknown}
                          onClick={() => {
                            dirty.current = true;
                            form.setFieldValue(
                              ['configuration', field.key],
                              field.unknown,
                            );
                            void form
                              .validateFields([['configuration', field.key]])
                              .catch(() => undefined);
                          }}
                        >
                          {field.unknown}
                        </Button>
                      ) : null}
                      <Form.Item
                        name={['configuration', field.key]}
                        label={field.label}
                        required
                        rules={[
                          {
                            validator: (_, value) => {
                              const issue = fieldIssue(field, value);
                              return issue
                                ? Promise.reject(new Error(issue))
                                : Promise.resolve();
                            },
                          },
                        ]}
                      >
                        {field.textarea ? (
                          <Input.TextArea
                            rows={3}
                            maxLength={field.maxLength}
                            showCount
                            placeholder={field.placeholder}
                          />
                        ) : (
                          <Input
                            maxLength={field.maxLength}
                            placeholder={field.placeholder}
                          />
                        )}
                      </Form.Item>
                    </div>
                  ))}
              </div>
            </section>
          ))}
          {type === 'PROP' ? (
            <section className={styles.section}>
              <Form.Item name="ownerCharacterId" label="持有角色（选填）">
                <Select
                  allowClear
                  showSearch={{ optionFilterProp: 'label' }}
                  placeholder="归属不明可不选"
                  options={characters.map((character) => ({
                    value: character.id,
                    label: character.name,
                  }))}
                />
              </Form.Item>
              <p className={styles.hint}>
                通用道具建议用“持有人＋道具名”命名。持有人不出现在道具展示画面中。
              </p>
            </section>
          ) : null}
        </Form>
        <aside className={styles.preview} aria-label="形象生成预览">
          <div className={styles.previewHeading}>
            <h3>形象提示词</h3>
            <span>随设定实时更新</span>
          </div>
          <p className={styles.hint}>
            保存后用于主形象生成，可在形象配置中继续调整。
          </p>
          <section
            className={`${styles.diagram} ${styles[type.toLowerCase()]}`}
            aria-label="输出画面布局"
          >
            {type === 'CHARACTER' ? (
              <>
                <span>
                  半身特写
                  <br />约 40%
                </span>
                <span>
                  正面
                  <br />
                  全身
                </span>
                <span>
                  侧面
                  <br />
                  全身
                </span>
                <span>
                  背面
                  <br />
                  全身
                </span>
              </>
            ) : type === 'SCENE' ? (
              ['正视图', '俯视图', '背视图', '侧视图'].map((label) => (
                <span key={label}>{label}</span>
              ))
            ) : (
              <span>道具完整入画 · 纯白背景 #FFFFFF</span>
            )}
          </section>
          <section className={styles.prompt} aria-label="自动生成的提示词">
            {prompt}
          </section>
          <p className={styles.hint}>
            未填写项仅用于预览，完整填写后才能保存。创建资产不会自动生成图片或扣费。
          </p>
        </aside>
      </div>
    </Modal>
  );
}

export default function ManualAssetCreate(props: Props) {
  const { message } = App.useApp();
  const [options, setOptions] = useState<ManualAssetOptions>();
  const [failed, setFailed] = useState(false);
  const [version, setVersion] = useState(0);
  const [open, setOpen] = useState(false);
  useEffect(() => {
    let active = true;
    setFailed(false);
    queryManualAssetOptions(props.projectId)
      .then((response) => {
        if (!response.success || !response.data)
          throw new Error('配置加载失败');
        if (active) setOptions(response.data);
      })
      .catch(() => {
        if (active) setFailed(true);
      });
    return () => {
      active = false;
    };
  }, [props.projectId, props.blocked, version]);
  const reason = props.blocked
    ? '资产提取或归一尚未完成'
    : options?.disabledReason ||
      (failed
        ? '新增配置加载失败，请重试'
        : !options
          ? '正在加载新增配置'
          : '');
  return (
    <>
      <span title={reason}>
        <Button
          aria-label={`新增${labels[props.type]}`}
          icon={<PlusOutlined />}
          disabled={!options?.canCreate || props.blocked}
          onClick={() => {
            if (!options?.scriptId) {
              message.warning('请先创建或导入剧本');
              return;
            }
            setOpen(true);
          }}
        >
          新增{labels[props.type]}
        </Button>
      </span>
      {failed ? (
        <Button
          type="text"
          icon={<ReloadOutlined />}
          aria-label="重试加载新增配置"
          onClick={() => setVersion((value) => value + 1)}
        />
      ) : null}
      {open && options ? (
        <ConfigurationModal
          key={`${props.projectId}-${props.type}`}
          {...props}
          options={options}
          onClose={() => setOpen(false)}
        />
      ) : null}
    </>
  );
}
