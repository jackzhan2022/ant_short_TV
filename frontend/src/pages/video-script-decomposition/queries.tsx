import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  type ReactNode,
  useEffect,
  useState,
  useSyncExternalStore,
} from 'react';
import { getCurrentTenantId } from '@/services/account-team/auth';

const terminal = new Set([
  'SUCCEEDED',
  'FAILED',
  'PARTIAL_FAILED',
  'PENDING_REVIEW',
  'CONFIRMED',
]);

export const shouldPollVideoDecompositionBatch = (status?: string) =>
  Boolean(status && !terminal.has(status));

export const pollingDelay = (failures: number) =>
  Math.min(30000, 3000 * 2 ** failures);

function subscribe(listener: () => void) {
  window.addEventListener('current-tenant-changed', listener);
  window.addEventListener('storage', listener);
  return () => {
    window.removeEventListener('current-tenant-changed', listener);
    window.removeEventListener('storage', listener);
  };
}

function QueryScope({ children }: { children: ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: false,
            gcTime: 15 * 60 * 1000,
            refetchIntervalInBackground: false,
            refetchOnWindowFocus: true,
          },
        },
      }),
  );
  useEffect(() => () => client.clear(), [client]);
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

// Cache lifetime is limited to this page and reset on a team switch.
export function VideoDecompositionQueries({
  children,
}: {
  children: ReactNode;
}) {
  const tenantId = useSyncExternalStore(
    subscribe,
    getCurrentTenantId,
    () => undefined,
  );
  return <QueryScope key={tenantId ?? 'none'}>{children}</QueryScope>;
}
