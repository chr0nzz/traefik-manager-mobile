import { apiFetch, apiPost } from './client';

export interface Backup {
  name: string;
  size: number;
  modified: string;
}

export function getBackups(): Promise<Backup[]> {
  return apiFetch('/api/backups');
}

export function createBackup(): Promise<{ ok: boolean }> {
  return apiPost('/api/backup/create');
}

export function restoreBackup(name: string): Promise<{ ok: boolean; message?: string }> {
  return apiPost(`/api/restore/${encodeURIComponent(name)}`);
}

export function deleteBackup(name: string): Promise<{ ok: boolean }> {
  return apiPost(`/api/backup/delete/${encodeURIComponent(name)}`);
}
