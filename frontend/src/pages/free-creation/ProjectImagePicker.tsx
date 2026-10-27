import { FolderOpenOutlined } from '@ant-design/icons';
import { Modal, Pagination, Spin } from 'antd';
import { useEffect, useState } from 'react';
import { queryProjects } from '@/services/account-team/project';
import type { Project } from '@/services/account-team/types';
import {
  type AssetLibraryItem,
  queryProjectAssetLibrary,
} from '@/pages/asset-library/service';
import type { ReferenceDraft } from './ReferenceFiles';
import styles from './index.module.css';

export default function ProjectImagePicker({
  references,
  maxCount,
  disabled,
  onConfirm,
  onCancel,
}: {
  references: ReferenceDraft[];
  maxCount?: number;
  disabled?: boolean;
  onConfirm: (references: ReferenceDraft[]) => void;
  onCancel: () => void;
}) {
  const [project, setProject] = useState<Pick<Project, 'id' | 'name'>>();
  const [projects, setProjects] = useState<Project[]>([]);
  const [images, setImages] = useState<AssetLibraryItem[]>([]);
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  const [selected, setSelected] = useState<ReferenceDraft[]>([]);
  const remaining =
    maxCount === undefined
      ? undefined
      : Math.max(0, maxCount - references.length);
  useEffect(() => {
    const abort = new AbortController();
    setLoading(true);
    setError('');
    const load = async () => {
      if (project) {
        const response = await queryProjectAssetLibrary(
          project.id,
          {
            mediaType: 'IMAGE',
            keyword: keyword.trim() || undefined,
            current: page,
            pageSize: 12,
          },
          abort.signal,
        );
        if (abort.signal.aborted) return;
        if (!response.success || !response.data?.items)
          throw new Error(response.errorMessage || '项目图片加载失败');
        setImages(response.data.items.data);
        setTotal(response.data.items.total);
      } else {
        const response = await queryProjects(
          { keyword: keyword.trim() || undefined, current: page, pageSize: 12 },
          abort.signal,
        );
        if (abort.signal.aborted) return;
        if (!response.success || !response.data)
          throw new Error(response.errorMessage || '项目加载失败');
        setProjects(response.data.data);
        setTotal(response.data.total);
      }
    };
    void load()
      .catch((failure) => {
        if (!abort.signal.aborted)
          setError(
            failure instanceof Error ? failure.message : '加载失败，请重试',
          );
      })
      .finally(() => {
        if (!abort.signal.aborted) setLoading(false);
      });
    return () => abort.abort();
  }, [project, keyword, page, revision]);
  const navigate = (next?: Pick<Project, 'id' | 'name'>) => {
    setProject(next);
    setKeyword('');
    setPage(1);
  };
  return (
    <Modal
      open
      title="引用项目资产"
      width={720}
      onCancel={onCancel}
      okText={`引用选中图片${selected.length ? `（${selected.length}）` : ''}`}
      cancelText="取消"
      onOk={() => onConfirm(selected)}
      okButtonProps={{
        disabled:
          disabled ||
          !selected.length ||
          (remaining !== undefined && selected.length > remaining),
      }}
    >
      <div className={styles.assetPickerToolbar}>
        {project ? (
          <button type="button" onClick={() => navigate()}>
            返回项目
          </button>
        ) : null}
        <strong>{project?.name || '选择项目'}</strong>
        <input
          aria-label={project ? '搜索项目图片' : '搜索项目'}
          placeholder={project ? '搜索资产名称' : '搜索项目名称'}
          value={keyword}
          onChange={(event) => {
            setKeyword(event.target.value);
            setPage(1);
          }}
        />
      </div>
      <p className={styles.help}>
        已选 {selected.length} 张
        {remaining !== undefined
          ? ` · 还可引用 ${Math.max(0, remaining - selected.length)} 张`
          : ''}
        ，仅展示有权访问的项目图片。
      </p>
      {loading ? (
        <div className={styles.assetPickerEmpty}>
          <Spin size="small" /> 正在加载…
        </div>
      ) : error ? (
        <div className={styles.assetPickerEmpty}>
          <p role="alert">{error}</p>
          <button
            type="button"
            onClick={() => setRevision((value) => value + 1)}
          >
            重新加载
          </button>
        </div>
      ) : (
        <>
          <div className={styles.assetPickerGrid}>
            {project
              ? images.map((image) => {
                  const id = `project-${project.id}-image-${image.id}`;
                  const checked = selected.some((item) => item.id === id);
                  const alreadyAdded = references.some(
                    (item) =>
                      item.projectImage?.projectId === project.id &&
                      item.projectImage.resultId === image.id,
                  );
                  return (
                    <label key={image.id} className={styles.assetPickerImage}>
                      <img src={image.thumbnailUrl} alt="" loading="lazy" />
                      <span>
                        <input
                          type="checkbox"
                          aria-label={image.name}
                          checked={checked || alreadyAdded}
                          disabled={
                            disabled ||
                            alreadyAdded ||
                            (!checked &&
                              remaining !== undefined &&
                              selected.length >= remaining)
                          }
                          onChange={() =>
                            setSelected((current) =>
                              checked
                                ? current.filter((item) => item.id !== id)
                                : [
                                    ...current,
                                    {
                                      id,
                                      kind: 'image',
                                      name: image.name,
                                      url: image.thumbnailUrl,
                                      projectImage: {
                                        projectId: project.id,
                                        resultId: image.id,
                                      },
                                      status: 'complete',
                                      percent: 100,
                                    },
                                  ],
                            )
                          }
                        />
                        {image.name}
                        {alreadyAdded ? '（已引用）' : ''}
                      </span>
                    </label>
                  );
                })
              : projects.map((item) => (
                  <button
                    type="button"
                    key={item.id}
                    className={styles.assetPickerProject}
                    onClick={() => navigate({ id: item.id, name: item.name })}
                  >
                    <FolderOpenOutlined aria-hidden="true" />
                    {item.name}
                  </button>
                ))}
          </div>
          {!total ? (
            <p className={styles.assetPickerEmpty}>
              {project
                ? '暂无可引用的图片，请先在项目中添加图片资产'
                : '暂无可访问的项目'}
            </p>
          ) : null}
          <Pagination
            current={page}
            pageSize={12}
            total={total}
            onChange={setPage}
            showSizeChanger={false}
            hideOnSinglePage
          />
        </>
      )}
    </Modal>
  );
}
