import { useQuery } from '@tanstack/react-query';
import { getDashboardConfig, getEntrypoints, getOverview } from '../api/traefik';
import { useConnection } from '../store/connection';
import { DEMO_ENTRYPOINTS, DEMO_OVERVIEW } from '../demo/data';

export function useDashboard() {
  const demoMode = useConnection(s => s.demoMode);

  const overview = useQuery({
    queryKey: ['overview'],
    queryFn: demoMode ? () => DEMO_OVERVIEW : getOverview,
    staleTime: 30_000,
    retry: demoMode ? 0 : 2,
  });

  const entrypoints = useQuery({
    queryKey: ['entrypoints'],
    queryFn: demoMode ? () => DEMO_ENTRYPOINTS : getEntrypoints,
    staleTime: 60_000,
    retry: demoMode ? 0 : 2,
  });

  return { overview, entrypoints };
}

export function useDashboardConfig() {
  return useQuery({
    queryKey: ['dashboardConfig'],
    queryFn: getDashboardConfig,
    staleTime: 60_000,
    retry: 1,
  });
}
