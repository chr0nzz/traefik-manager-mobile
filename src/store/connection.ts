import * as SecureStore from 'expo-secure-store';
import { create } from 'zustand';

const KEY_URL = 'tm_base_url';
const KEY_API = 'tm_api_key';

interface ConnectionState {
  baseUrl: string;
  apiKey: string;
  ready: boolean;
  demoMode: boolean;
  setConnection: (baseUrl: string, apiKey: string) => Promise<void>;
  loadConnection: () => Promise<void>;
  clearConnection: () => Promise<void>;
  enterDemoMode: () => void;
  exitDemoMode: () => void;
}

export const useConnection = create<ConnectionState>((set) => ({
  baseUrl: '',
  apiKey: '',
  ready: false,
  demoMode: false,

  setConnection: async (baseUrl, apiKey) => {
    await SecureStore.setItemAsync(KEY_URL, baseUrl);
    await SecureStore.setItemAsync(KEY_API, apiKey);
    set({ baseUrl, apiKey, demoMode: false });
  },

  loadConnection: async () => {
    const baseUrl = await SecureStore.getItemAsync(KEY_URL) ?? '';
    const apiKey  = await SecureStore.getItemAsync(KEY_API)  ?? '';
    set({ baseUrl, apiKey, ready: true });
  },

  clearConnection: async () => {
    await SecureStore.deleteItemAsync(KEY_URL);
    await SecureStore.deleteItemAsync(KEY_API);
    set({ baseUrl: '', apiKey: '', demoMode: false });
  },

  enterDemoMode: () => set({ demoMode: true }),
  exitDemoMode:  () => set({ demoMode: false }),
}));
