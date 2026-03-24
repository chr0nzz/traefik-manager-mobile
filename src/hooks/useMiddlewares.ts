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
    mutationFn: ({ name, content, isEdit, originalId }: { name: string; content: string; isEdit?: boolean; originalId?: string }) =>
      saveMiddleware(name, content, isEdit, originalId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['middlewares'] }),
  });
}

export function useDeleteMiddleware() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => deleteMiddleware(name),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['middlewares'] }),
  });
}
