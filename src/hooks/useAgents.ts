import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AgentHealth, AgentInfo, getAgentHealth, getAgents, renameAgent } from '../api/agents';
import { useConnection } from '../store/connection';

export function useAgents() {
  const demoMode = useConnection(s => s.demoMode);
  return useQuery<AgentInfo[]>({
    queryKey: ['agents'],
    queryFn: demoMode ? () => [] : getAgents,
    staleTime: 30_000,
    retry: demoMode ? 0 : 2,
  });
}

export function useRenameAgent() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, name }: { id: string; name: string }) => renameAgent(id, name),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['agents'] }),
  });
}

export function useAgentHealth(id: string) {
  const demoMode = useConnection(s => s.demoMode);
  return useQuery<AgentHealth>({
    queryKey: ['agent-health', id],
    queryFn: demoMode ? () => ({ ok: true, version: '1.9.0', latency_ms: 12 }) : () => getAgentHealth(id),
    staleTime: 30_000,
    refetchInterval: 30_000,
    retry: 1,
  });
}
