import { useQuery } from '@tanstack/react-query';
import { getServices } from '../api/traefik';
import { useConnection } from '../store/connection';
import { DEMO_SERVICES } from '../demo/data';

export function useLive() {
  const demoMode = useConnection(s => s.demoMode);
  return useQuery({
    queryKey: ['live-services'],
    queryFn: demoMode ? () => DEMO_SERVICES : getServices,
    staleTime: 15_000,
    retry: demoMode ? 0 : 2,
  });
}
