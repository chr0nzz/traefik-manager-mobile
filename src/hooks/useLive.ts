import { useQuery } from '@tanstack/react-query';
import { getServices } from '../api/traefik';

export function useLive() {
  return useQuery({
    queryKey: ['live-services'],
    queryFn: getServices,
    staleTime: 15_000,
    retry: 2,
  });
}
