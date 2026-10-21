import { type ReactNode, useEffect, useRef, useState } from 'react';

const ViewportMount = ({
  id,
  eager = false,
  children,
}: {
  id: string;
  eager?: boolean;
  children: ReactNode;
}) => {
  const target = useRef<HTMLDivElement>(null);
  const [mounted, setMounted] = useState(
    () => eager || typeof IntersectionObserver === 'undefined',
  );

  useEffect(() => {
    if (mounted || !target.current) return;
    if (eager || typeof IntersectionObserver === 'undefined') {
      setMounted(true);
      return;
    }
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        setMounted(true);
        observer.disconnect();
      }
    }, { rootMargin: '400px' });
    observer.observe(target.current);
    return () => observer.disconnect();
  }, [eager, mounted]);

  return (
    <div id={id} tabIndex={-1} ref={target} style={{ minHeight: mounted ? undefined : 560 }}>
      {mounted ? children : null}
    </div>
  );
};

export default ViewportMount;
