import { useQuery } from '@tanstack/react-query';
import { GeoEntry, countByCountry, getGeoIpStatus, lookupIps } from '../api/geoip';

export function useGeoIp(ips: string[]) {
  const status = useQuery({
    queryKey: ['geoip-status'],
    queryFn:  getGeoIpStatus,
    staleTime: 5 * 60_000,
    retry: 1,
  });

  const on = !!status.data?.enabled && !!status.data?.available;

  const unique = Array.from(new Set(ips.filter(Boolean))).sort();

  const lookup = useQuery({
    queryKey: ['geoip-lookup', unique.join(',')],
    queryFn:  () => lookupIps(unique),
    enabled:  on && unique.length > 0,
    staleTime: 5 * 60_000,
    retry: 1,
  });

  const results: Record<string, GeoEntry> = lookup.data?.results ?? {};

  return {
    enabled:   on,
    unsupported: status.isError,
    results,
    countries: countByCountry(ips, results),
    loading:   status.isLoading || lookup.isLoading,
  };
}
