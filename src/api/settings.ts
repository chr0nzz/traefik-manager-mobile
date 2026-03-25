import { apiFetch, apiPost } from './client';

export interface AppSettings {
  domains: string[];
  cert_resolver: string;
  traefik_api_url: string;
  auth_enabled: boolean;
  visible_tabs: Record<string, boolean>;
}

export function getSettings(): Promise<AppSettings> {
  return apiFetch('/api/settings');
}

export function saveSettings(s: Partial<AppSettings>): Promise<{ ok: boolean; message?: string }> {
  return apiPost('/api/settings', s);
}

export function testTraefikUrl(url: string): Promise<{ ok: boolean; version?: string; error?: string }> {
  return apiPost('/api/setup/test-connection', { url });
}
