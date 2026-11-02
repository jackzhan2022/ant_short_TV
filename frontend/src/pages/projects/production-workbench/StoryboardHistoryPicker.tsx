import { App, Button, Empty, Modal, Pagination, Spin } from 'antd';
import { useEffect, useRef, useState } from 'react';
import LazyMediaImage from '@/components/LazyMediaImage';
import type { MediaPage } from '@/services/media/paging';
import type { AiImageResult, AiVideoResult } from './service';
import { useStoryboardMedia } from './storyboard-card-context';

type StoryboardHistoryPickerProps = {
  storyboardId: number;
  storyboardNo: number;
  type: 'IMAGE' | 'VIDEO';
};

const StoryboardHistoryPicker = ({
  storyboardId,
  storyboardNo,
  type,
}: StoryboardHistoryPickerProps) => {
  const { message } = App.useApp();
  const {
    loadImageCandidates,
    loadVideoCandidates,
    selectFirstFrame,
    bindVideoResult,
    selectingFirstFrameId,
    bindingVideoId,
  } = useStoryboardMedia(storyboardId);
  const [open, setOpen] = useState(false);
  const [candidateLoading, setCandidateLoading] = useState(false);
  const [imageCandidates, setImageCandidates] =
    useState<MediaPage<AiImageResult>>();
  const [videoCandidates, setVideoCandidates] =
    useState<MediaPage<AiVideoResult>>();
  const candidateRequest = useRef<{ id: number; controller?: AbortController }>(
    { id: 0 },
  );
  useEffect(() => () => candidateRequest.current.controller?.abort(), []);
  const loadCandidates = async (current = 1, pageSize = 20) => {
    candidateRequest.current.controller?.abort();
    const controller = new AbortController();
    const requestId = candidateRequest.current.id + 1;
    candidateRequest.current = { id: requestId, controller };
    setCandidateLoading(true);
    try {
      if (type === 'IMAGE') {
        const page = await loadImageCandidates(
          current,
          pageSize,
          controller.signal,
        );
        if (
          !controller.signal.aborted &&
          candidateRequest.current.id === requestId
        )
          setImageCandidates(page);
      } else {
        const page = await loadVideoCandidates(
          current,
          pageSize,
          controller.signal,
        );
        if (
          !controller.signal.aborted &&
          candidateRequest.current.id === requestId
        )
          setVideoCandidates(page);
      }
    } catch {
      if (
        !controller.signal.aborted &&
        candidateRequest.current.id === requestId
      ) {
        message.error('历史媒体加载失败');
      }
    } finally {
      if (
        !controller.signal.aborted &&
        candidateRequest.current.id === requestId
      )
        setCandidateLoading(false);
    }
  };
  const openCandidates = () => {
    setOpen(true);
    void loadCandidates();
  };
  const closeCandidates = () => {
    candidateRequest.current.controller?.abort();
    candidateRequest.current = { id: candidateRequest.current.id + 1 };
    setCandidateLoading(false);
    setOpen(false);
  };
  const candidatePage = type === 'IMAGE' ? imageCandidates : videoCandidates;
  return (
    <>
      <Button size="small" onClick={openCandidates}>
        {type === 'IMAGE' ? '选择历史首帧' : '选择历史视频'}
      </Button>
      <Modal
        title={
          type === 'IMAGE'
            ? `分镜${storyboardNo}历史首帧`
            : `分镜${storyboardNo}历史视频`
        }
        open={open}
        footer={null}
        width={760}
        destroyOnHidden
        onCancel={closeCandidates}
      >
        <Spin spinning={candidateLoading}>
          {candidatePage?.data.length ? (
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))',
                gap: 12,
              }}
            >
              {type === 'IMAGE'
                ? imageCandidates?.data.map((candidate) => (
                    <div key={candidate.id} style={{ minWidth: 0 }}>
                      <LazyMediaImage
                        native
                        active={open}
                        src={candidate.thumbnailUrl || undefined}
                        alt={`首帧候选${candidate.id}`}
                        height={112}
                        preview={false}
                      />
                      <Button
                        block
                        size="small"
                        loading={selectingFirstFrameId === candidate.id}
                        disabled={candidate.selected}
                        onClick={() => selectFirstFrame(candidate.id)}
                        style={{ marginTop: 6 }}
                      >
                        {candidate.selected ? '当前首帧' : '设为首帧'}
                      </Button>
                    </div>
                  ))
                : videoCandidates?.data.map((candidate) => (
                    <div key={candidate.id} style={{ minWidth: 0 }}>
                      <LazyMediaImage
                        native
                        active={open}
                        src={candidate.coverUrl || undefined}
                        alt={`视频候选${candidate.id}`}
                        height={112}
                        preview={false}
                      />
                      <Button
                        block
                        size="small"
                        loading={bindingVideoId === candidate.id}
                        disabled={candidate.isSelected}
                        onClick={() => bindVideoResult(candidate.id)}
                        style={{ marginTop: 6 }}
                      >
                        {candidate.isSelected ? '当前视频' : '设为当前视频'}
                      </Button>
                    </div>
                  ))}
            </div>
          ) : (
            <Empty description={candidateLoading ? '加载中' : '暂无历史结果'} />
          )}
          {(candidatePage?.total || 0) > (candidatePage?.pageSize || 20) ? (
            <Pagination
              current={candidatePage?.current || 1}
              pageSize={candidatePage?.pageSize || 20}
              total={candidatePage?.total || 0}
              showSizeChanger
              onChange={(page, size) => void loadCandidates(page, size)}
              style={{ marginTop: 16 }}
            />
          ) : null}
        </Spin>
      </Modal>
    </>
  );
};

export default StoryboardHistoryPicker;
