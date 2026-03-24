import { apiFetch } from './client';

export interface TraefikOverview {
  http: { routers: { total: number; warnings: number; errors: number };
           services: { total: number; warnings: number; errors: number };
           middlewares: { total: number; warnings: number; errors: number } };
  tcp:  { routers: { total: number; warnings: number; errors: number };
           services: { total: number; warnings: number; errors: number } };
  udp:  { routers: { total: number; warnings: number; errors: number };
           services: { total: number; warnings: number; errors: number } };
}

export interface TraefikService {
  name: string;
  type?: string;
  status: string;
  serverStatus?: Record<string, string>;
  _proto?: string;
  provider?: string;
  usedBy?: string[];
  loadBalancer?: {
    passHostHeader?: boolean;
    servers?: Array<{ url?: string; address?: string }>;
  };
  [key: string]: unknown;
}

export interface TraefikVersion {
  Version: string;
  Codename: string;
}

export interface TraefikEntrypoint {
  name: string;
  address: string;
}

export function getOverview(): Promise<TraefikOverview> {
  return apiFetch('/api/traefik/overview');
}

export async function getServices(): Promise<TraefikService[]> {
  const data = await apiFetch<{ http?: TraefikService[]; tcp?: TraefikService[]; udp?: TraefikService[] }>(
    '/api/traefik/services',
  );
  const http = (data.http ?? []).map(s => ({ ...s, _proto: 'http' }));
  const tcp  = (data.tcp  ?? []).map(s => ({ ...s, _proto: 'tcp'  }));
  const udp  = (data.udp  ?? []).map(s => ({ ...s, _proto: 'udp'  }));
  return [...http, ...tcp, ...udp];
}

export function getVersion(): Promise<TraefikVersion> {
  return apiFetch('/api/traefik/version');
}

export function getEntrypoints(): Promise<TraefikEntrypoint[]> {
  return apiFetch('/api/traefik/entrypoints');
}

export function getApiKeyStatus(): Promise<{ enabled: boolean; has_key: boolean }> {
  return apiFetch('/api/auth/apikey/status');
}
