import { useParams } from '@umijs/max';
import AiImageProductionWorkspace from '../detail/components/AiImageProductionWorkspace';

const ProductionWorkbenchSettings = () => {
  const params = useParams<{ id: string }>();
  return <AiImageProductionWorkspace projectId={Number(params.id)} />;
};

export default ProductionWorkbenchSettings;
