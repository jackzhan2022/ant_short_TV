import { EditOutlined } from '@ant-design/icons';
import {
  ModalForm,
  ProFormDatePicker,
  ProFormSelect,
  ProFormText,
  ProFormTextArea,
} from '@ant-design/pro-components';
import { App, Button } from 'antd';
import { useEffect, useRef, useState } from 'react';
import type { ProjectFormValues } from '@/services/account-team/project';
import type { Project, TenantMember } from '@/services/account-team/types';
import type { MediaUploadHandle } from '@/services/mediaUpload';
import ProjectCoverPicker, {
  type ProjectCoverDraft,
} from './ProjectCoverPicker';
import {
  bindProjectCoverUpload,
  startProjectCoverUpload,
  updateProject,
} from './service';

const emptyDraft: ProjectCoverDraft = { removed: false, ready: false };
export default function ProjectEditor({
  project,
  members,
  onDone,
  onOpen,
}: {
  project: Project;
  members: TenantMember[];
  onDone: () => void;
  onOpen: () => void;
}) {
  const { message } = App.useApp();
  const [draft, setDraft] = useState<ProjectCoverDraft>(emptyDraft);
  const [saving, setSaving] = useState(false);
  const submitting = useRef(false);
  const mounted = useRef(true);
  const upload = useRef<
    | {
        file: File;
        handle: MediaUploadHandle;
        sessionToken?: string;
        failedAttempt: boolean;
      }
    | undefined
  >(undefined);
  const clearUpload = () => {
    if (upload.current && !upload.current.sessionToken)
      upload.current.handle.cancel();
    upload.current = undefined;
  };
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      clearUpload();
    };
  }, []);
  const changeDraft = (next: ProjectCoverDraft) => {
    if (draft.file !== next.file) clearUpload();
    setDraft(next);
  };

  return (
    <ModalForm<ProjectFormValues>
      title="编辑项目"
      trigger={
        <Button
          aria-label="编辑"
          type="link"
          icon={<EditOutlined />}
          onClick={onOpen}
        >
          编辑
        </Button>
      }
      modalProps={{
        destroyOnHidden: true,
        closable: !saving,
        maskClosable: !saving,
        keyboard: !saving,
        style: { top: 32 },
        styles: {
          body: { maxHeight: 'calc(100dvh - 220px)', overflowY: 'auto' },
        },
      }}
      submitter={{
        resetButtonProps: { disabled: saving },
        submitButtonProps: {
          disabled: saving || (!!draft.file && !draft.ready),
          loading: saving,
        },
      }}
      onOpenChange={(open) => {
        if (!open) {
          clearUpload();
          setDraft(emptyDraft);
        }
      }}
      initialValues={{
        name: project.name,
        code: project.code,
        description: project.description || undefined,
        ownerId: project.ownerId,
        startDate: project.startDate || undefined,
        endDate: project.endDate || undefined,
      }}
      onFinish={async (values) => {
        if (submitting.current || (draft.file && !draft.ready)) return false;
        submitting.current = true;
        setSaving(true);
        let fieldsSaved = false;
        try {
          let sessionToken: string | undefined;
          if (draft.file) {
            if (!upload.current || upload.current.file !== draft.file) {
              const handle = await startProjectCoverUpload(
                project.id,
                draft.file,
              );
              if (!mounted.current) {
                handle.cancel();
                return false;
              }
              upload.current = {
                file: draft.file,
                handle,
                failedAttempt: false,
              };
            }
            const current = upload.current;
            if (!current.sessionToken) {
              try {
                const verified = await (current.failedAttempt
                  ? current.handle.retry()
                  : current.handle.attempt);
                current.sessionToken = verified.sessionToken;
                current.failedAttempt = false;
              } catch (error) {
                current.failedAttempt = true;
                throw error;
              }
            }

            sessionToken = current.sessionToken;
          }
          if (!mounted.current) return false;
          const {
            coverUrl: _url,
            coverSource: _source,
            clearCover: _clear,
            ...fields
          } = values;
          await updateProject(project.id, {
            aspectRatio: project.aspectRatio || undefined,
            videoResolution: project.videoResolution || undefined,
            videoGenerateAudio: project.videoGenerateAudio ?? undefined,
            videoWatermark: project.videoWatermark ?? undefined,
            fileFormat: project.fileFormat || undefined,
            scriptType: project.scriptType || undefined,
            breakdownStrength: project.breakdownStrength || undefined,
            visualStyle: project.visualStyle || undefined,
            initialScriptContent: project.initialScriptContent || undefined,
            ...fields,
            code: fields.code?.trim().toUpperCase(),
            ...(draft.removed ? { clearCover: true } : {}),
          });
          fieldsSaved = true;
          let coverFailed = false;
          if (sessionToken) {
            const response = await bindProjectCoverUpload(
              project.id,
              sessionToken,
            );
            coverFailed = response.data.status === 'FAILED';
          }
          if (!mounted.current) return false;
          if (coverFailed)
            message.warning(
              '项目信息已保存，但封面处理失败，可在项目列表重试封面。',
            );
          else message.success('项目已更新');
          upload.current = undefined;
          onDone();
          return true;
        } catch {
          if (mounted.current)
            message.error(
              fieldsSaved
                ? '项目信息已保存，但封面未保存成功，请重试。'
                : '项目保存失败，请重试。',
            );
          return false;
        } finally {
          submitting.current = false;
          if (mounted.current) setSaving(false);
        }
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
        options={members.map((member) => ({
          label: member.nickname || member.mobile || String(member.userId),
          value: member.userId,
        }))}
        rules={[{ required: true, message: '请选择负责人' }]}
      />
      <ProFormTextArea name="description" label="项目描述" />
      <ProjectCoverPicker
        project={project}
        value={draft}
        onChange={changeDraft}
        disabled={saving}
      />
      <ProFormDatePicker name="startDate" label="开始时间" />
      <ProFormDatePicker name="endDate" label="结束时间" />
    </ModalForm>
  );
}
