import { apiFetch, apiFormPost } from './client';

export interface Middleware {
  name: string;
  type?: string;
  status: string;
  provider?: string;
  _proto?: string;
  [key: string]: unknown;
}

export function saveMiddleware(
  name: string,
  content: string,
  isEdit = false,
  originalId = '',
): Promise<{ ok: boolean; message?: string }> {
  return apiFormPost('/save-middleware', {
    middlewareName: name,
    middlewareContent: content,
    isMwEdit: isEdit ? 'true' : 'false',
    originalMwId: originalId,
  });
}

export function deleteMiddleware(name: string): Promise<{ ok: boolean; message?: string }> {
  return apiFormPost(`/delete-middleware/${encodeURIComponent(name)}`, {});
}

export async function getMiddlewares(): Promise<Middleware[]> {
  const data = await apiFetch<{ http?: Middleware[]; tcp?: Middleware[] }>('/api/traefik/middlewares');
  const http = (data.http ?? []).map(m => ({ ...m, _proto: 'http' }));
  const tcp  = (data.tcp  ?? []).map(m => ({ ...m, _proto: 'tcp'  }));
  return [...http, ...tcp];
}
