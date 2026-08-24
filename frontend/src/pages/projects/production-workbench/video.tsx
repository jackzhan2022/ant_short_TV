import { useParams } from '@umijs/max';
import ShotProductionWorkspace from './ShotProductionWorkspace';

const ProductionWorkbenchVideo = () => {
  const params = useParams<{ id: string }>();
  return <ShotProductionWorkspace projectId={Number(params.id)} />;
};

export default ProductionWorkbenchVideo;
