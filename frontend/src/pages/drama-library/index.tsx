import { PageContainer } from '@ant-design/pro-components';
import { useAccess, useIntl, useModel } from '@umijs/max';
import {
  Alert,
  App,
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { Key } from 'react';
import { useCallback, useEffect, useRef, useState } from 'react';
import ImportEpisode from './ImportEpisode';
import OfficialAuthorization from './OfficialAuthorization';
import {
  canManageCode,
  canUseMiniapp,
  type Drama,
  type DramaInput,
  deleteDrama,
  deleteEpisode,
  type Episode,
  enableMiniapp,
  getDrama,
  listDramas,
  listEpisodes,
  listMiniapps,
  listReleases,
  type Miniapp,
  publishDrama,
  type Release,
  saveDrama,
  unpublishDrama,
} from './service';
import TrialCode from './TrialCode';

function DramaLibraryContent() {
  const access = useAccess();
  const { initialState } = useModel('@@initialState');
  const tenantId = initialState?.selectedTenant?.tenant.id;
  const [codeAppId, setCodeAppId] = useState<number>();
  const intl = useIntl();
  const t = (key: string, text: string) =>
    intl.formatMessage({ id: `drama.${key}`, defaultMessage: text });
  const { message } = App.useApp();
  const [dramas, setDramas] = useState<Drama[]>([]);
  const [apps, setApps] = useState<Miniapp[]>([]);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [editor, setEditor] = useState<Drama | 'new'>();
  const [dramaForm] = Form.useForm<DramaInput>();
  const [detail, setDetail] = useState<Drama>();
  const [episodes, setEpisodes] = useState<Episode[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailFailed, setDetailFailed] = useState(false);
  const [importing, setImporting] = useState(false);
  const [selected, setSelected] = useState<Key[]>([]);
  const [publishAppId, setPublishAppId] = useState<number>();
  const [releaseApp, setReleaseApp] = useState<Miniapp>();
  const [releases, setReleases] = useState<Release[]>([]);
  const [releaseLoading, setReleaseLoading] = useState(false);
  const [releaseFailed, setReleaseFailed] = useState(false);
  const detailGeneration = useRef(0);
  const releaseGeneration = useRef(0);
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      detailGeneration.current++;
      releaseGeneration.current++;
    };
  }, []);
  const reloadGeneration = useRef(0);
  const reload = useCallback(async () => {
    if (!mounted.current) return;
    const generation = ++reloadGeneration.current;
    setLoading(true);
    setFailed(false);
    try {
      const [library, miniapps] = await Promise.all([
        listDramas(),
        listMiniapps(),
      ]);
      if (mounted.current && generation === reloadGeneration.current) {
        setDramas(library);
        setApps(miniapps);
      }
    } catch {
      if (mounted.current && generation === reloadGeneration.current)
        setFailed(true);
    } finally {
      if (mounted.current && generation === reloadGeneration.current)
        setLoading(false);
    }
  }, []);
  useEffect(() => {
    void reload();
  }, [reload]);
  const loadDetail = async (id: number) => {
    const generation = ++detailGeneration.current;
    setDetailLoading(true);
    setDetailFailed(false);
    setEpisodes([]);
    setSelected([]);
    setPublishAppId(undefined);
    try {
      const [drama, items] = await Promise.all([
        getDrama(id),
        listEpisodes(id),
      ]);
      if (generation === detailGeneration.current) {
        setDetail(drama);
        setEpisodes(items);
      }
    } catch {
      if (generation === detailGeneration.current) setDetailFailed(true);
    } finally {
      if (generation === detailGeneration.current) setDetailLoading(false);
    }
  };
  const loadReleases = async (app: Miniapp) => {
    const generation = ++releaseGeneration.current;
    setReleaseApp(app);
    setReleaseLoading(true);
    setReleaseFailed(false);
    setReleases([]);
    try {
      const rows = await listReleases(app.id);
      if (generation === releaseGeneration.current) setReleases(rows);
    } catch {
      if (generation === releaseGeneration.current) setReleaseFailed(true);
    } finally {
      if (generation === releaseGeneration.current) setReleaseLoading(false);
    }
  };
  const mutate = async (
    action: () => Promise<unknown>,
    refresh: () => Promise<void>,
  ) => {
    setBusy(true);
    try {
      await action();
      message.success(t('saved', '操作成功'));
      await refresh();
      return true;
    } catch {
      return false;
    } finally {
      if (mounted.current) setBusy(false);
    }
  };
  const closeDetail = () => {
    ++detailGeneration.current;
    setDetail(undefined);
    setImporting(false);
    setEpisodes([]);
    setSelected([]);
  };
  const openEditor = (drama?: Drama) => {
    setEditor(drama || 'new');
    dramaForm.resetFields();
    dramaForm.setFieldsValue({
      title: drama?.title ?? '',
      description: drama?.description ?? '',
    });
  };
  const manualNotice = (
    <Alert
      type="warning"
      showIcon
      title={t('manual', '手工绑定 · 非官方授权')}
      description={t(
        'manualHint',
        '仅登记本团队小程序 AppID 与名称，不代表微信官方授权或审核通过。AppSecret 仅在服务端配置，本页面不收集密钥。上架仅更新本系统分发目录。',
      )}
    />
  );
  return (
    <PageContainer
      title={t('title', '剧集管理')}
      subTitle={t('subtitle', '独立维护剧库与单集，按小程序分别分发')}
    >
      <Space orientation="vertical" size="large" style={{ width: '100%' }}>
        {failed && (
          <Alert
            type="error"
            title={t('loadFailed', '加载失败，请重试')}
            action={
              <Button onClick={() => void reload()}>
                {t('reload', '刷新')}
              </Button>
            }
          />
        )}
        <Tabs
          items={[
            {
              key: 'library',
              label: t('library', '剧库'),
              children: (
                <Space orientation="vertical" style={{ width: '100%' }}>
                  <Space wrap>
                    <Input
                      aria-label={t('search', '搜索剧名')}
                      placeholder={t('search', '搜索剧名')}
                      value={keyword}
                      onChange={(event) => setKeyword(event.target.value)}
                      allowClear
                    />
                    <Button loading={loading} onClick={() => void reload()}>
                      {t('reload', '刷新')}
                    </Button>
                    {access.canManageDramas && (
                      <Button type="primary" onClick={() => openEditor()}>
                        {t('create', '新建剧集')}
                      </Button>
                    )}
                  </Space>
                  <Table<Drama>
                    rowKey="id"
                    loading={loading}
                    dataSource={dramas.filter((drama) =>
                      drama.title.toLowerCase().includes(keyword.toLowerCase()),
                    )}
                    scroll={{ x: 720 }}
                    pagination={{ pageSize: 10 }}
                    locale={{
                      emptyText: t(
                        'emptyLibrary',
                        '暂无剧集。新建剧集后，可从本地视频或项目成片导入单集。',
                      ),
                    }}
                    columns={[
                      {
                        title: t('dramaTitle', '剧名'),
                        dataIndex: 'title',
                        width: 240,
                        render: (_, drama) => (
                          <Button
                            type="link"
                            onClick={() => {
                              setDetail(drama);
                              void loadDetail(drama.id);
                            }}
                          >
                            {drama.title}
                          </Button>
                        ),
                      },
                      {
                        title: t('description', '简介'),
                        dataIndex: 'description',
                        render: (text) => (
                          <Typography.Paragraph
                            ellipsis={{ rows: 2, expandable: true }}
                          >
                            {text || '—'}
                          </Typography.Paragraph>
                        ),
                      },
                      {
                        title: t('createdAt', '创建时间'),
                        dataIndex: 'createdAt',
                        width: 180,
                      },
                      {
                        title: t('actions', '操作'),
                        width: 220,
                        render: (_, drama) => (
                          <Space>
                            <Button
                              type="link"
                              onClick={() => {
                                setDetail(drama);
                                void loadDetail(drama.id);
                              }}
                            >
                              {t('directory', '单集目录')}
                            </Button>
                            {access.canManageDramas && (
                              <>
                                <Button
                                  type="link"
                                  disabled={busy}
                                  onClick={() => openEditor(drama)}
                                >
                                  {t('edit', '编辑')}
                                </Button>
                                <Popconfirm
                                  title={t(
                                    'deleteDrama',
                                    '删除该剧及单集目录？',
                                  )}
                                  description={t(
                                    'deleteHint',
                                    '已发布资源须先从所有小程序下架。',
                                  )}
                                  onConfirm={() =>
                                    mutate(() => deleteDrama(drama.id), reload)
                                  }
                                >
                                  <Button type="link" danger disabled={busy}>
                                    {t('delete', '删除')}
                                  </Button>
                                </Popconfirm>
                              </>
                            )}
                          </Space>
                        ),
                      },
                    ]}
                  />
                </Space>
              ),
            },
            {
              key: 'miniapps',
              label: t('miniapps', '团队小程序分发'),
              children: (
                <Space orientation="vertical" style={{ width: '100%' }}>
                  {access.canManageMiniapps && (
                    <OfficialAuthorization onAuthorized={reload} />
                  )}
                  <Button loading={loading} onClick={() => void reload()}>
                    {t('reload', '刷新')}
                  </Button>
                  <Table<Miniapp>
                    rowKey="id"
                    loading={loading}
                    dataSource={apps}
                    scroll={{ x: 780 }}
                    columns={[
                      { title: t('appName', '小程序名称'), dataIndex: 'name' },
                      { title: 'AppID', dataIndex: 'appid' },
                      {
                        title: t('bindingType', '绑定方式'),
                        render: (_, app) => (
                          <Tag
                            color={
                              app.bindingType === 'OFFICIAL'
                                ? 'success'
                                : 'warning'
                            }
                          >
                            {app.bindingType === 'OFFICIAL'
                              ? t('official', '微信官方授权')
                              : app.bindingType === 'MANUAL'
                                ? t('manual', '手工绑定 · 非官方授权')
                                : `${t('unknownBinding', '未知绑定方式')} (${app.bindingType})`}
                          </Tag>
                        ),
                      },
                      {
                        title: t('authorizationStatus', '授权状态'),
                        render: (_, app) =>
                          app.bindingType === 'OFFICIAL' ? (
                            <Tag
                              color={canUseMiniapp(app) ? 'success' : 'error'}
                            >
                              {app.authStatus === 'AUTHORIZED'
                                ? t('authorized', '已授权')
                                : app.authStatus === 'REVOKED'
                                  ? t('revoked', '已取消授权，请重新官方授权')
                                  : t('unauthorized', '未授权，无法启用或发布')}
                            </Tag>
                          ) : (
                            t('historicalManual', '历史手工记录（非官方授权）')
                          ),
                      },
                      {
                        title: t('enabled', '启用状态'),
                        render: (_, app) => (
                          <Switch
                            checked={app.enabled}
                            disabled={
                              !access.canManageMiniapps ||
                              busy ||
                              !canUseMiniapp(app)
                            }
                            checkedChildren={t('enabled', '启用状态')}
                            unCheckedChildren={t('disabled', '已停用')}
                            onChange={(enabled) =>
                              void mutate(
                                () => enableMiniapp(app.id, enabled),
                                reload,
                              )
                            }
                          />
                        ),
                      },
                      {
                        title: t('actions', '操作'),
                        render: (_, app) => (
                          <Space>
                            <Button
                              type="link"
                              onClick={() => void loadReleases(app)}
                            >
                              {t('releases', '发布记录')}
                            </Button>
                            {access.canManageMiniapps && (
                              <Button
                                type="link"
                                disabled={busy || !canManageCode(app)}
                                onClick={() => setCodeAppId(app.id)}
                              >
                                {t('code.title', '体验版管理')}
                              </Button>
                            )}
                          </Space>
                        ),
                      },
                    ]}
                  />
                </Space>
              ),
            },
          ]}
        />
      </Space>
      <Modal
        open={Boolean(editor)}
        title={
          editor === 'new'
            ? t('create', '新建剧集')
            : t('editDrama', '编辑剧集')
        }
        confirmLoading={busy}
        onCancel={() => setEditor(undefined)}
        cancelButtonProps={{ disabled: busy }}
        closable={!busy}
        mask={{ closable: !busy }}
        keyboard={!busy}
        onOk={async () => {
          const values = await dramaForm.validateFields();
          const ok = await mutate(
            () =>
              saveDrama(
                { ...values, title: values.title.trim() },
                editor && editor !== 'new' ? editor.id : undefined,
              ),
            reload,
          );
          if (ok) setEditor(undefined);
        }}
      >
        <Form form={dramaForm} layout="vertical">
          <Form.Item
            name="title"
            label={t('dramaTitle', '剧名')}
            rules={[{ required: true, whitespace: true, max: 200 }]}
          >
            <Input maxLength={200} />
          </Form.Item>
          <Form.Item
            name="description"
            label={t('description', '简介')}
            rules={[{ max: 4000 }]}
          >
            <Input.TextArea maxLength={4000} showCount rows={5} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={Boolean(detail)}
        width={960}
        title={detail?.title}
        footer={null}
        onCancel={closeDetail}
        closable={!busy && !importing}
        mask={{ closable: !busy && !importing }}
        keyboard={!busy && !importing}
      >
        <Space orientation="vertical" style={{ width: '100%' }}>
          <Typography.Paragraph>
            {detail?.description || t('noDescription', '暂无简介')}
          </Typography.Paragraph>
          {detailFailed && (
            <Alert type="error" title={t('loadFailed', '加载失败，请重试')} />
          )}
          <Space>
            <Button
              loading={detailLoading}
              disabled={busy}
              onClick={() => detail && void loadDetail(detail.id)}
            >
              {t('reload', '刷新')}
            </Button>
            {access.canManageDramas && (
              <Button
                type="primary"
                disabled={detailLoading || detailFailed || busy}
                onClick={() => setImporting(true)}
              >
                {t('import', '导入单集')}
              </Button>
            )}
          </Space>
          <Table<Episode>
            rowKey="id"
            loading={detailLoading}
            dataSource={episodes}
            scroll={{ x: 640 }}
            pagination={{ pageSize: 10 }}
            rowSelection={
              access.canPublishDramas
                ? {
                    selectedRowKeys: selected,
                    onChange: setSelected,
                    getCheckboxProps: () => ({ disabled: busy }),
                  }
                : undefined
            }
            columns={[
              {
                title: t('episodeNo', '剧库集号'),
                dataIndex: 'episodeNo',
                sorter: (a, b) => a.episodeNo - b.episodeNo,
              },
              { title: t('episodeTitle', '单集标题'), dataIndex: 'title' },
              {
                title: t('source', '来源'),
                render: (_, episode) => (
                  <Space orientation="vertical">
                    <Tag>
                      {episode.sourceType === 'LOCAL_UPLOAD'
                        ? t('local', '本地视频')
                        : episode.sourceType === 'PROJECT_VERSION'
                          ? t('projectSource', '项目成片')
                          : episode.sourceType}
                    </Tag>
                    {episode.sourceProjectId && (
                      <Typography.Text type="secondary">
                        {t('project', '项目')} #{episode.sourceProjectId} /{' '}
                        {t('version', '成片版本')} #{episode.sourceVersionId}
                      </Typography.Text>
                    )}
                  </Space>
                ),
              },
              {
                title: t('fileSize', '文件大小'),
                render: (_, episode) =>
                  episode.fileSize
                    ? `${(episode.fileSize / 1024 / 1024).toFixed(1)} MB`
                    : '—',
              },
              {
                title: t('actions', '操作'),
                render: (_, episode) =>
                  access.canManageDramas && (
                    <Popconfirm
                      title={t('deleteEpisode', '删除该单集？')}
                      description={t(
                        'deleteHint',
                        '已发布资源须先从所有小程序下架。',
                      )}
                      onConfirm={() => {
                        if (!detail) return;
                        const id = detail.id;
                        return mutate(
                          () => deleteEpisode(id, episode.id),
                          () => loadDetail(id),
                        );
                      }}
                    >
                      <Button danger type="link" disabled={busy}>
                        {t('delete', '删除')}
                      </Button>
                    </Popconfirm>
                  ),
              },
            ]}
          />
          {access.canPublishDramas && (
            <>
              <Alert
                type="info"
                title={t(
                  'publishHint',
                  '选择单集并指定目标小程序。重新上架会替换该小程序的当前发布目录，不影响其他小程序。',
                )}
              />
              <Space wrap>
                <Select
                  style={{ minWidth: 240 }}
                  aria-label={t('target', '目标小程序')}
                  placeholder={t('target', '目标小程序')}
                  value={publishAppId}
                  disabled={busy}
                  onChange={setPublishAppId}
                  options={apps.map((app) => ({
                    value: app.id,
                    label: `${app.name} (${app.appid})`,
                    disabled: !app.enabled || !canUseMiniapp(app),
                  }))}
                />
                <Popconfirm
                  title={t(
                    'publishConfirm',
                    '确认将所选单集上架至目标小程序？',
                  )}
                  description={t(
                    'publishSnapshot',
                    '剧名、简介及单集将生成新的发布快照。',
                  )}
                  disabled={
                    !publishAppId ||
                    selected.length === 0 ||
                    selected.length > 500
                  }
                  onConfirm={() => {
                    if (
                      !publishAppId ||
                      !detail ||
                      !apps.some(
                        (app) =>
                          app.id === publishAppId &&
                          app.enabled &&
                          canUseMiniapp(app),
                      )
                    )
                      return;
                    const appId = publishAppId;
                    const dramaId = detail.id;
                    return mutate(
                      () => publishDrama(appId, dramaId, selected.map(Number)),
                      async () => {
                        setSelected([]);
                      },
                    );
                  }}
                >
                  <Button
                    type="primary"
                    loading={busy}
                    disabled={
                      !publishAppId ||
                      !apps.some(
                        (app) =>
                          app.id === publishAppId &&
                          app.enabled &&
                          canUseMiniapp(app),
                      ) ||
                      selected.length === 0 ||
                      selected.length > 500 ||
                      detailLoading ||
                      detailFailed
                    }
                  >
                    {t('publish', '上架所选单集')} ({selected.length}/500)
                  </Button>
                </Popconfirm>
              </Space>
            </>
          )}
        </Space>
      </Modal>
      {access.canManageMiniapps &&
        tenantId != null &&
        apps
          .filter((app) => app.id === codeAppId && canManageCode(app))
          .map((app) => (
            <TrialCode
              key={`${tenantId}:${app.id}`}
              tenantId={tenantId}
              app={app}
              onClose={() => setCodeAppId(undefined)}
            />
          ))}
      {importing && detail && (
        <ImportEpisode
          dramaId={detail.id}
          nextNo={
            Math.max(0, ...episodes.map((episode) => episode.episodeNo)) + 1
          }
          onClose={() => setImporting(false)}
          onImported={() => loadDetail(detail.id)}
        />
      )}
      <Modal
        open={Boolean(releaseApp)}
        width={900}
        title={`${releaseApp?.name || ''} · ${t('releases', '发布记录')}`}
        footer={null}
        onCancel={() => {
          releaseGeneration.current++;
          setReleaseApp(undefined);
        }}
        closable={!busy}
        mask={{ closable: !busy }}
        keyboard={!busy}
      >
        <Space orientation="vertical" style={{ width: '100%' }}>
          {releaseApp?.bindingType === 'MANUAL' && manualNotice}
          {releaseFailed && (
            <Alert type="error" title={t('loadFailed', '加载失败，请重试')} />
          )}
          <Button
            loading={releaseLoading}
            onClick={() => releaseApp && void loadReleases(releaseApp)}
          >
            {t('reload', '刷新')}
          </Button>
          <Table<Release>
            rowKey="id"
            loading={releaseLoading}
            dataSource={releases}
            scroll={{ x: 650 }}
            columns={[
              { title: t('dramaTitle', '剧名'), dataIndex: 'title' },
              {
                title: t('description', '简介'),
                dataIndex: 'description',
                ellipsis: true,
              },
              { title: t('createdAt', '创建时间'), dataIndex: 'createdAt' },
              {
                title: t('status', '状态'),
                render: (_, release) => (
                  <Tag color={release.published ? 'success' : 'default'}>
                    {release.published
                      ? t('published', '已上架')
                      : t('unpublished', '已下架')}
                  </Tag>
                ),
              },
              {
                title: t('actions', '操作'),
                render: (_, release) =>
                  access.canPublishDramas &&
                  release.published && (
                    <Popconfirm
                      title={t('unpublishConfirm', '确认从此小程序下架该剧？')}
                      onConfirm={() => {
                        if (!releaseApp) return;
                        const app = releaseApp;
                        return mutate(
                          () => unpublishDrama(app.id, release.dramaId),
                          () => loadReleases(app),
                        );
                      }}
                    >
                      <Button type="link" danger disabled={busy}>
                        {t('unpublish', '下架')}
                      </Button>
                    </Popconfirm>
                  ),
              },
            ]}
          />
        </Space>
      </Modal>
    </PageContainer>
  );
}

export default function DramaLibraryPage() {
  const { initialState } = useModel('@@initialState');
  const access = useAccess();
  if (!access.canViewDramas) return null;
  return (
    <DramaLibraryContent
      key={initialState?.selectedTenant?.tenant.id ?? 'none'}
    />
  );
}
