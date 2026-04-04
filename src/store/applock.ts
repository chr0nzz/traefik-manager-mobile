import * as SecureStore from 'expo-secure-store';
import { create } from 'zustand';

const KEY = 'tm_app_lock_enabled';

interface AppLockState {
  enabled: boolean;
  ready: boolean;
  load: () => Promise<void>;
  setEnabled: (v: boolean) => Promise<void>;
}

export const useAppLock = create<AppLockState>((set) => ({
  enabled: false,
  ready: false,

  load: async () => {
    try {
      const v = await SecureStore.getItemAsync(KEY);
      set({ enabled: v === 'true', ready: true });
    } catch {
      set({ enabled: false, ready: true });
    }
  },

  setEnabled: async (v: boolean) => {
    await SecureStore.setItemAsync(KEY, v ? 'true' : 'false');
    set({ enabled: v });
  },
}));
