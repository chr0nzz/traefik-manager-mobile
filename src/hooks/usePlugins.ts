import { useQuery } from '@tanstack/react-query';
import { getPlugins } from '../api/traefik';
import { useConnection } from '../store/connection';

export function usePlugins() {
  const demoMode = useConnection(s => s.demoMode);
  return useQuery({
    queryKey: ['plugins'],
    queryFn: demoMode ? () => ({ plugins: [] }) : getPlugins,
    staleTime: 60_000,
    retry: demoMode ? 0 : 2,
  });
}
