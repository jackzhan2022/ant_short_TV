import styles from './settings.module.css';

const paths = {
  user: 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0ZM4 21v-3a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v3',
  scene: 'M3 21V8l9-5 9 5v13Zm5 0v-7h8v7M3 8l9 5 9-5M12 3v10',
  cube: 'm12 2 10 5v10l-10 5-10-5V7Zm0 10L2 7m10 5 10-5M12 12v10M7 4.5l10 5',
  clock: 'M12 7v5l3 2m7-2a10 10 0 1 1-20 0 10 10 0 0 1 20 0',
  check: 'm5 12 4 4L19 6',
  video: 'M3 6h12v12H3Zm12 4 6-3v10l-6-3',
  more: 'M5 12h.01M12 12h.01M19 12h.01',
  grid: 'M3 3h7v7H3Zm11 0h7v7h-7ZM3 14h7v7H3Zm11 0h7v7h-7Z',
  dense:
    'M2 3h5v5H2Zm8 0h5v5h-5Zm8 0h5v5h-5ZM2 11h5v5H2Zm8 0h5v5h-5Zm8 0h5v5h-5ZM2 19h5v3H2Zm8 0h5v3h-5Zm8 0h5v3h-5Z',
  spark:
    'm12 3 2.4 6.6L21 12l-6.6 2.4L12 21l-2.4-6.6L3 12l6.6-2.4Zm8-2v4m-2-2h4',
};

export default function AssetSettingsIcon({
  name,
}: {
  name: keyof typeof paths;
}) {
  return (
    <svg
      className={styles.assetIcon}
      viewBox="0 0 24 24"
      aria-hidden="true"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d={paths[name]} />
    </svg>
  );
}

export function AssetSettingsPlaceholder({
  type,
}: {
  type: 'CHARACTER' | 'SCENE' | 'PROP';
}) {
  return (
    <div
      className={styles.emptyIllustration}
      role="img"
      aria-label="参考图待生成"
    >
      <AssetSettingsIcon
        name={
          type === 'CHARACTER' ? 'user' : type === 'SCENE' ? 'scene' : 'cube'
        }
      />
      <span>参考图待生成</span>
    </div>
  );
}
