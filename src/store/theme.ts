import * as SecureStore from 'expo-secure-store';
import { create } from 'zustand';
import { darkColors, lightColors } from '../theme';

type Colors = typeof darkColors;
export type ThemeMode = 'dark' | 'light' | 'system';

interface ThemeState {
  mode: ThemeMode;
  isDark: boolean;
  colors: Colors;
  setMode: (mode: ThemeMode, systemIsDark?: boolean) => void;
  applySystem: (systemIsDark: boolean) => void;
  load: (systemIsDark: boolean) => Promise<void>;
}

function resolveIsDark(mode: ThemeMode, systemIsDark: boolean): boolean {
  if (mode === 'system') return systemIsDark;
  return mode === 'dark';
}

export const useThemeStore = create<ThemeState>((set, get) => ({
  mode:   'system',
  isDark: true,
  colors: darkColors,

  setMode: (mode, systemIsDark = get().isDark) => {
    SecureStore.setItemAsync('tm_theme_mode', mode).catch(() => {});
    const isDark = resolveIsDark(mode, systemIsDark);
    set({ mode, isDark, colors: isDark ? darkColors : lightColors });
  },

  applySystem: (systemIsDark: boolean) => {
    if (get().mode === 'system') {
      set({ isDark: systemIsDark, colors: systemIsDark ? darkColors : lightColors });
    }
  },

  load: async (systemIsDark: boolean) => {
    try {
      const saved = await SecureStore.getItemAsync('tm_theme_mode') as ThemeMode | null;
      const mode: ThemeMode = (saved === 'dark' || saved === 'light' || saved === 'system') ? saved : 'system';
      const isDark = resolveIsDark(mode, systemIsDark);
      set({ mode, isDark, colors: isDark ? darkColors : lightColors });
    } catch {
      const isDark = systemIsDark;
      set({ mode: 'system', isDark, colors: isDark ? darkColors : lightColors });
    }
  },
}));
