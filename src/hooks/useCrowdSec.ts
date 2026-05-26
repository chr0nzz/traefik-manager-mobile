import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getCrowdSecDecisions,
  getCrowdSecAlerts,
  deleteCrowdSecDecision,
  type CrowdSecDecision,
} from '../api/crowdsec';
import { useConnection } from '../store/connection';
import { DEMO_CROWDSEC_DECISIONS, DEMO_CROWDSEC_ALERTS } from '../demo/data';

export function useCrowdSecDecisions() {
  const demoMode = useConnection(s => s.demoMode);
  return useQuery({
    queryKey: ['crowdsec-decisions'],
    queryFn: demoMode ? () => DEMO_CROWDSEC_DECISIONS as CrowdSecDecision[] : getCrowdSecDecisions,
    staleTime: 30_000,
    retry: demoMode ? 0 : 1,
  });
}

export function useCrowdSecAlerts() {
  const demoMode = useConnection(s => s.demoMode);
  return useQuery({
    queryKey: ['crowdsec-alerts'],
    queryFn: demoMode ? () => DEMO_CROWDSEC_ALERTS : getCrowdSecAlerts,
    staleTime: 30_000,
    retry: demoMode ? 0 : 1,
  });
}

export function useDeleteDecision() {
  const qc = useQueryClient();
  const demoMode = useConnection(s => s.demoMode);
  return useMutation({
    mutationFn: async (id: number) => {
      if (demoMode) {
        const current = qc.getQueryData<CrowdSecDecision[]>(['crowdsec-decisions']) ?? [];
        qc.setQueryData(['crowdsec-decisions'], current.filter(d => d.id !== id));
        return { ok: true };
      }
      return deleteCrowdSecDecision(id);
    },
    onSuccess: () => {
      if (!demoMode) {
        qc.invalidateQueries({ queryKey: ['crowdsec-decisions'] });
      }
    },
  });
}
