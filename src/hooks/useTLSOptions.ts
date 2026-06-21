import { useQuery } from '@tanstack/react-query';
import { TLSOption, getTLSOptions } from '../api/tlsOptions';
import { useConnection } from '../store/connection';

export function useTLSOptions() {
  const demoMode = useConnection(s => s.demoMode);
  return useQuery<TLSOption[]>({
    queryKey: ['tls-options'],
    queryFn: demoMode ? () => [] : getTLSOptions,
    staleTime: 60_000,
    retry: demoMode ? 0 : 1,
  });
}
