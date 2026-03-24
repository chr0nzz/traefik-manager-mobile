import { useQuery } from '@tanstack/react-query';
import { getEntrypoints, getOverview, getVersion } from '../api/traefik';

export function useDashboard() {
  const overview = useQuery({
    queryKey: ['overview'],
    queryFn: getOverview,
    staleTime: 30_000,
    retry: 2,
  });

  const version = useQuery({
    queryKey: ['version'],
    queryFn: getVersion,
    staleTime: 60_000,
    retry: 1,
  });

  const entrypoints = useQuery({
    queryKey: ['entrypoints'],
    queryFn: getEntrypoints,
    staleTime: 60_000,
    retry: 2,
  });

  return { overview, version, entrypoints };
}
