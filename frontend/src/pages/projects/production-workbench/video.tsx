import { useOutletContext, useParams } from '@umijs/max';
import type { Project } from '@/services/account-team/types';
import VideoWorkbench from './video-workbench';

const ProductionWorkbenchVideo = () => {
  const params = useParams<{ id: string }>();
  const { project } = useOutletContext<{ project?: Project }>();
  const projectId = Number(params.id);
  return (
    <VideoWorkbench
      key={projectId}
      projectId={projectId}
      canEdit={project?.capabilities?.canEdit}
    />
  );
};

export default ProductionWorkbenchVideo;
