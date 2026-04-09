import * as SecureStore from 'expo-secure-store';
import * as ScreenOrientation from 'expo-screen-orientation';
import { create } from 'zustand';

interface OrientationState {
  locked: boolean;
  ready: boolean;
  setLocked: (locked: boolean) => Promise<void>;
  load: () => Promise<void>;
}

export const useOrientationStore = create<OrientationState>((set) => ({
  locked: false,
  ready: false,

  setLocked: async (locked: boolean) => {
    await SecureStore.setItemAsync('tm_orientation_lock', locked ? '1' : '0');
    if (locked) {
      await ScreenOrientation.lockAsync(ScreenOrientation.OrientationLock.PORTRAIT_UP);
    } else {
      await ScreenOrientation.unlockAsync();
    }
    set({ locked });
  },

  load: async () => {
    try {
      const saved = await SecureStore.getItemAsync('tm_orientation_lock');
      const locked = saved === '1';
      if (locked) {
        await ScreenOrientation.lockAsync(ScreenOrientation.OrientationLock.PORTRAIT_UP);
      }
      set({ locked, ready: true });
    } catch {
      set({ locked: false, ready: true });
    }
  },
}));
