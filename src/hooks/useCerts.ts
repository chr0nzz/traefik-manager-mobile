import { useQuery } from '@tanstack/react-query';
import { getCerts } from '../api/traefik';
import { useConnection } from '../store/connection';

export function useCerts() {
  const demoMode = useConnection(s => s.demoMode);
  return useQuery({
    queryKey: ['certs'],
    queryFn: demoMode ? () => ({ certs: [], errors: [] }) : getCerts,
    staleTime: 60_000,
    retry: demoMode ? 0 : 2,
  });
}
