import PointEstimateButton from '@/components/PointEstimateButton';
import { isPointEstimateCancelled } from '@/services/point-estimate';
import { CompressOutlined } from '@ant-design/icons';
import { Alert, App, Button, Checkbox, Modal, Popconfirm, Select } from 'antd';
import { useEffect, useRef, useState } from 'react';
import {
  applyPropSimplification, type PropProductionMode, type PropSimplificationGroup,
  type PropSimplificationStatus, queryPropSimplification, submitPropSimplification,
  undoPropSimplification,
} from './prop-simplification-service';
import styles from './PropSimplificationControl.module.css';

export const productionModeLabels: Record<PropProductionMode, string> = {
  INDEPENDENT: '单独制作', SHARED: '共用外观', VARIANTS: '保留不同版本', SCENE: '放入场景',
};
type Props = {
  projectId: number; visible: boolean; disabled: boolean; revision: string;
  onChange: (status: PropSimplificationStatus) => void;
  onRefresh: () => Promise<void>;
  onTrack: (executionId: number) => void;
};
export default function PropSimplificationControl(props: Props) {
  const { projectId, visible, disabled, revision } = props;
  const { message } = App.useApp();
  const callbacks = useRef(props); callbacks.current = props;
  const scope = useRef(projectId); scope.current = projectId;
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const [status, setStatus] = useState<PropSimplificationStatus>();
  const [failed, setFailed] = useState(false);
  const [version, setVersion] = useState(0);
  const [open, setOpen] = useState(false);
  const [groups, setGroups] = useState<PropSimplificationGroup[]>([]);
  const [excluded, setExcluded] = useState<number[]>([]);
  const [selected, setSelected] = useState(0);
  const [action, setAction] = useState<string>();
  const pending = useRef(false);
  const busy = action === 'analyze' || status?.status === 'RUNNING';
  const readOnly = disabled || status?.status !== 'READY' || Boolean(action);
  const operations = [{ url: `/api/projects/${projectId}/prop-simplification` }];

  const accept = (next: PropSimplificationStatus) => {
    setStatus(next); setFailed(false); callbacks.current.onChange(next);
  };
  useEffect(() => {
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    const poll = async () => {
      try {
        const response = await queryPropSimplification(projectId);
        if (!active) return;
        accept(response.data);
        if (response.data.status === 'RUNNING') timer = setTimeout(poll, 2500);
      } catch { if (active) { setFailed(true); timer = setTimeout(poll, 5000); } }
    };
    void poll();
    return () => { active = false; clearTimeout(timer); };
  }, [projectId, revision, version]);
  useEffect(() => {
    setOpen(false); setStatus(undefined); setGroups([]); setExcluded([]); setSelected(0);
  }, [projectId]);
  useEffect(() => {
    if (status?.decision?.groups && !busy) {
      setGroups(structuredClone(status.decision.groups)); setExcluded([]); setSelected(0);
    } else if (!busy) {
      setGroups([]); setExcluded([]); setSelected(0);
    }
  }, [status?.id, status?.status, busy]);

  const current = () => mounted.current && scope.current === projectId;
  const analyze = async () => {
    if (pending.current) return;
    pending.current = true; setAction('analyze');
    try {
      const response = await submitPropSimplification(projectId);
      if (!current()) return;
      if (!response.data.id) throw new Error('精简任务未返回编号');
      setStatus((value) => value ? { ...value, status: 'RUNNING', executionId: response.data.id } : undefined);
      callbacks.current.onTrack(response.data.id); setVersion((value) => value + 1); setOpen(true);
    } catch (error) {
      if (current() && !isPointEstimateCancelled(error)) message.error(error instanceof Error ? error.message : '精简任务提交失败');
    } finally { pending.current = false; if (current()) setAction(undefined); }
  };
  const refreshAfterWrite = async (next: PropSimplificationStatus) => {
    if (!current()) return;
    accept(next); setGroups(structuredClone(next.decision?.groups ?? []));
    try { await callbacks.current.onRefresh(); }
    catch { if (current()) message.warning('方案已更新，但资产列表刷新失败，请重新加载页面。'); }
  };
  const apply = async () => {
    if (!status?.id || pending.current) return;
    pending.current = true; setAction('apply');
    try {
      const decision = groups.map((group, index) => excluded.includes(index) ? { ...group, mode: 'INDEPENDENT' as const } : group);
      const response = await applyPropSimplification(projectId, status.id, decision);
      await refreshAfterWrite(response.data);
      if (current()) { setOpen(false); message.success('道具精简已应用'); }
    } catch (error) { if (current()) message.error(error instanceof Error ? error.message : '应用失败，资产可能已变化，请重新分析。'); }
    finally { pending.current = false; if (current()) setAction(undefined); }
  };
  const undo = async () => {
    if (!status?.appliedPlanId || pending.current) return;
    pending.current = true; setAction('undo');
    try {
      const response = await undoPropSimplification(projectId, status.appliedPlanId);
      await refreshAfterWrite(response.data);
      if (current()) { setOpen(false); message.success('已撤销制作关系，已有图片保留'); }
    } catch (error) { if (current()) message.error(error instanceof Error ? error.message : '撤销失败'); }
    finally { pending.current = false; if (current()) setAction(undefined); }
  };
  const group = groups[selected];
  const propNames = new Map(status?.inventory?.props.map((item) => [item.id, item.name]));
  const updateGroup = (patch: Partial<PropSimplificationGroup>) => setGroups((items) => items.map((item, index) => index === selected ? { ...item, ...patch } : item));
  const productionCount = groups.reduce((count, item, index) => count + (excluded.includes(index) || item.mode === 'INDEPENDENT' ? item.memberIds.length : item.mode === 'SCENE' ? 0 : 1), 0);
  const sceneCount = groups.reduce((count, item, index) => count + (!excluded.includes(index) && item.mode === 'SCENE' ? item.memberIds.length : 0), 0);
  const launch = status?.decision?.groups?.length
    ? <Button type="text" size="small" icon={<CompressOutlined />} disabled={disabled} onClick={() => setOpen(true)}>道具精简</Button>
    : <PointEstimateButton type="text" size="small" icon={<CompressOutlined />} operations={operations} disabled={disabled || failed || !status || busy} loading={busy} onClick={() => void analyze()}>道具精简</PointEstimateButton>;
  return <>
    {visible ? launch : null}
    {visible && busy ? <Button type="link" size="small" onClick={() => setOpen(true)}>查看精简进度</Button> : null}
    {visible && failed ? <Button type="link" size="small" onClick={() => setVersion((value) => value + 1)}>重试精简状态</Button> : null}
    <Modal title="道具精简方案" open={open} width={980} onCancel={() => setOpen(false)} footer={<div className={styles.footer}>
      <span>{groups.length ? `已选择 ${groups.length - excluded.length} 组建议` : '分析后确认应用，原道具记录保留'}</span>
      {status?.appliedPlanId ? <Popconfirm title="撤销最近的道具精简？已有图片和形态保留。" onConfirm={() => void undo()}><Button disabled={disabled || Boolean(action)} loading={action === 'undo'}>撤销精简</Button></Popconfirm> : null}
      <PointEstimateButton operations={operations} disabled={disabled || busy || Boolean(action)} loading={action === 'analyze'} onClick={() => void analyze()}>{groups.length ? '重新分析' : '开始分析'}</PointEstimateButton>
      <Button onClick={() => setOpen(false)}>返回道具</Button>
      <Button type="primary" disabled={readOnly || !groups.length || groups.length === excluded.length} loading={action === 'apply'} onClick={() => void apply()}>应用方案</Button>
    </div>}>
      {busy ? <Alert type="info" title="正在分析全剧道具，可关闭窗口继续浏览。" description={status?.executionId ? `任务 #${status.executionId}；完成后在此确认方案。` : '任务已提交，正在获取进度。'} showIcon /> : null}
      {status?.errorMessage ? <Alert type="error" title={status.errorMessage} showIcon /> : null}
      {status?.production.needsReview ? <Alert type="warning" title="原资产或剧本已变化，请重新分析精简方案。" showIcon /> : null}
      {status?.status === 'APPLIED' ? <Alert type="success" title="方案已应用；需要调整时请重新分析，或撤销最近的精简。" showIcon /> : null}
      {groups.length && !busy ? <>
        <div className={styles.summary}><span>原道具 {status?.inventory?.props.length} 项</span><span>独立制作<strong>{productionCount}</strong>项</span><span>随场景呈现<strong>{sceneCount}</strong>项</span></div>
        <div className={styles.review}><div className={styles.groups}>{groups.map((item, index) => <div key={item.memberIds.join('-')} className={`${styles.group} ${selected === index ? styles.selected : ''}`}>
          <Checkbox aria-label={`采用${item.name}的建议`} disabled={readOnly} checked={!excluded.includes(index)} onChange={(event) => setExcluded((items) => event.target.checked ? items.filter((value) => value !== index) : [...items, index])} />
          <button type="button" className={styles.groupButton} aria-pressed={selected === index} onClick={() => setSelected(index)}><span>{item.name}<small>{item.memberIds.length} 项原道具{item.mode === 'VARIANTS' ? ` · ${item.variants.length} 个版本` : ''}</small></span><span className={styles.mode}>{excluded.includes(index) ? '保持原样' : productionModeLabels[item.mode]}</span></button>
        </div>)}</div>{group ? <div className={styles.details}>
          <strong>{group.name}</strong>
          <div className={styles.field}><span>处理方式</span><Select aria-label="处理方式" disabled={readOnly} value={group.mode} options={Object.entries(productionModeLabels).map(([value, label]) => ({ value, label, disabled: value === 'VARIANTS' && !group.variants.length }))} onChange={(mode) => updateGroup({ mode })} /></div>
          <div className={styles.field}><span>对应的原道具</span><div className={styles.sources}>{group.memberIds.map((id) => <span className={styles.source} key={id}>{propNames.get(id) ?? `道具 #${id}`}</span>)}</div></div>
          {['SHARED', 'VARIANTS'].includes(group.mode) ? <div className={styles.field}><span>保留的制作项</span><Select aria-label="保留的制作项" disabled={readOnly} value={group.targetId} options={group.memberIds.map((id) => ({ value: id, label: propNames.get(id) ?? `道具 #${id}` }))} onChange={(targetId) => updateGroup({ targetId })} /></div> : null}
          {group.mode === 'SCENE' ? <div className={styles.field}><span>对应场景 · 应用后仍需确认或更新场景图</span><Select mode="multiple" aria-label="对应场景" disabled={readOnly} value={group.sceneIds} options={status?.inventory?.scenes.map((scene) => ({ value: scene.id, label: scene.name }))} onChange={(sceneIds) => updateGroup({ sceneIds })} /></div> : null}
          {group.mode === 'VARIANTS' ? <div className={styles.field}><span>保留的关键版本</span><div className={styles.sources}>{group.variants.map((item) => <span className={styles.source} key={item.name}>{item.name}</span>)}</div></div> : null}
          <div className={styles.reason}>{group.reason}</div>
          {group.evidence.map((item) => <blockquote className={styles.evidence} key={`${item.episodeId}-${item.quote}`}>第 {status?.inventory?.episodes?.find((episode) => episode.id === item.episodeId)?.episode_no ?? item.episodeId} 集：“{item.quote}”</blockquote>)}
        </div> : null}</div>
      </> : null}
    </Modal>
  </>;
}
