import { apiFetch, apiFormPost, apiPost, activeAgentId } from './client';

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
  servers?: string[];
  sticky?: Record<string, unknown>;
  stickyEnabled?: boolean;
  healthCheck?: Record<string, unknown>;
  priority?: number | null;
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
  const agentId = activeAgentId();
  return apiFetch(agentId ? `/api/agents/${agentId}/routes` : '/api/routes');
}

export function toggleRoute(id: string, enable: boolean): Promise<{ ok: boolean; message?: string }> {
  const agentId = activeAgentId();
  return apiPost(`/api/routes/${encodeURIComponent(id)}/toggle`, agentId ? { enable, agent_id: agentId } : { enable });
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
  httpRule?: string;
  tcpRule?: string;
  tcpEntryPoints?: string;
  udpEntryPoint?: string;
  tlsMainDomain?: string;
  tlsSans?: string[];
  tlsOptions?: string;
}

export function saveRoute(
  data: RouteFormData,
  isEdit = false,
  originalId = '',
): Promise<{ ok: boolean; message?: string }> {
  const slot = data.protocol === 'tcp' ? 1 : data.protocol === 'udp' ? 2 : 0;
  const ips    = ['', '', ''];
  const ports  = ['', '', ''];
  ips[slot]    = data.targetIp;
  ports[slot]  = data.targetPort;

  const base: Record<string, string | string[]> = {
    serviceName: data.serviceName,
    targetIp:    ips,
    targetPort:  ports,
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
    if (data.httpRule) base.httpRule = data.httpRule;
    if (data.tlsMainDomain) base.tls_main_domain = data.tlsMainDomain;
    if (data.tlsSans && data.tlsSans.length > 0) base['tls_sans[]'] = data.tlsSans;
    if (data.tlsOptions) base.tls_options = data.tlsOptions;
  } else if (data.protocol === 'tcp') {
    base.tcpRule     = data.tcpRule ?? '';
    base.entryPoints = ['', data.tcpEntryPoints || ''];
    base.certResolver = data.certResolver ?? '';
    if (data.tlsMainDomain) base.tls_main_domain = data.tlsMainDomain;
    if (data.tlsSans && data.tlsSans.length > 0) base['tls_sans[]'] = data.tlsSans;
    if (data.tlsOptions) base.tls_options = data.tlsOptions;
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
