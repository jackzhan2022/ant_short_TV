import { request } from '@umijs/max';
import { useEffect, useRef, useState } from 'react';
import LazyMediaImage, { type LazyMediaImageProps } from '../LazyMediaImage';
import { useMediaVisibility } from '../LazyMediaImage/useMediaVisibility';

export type ProjectCoverStatus = 'MISSING' | 'PENDING' | 'READY' | 'FAILED';
type Props = Omit<LazyMediaImageProps, 'inView'> & {
  status?: ProjectCoverStatus;
};

const ProjectCoverImage = ({
  src,
  status,
  active = true,
  style,
  ...props
}: Props) => {
  const { ref, visible } = useMediaVisibility(src, active);
  const [state, setState] = useState<{
    src?: string;
    status: ProjectCoverStatus;
  }>({ src, status: status || 'PENDING' });
  const attempts = useRef({ src, count: 0 });
  const [retrying, setRetrying] = useState(false);
  const currentStatus = state.src === src ? state.status : status || 'PENDING';

  useEffect(() => {
    setState({ src, status: status || 'PENDING' });
    attempts.current = { src, count: 0 };
  }, [src, status]);

  useEffect(() => {
    if (
      !src ||
      !visible ||
      currentStatus !== 'PENDING' ||
      attempts.current.count >= 12
    )
      return;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    const poll = async () => {
      if (controller.signal.aborted || attempts.current.count >= 12) return;
      attempts.current.count += 1;
      try {
        const response = await request<{
          data: { status: ProjectCoverStatus };
        }>(`${src}/status`, { signal: controller.signal });
        if (controller.signal.aborted) return;
        setState({ src, status: response.data.status });
        if (response.data.status !== 'PENDING') return;
      } catch {
        if (controller.signal.aborted) return;
      }
      if (attempts.current.count < 12)
        timer = setTimeout(() => void poll(), 5000);
    };
    void poll();
    return () => {
      controller.abort();
      clearTimeout(timer);
    };
  }, [currentStatus, src, visible]);

  return (
    <span
      ref={ref}
      style={{
        position: 'relative',
        display: 'block',
        width: props.width || '100%',
        height: props.height,
        ...style,
      }}
    >
      <LazyMediaImage
        {...props}
        active={active}
        src={currentStatus === 'READY' ? src : undefined}
        inView={visible}
      />
      {src && currentStatus === 'FAILED' ? (
        <button
          type="button"
          aria-label={`重试${props.alt || '封面'}`}
          disabled={retrying}
          onClick={async () => {
            setRetrying(true);
            try {
              const response = await request<{ data: { status: ProjectCoverStatus } }>(
                `${src}/retry`, { method: 'POST' },
              );
              setState({ src, status: response.data.status });
              attempts.current = { src, count: 0 };
            } catch {
              setState({ src, status: 'FAILED' });
            } finally {
              setRetrying(false);
            }
          }}
          style={{ position: 'absolute', inset: 0, margin: 'auto', width: 'fit-content', height: 30 }}
        >
          重试封面
        </button>
      ) : null}
    </span>
  );
};

export default ProjectCoverImage;
