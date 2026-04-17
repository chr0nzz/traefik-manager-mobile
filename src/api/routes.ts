import { apiFetch, apiFormPost, apiPost } from './client';

export interface Route {
  id: string;
  name: string;
  protocol: string;
  rule: string;
  service_name: string;
  target: string;
  tls: boolean;
  enabled: boolean;
  middlewares?: string[];
  entryPoints?: string[];
  passHostHeader?: boolean;
  certResolver?: string;
  configFile?: string;
  provider?: string;
  insecureSkipVerify?: boolean;
}

export function domainFromRule(rule: string): string {
  if (!rule) return '';
  const matches = [...rule.matchAll(/Host\(`([^`]+)`\)/gi)].map(m => m[1]);
  return matches.join(', ');
}

export function domainsFromRule(rule: string): string[] {
  if (!rule) return [];
  return [...rule.matchAll(/Host\(`([^`]+)`\)/gi)].map(m => m[1]);
}

export function getRoutes(): Promise<{ apps: Route[]; middlewares: unknown[] }> {
  return apiFetch('/api/routes');
}

export function toggleRoute(id: string, enable: boolean): Promise<{ ok: boolean; message?: string }> {
  return apiPost(`/api/routes/${encodeURIComponent(id)}/toggle`, { enable });
}

export interface RouteFormData {
  serviceName: string;
  protocol: string;
  targetIp: string;
  targetPort: string;
  configFile?: string;
  subdomain?: string;
  domains?: string[];
  entryPoints?: string;
  middlewares?: string;
  scheme?: string;
  passHostHeader?: boolean;
  certResolver?: string;
  insecureSkipVerify?: boolean;
  tcpRule?: string;
  tcpEntryPoints?: string;
  udpEntryPoint?: string;
}

export function saveRoute(
  data: RouteFormData,
  isEdit = false,
  originalId = '',
): Promise<{ ok: boolean; message?: string }> {
  const base: Record<string, string | string[]> = {
    serviceName: data.serviceName,
    targetIp:    data.targetIp,
    targetPort:  data.targetPort,
    protocol:    data.protocol,
    configFile:  data.configFile ?? '',
    isEdit:      isEdit ? 'true' : 'false',
    originalId,
  };

  if (data.protocol === 'http') {
    base.subdomain         = data.subdomain ?? '';
    base.domains           = data.domains && data.domains.length > 0 ? data.domains : [];
    base.entryPoints       = [data.entryPoints || 'https', ''];
    base.middlewares       = data.middlewares ?? '';
    base.scheme            = data.scheme ?? 'http';
    base.passHostHeader    = data.passHostHeader !== false ? 'true' : '';
    base.certResolver      = data.certResolver ?? '';
    base.insecureSkipVerify = data.insecureSkipVerify ? 'true' : '';
  } else if (data.protocol === 'tcp') {
    base.tcpRule     = data.tcpRule ?? '';
    base.entryPoints = ['', data.tcpEntryPoints || ''];
    base.certResolver = data.certResolver ?? '';
  } else if (data.protocol === 'udp') {
    base.udpEntryPoint = data.udpEntryPoint ?? '';
  }

  return apiFormPost('/save', base);
}

export function deleteRoute(id: string, configFile = ''): Promise<{ ok: boolean; message?: string }> {
  return apiFormPost(`/delete/${encodeURIComponent(id)}`, { configFile });
}

export interface Entrypoint {
  name: string;
  address: string;
}

export function getEntrypoints(): Promise<Entrypoint[]> {
  return apiFetch('/api/traefik/entrypoints');
}

export interface MiddlewareEntry {
  name: string;
  type?: string;
}

export function getMiddlewares(): Promise<{ http: MiddlewareEntry[]; tcp: MiddlewareEntry[] }> {
  return apiFetch('/api/traefik/middlewares');
}

export interface ConfigEntry {
  label: string;
  path: string;
}

export interface ConfigsResponse {
  files: ConfigEntry[];
  configDirSet: boolean;
}

export function getConfigs(): Promise<ConfigsResponse> {
  return apiFetch('/api/configs');
}

export interface LogsResponse {
  lines: string[];
  error?: string;
}

export function getLogs(lines = 100): Promise<LogsResponse> {
  return apiFetch(`/api/traefik/logs?lines=${lines}`);
}
