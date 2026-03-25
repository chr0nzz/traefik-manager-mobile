import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AppSettings, getSettings, saveSettings } from '../api/settings';

export function useSettings() {
  return useQuery({
    queryKey: ['settings'],
    queryFn: getSettings,
    staleTime: 60_000,
    retry: 2,
  });
}

export function useSaveSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (s: Partial<AppSettings>) => saveSettings(s),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['settings'] }),
  });
}
