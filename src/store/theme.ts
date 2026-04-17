import * as SecureStore from 'expo-secure-store';
import { create } from 'zustand';
import { darkColors, lightColors } from '../theme';

type Colors = typeof darkColors;
export type ThemeMode = 'dark' | 'light' | 'system';

interface ThemeState {
  mode: ThemeMode;
  dynamicColors: boolean;
  isDark: boolean;
  colors: Colors;
  setMode: (mode: ThemeMode, systemIsDark?: boolean) => void;
  setDynamicColors: (enabled: boolean) => void;
  setColors: (colors: Colors) => void;
  applySystem: (systemIsDark: boolean) => void;
  load: (systemIsDark: boolean) => Promise<void>;
}

function resolveIsDark(mode: ThemeMode, systemIsDark: boolean): boolean {
  if (mode === 'system') return systemIsDark;
  return mode === 'dark';
}

export const useThemeStore = create<ThemeState>((set, get) => ({
  mode:          'system',
  dynamicColors: false,
  isDark:        true,
  colors:        darkColors,

  setMode: (mode, systemIsDark = get().isDark) => {
    SecureStore.setItemAsync('tm_theme_mode', mode).catch(() => {});
    const isDark = resolveIsDark(mode, systemIsDark);
    const { dynamicColors } = get();
    if (!dynamicColors) {
      set({ mode, isDark, colors: isDark ? darkColors : lightColors });
    } else {
      set({ mode, isDark });
    }
  },

  setDynamicColors: (enabled: boolean) => {
    SecureStore.setItemAsync('tm_dynamic_colors', enabled ? '1' : '0').catch(() => {});
    const { isDark } = get();
    if (!enabled) {
      set({ dynamicColors: false, colors: isDark ? darkColors : lightColors });
    } else {
      set({ dynamicColors: true });
    }
  },

  setColors: (colors: Colors) => {
    set({ colors });
  },

  applySystem: (systemIsDark: boolean) => {
    const { mode, dynamicColors } = get();
    if (mode === 'system') {
      if (!dynamicColors) {
        set({ isDark: systemIsDark, colors: systemIsDark ? darkColors : lightColors });
      } else {
        set({ isDark: systemIsDark });
      }
    }
  },

  load: async (systemIsDark: boolean) => {
    try {
      const [savedMode, savedDynamic] = await Promise.all([
        SecureStore.getItemAsync('tm_theme_mode'),
        SecureStore.getItemAsync('tm_dynamic_colors'),
      ]);
      const mode: ThemeMode = (savedMode === 'dark' || savedMode === 'light' || savedMode === 'system') ? savedMode : 'system';
      const dynamicColors = savedDynamic === '1';
      const isDark = resolveIsDark(mode, systemIsDark);
      if (!dynamicColors) {
        set({ mode, dynamicColors, isDark, colors: isDark ? darkColors : lightColors });
      } else {
        set({ mode, dynamicColors, isDark });
      }
    } catch {
      set({ mode: 'system', dynamicColors: false, isDark: systemIsDark, colors: systemIsDark ? darkColors : lightColors });
    }
  },
}));
