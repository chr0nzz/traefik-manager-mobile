import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Backup, createBackup, createStaticBackup, deleteBackup, getBackups, restoreBackup } from '../api/backups';
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
