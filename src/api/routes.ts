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
}

export function domainFromRule(rule: string): string {
  if (!rule) return '';
  const m = rule.match(/Host\(`([^`]+)`\)/i);
  return m ? m[1] : '';
}

export function getRoutes(): Promise<{ apps: Route[]; middlewares: unknown[] }> {
  return apiFetch('/api/routes');
}

export function toggleRoute(id: string, enable: boolean): Promise<{ ok: boolean; message?: string }> {
  return apiPost(`/api/routes/${encodeURIComponent(id)}/toggle`, { enable });
}

export interface RouteFormData {
  serviceName: string;
  subdomain: string;
  targetIp: string;
  targetPort: string;
  protocol: string;
  middlewares?: string;
  scheme?: string;
  passHostHeader?: boolean;
  certResolver?: string;
  configFile?: string;
}

export function saveRoute(
  data: RouteFormData,
  isEdit = false,
  originalId = '',
): Promise<{ ok: boolean; message?: string }> {
  return apiFormPost('/save', {
    serviceName: data.serviceName,
    subdomain: data.subdomain,
    targetIp: data.targetIp,
    targetPort: data.targetPort,
    protocol: data.protocol,
    middlewares: data.middlewares ?? '',
    scheme: data.scheme ?? 'http',
    passHostHeader: (data.passHostHeader !== false) ? 'true' : '',
    certResolver: data.certResolver ?? '',
    configFile: data.configFile ?? '',
    isEdit: isEdit ? 'true' : 'false',
    originalId,
  });
}

export function deleteRoute(id: string, configFile = ''): Promise<{ ok: boolean; message?: string }> {
  return apiFormPost(`/delete/${encodeURIComponent(id)}`, { configFile });
}

export interface ConfigEntry {
  label: string;
  path: string;
}

export function getConfigs(): Promise<ConfigEntry[]> {
  return apiFetch('/api/configs');
}
