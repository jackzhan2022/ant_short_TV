import { history, useAccess, useModel } from '@umijs/max';
import { useEffect } from 'react';
import { workspaceLandingPath } from '@/services/account-team/navigation';

export default function WorkspaceEntry() {
  const permissions = useAccess();
  const { initialState } = useModel('@@initialState');
  const destination = workspaceLandingPath(permissions);
  const signedIn = Boolean(initialState?.currentUser);
  useEffect(() => {
    if (signedIn) history.replace(destination);
  }, [destination, signedIn]);
  return null;
}
