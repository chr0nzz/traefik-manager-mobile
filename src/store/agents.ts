import * as SecureStore from 'expo-secure-store';
import { create } from 'zustand';
import { AgentInfo } from '../api/agents';

const KEY_ACTIVE_AGENT = 'tm_active_agent_id';

interface AgentsState {
  agents: AgentInfo[];
  activeAgentId: string | null;
  setAgents: (agents: AgentInfo[]) => void;
  setActiveAgent: (id: string | null) => Promise<void>;
  load: () => Promise<void>;
}

export const useAgentsStore = create<AgentsState>((set) => ({
  agents: [],
  activeAgentId: null,

  setAgents: (agents) => set({ agents }),

  setActiveAgent: async (id) => {
    if (id) {
      await SecureStore.setItemAsync(KEY_ACTIVE_AGENT, id);
    } else {
      await SecureStore.deleteItemAsync(KEY_ACTIVE_AGENT);
    }
    set({ activeAgentId: id });
  },

  load: async () => {
    const stored = await SecureStore.getItemAsync(KEY_ACTIVE_AGENT);
    set({ activeAgentId: stored ?? null });
  },
}));
