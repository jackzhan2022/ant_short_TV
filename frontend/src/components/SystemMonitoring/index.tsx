import { useEffect } from 'react';
import { installSystemMonitoring } from '@/utils/systemMonitoring';

export default function SystemMonitoring() {
  useEffect(installSystemMonitoring, []);
  return null;
}
