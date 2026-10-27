import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, afterEach, expect, it, vi } from 'vitest';
import SubtitleEraseModal from './SubtitleEraseModal';
const mocks = vi.hoisted(() => ({ create: vi.fn() }));
vi.mock('./subtitle-erase-service', () => ({ createSubtitleEraseTask: mocks.create, subtitleEraseSource: () => '/playback' }));
vi.mock('antd', () => ({
  Modal: ({ children, title, onCancel, closable, footer }: any) => <div role="dialog" aria-label={title}>{closable !== false && <button type="button" onClick={onCancel}>关闭</button>}{children}{footer}</div>,
  Button: ({ children, onClick, disabled, loading }: any) => <button type="button" onClick={onClick} disabled={disabled || loading}>{children}</button>,
  Alert: ({ title }: any) => <div role="alert">{title}</div>,
  Select: ({ value, onChange, disabled, options }: any) => <select aria-label="处理版本" value={value} disabled={disabled} onChange={event => onChange(event.target.value)}>{options.map((option: any) => <option key={option.value} value={option.value}>{option.label}</option>)}</select>,
}));
beforeEach(() => { vi.resetAllMocks(); vi.spyOn(HTMLMediaElement.prototype, 'pause').mockImplementation(() => {}); vi.spyOn(HTMLMediaElement.prototype, 'load').mockImplementation(() => {}); });
afterEach(() => vi.restoreAllMocks());
function ready() {
 const video = screen.getByLabelText('待擦除字幕的视频');
 Object.defineProperties(video, { videoWidth: { value: 720 }, videoHeight: { value: 1280 }, duration: { value: 5 }, readyState: { value: 2 } });
 fireEvent.loadedMetadata(video); fireEvent.loadedData(video);
}
it('uses the chosen displayed result and submits only once while preserving the original selection', async () => {
 let finish!: (value: unknown) => void; mocks.create.mockImplementation(() => new Promise(resolve => { finish = resolve; }));
 const done = vi.fn(), close = vi.fn();
 render(<SubtitleEraseModal projectId={1} resultId={22} storyboardNo={3} onClose={close} onSubmitted={done} />);
 expect(screen.getByRole('button', { name: '开始擦除' })).toBeDisabled(); ready();
 fireEvent.click(screen.getByRole('button', { name: '开始擦除' })); fireEvent.click(screen.getByRole('button', { name: '开始擦除' }));
 await waitFor(() => expect(mocks.create).toHaveBeenCalledOnce());
 expect(mocks.create.mock.calls[0].slice(0, 3)).toEqual([1, 22, { method: 'AUTO', quality: 'PRO' }]);
 expect(close).not.toHaveBeenCalled();
 await act(async () => finish({ id: 4, sourceResultId: 22, status: 'PENDING' }));
 expect(done).toHaveBeenCalledWith(expect.objectContaining({ sourceResultId: 22 }));
 expect(close).toHaveBeenCalledOnce();
});
it('forces pro quality for region erasure and retries a failed request with the same idempotency key', async () => {
 mocks.create.mockRejectedValueOnce(new Error('服务暂时不可用')).mockResolvedValueOnce({ id: 5 });
 render(<SubtitleEraseModal projectId={1} resultId={22} storyboardNo={3} onClose={vi.fn()} onSubmitted={vi.fn()} />); ready();
 fireEvent.click(screen.getByLabelText('指定区域擦除'));
 expect(screen.getByRole('combobox')).toBeDisabled();
 expect(screen.getByLabelText('字幕擦除区域')).toBeInTheDocument();
 fireEvent.click(screen.getByRole('button', { name: '开始擦除' })); await screen.findByRole('alert');
 fireEvent.click(screen.getByRole('button', { name: '开始擦除' }));
 await waitFor(() => expect(mocks.create).toHaveBeenCalledTimes(2));
 expect(mocks.create.mock.calls[1][3]).toEqual(mocks.create.mock.calls[0][3]);
 expect(mocks.create.mock.calls[1][2]).toMatchObject({ method: 'REGION', quality: 'PRO', region: expect.any(Object) });
});
