import { apiFetch } from './client';

export interface CrowdSecDecision {
  id: number;
  value: string;
  type: string;
  duration: string;
  scenario: string;
  origin: string;
}

export interface CrowdSecAlert {
  startAt: string;
  source: { ip: string };
  scenario: string;
  decisions: CrowdSecDecision[];
}

export function getCrowdSecDecisions(): Promise<CrowdSecDecision[]> {
  return apiFetch('/api/crowdsec/decisions');
}

export function getCrowdSecAlerts(): Promise<CrowdSecAlert[]> {
  return apiFetch('/api/crowdsec/alerts');
}

export function deleteCrowdSecDecision(id: number): Promise<{ ok: boolean; error?: string }> {
  return apiFetch(`/api/crowdsec/decisions/${id}`, { method: 'DELETE' });
}
