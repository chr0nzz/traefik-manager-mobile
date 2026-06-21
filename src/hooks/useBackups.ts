import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Backup, GitCommit, GitStatus, createBackup, createStaticBackup, deleteBackup, getBackups, getGitCommits, getGitStatus, gitPush, restoreBackup, restoreFromGit } from '../api/backups';
import { DEMO_GIT_COMMITS, DEMO_GIT_STATUS } from '../demo/data';
import { useConnection } from '../store/connection';

export function useBackups() {
  const demoMode = useConnection(s => s.demoMode);
  return useQuery<Backup[]>({
    queryKey: ['backups'],
    queryFn: demoMode ? () => [] : getBackups,
    staleTime: 30_000,
    retry: demoMode ? 0 : 2,
  });
}

export function useBackupMutations() {
  const qc = useQueryClient();
  const demoMode = useConnection(s => s.demoMode);
  const invalidate = () => qc.invalidateQueries({ queryKey: ['backups'] });

  const noop        = async (): Promise<{ ok: boolean }> => ({ ok: true });
  const noopRestore = async (_: string): Promise<{ ok: boolean; message?: string }> => ({ ok: true });
  const noopNamed   = async (_: string): Promise<{ ok: boolean }> => ({ ok: true });

  const create       = useMutation({ mutationFn: demoMode ? noop : createBackup,             onSuccess: demoMode ? undefined : invalidate });
  const createStatic = useMutation({ mutationFn: demoMode ? noop : createStaticBackup,       onSuccess: demoMode ? undefined : invalidate });
  const restore      = useMutation({ mutationFn: demoMode ? noopRestore : restoreBackup,     onSuccess: demoMode ? undefined : invalidate });
  const remove       = useMutation({ mutationFn: demoMode ? noopNamed   : deleteBackup,      onSuccess: demoMode ? undefined : invalidate });

  return { create, createStatic, restore, remove };
}

export function useGitStatus() {
  const demoMode = useConnection(s => s.demoMode);
  return useQuery<GitStatus>({
    queryKey: ['git-status'],
    queryFn: demoMode ? () => DEMO_GIT_STATUS as GitStatus : getGitStatus,
    staleTime: 30_000,
    retry: demoMode ? 0 : 1,
  });
}

export function useGitCommits() {
  const demoMode = useConnection(s => s.demoMode);
  return useQuery<GitCommit[]>({
    queryKey: ['git-commits'],
    queryFn: demoMode ? () => DEMO_GIT_COMMITS as GitCommit[] : getGitCommits,
    staleTime: 30_000,
    retry: demoMode ? 0 : 1,
  });
}

export function useGitMutations() {
  const qc = useQueryClient();
  const demoMode = useConnection(s => s.demoMode);
  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['git-status'] });
    qc.invalidateQueries({ queryKey: ['git-commits'] });
  };

  const noop      = async (): Promise<{ ok: boolean }> => ({ ok: true });
  const noopSha   = async (_: string): Promise<{ ok: boolean }> => ({ ok: true });

  const push    = useMutation({ mutationFn: demoMode ? noop : gitPush,                         onSuccess: demoMode ? undefined : invalidate });
  const restore = useMutation({ mutationFn: demoMode ? noopSha : restoreFromGit,               onSuccess: demoMode ? undefined : invalidate });

  return { push, restore };
}
