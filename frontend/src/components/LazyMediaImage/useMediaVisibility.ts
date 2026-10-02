import { useEffect, useRef, useState } from 'react';

export const useMediaVisibility = (
  source?: string | null,
  active = true,
  inView?: boolean,
) => {
  const ref = useRef<HTMLSpanElement>(null);
  const [visibleSource, setVisibleSource] = useState<string>();
  const [enteredSource, setEnteredSource] = useState<string>();
  const [documentVisible, setDocumentVisible] = useState(
    () => !document.hidden,
  );
  const supported = typeof IntersectionObserver !== 'undefined';

  useEffect(() => {
    const update = () => setDocumentVisible(!document.hidden);
    document.addEventListener('visibilitychange', update);
    return () => document.removeEventListener('visibilitychange', update);
  }, []);

  useEffect(() => {
    if (!source || !active || inView !== undefined || !supported) return;
    const target = ref.current;
    if (!target) return;
    const observer = new IntersectionObserver(
      ([entry]) => {
        setVisibleSource(entry.isIntersecting ? source : undefined);
        if (entry.isIntersecting) setEnteredSource(source);
      },
      { rootMargin: '200px' },
    );
    observer.observe(target);
    return () => observer.disconnect();
  }, [active, inView, source, supported]);

  useEffect(() => {
    if (source && active && inView) setEnteredSource(source);
  }, [active, inView, source]);

  return {
    ref,
    visible: Boolean(
      source &&
        active &&
        documentVisible &&
        (inView ?? (!supported || visibleSource === source)),
    ),
    shouldLoad: Boolean(
      source && active && (!supported || inView || enteredSource === source),
    ),
  };
};
