import { useQuery } from '@tanstack/react-query';
import { getEntrypoints, getOverview } from '../api/traefik';

export function useDashboard() {
  const overview = useQuery({
    queryKey: ['overview'],
    queryFn: getOverview,
    staleTime: 30_000,
    retry: 2,
  });

  const entrypoints = useQuery({
    queryKey: ['entrypoints'],
    queryFn: getEntrypoints,
    staleTime: 60_000,
    retry: 2,
  });

  return { overview, entrypoints };
}
