import { useQuery } from '@tanstack/react-query';
import { getConfigs } from '../api/routes';

export function useConfigs() {
  return useQuery({
    queryKey: ['configs'],
    queryFn: getConfigs,
    staleTime: Infinity,
    retry: 1,
  });
}
