import { useParams } from '@umijs/max';
import ScriptCreationWorkspace from '../detail/components/ScriptCreationWorkspace';

const ProductionWorkbenchScript = () => {
  const params = useParams<{ id: string }>();
  return <ScriptCreationWorkspace projectId={Number(params.id)} />;
};

export default ProductionWorkbenchScript;
