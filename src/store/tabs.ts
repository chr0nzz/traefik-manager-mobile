import * as SecureStore from 'expo-secure-store';
import { create } from 'zustand';

const KEY_SHOW_LOGS  = 'tm_show_logs_tab';
const KEY_LOG_LINES  = 'tm_log_lines';

interface TabsState {
  showLogsTab: boolean;
  logLines: number;
  ready: boolean;
  setShowLogsTab: (val: boolean) => Promise<void>;
  setLogLines: (val: number) => Promise<void>;
  load: () => Promise<void>;
}

export const useTabsStore = create<TabsState>((set) => ({
  showLogsTab: false,
  logLines: 100,
  ready: false,

  setShowLogsTab: async (val) => {
    await SecureStore.setItemAsync(KEY_SHOW_LOGS, val ? '1' : '0');
    set({ showLogsTab: val });
  },

  setLogLines: async (val) => {
    await SecureStore.setItemAsync(KEY_LOG_LINES, String(val));
    set({ logLines: val });
  },

  load: async () => {
    const showLogs = await SecureStore.getItemAsync(KEY_SHOW_LOGS);
    const logLines = await SecureStore.getItemAsync(KEY_LOG_LINES);
    set({
      showLogsTab: showLogs === '1',
      logLines: logLines ? parseInt(logLines, 10) : 100,
      ready: true,
    });
  },
}));
