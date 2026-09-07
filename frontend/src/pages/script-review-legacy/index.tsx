import { history } from '@umijs/max';
import { useEffect } from 'react';

const ScriptReviewLegacyPage = () => {
  useEffect(() => {
    const projectId = new URLSearchParams(window.location.search).get('projectId');
    const taskId = new URLSearchParams(window.location.search).get('taskId');
    if (taskId) history.replace(`/script-review/tasks/${taskId}`);
    else if (projectId) history.replace(`/script-review/projects/${projectId}/reviews`);
    else history.replace('/script-review-library');
  }, []);
  return null;
};

export default ScriptReviewLegacyPage;
