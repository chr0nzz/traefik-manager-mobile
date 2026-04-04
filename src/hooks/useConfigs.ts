import { useQuery } from '@tanstack/react-query';
import { getConfigs } from '../api/routes';
import { useConnection } from '../store/connection';
import { DEMO_CONFIGS } from '../demo/data';

export function useConfigs() {
  const demoMode = useConnection(s => s.demoMode);
  return useQuery({
    queryKey: ['configs'],
    queryFn: demoMode ? () => DEMO_CONFIGS : getConfigs,
    staleTime: Infinity,
    retry: demoMode ? 0 : 1,
  });
}
