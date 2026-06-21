import { useConnection } from '../store/connection';
import { useAgentsStore } from '../store/agents';

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

const AGENT_PROXY_PREFIXES = [
  '/api/traefik/',
  '/api/backups',
  '/api/backup/',
  '/api/restore/',
  '/api/crowdsec/',
  '/api/static',
  '/api/configs',
];

function buildPath(path: string): string {
  const { activeAgentId } = useAgentsStore.getState();
  if (activeAgentId && AGENT_PROXY_PREFIXES.some(p => path.startsWith(p))) {
    // TM's proxy re-prepends /api/, so strip it here (matches the web app)
    return `/api/agents/proxy/${activeAgentId}/${path.replace(/^\/api\//, '')}`;
  }
  return path;
}

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const { baseUrl, apiKey } = useConnection.getState();
  if (!baseUrl || !apiKey) throw new ApiError(0, 'Not connected');

  const url = `${baseUrl}${buildPath(path)}`;
  const headers: Record<string, string> = {
    'X-Api-Key': apiKey,
    'X-Requested-With': 'fetch',
    ...(options.headers as Record<string, string> ?? {}),
  };
  if (options.body && typeof options.body === 'string') {
    headers['Content-Type'] = 'application/json';
  }

  const res = await fetch(url, { ...options, headers });

  if (res.status === 401) {
    throw new ApiError(401, 'Invalid API key');
  }
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    throw new ApiError(res.status, text);
  }
  return res.json() as Promise<T>;
}

export async function apiPost<T>(path: string, body?: unknown): Promise<T> {
  return apiFetch<T>(path, {
    method: 'POST',
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
}

export function activeAgentId(): string | null {
  return useAgentsStore.getState().activeAgentId;
}

export async function apiFormPost<T>(path: string, params: Record<string, string | string[]>): Promise<T> {
  const { baseUrl, apiKey } = useConnection.getState();
  if (!baseUrl || !apiKey) throw new ApiError(0, 'Not connected');
  const sp = new URLSearchParams();
  const agentId = useAgentsStore.getState().activeAgentId;
  if (agentId) sp.append('agent_id', agentId);
  for (const [key, val] of Object.entries(params)) {
    if (Array.isArray(val)) {
      val.forEach(v => sp.append(key, v));
    } else {
      sp.append(key, val);
    }
  }
  const res = await fetch(`${baseUrl}${path}`, {
    method: 'POST',
    headers: {
      'X-Api-Key': apiKey,
      'X-Requested-With': 'fetch',
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: sp.toString(),
  });
  if (res.status === 401) throw new ApiError(401, 'Invalid API key');
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText);
    throw new ApiError(res.status, text);
  }
  return res.json() as Promise<T>;
}
