import { PlayCircleOutlined } from '@ant-design/icons';
import { App, Button, Flex, Input, Modal, Select, Typography } from 'antd';
import { type ReactNode, useState } from 'react';
import { useStoryboardMedia } from './storyboard-card-context';

export type VoiceGenerationValues = {
  voiceType: string;
  speakerName: string;
  voiceId: string;
  textContent: string;
  speed: number;
  pitch: number;
  volume: number;
};

type StoryboardVoiceGeneratorProps = {
  storyboardId: number;
  storyboardNo: number;
  defaultSpeaker: string;
  textContent: string;
  children?: (open: (speakerName?: string) => void) => ReactNode;
};

const StoryboardVoiceGenerator = ({
  storyboardId,
  storyboardNo,
  defaultSpeaker,
  textContent,
  children,
}: StoryboardVoiceGeneratorProps) => {
  const { message } = App.useApp();
  const { generateVoice } = useStoryboardMedia(storyboardId);
  const [voiceModalOpen, setVoiceModalOpen] = useState(false);
  const [voiceSubmitting, setVoiceSubmitting] = useState(false);
  const [voiceValues, setVoiceValues] = useState<VoiceGenerationValues>({
    voiceType: 'DIALOGUE',
    speakerName: defaultSpeaker,
    voiceId: 'default-cn-voice',
    textContent,
    speed: 1,
    pitch: 1,
    volume: 1,
  });
  const open = (speakerName?: string) => {
    if (speakerName !== undefined) {
      setVoiceValues((previous) => ({ ...previous, speakerName }));
    }
    setVoiceModalOpen(true);
  };
  return (
    <>
      {children ? (
        children(open)
      ) : (
        <Button
          icon={<PlayCircleOutlined />}
          aria-label={`分镜${storyboardNo}自定义音色`}
          onClick={() => open()}
        >
          自定义音色
        </Button>
      )}
      <Modal
        title={`分镜${storyboardNo}自定义音色`}
        open={voiceModalOpen}
        okText="生成语音"
        confirmLoading={voiceSubmitting}
        onCancel={() => setVoiceModalOpen(false)}
        onOk={async () => {
          if (!voiceValues.voiceId.trim() || !voiceValues.textContent.trim()) {
            message.warning('请填写音色ID和合成文本');
            return;
          }
          setVoiceSubmitting(true);
          try {
            await generateVoice(voiceValues);
            setVoiceModalOpen(false);
          } catch {
            // The media action reports the error; keep the entered values for retry.
          } finally {
            setVoiceSubmitting(false);
          }
        }}
      >
        <Flex vertical gap={12}>
          <div>
            <Typography.Text>语音类型</Typography.Text>
            <Select
              aria-label={`分镜${storyboardNo}语音类型`}
              value={voiceValues.voiceType}
              options={[
                { label: '对白', value: 'DIALOGUE' },
                { label: '旁白', value: 'NARRATION' },
                { label: '内心独白', value: 'MONOLOGUE' },
              ]}
              onChange={(voiceType) =>
                setVoiceValues((previous) => ({ ...previous, voiceType }))
              }
              style={{ width: '100%' }}
            />
          </div>
          <div>
            <Typography.Text>说话人</Typography.Text>
            <input
              aria-label={`分镜${storyboardNo}说话人`}
              value={voiceValues.speakerName}
              onChange={(event) =>
                setVoiceValues((previous) => ({
                  ...previous,
                  speakerName: event.target.value,
                }))
              }
            />
          </div>
          <div>
            <Typography.Text>音色ID</Typography.Text>
            <input
              aria-label={`分镜${storyboardNo}音色ID`}
              value={voiceValues.voiceId}
              onChange={(event) =>
                setVoiceValues((previous) => ({
                  ...previous,
                  voiceId: event.target.value,
                }))
              }
            />
          </div>
          <div>
            <Typography.Text>合成文本</Typography.Text>
            <Input.TextArea
              aria-label={`分镜${storyboardNo}合成文本`}
              value={voiceValues.textContent}
              onChange={(event) =>
                setVoiceValues((previous) => ({
                  ...previous,
                  textContent: event.target.value,
                }))
              }
              autoSize={{ minRows: 4, maxRows: 8 }}
            />
          </div>
        </Flex>
      </Modal>
    </>
  );
};

export default StoryboardVoiceGenerator;
