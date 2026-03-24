import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Backup, createBackup, deleteBackup, getBackups, restoreBackup } from '../api/backups';

export function useBackups() {
  return useQuery({
    queryKey: ['backups'],
    queryFn: getBackups,
    staleTime: 30_000,
    retry: 2,
  });
}

export function useBackupMutations() {
  const qc = useQueryClient();
  const invalidate = () => qc.invalidateQueries({ queryKey: ['backups'] });

  const create = useMutation({ mutationFn: createBackup, onSuccess: invalidate });
  const restore = useMutation({ mutationFn: restoreBackup, onSuccess: invalidate });
  const remove = useMutation({ mutationFn: deleteBackup, onSuccess: invalidate });

  return { create, restore, remove };
}
