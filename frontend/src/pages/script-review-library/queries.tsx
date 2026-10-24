import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  type ReactNode,
  useEffect,
  useState,
  useSyncExternalStore,
} from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';

function subscribe(listener: () => void) {
  window.addEventListener('current-tenant-changed', listener);
  window.addEventListener('storage', listener);
  return () => {
    window.removeEventListener('current-tenant-changed', listener);
    window.removeEventListener('storage', listener);
  };
}

export const reviewPollingDelay = (status?: string, failures = 0) =>
  status && !['PENDING', 'RUNNING'].includes(status)
    ? false
    : Math.min(30000, 3000 * 2 ** failures);

function Scope({ children }: { children: ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: false,
            staleTime: 30000,
            gcTime: 15 * 60 * 1000,
            refetchIntervalInBackground: false,
          },
        },
      }),
  );
  useEffect(() => () => client.clear(), [client]);
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

export function ReviewQueries({
  children,
}: {
  children: (tenantId: number | undefined) => ReactNode;
}) {
  const tenantId = useSyncExternalStore(
    subscribe,
    getCurrentTenantId,
    () => undefined,
  );
  return <Scope key={tenantId ?? 'none'}>{children(tenantId)}</Scope>;
}
