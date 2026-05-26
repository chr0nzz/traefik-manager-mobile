import * as SecureStore from 'expo-secure-store';
import { create } from 'zustand';

const KEY_SHOW_LOGS      = 'tm_show_logs_tab';
const KEY_SHOW_CERTS     = 'tm_show_certs_tab';
const KEY_SHOW_PLUGINS   = 'tm_show_plugins_tab';
const KEY_SHOW_CROWDSEC  = 'tm_show_crowdsec_tab';
const KEY_LOG_LINES      = 'tm_log_lines';

interface TabsState {
  showLogsTab:      boolean;
  showCertsTab:     boolean;
  showPluginsTab:   boolean;
  showCrowdSecTab:  boolean;
  logLines:         number;
  ready:            boolean;
  setShowLogsTab:      (val: boolean) => Promise<void>;
  setShowCertsTab:     (val: boolean) => Promise<void>;
  setShowPluginsTab:   (val: boolean) => Promise<void>;
  setShowCrowdSecTab:  (val: boolean) => Promise<void>;
  setLogLines:         (val: number)  => Promise<void>;
  load: () => Promise<void>;
}

export const useTabsStore = create<TabsState>((set) => ({
  showLogsTab:      false,
  showCertsTab:     false,
  showPluginsTab:   false,
  showCrowdSecTab:  false,
  logLines:         100,
  ready:            false,

  setShowLogsTab: async (val) => {
    await SecureStore.setItemAsync(KEY_SHOW_LOGS, val ? '1' : '0');
    set({ showLogsTab: val });
  },

  setShowCertsTab: async (val) => {
    await SecureStore.setItemAsync(KEY_SHOW_CERTS, val ? '1' : '0');
    set({ showCertsTab: val });
  },

  setShowPluginsTab: async (val) => {
    await SecureStore.setItemAsync(KEY_SHOW_PLUGINS, val ? '1' : '0');
    set({ showPluginsTab: val });
  },

  setShowCrowdSecTab: async (val) => {
    await SecureStore.setItemAsync(KEY_SHOW_CROWDSEC, val ? '1' : '0');
    set({ showCrowdSecTab: val });
  },

  setLogLines: async (val) => {
    await SecureStore.setItemAsync(KEY_LOG_LINES, String(val));
    set({ logLines: val });
  },

  load: async () => {
    const [showLogs, showCerts, showPlugins, showCrowdSec, logLines] = await Promise.all([
      SecureStore.getItemAsync(KEY_SHOW_LOGS),
      SecureStore.getItemAsync(KEY_SHOW_CERTS),
      SecureStore.getItemAsync(KEY_SHOW_PLUGINS),
      SecureStore.getItemAsync(KEY_SHOW_CROWDSEC),
      SecureStore.getItemAsync(KEY_LOG_LINES),
    ]);
    set({
      showLogsTab:      showLogs      === '1',
      showCertsTab:     showCerts     === '1',
      showPluginsTab:   showPlugins   === '1',
      showCrowdSecTab:  showCrowdSec  === '1',
      logLines:         logLines ? parseInt(logLines, 10) : 100,
      ready:            true,
    });
  },
}));
