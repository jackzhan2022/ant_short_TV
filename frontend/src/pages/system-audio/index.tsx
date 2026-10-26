import SystemAudioList from './AudioList';
import styles from './index.module.css';

export default function SystemAudioLibrary() {
  return (
    <main className={styles.page} aria-label="系统音频库">
      <h1>系统音频库</h1>
      <SystemAudioList />
    </main>
  );
}
