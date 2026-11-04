import { useEffect, useLayoutEffect, useRef } from 'react';
import { createAssetVisualPoller, type VisualPollTarget } from './asset-visual-polling';
import { queryAssetVisualStatuses, queryAssetVisualWorkspace } from './service';
import type { AssetVisualStatus, AssetVisualWorkspace } from './service';

export default function useAssetVisualPolling(
  projectId: number,
  targets: VisualPollTarget[],
  onWorkspace: (target: VisualPollTarget, workspace: AssetVisualWorkspace) => void,
  onSettled: (target: VisualPollTarget, status: AssetVisualStatus) => void,
) {
  const callbacks = useRef({ onWorkspace, onSettled });
  callbacks.current = { onWorkspace, onSettled };
  const poller = useRef<ReturnType<typeof createAssetVisualPoller> | undefined>(undefined);
  useLayoutEffect(() => {
    if (!projectId) return;
    const current = createAssetVisualPoller({
      readStatus: async (ids, signal) => (await queryAssetVisualStatuses(projectId, ids, signal)).data,
      readWorkspace: async (target, signal) => (await queryAssetVisualWorkspace(
        projectId, target.assetType, target.assetId, { selectedVariantId: target.variantId }, signal,
      )).data,
      onWorkspace: (target, visual) => callbacks.current.onWorkspace(target, visual),
      onSettled: (target, status) => callbacks.current.onSettled(target, status),
    });
    poller.current = current;
    return () => { current.stop(); if (poller.current === current) poller.current = undefined; };
  }, [projectId]);
  useEffect(() => { poller.current?.watch(targets); }, [projectId, targets]);
}
