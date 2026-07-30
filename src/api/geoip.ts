import { apiFetch, apiPost } from './client';

export interface GeoIpStatus {
  enabled: boolean;
  available: boolean;
  db_path: string;
  db_date: string | null;
}

export interface GeoEntry {
  country_code: string;
  country_name: string;
}

export interface GeoLookupResponse {
  enabled: boolean;
  available: boolean;
  results: Record<string, GeoEntry>;
}

export interface CountryCount {
  code: string;
  name: string;
  count: number;
}

export function getGeoIpStatus(): Promise<GeoIpStatus> {
  return apiFetch('/api/geoip/status');
}

export function lookupIps(ips: string[]): Promise<GeoLookupResponse> {
  return apiPost('/api/geoip/lookup', { ips });
}

export function flagEmoji(cc: string): string {
  if (!cc || cc.length !== 2) return '';
  const base = 0x1f1e6;
  const a = cc.toUpperCase().charCodeAt(0) - 65;
  const b = cc.toUpperCase().charCodeAt(1) - 65;
  if (a < 0 || a > 25 || b < 0 || b > 25) return '';
  return String.fromCodePoint(base + a, base + b);
}

const IPV4 = /\b(?:\d{1,3}\.){3}\d{1,3}\b/;

export function extractIp(line: string): string {
  const trimmed = line.trim();
  if (trimmed.startsWith('{')) {
    try {
      const j = JSON.parse(trimmed);
      const v = j.ClientHost || String(j.ClientAddr || '').split(':')[0] || '';
      if (v) return v;
    } catch {}
  }
  const clf = trimmed.match(/^(\S+) \S+ \S+ \[/);
  if (clf) return clf[1];
  const any = trimmed.match(IPV4);
  return any ? any[0] : '';
}

export function countByCountry(
  ips: string[],
  results: Record<string, GeoEntry>,
): CountryCount[] {
  const byCode: Record<string, CountryCount> = {};
  for (const ip of ips) {
    const geo = results[ip];
    if (!geo || !geo.country_code) continue;
    const entry = byCode[geo.country_code]
      || (byCode[geo.country_code] = { code: geo.country_code, name: geo.country_name, count: 0 });
    entry.count += 1;
  }
  return Object.values(byCode).sort((a, b) => b.count - a.count || a.name.localeCompare(b.name));
}
