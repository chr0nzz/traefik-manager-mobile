import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { RouteFormData, deleteRoute, getRoutes, saveRoute, toggleRoute } from '../api/routes';

export function useRoutes() {
  return useQuery({
    queryKey: ['routes'],
    queryFn: getRoutes,
    staleTime: 15_000,
    retry: 2,
  });
}

export function useToggleRoute() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, enable }: { id: string; enable: boolean }) => toggleRoute(id, enable),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['routes'] }),
  });
}

export function useSaveRoute() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ data, isEdit, originalId }: { data: RouteFormData; isEdit?: boolean; originalId?: string }) =>
      saveRoute(data, isEdit, originalId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['routes'] }),
  });
}

export function useDeleteRoute() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteRoute(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['routes'] }),
  });
}
