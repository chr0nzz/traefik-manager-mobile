import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { RouteFormData, deleteRoute, getEntrypoints, getMiddlewares, getRoutes, saveRoute, toggleRoute } from '../api/routes';
import { useConnection } from '../store/connection';
import { DEMO_ROUTES_DATA } from '../demo/data';

export function useRoutes() {
  const demoMode = useConnection(s => s.demoMode);
  return useQuery({
    queryKey: ['routes'],
    queryFn: demoMode ? () => DEMO_ROUTES_DATA : getRoutes,
    staleTime: 15_000,
    retry: demoMode ? 0 : 2,
  });
}

export function useToggleRoute() {
  const qc = useQueryClient();
  const demoMode = useConnection(s => s.demoMode);
  return useMutation({
    mutationFn: async ({ id, enable }: { id: string; enable: boolean }) => {
      if (demoMode) {
        const current = qc.getQueryData<typeof DEMO_ROUTES_DATA>(['routes']) ?? DEMO_ROUTES_DATA;
        qc.setQueryData(['routes'], {
          ...current,
          apps: current.apps.map(r => r.id === id ? { ...r, enabled: enable } : r),
        });
        return { ok: true };
      }
      return toggleRoute(id, enable);
    },
    onSuccess: () => {
      if (!demoMode) qc.invalidateQueries({ queryKey: ['routes'] });
    },
  });
}

export function useSaveRoute() {
  const qc = useQueryClient();
  const demoMode = useConnection(s => s.demoMode);
  return useMutation({
    mutationFn: async ({ data, isEdit, originalId }: { data: RouteFormData; isEdit?: boolean; originalId?: string }) => {
      if (demoMode) {
        const current = qc.getQueryData<typeof DEMO_ROUTES_DATA>(['routes']) ?? DEMO_ROUTES_DATA;
        const id = data.subdomain || data.serviceName || String(Date.now());
        if (isEdit && originalId) {
          qc.setQueryData(['routes'], {
            ...current,
            apps: current.apps.map(r => r.id === originalId ? { ...r, ...data, id: originalId, name: originalId } : r),
          });
        } else {
          const newRoute = {
            id,
            name: id,
            protocol: data.protocol ?? 'HTTP',
            rule: `Host(\`${data.subdomain}\`)`,
            service_name: data.serviceName ?? id,
            target: `${data.scheme ?? 'http'}://${data.targetIp}:${data.targetPort}`,
            tls: !!data.certResolver,
            enabled: true,
            middlewares: data.middlewares ?? [],
            entryPoints: data.certResolver ? ['websecure'] : ['web'],
            certResolver: data.certResolver ?? '',
            configFile: data.configFile ?? 'dynamic.yml',
            provider: 'file',
          };
          qc.setQueryData(['routes'], { ...current, apps: [...current.apps, newRoute] });
        }
        return { ok: true };
      }
      return saveRoute(data, isEdit, originalId);
    },
    onSuccess: () => {
      if (!demoMode) qc.invalidateQueries({ queryKey: ['routes'] });
    },
  });
}

export function useEntrypoints() {
  const demoMode = useConnection(s => s.demoMode);
  return useQuery({
    queryKey: ['entrypoints'],
    queryFn: demoMode ? () => [] : getEntrypoints,
    staleTime: 60_000,
    retry: 1,
  });
}

export function useMiddlewares() {
  const demoMode = useConnection(s => s.demoMode);
  return useQuery({
    queryKey: ['middlewares-form'],
    queryFn: demoMode ? () => ({ http: [], tcp: [] }) : getMiddlewares,
    staleTime: 60_000,
    retry: 1,
  });
}

export function useDeleteRoute() {
  const qc = useQueryClient();
  const demoMode = useConnection(s => s.demoMode);
  return useMutation({
    mutationFn: async ({ id, configFile }: { id: string; configFile?: string }) => {
      if (demoMode) {
        const current = qc.getQueryData<typeof DEMO_ROUTES_DATA>(['routes']) ?? DEMO_ROUTES_DATA;
        qc.setQueryData(['routes'], { ...current, apps: current.apps.filter(r => r.id !== id) });
        return { ok: true };
      }
      return deleteRoute(id, configFile);
    },
    onSuccess: () => {
      if (!demoMode) qc.invalidateQueries({ queryKey: ['routes'] });
    },
  });
}
