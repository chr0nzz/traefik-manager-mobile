import { useQuery } from '@tanstack/react-query';
import { getCerts } from '../api/traefik';
import { useConnection } from '../store/connection';
import { DEMO_CERTS } from '../demo/data';

export function useCerts() {
  const demoMode = useConnection(s => s.demoMode);
  return useQuery({
    queryKey: ['certs'],
    queryFn: demoMode ? () => DEMO_CERTS : getCerts,
    staleTime: 60_000,
    retry: demoMode ? 0 : 2,
  });
}
