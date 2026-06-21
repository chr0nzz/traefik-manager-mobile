import { apiFetch, apiPost } from './client';

export interface Backup {
  name: string;
  size: number;
  modified: string;
  kind?: string;
}

export interface GitStatus {
  enabled: boolean;
  repo?: string;
  branch?: string;
  last_push?: string;
}

export interface GitCommit {
  sha: string;
  message: string;
  timestamp: string;
}

export async function getBackups(): Promise<Backup[]> {
  const res: any = await apiFetch('/api/backups');
  const arr = Array.isArray(res) ? res : (Array.isArray(res?.backups) ? res.backups : []);
  return arr.map((b: any) => ({ ...b, modified: b.modified || b.date || '' }));
}

export function createBackup(): Promise<{ ok: boolean }> {
  return apiPost('/api/backup/create');
}

export function createStaticBackup(): Promise<{ ok: boolean }> {
  return apiPost('/api/static/backup/create');
}

export function restoreBackup(name: string): Promise<{ ok: boolean; message?: string }> {
  return apiPost(`/api/restore/${encodeURIComponent(name)}`);
}

export function deleteBackup(name: string): Promise<{ ok: boolean }> {
  return apiPost(`/api/backup/delete/${encodeURIComponent(name)}`);
}

export function getGitStatus(): Promise<GitStatus> {
  return apiFetch('/api/backup/git/status');
}

export function gitPush(): Promise<{ ok: boolean }> {
  return apiPost('/api/backup/git/push');
}

export function getGitCommits(): Promise<GitCommit[]> {
  return apiFetch('/api/backup/git/commits');
}

export function restoreFromGit(sha: string): Promise<{ ok: boolean }> {
  return apiPost(`/api/backup/git/restore/${encodeURIComponent(sha)}`);
}
