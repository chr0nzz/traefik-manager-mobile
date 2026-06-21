import { apiFetch } from './client';

export interface AgentInfo {
  id: string;
  name: string;
  url: string;
  domains?: string[];
}

export interface AgentHealth {
  ok: boolean;
  version: string;
  latency_ms: number;
}

export async function getAgents(): Promise<AgentInfo[]> {
  const res: any = await apiFetch('/api/agents');
  if (Array.isArray(res)) return res;
  return Array.isArray(res?.agents) ? res.agents : [];
}

export function getAgentHealth(id: string): Promise<AgentHealth> {
  return apiFetch(`/api/agents/${encodeURIComponent(id)}/health`);
}

export function renameAgent(id: string, name: string): Promise<{ ok: boolean }> {
  return apiFetch(`/api/agents/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  });
}
