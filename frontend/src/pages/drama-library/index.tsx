import {
  ArrowLeftOutlined,
  ClockCircleOutlined,
  EditOutlined,
  EllipsisOutlined,
  PlaySquareOutlined,
  PlusOutlined,
  ReloadOutlined,
  RightOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { PageContainer } from '@ant-design/pro-components';
import { useAccess, useIntl, useModel } from '@umijs/max';
import {
  Alert,
  App,
  Button,
  Dropdown,
  Empty,
  Form,
  Input,
  Modal,
  Pagination,
  Popconfirm,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import dayjs from 'dayjs';
import type { Key } from 'react';
import { useCallback, useEffect, useRef, useState } from 'react';
import ImportEpisode from './ImportEpisode';
import { useStyles } from './index.style';
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
  const { styles } = useStyles();
  const { initialState } = useModel('@@initialState');
  const tenantId = initialState?.selectedTenant?.tenant.id;
  const [codeAppId, setCodeAppId] = useState<number>();
  const intl = useIntl();
  const t = (key: string, text: string) =>
    intl.formatMessage({ id: `drama.${key}`, defaultMessage: text });
  const { message, modal } = App.useApp();
  const [dramas, setDramas] = useState<Drama[]>([]);
  const [apps, setApps] = useState<Miniapp[]>([]);
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [sort, setSort] = useState('newest');
  const [page, setPage] = useState(1);
  const [tab, setTab] = useState('library');
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
  const filteredDramas = dramas
    .filter((drama) =>
      `${drama.title} ${drama.description || ''}`
        .toLowerCase()
        .includes(keyword.trim().toLowerCase()),
    )
    .sort((a, b) => {
      const difference =
        dayjs(b.createdAt).valueOf() - dayjs(a.createdAt).valueOf();
      return sort === 'newest' ? difference : -difference;
    });
  const currentPage = Math.min(
    page,
    Math.max(1, Math.ceil(filteredDramas.length / 12)),
  );
  const openDetail = (drama: Drama) => {
    setDetail(drama);
    void loadDetail(drama.id);
  };
  const confirmDelete = (drama: Drama) => {
    modal.confirm({
      title: t('deleteDrama', '删除该剧及单集目录？'),
      content: t('deleteHint', '已发布资源须先从所有小程序下架。'),
      okButtonProps: { danger: true },
      onOk: async () => {
        const ok = await mutate(() => deleteDrama(drama.id), reload);
        if (!ok) throw new Error('删除失败');
      },
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
    <PageContainer pageHeaderRender={false} className={styles.page}>
      <div hidden={Boolean(detail)}>
        <header className={styles.header}>
          <div>
            <h1>{t('title', '剧集管理')}</h1>
            <p>剧集、单集与小程序分发</p>
          </div>
          {access.canManageDramas && tab === 'library' && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => openEditor()}
            >
              {t('create', '新建剧集')}
            </Button>
          )}
        </header>
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
            className={styles.tabs}
            activeKey={tab}
            onChange={setTab}
            items={[
              {
                key: 'library',
                label: t('library', '剧库'),
                children: (
                  <>
                    <div className={styles.toolbar}>
                      <Input
                        aria-label={t('searchDescription', '搜索剧名或简介')}
                        placeholder={t('searchDescription', '搜索剧名或简介')}
                        prefix={<SearchOutlined />}
                        value={keyword}
                        onChange={(event) => {
                          setKeyword(event.target.value);
                          setPage(1);
                        }}
                        allowClear
                      />
                      <Space size={12} wrap>
                        <Typography.Text
                          type="secondary"
                          style={{ fontSize: 12 }}
                        >
                          共 {filteredDramas.length} 部剧集
                        </Typography.Text>
                        <Select
                          aria-label="剧集排序"
                          value={sort}
                          onChange={(value) => {
                            setSort(value);
                            setPage(1);
                          }}
                          options={[
                            { value: 'newest', label: '最近创建' },
                            { value: 'oldest', label: '最早创建' },
                          ]}
                        />
                        <Button
                          type="text"
                          aria-label={t('reload', '刷新')}
                          icon={<ReloadOutlined spin={loading} />}
                          disabled={loading}
                          onClick={() => void reload()}
                        />
                      </Space>
                    </div>
                    <Spin spinning={loading}>
                      {!loading && !filteredDramas.length ? (
                        <div className={styles.empty}>
                          <Empty
                            image={Empty.PRESENTED_IMAGE_SIMPLE}
                            description={
                              keyword
                                ? '暂无匹配剧集'
                                : t(
                                    'emptyLibrary',
                                    '暂无剧集。新建剧集后，可从本地视频或项目成片导入单集。',
                                  )
                            }
                          />
                          {keyword && (
                            <div style={{ textAlign: 'center' }}>
                              <Button
                                type="link"
                                onClick={() => {
                                  setKeyword('');
                                  setPage(1);
                                }}
                              >
                                重置搜索
                              </Button>
                            </div>
                          )}
                        </div>
                      ) : (
                        <div className={styles.grid}>
                          {filteredDramas
                            .slice((currentPage - 1) * 12, currentPage * 12)
                            .map((drama) => (
                              <article
                                key={drama.id}
                                className={styles.card}
                                aria-label={`剧集：${drama.title}`}
                              >
                                {access.canManageDramas && (
                                  <Dropdown
                                    trigger={['click']}
                                    menu={{
                                      items: [
                                        {
                                          key: 'edit',
                                          label: t('editDrama', '编辑剧集'),
                                        },
                                        {
                                          key: 'delete',
                                          label: t('delete', '删除'),
                                          danger: true,
                                        },
                                      ],
                                      onClick: ({ key }) => {
                                        if (key === 'edit') openEditor(drama);
                                        else confirmDelete(drama);
                                      },
                                    }}
                                  >
                                    <Button
                                      type="text"
                                      className={styles.more}
                                      disabled={busy}
                                      icon={<EllipsisOutlined />}
                                      aria-label={`${drama.title}更多操作`}
                                    />
                                  </Dropdown>
                                )}
                                <header className={styles.cardTop}>
                                  <div className={styles.identity}>
                                    <PlaySquareOutlined />
                                  </div>
                                  <div className={styles.cardTitle}>
                                    <h2>
                                      <button
                                        type="button"
                                        title={drama.title}
                                        onClick={() => openDetail(drama)}
                                      >
                                        {drama.title}
                                      </button>
                                    </h2>
                                    <span>剧集目录</span>
                                  </div>
                                </header>
                                <p
                                  className={styles.description}
                                  title={drama.description || undefined}
                                >
                                  {drama.description ||
                                    t('noDescription', '暂无简介')}
                                </p>
                                <footer className={styles.cardBottom}>
                                  <time dateTime={drama.createdAt}>
                                    <ClockCircleOutlined
                                      style={{ marginRight: 5 }}
                                    />
                                    {dayjs(drama.createdAt).isValid()
                                      ? dayjs(drama.createdAt).format(
                                          'YYYY-MM-DD',
                                        )
                                      : '—'}
                                  </time>
                                  <Button
                                    size="small"
                                    onClick={() => openDetail(drama)}
                                  >
                                    {t('directory', '单集目录')}
                                    <RightOutlined aria-hidden />
                                  </Button>
                                </footer>
                              </article>
                            ))}
                          {access.canManageDramas && !keyword && (
                            <button
                              type="button"
                              className={styles.add}
                              onClick={() => openEditor()}
                            >
                              <span>
                                <PlusOutlined />
                              </span>
                              {t('create', '新建剧集')}
                            </button>
                          )}
                        </div>
                      )}
                    </Spin>
                    <footer className={styles.pagination}>
                      <span>共 {filteredDramas.length} 部剧集</span>
                      <Pagination
                        size="small"
                        current={currentPage}
                        total={filteredDramas.length}
                        pageSize={12}
                        hideOnSinglePage
                        showSizeChanger={false}
                        onChange={setPage}
                      />
                    </footer>
                  </>
                ),
              },
              {
                key: 'miniapps',
                label: t('miniapps', '团队小程序分发'),
                children: (
                  <div className={styles.miniapps}>
                    {access.canManageMiniapps && (
                      <OfficialAuthorization onAuthorized={reload} />
                    )}
                    <div className={styles.surface}>
                      <header className={styles.surfaceHeader}>
                        <h2>
                          {t('miniapps', '团队小程序分发')}{' '}
                          <span className={styles.count}>{apps.length}</span>
                        </h2>
                        <Button
                          type="text"
                          aria-label={t('reload', '刷新')}
                          icon={<ReloadOutlined spin={loading} />}
                          disabled={loading}
                          onClick={() => void reload()}
                        />
                      </header>
                      <Table<Miniapp>
                        rowKey="id"
                        loading={loading}
                        dataSource={apps}
                        scroll={{ x: 780 }}
                        columns={[
                          {
                            title: t('appName', '小程序名称'),
                            render: (_, app) => (
                              <div className={styles.appIdentity}>
                                <span className={styles.appIcon}>
                                  <PlaySquareOutlined aria-hidden />
                                </span>
                                <div>
                                  <strong>{app.name}</strong>
                                  <code>{app.appid}</code>
                                </div>
                              </div>
                            ),
                          },
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
                                  color={
                                    canUseMiniapp(app) ? 'success' : 'error'
                                  }
                                >
                                  {app.authStatus === 'AUTHORIZED'
                                    ? t('authorized', '已授权')
                                    : app.authStatus === 'REVOKED'
                                      ? t(
                                          'revoked',
                                          '已取消授权，请重新官方授权',
                                        )
                                      : t(
                                          'unauthorized',
                                          '未授权，无法启用或发布',
                                        )}
                                </Tag>
                              ) : (
                                t(
                                  'historicalManual',
                                  '历史手工记录（非官方授权）',
                                )
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
                    </div>
                  </div>
                ),
              },
            ]}
          />
        </Space>
      </div>
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
          const ok = await mutate(async () => {
            const saved = await saveDrama(
              { ...values, title: values.title.trim() },
              editor && editor !== 'new' ? editor.id : undefined,
            );
            if (mounted.current)
              setDetail((current) =>
                current?.id === saved.id ? saved : current,
              );
          }, reload);
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
      {detail && (
        <section aria-label={`${detail.title}的单集目录`}>
          <Button
            type="text"
            className={styles.back}
            icon={<ArrowLeftOutlined aria-hidden />}
            disabled={busy || importing}
            onClick={closeDetail}
          >
            全部剧集
          </Button>
          <header className={styles.detailHeader}>
            <div className={styles.detailTitle}>
              <div className={styles.identity}>
                <PlaySquareOutlined />
              </div>
              <div>
                <Space align="center" wrap>
                  <h1>{detail.title}</h1>
                  {!detailLoading && !detailFailed && (
                    <span className={styles.count}>{episodes.length} 集</span>
                  )}
                </Space>
                <p>{detail.description || t('noDescription', '暂无简介')}</p>
              </div>
            </div>
            <Space wrap>
              {access.canManageDramas && (
                <>
                  <Button
                    icon={<EditOutlined />}
                    disabled={busy}
                    onClick={() => openEditor(detail)}
                  >
                    {t('editDrama', '编辑剧集')}
                  </Button>
                  <Button
                    type="primary"
                    icon={<PlusOutlined />}
                    disabled={detailLoading || detailFailed || busy}
                    onClick={() => setImporting(true)}
                  >
                    {t('import', '导入单集')}
                  </Button>
                </>
              )}
            </Space>
          </header>
          {detailFailed && (
            <Alert
              type="error"
              showIcon
              title={t('loadFailed', '加载失败，请重试')}
              style={{ marginBottom: 20 }}
              action={
                <Button onClick={() => void loadDetail(detail.id)}>
                  {t('reload', '刷新')}
                </Button>
              }
            />
          )}
          <div className={styles.surface}>
            <header className={styles.surfaceHeader}>
              <h2>
                {t('directory', '单集目录')}{' '}
                {!detailLoading && (
                  <span className={styles.count}>{episodes.length}</span>
                )}
              </h2>
              <Button
                type="text"
                icon={<ReloadOutlined spin={detailLoading} />}
                aria-label="刷新单集"
                disabled={busy || detailLoading}
                onClick={() => void loadDetail(detail.id)}
              />
            </header>
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
                  width: 110,
                  render: (value) => (
                    <span className={styles.number}>
                      {String(value).padStart(2, '0')}
                    </span>
                  ),
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
          </div>
          {access.canPublishDramas && (
            <footer className={styles.publish}>
              <div className={styles.publishNote}>
                <span role="status" aria-label="已选单集">
                  已选 <strong>{selected.length}</strong> 集
                </span>
                <p>重新上架将替换目标小程序的当前目录</p>
              </div>
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
            </footer>
          )}
        </section>
      )}
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
