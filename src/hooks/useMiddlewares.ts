import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { deleteMiddleware, getMiddlewares, saveMiddleware } from '../api/middlewares';

export function useMiddlewares() {
  return useQuery({
    queryKey: ['middlewares'],
    queryFn: getMiddlewares,
    staleTime: 30_000,
  });
}

export function useSaveMiddleware() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ name, content, isEdit, originalId, configFile }: { name: string; content: string; isEdit?: boolean; originalId?: string; configFile?: string }) =>
      saveMiddleware(name, content, isEdit, originalId, configFile),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['middlewares'] }),
  });
}

export function useDeleteMiddleware() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ name, configFile }: { name: string; configFile?: string }) => deleteMiddleware(name, configFile),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['middlewares'] }),
  });
}
