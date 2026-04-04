import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Backup, createBackup, deleteBackup, getBackups, restoreBackup } from '../api/backups';
import { useConnection } from '../store/connection';

export function useBackups() {
  const demoMode = useConnection(s => s.demoMode);
  return useQuery({
    queryKey: ['backups'],
    queryFn: demoMode ? () => ({ backups: [] as Backup[] }) : getBackups,
    staleTime: 30_000,
    retry: demoMode ? 0 : 2,
  });
}

export function useBackupMutations() {
  const qc = useQueryClient();
  const demoMode = useConnection(s => s.demoMode);
  const invalidate = () => qc.invalidateQueries({ queryKey: ['backups'] });

  const noop = async () => ({ ok: false as const });

  const create  = useMutation({ mutationFn: demoMode ? noop : createBackup,  onSuccess: demoMode ? undefined : invalidate });
  const restore = useMutation({ mutationFn: demoMode ? noop : restoreBackup, onSuccess: demoMode ? undefined : invalidate });
  const remove  = useMutation({ mutationFn: demoMode ? noop : deleteBackup,  onSuccess: demoMode ? undefined : invalidate });

  return { create, restore, remove };
}
