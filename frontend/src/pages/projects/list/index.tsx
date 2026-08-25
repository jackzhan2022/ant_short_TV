import {
  EditOutlined,
  FolderOpenOutlined,
  MoreOutlined,
  PlusOutlined,
  ProfileOutlined,
} from '@ant-design/icons';
import {
  ModalForm,
  PageContainer,
  ProFormDatePicker,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { history, useAccess } from '@umijs/max';
import { App, Button, Empty, Tag } from 'antd';
import { useEffect, useState } from 'react';
import styles from './index.module.css';
import { getCurrentTenantId } from '@/services/account-team/auth';
import type {
  Project,
  ProjectStatus,
  TenantMember,
} from '@/services/account-team/types';
import type { ProjectFormValues } from '@/services/account-team/project';
import {
  queryProjects,
  queryTenantMembers,
  updateProject,
  updateProjectStatus,
} from './service';

const statusText: Record<ProjectStatus, string> = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '进行中',
  PAUSED: '已暂停',
  COMPLETED: '已完成',
  ARCHIVED: '已归档',
};

const statusColor: Record<ProjectStatus, string> = {
  NOT_STARTED: 'default',
  IN_PROGRESS: 'processing',
  PAUSED: 'warning',
  COMPLETED: 'success',
  ARCHIVED: 'default',
};

const ProjectEditor = ({
  project,
  members,
  onDone,
}: {
  project: Project;
  members: TenantMember[];
  onDone: () => void;
}) => {
  const { message } = App.useApp();
  const memberOptions = members.map((member) => ({
    label: member.nickname || member.mobile || String(member.userId),
    value: member.userId,
  }));

  return (
    <ModalForm<ProjectFormValues>
      title="编辑项目"
      trigger={
        <Button type="link" icon={<EditOutlined />}>
          编辑
        </Button>
      }
      modalProps={{ destroyOnHidden: true }}
      initialValues={{
        name: project?.name,
        code: project?.code,
        description: project?.description || undefined,
        coverUrl: project?.coverUrl || undefined,
        ownerId: project?.ownerId,
        startDate: project?.startDate || undefined,
        endDate: project?.endDate || undefined,
      }}
      onFinish={async (values) => {
        const payload = {
          ...values,
          code: values.code?.trim().toUpperCase(),
        };
        await updateProject(project.id, payload);
        message.success('项目已更新');
        onDone();
        return true;
      }}
    >
      <ProFormText
        name="name"
        label="项目名称"
        rules={[{ required: true, message: '请输入项目名称' }]}
      />
      <ProFormSelect
        name="ownerId"
        label="负责人"
        options={memberOptions}
        rules={[{ required: true, message: '请选择负责人' }]}
      />
      <ProFormTextArea name="description" label="项目描述" />
      <ProFormText name="coverUrl" label="封面地址" />
      <ProFormDatePicker name="startDate" label="开始时间" />
      <ProFormDatePicker name="endDate" label="结束时间" />
    </ModalForm>
  );
};

const formatCreatedAt = (createdAt?: string | null) => {
  if (!createdAt) return '创建时间未知';
  return `创建于 ${createdAt.slice(0, 10)}`;
};

const ProjectCard = ({
  project,
  members,
  onDone,
}: {
  project: Project;
  members: TenantMember[];
  onDone: () => void;
}) => {
  const { message } = App.useApp();
  const openProject = () =>
    history.push(`/projects/${project.id}/production-workbench/script`);

  return (
    <article className={styles.card}>
      <button className={styles.coverButton} type="button" onClick={openProject}>
        {project.coverUrl ? (
          <img
            className={styles.cover}
            src={project.coverUrl}
            alt={`${project.name}封面`}
          />
        ) : (
          <div
            className={styles.coverPlaceholder}
            role="img"
            aria-label={`${project.name}封面`}
          >
            <span>{project.name.slice(0, 1)}</span>
          </div>
        )}
      </button>
      <div className={styles.cardBody}>
        <div className={styles.cardHeading}>
          <button className={styles.titleButton} type="button" onClick={openProject}>
            {project.name}
          </button>
          <details className={styles.moreMenu}>
            <summary aria-label={`${project.name}更多操作`}>
              <MoreOutlined />
            </summary>
            <div className={styles.menuPanel}>
              {project.capabilities.canEdit && (
                <>
                  <ProjectEditor project={project} members={members} onDone={onDone} />
                  <Button
                    type="text"
                    disabled={project.status === 'ARCHIVED'}
                    onClick={async () => {
                      const nextStatus =
                        project.status === 'NOT_STARTED' ? 'IN_PROGRESS' : 'ARCHIVED';
                      await updateProjectStatus(project.id, nextStatus);
                      message.success('项目状态已更新');
                      onDone();
                    }}
                  >
                    {project.status === 'NOT_STARTED' ? '启动' : '归档'}
                  </Button>
                </>
              )}
              <span className={styles.projectCode}>{project.code}</span>
            </div>
          </details>
        </div>
        <div className={styles.cardTags}>
          <Tag color={statusColor[project.status]}>{statusText[project.status]}</Tag>
          <span className={styles.projectType}>短剧项目</span>
        </div>
        <div className={styles.metadata}>
          <span>{formatCreatedAt(project.createdAt)}</span>
          <span>{project.memberCount} 位成员</span>
        </div>
        {project.ownerName && (
          <div className={styles.owner}>负责人：{project.ownerName}</div>
        )}
        <div className={styles.actions}>
          <Button
            type="link"
            icon={<FolderOpenOutlined />}
            onClick={openProject}
          >
            进入
          </Button>
          <Button
            type="link"
            icon={<ProfileOutlined />}
            onClick={() =>
              history.push(`/projects/${project.id}/production-workbench`)
            }
          >
            进度
          </Button>
        </div>
      </div>
    </article>
  );
};

const ProjectList = () => {
  const tenantId = getCurrentTenantId();
  const access = useAccess();
  const [members, setMembers] = useState<TenantMember[]>([]);
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(false);

  const loadOptions = async () => {
    const memberResponse = tenantId
      ? await queryTenantMembers(tenantId)
      : { data: [] };
    setMembers(memberResponse.data as TenantMember[]);
  };

  useEffect(() => {
    if (tenantId) {
      loadOptions();
    }
  }, [tenantId]);

  const loadProjects = async () => {
    setLoading(true);
    try {
      const response = await queryProjects();
      setProjects(response.data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (tenantId) loadProjects();
  }, [tenantId]);

  if (!tenantId) {
    return (
      <PageContainer>
        <Empty description="请先在我的团队中选择当前创作团队" />
      </PageContainer>
    );
  }

  return (
    <PageContainer>
      <div className={styles.toolbar}>
        <div>
          <h1 className={styles.title}>项目列表</h1>
          <span className={styles.count}>共 {projects.length} 个项目</span>
        </div>
        {access.canCreateProject && (
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => history.push('/short-drama-creation')}
          >
            创建项目
          </Button>
        )}
      </div>
      {loading ? (
        <div className={styles.loading}>加载项目中...</div>
      ) : projects.length ? (
        <div className={styles.grid}>
          {projects.map((project) => (
            <ProjectCard
              key={project.id}
              project={project}
              members={members}
              onDone={loadProjects}
            />
          ))}
        </div>
      ) : (
        <Empty description="暂无项目" />
      )}
    </PageContainer>
  );
};

export default ProjectList;
