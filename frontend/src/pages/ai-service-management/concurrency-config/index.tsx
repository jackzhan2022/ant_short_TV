import { useAccess } from '@umijs/max';
import { Alert, App, Button, Card, Form, InputNumber, Spin } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useStyles } from './index.style';
import { getConcurrencyConfig, updateConcurrencyConfig } from './service';
import {
  type ConcurrencyConfig,
  type ConcurrencySettings,
  settingMaximums,
} from './types';

const groups: Array<{
  title: string;
  description: string;
  fields: Array<{ name: keyof ConcurrencySettings; label: string }>;
}> = [
  {
    title: '团队分类上限',
    description:
      '以下上限对每个团队分别生效，所有团队使用相同的上限。视频在途数包含已提交、等待生成结果的任务，不等于本机正在提交或查询的并行数。',
    fields: [
      { name: 'teamTextLimit', label: '文本并行上限' },
      { name: 'teamImageLimit', label: '图片并行上限' },
      { name: 'teamVideoLimit', label: '视频在途上限' },
    ],
  },
  {
    title: '本机资源池',
    description:
      '控制当前服务器同时处理的任务数。视频提交与结果查询分别分配处理能力，等待生成结果的视频不持续占用提交资源。',
    fields: [
      { name: 'textWorkers', label: '文本处理并行数' },
      { name: 'imageWorkers', label: '图片处理并行数' },
      { name: 'videoSubmitWorkers', label: '视频提交并行数' },
      { name: 'videoQueryWorkers', label: '视频查询并行数' },
    ],
  },
  {
    title: '共享保护',
    description:
      '当前服务器上，同一服务商的各类请求共享服务商总并发上限；同一模型的执行任务共享模型配额。',
    fields: [
      { name: 'providerLimit', label: '服务商请求总并发' },
      { name: 'modelLimit', label: '单模型执行配额' },
    ],
  },
];

const errorText = (error: unknown, fallback: string) =>
  error instanceof Error ? error.message || fallback : fallback;

const ConfigForm = ({
  config,
  canEdit,
}: {
  config: ConcurrencyConfig;
  canEdit: boolean;
}) => {
  const { styles } = useStyles();
  const { message } = App.useApp();
  const [form] = Form.useForm<ConcurrencySettings>();
  const [saving, setSaving] = useState(false);
  const pending = useRef(false);
  const [saveError, setSaveError] = useState<string>();
  const [saved, setSaved] = useState(false);
  const [databaseMaxConnections, setDatabaseMaxConnections] = useState(
    config.databaseMaxConnections,
  );

  const save = async (values: ConcurrencySettings) => {
    if (!canEdit || pending.current) return;
    pending.current = true;
    setSaving(true);
    setSaveError(undefined);
    setSaved(false);
    try {
      const result = await updateConcurrencyConfig(values);
      form.setFieldsValue(result.settings);
      setDatabaseMaxConnections(result.databaseMaxConnections);
      setSaved(true);
      message.success('配置保存成功', 4);
    } catch (error) {
      setSaveError(errorText(error, '保存失败，请重试'));
    } finally {
      pending.current = false;
      setSaving(false);
    }
  };

  return (
    <Form<ConcurrencySettings>
      form={form}
      className={styles.form}
      layout="vertical"
      initialValues={config.settings}
      disabled={!canEdit || saving}
      requiredMark={false}
      onFinish={save}
      onValuesChange={() => {
        setSaved(false);
        setSaveError(undefined);
      }}
    >
      {groups.map((group) => (
        <Card key={group.title} title={group.title}>
          <p className={styles.description}>{group.description}</p>
          <div className={styles.fields}>
            {group.fields.map(({ name, label }) => (
              <Form.Item
                key={name}
                name={name}
                label={label}
                extra={`1–${settingMaximums[name]}`}
                rules={[
                  {
                    type: 'integer',
                    required: true,
                    min: 1,
                    max: settingMaximums[name],
                    message: `请输入 1–${settingMaximums[name]} 的正整数`,
                  },
                ]}
              >
                <InputNumber
                  changeOnBlur={false}
                  onInput={(text) => {
                    // InputNumber does not emit onChange for unparseable input.
                    // Clear the stored value so a save cannot submit the old one.
                    if (text.trim() && !Number.isFinite(Number(text))) {
                      form.setFieldValue(name, null);
                      setSaved(false);
                      setSaveError(undefined);
                    }
                  }}
                />
              </Form.Item>
            ))}
          </div>
          {group.title === '本机资源池' && (
            <p className={styles.database}>
              数据库连接池最大连接数（只读）
              <output aria-label="数据库连接池最大连接数">
                {databaseMaxConnections}
              </output>
            </p>
          )}
        </Card>
      ))}
      {saveError && (
        <Alert type="error" showIcon title="保存失败" description={saveError} />
      )}
      <div className={styles.actions}>
        {canEdit && (
          <Button
            type="primary"
            htmlType="submit"
            loading={saving}
            disabled={saving}
          >
            {saving ? '保存中' : '保存配置'}
          </Button>
        )}
        {saved && (
          <span className={styles.saved} role="status" aria-label="保存状态">
            配置已保存
          </span>
        )}
        <p>
          保存后对新任务生效；降低上限不会取消正在运行的任务。未保存时继续使用现有配置。
        </p>
      </div>
    </Form>
  );
};

const ConcurrencyConfigPage = () => {
  const access = useAccess();
  const { styles } = useStyles();
  const canView = Boolean(access.canViewPlatformAiConcurrency);
  const [config, setConfig] = useState<ConcurrencyConfig>();
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string>();
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!canView) return;
    let cancelled = false;
    setLoading(true);
    setLoadError(undefined);
    getConcurrencyConfig().then(
      (result) => {
        if (!cancelled) {
          setConfig(result);
          setLoading(false);
        }
      },
      (error) => {
        if (!cancelled) {
          setLoadError(errorText(error, '读取失败，请重试'));
          setLoading(false);
        }
      },
    );
    return () => {
      cancelled = true;
    };
  }, [canView, attempt]);

  if (!canView) return null;
  return (
    <div className={styles.page}>
      {loading ? (
        <div role="status">
          <Spin /> 正在读取配置
        </div>
      ) : loadError ? (
        <Alert
          type="error"
          showIcon
          title="读取配置失败"
          description={loadError}
          action={
            <Button onClick={() => setAttempt((value) => value + 1)}>
              重试
            </Button>
          }
        />
      ) : config ? (
        <ConfigForm
          config={config}
          canEdit={Boolean(access.canEditPlatformAiConcurrency)}
        />
      ) : null}
    </div>
  );
};

export default ConcurrencyConfigPage;
