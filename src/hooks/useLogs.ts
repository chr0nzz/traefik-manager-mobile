import { useQuery } from '@tanstack/react-query';
import { getLogs } from '../api/routes';
import { useConnection } from '../store/connection';
import { useTabsStore } from '../store/tabs';
import { DEMO_LOGS } from '../demo/data';

export function useLogs() {
  const demoMode = useConnection(s => s.demoMode);
  const logLines = useTabsStore(s => s.logLines);
  return useQuery({
    queryKey: ['logs', logLines],
    queryFn: demoMode ? () => DEMO_LOGS : () => getLogs(logLines),
    staleTime: 30_000,
    retry: demoMode ? 0 : 1,
  });
}
