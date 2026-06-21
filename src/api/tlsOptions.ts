import { apiFetch } from './client';

export interface TLSOption {
  name: string;
}

export function getTLSOptions(): Promise<TLSOption[]> {
  return apiFetch('/api/tls-options');
}
