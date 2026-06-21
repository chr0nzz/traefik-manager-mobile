import { apiFetch, activeAgentId } from './client';

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

const isFileEntry = (r: any) => ((r?.provider || (r?.name ?? '').split('@')[1] || '') === 'file');

function countStatus(items: any): { total: number; warnings: number; errors: number } {
  const arr = (Array.isArray(items) ? items : []).filter(isFileEntry);
  let warnings = 0, errors = 0;
  for (const it of arr) {
    const s = String(it?.status ?? '').toLowerCase();
    if (s === 'warning') warnings++;
    else if (s === 'error') errors++;
  }
  return { total: arr.length, warnings, errors };
}

export async function getOverview(): Promise<TraefikOverview> {
  if (!activeAgentId()) {
    return apiFetch('/api/traefik/overview');
  }
  // Agents: build a file-provider overview from the lists, matching the routes/
  // services/middlewares tabs (Traefik's /api/overview counts all providers).
  const [routers, services, mws] = await Promise.all([
    apiFetch<any>('/api/traefik/routers'),
    apiFetch<any>('/api/traefik/services'),
    apiFetch<any>('/api/traefik/middlewares'),
  ]);
  return {
    http: {
      routers:     countStatus(routers?.http),
      services:    countStatus(services?.http),
      middlewares: countStatus(mws?.http),
    },
    tcp: {
      routers:  countStatus(routers?.tcp),
      services: countStatus(services?.tcp),
    },
    udp: {
      routers:  countStatus(routers?.udp),
      services: countStatus(services?.udp),
    },
  } as TraefikOverview;
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

export interface ManagerVersion {
  version: string;
  repo: string;
}

export function getManagerVersion(): Promise<ManagerVersion> {
  return apiFetch('/api/manager/version');
}

export function getEntrypoints(): Promise<TraefikEntrypoint[]> {
  return apiFetch('/api/traefik/entrypoints');
}

export function getApiKeyStatus(): Promise<{ enabled: boolean; has_key: boolean }> {
  return apiFetch('/api/auth/apikey/status');
}

export interface RouteOverride {
  icon_type?: 'auto' | 'slug' | 'url';
  icon_slug?: string;
  icon_url?:  string;
  display_name?: string;
  group?: string;
}

export interface DashboardConfig {
  custom_groups:   unknown[];
  route_overrides: Record<string, RouteOverride>;
}

export function getDashboardConfig(): Promise<DashboardConfig> {
  return apiFetch('/api/dashboard/config');
}

export interface Cert {
  resolver: string;
  main:     string;
  sans:     string[];
  not_after: string | null;
}

export interface CertsResponse {
  certs:  Cert[];
  errors: string[];
}

export function getCerts(): Promise<CertsResponse> {
  return apiFetch('/api/traefik/certs');
}

export interface Plugin {
  name:       string;
  moduleName: string;
  version:    string;
}

export interface PluginsResponse {
  plugins: Plugin[];
  error?:  string;
}

export function getPlugins(): Promise<PluginsResponse> {
  return apiFetch('/api/traefik/plugins');
}
