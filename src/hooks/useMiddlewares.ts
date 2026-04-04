import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { deleteMiddleware, getMiddlewares, saveMiddleware } from '../api/middlewares';
import { useConnection } from '../store/connection';
import { DEMO_MIDDLEWARES } from '../demo/data';

export function useMiddlewares() {
  const demoMode = useConnection(s => s.demoMode);
  return useQuery({
    queryKey: ['middlewares'],
    queryFn: demoMode ? () => DEMO_MIDDLEWARES : getMiddlewares,
    staleTime: 30_000,
    retry: demoMode ? 0 : 2,
  });
}

export function useSaveMiddleware() {
  const qc = useQueryClient();
  const demoMode = useConnection(s => s.demoMode);
  return useMutation({
    mutationFn: async ({ name, content, isEdit, originalId, configFile }: { name: string; content: string; isEdit?: boolean; originalId?: string; configFile?: string }) => {
      if (demoMode) {
        const current = (qc.getQueryData<typeof DEMO_MIDDLEWARES>(['middlewares']) ?? DEMO_MIDDLEWARES) as typeof DEMO_MIDDLEWARES;
        const entry = { name: `${name}@file`, type: 'custom', status: 'enabled', provider: 'file', _proto: 'http' };
        if (isEdit && originalId) {
          qc.setQueryData(['middlewares'], current.map(m => m.name === originalId ? { ...m, name: `${name}@file` } : m));
        } else {
          qc.setQueryData(['middlewares'], [...current, entry]);
        }
        return { ok: true };
      }
      return saveMiddleware(name, content, isEdit, originalId, configFile);
    },
    onSuccess: () => {
      if (!demoMode) qc.invalidateQueries({ queryKey: ['middlewares'] });
    },
  });
}

export function useDeleteMiddleware() {
  const qc = useQueryClient();
  const demoMode = useConnection(s => s.demoMode);
  return useMutation({
    mutationFn: async ({ name, configFile }: { name: string; configFile?: string }) => {
      if (demoMode) {
        const current = (qc.getQueryData<typeof DEMO_MIDDLEWARES>(['middlewares']) ?? DEMO_MIDDLEWARES) as typeof DEMO_MIDDLEWARES;
        qc.setQueryData(['middlewares'], current.filter(m => m.name !== name));
        return { ok: true };
      }
      return deleteMiddleware(name, configFile);
    },
    onSuccess: () => {
      if (!demoMode) qc.invalidateQueries({ queryKey: ['middlewares'] });
    },
  });
}
