import * as SecureStore from 'expo-secure-store';
import { create } from 'zustand';

const KEY_PREFS     = 'tm_tab_prefs_by_server';
const KEY_LOG_LINES = 'tm_log_lines';
// legacy keys (pre per-server) - migrated into the "host" entry on first load
const KEY_SHOW_LOGS     = 'tm_show_logs_tab';
const KEY_SHOW_CERTS    = 'tm_show_certs_tab';
const KEY_SHOW_PLUGINS  = 'tm_show_plugins_tab';
const KEY_SHOW_CROWDSEC = 'tm_show_crowdsec_tab';

interface TabPrefs { logs: boolean; certs: boolean; plugins: boolean; crowdsec: boolean }
const DEFAULTS: TabPrefs = { logs: false, certs: false, plugins: false, crowdsec: false };

interface TabsState {
  prefsByServer:   Record<string, TabPrefs>;
  activeServer:    string;
  showLogsTab:     boolean;
  showCertsTab:    boolean;
  showPluginsTab:  boolean;
  showCrowdSecTab: boolean;
  logLines:        number;
  ready:           boolean;
  selectServer:        (key: string | null) => void;
  setShowLogsTab:      (val: boolean) => Promise<void>;
  setShowCertsTab:     (val: boolean) => Promise<void>;
  setShowPluginsTab:   (val: boolean) => Promise<void>;
  setShowCrowdSecTab:  (val: boolean) => Promise<void>;
  setLogLines:         (val: number)  => Promise<void>;
  load: () => Promise<void>;
}

function viewFromPrefs(p: TabPrefs) {
  return {
    showLogsTab:     p.logs,
    showCertsTab:    p.certs,
    showPluginsTab:  p.plugins,
    showCrowdSecTab: p.crowdsec,
  };
}

export const useTabsStore = create<TabsState>((set, get) => {
  async function update(field: keyof TabPrefs, val: boolean) {
    const { activeServer, prefsByServer } = get();
    const current = prefsByServer[activeServer] ?? DEFAULTS;
    const next = { ...prefsByServer, [activeServer]: { ...current, [field]: val } };
    await SecureStore.setItemAsync(KEY_PREFS, JSON.stringify(next));
    set({ prefsByServer: next, ...viewFromPrefs(next[activeServer]) });
  }

  return {
    prefsByServer:   {},
    activeServer:    'host',
    showLogsTab:     false,
    showCertsTab:    false,
    showPluginsTab:  false,
    showCrowdSecTab: false,
    logLines:        100,
    ready:           false,

    selectServer: (key) => {
      const server = key ?? 'host';
      const prefs = get().prefsByServer[server] ?? DEFAULTS;
      set({ activeServer: server, ...viewFromPrefs(prefs) });
    },

    setShowLogsTab:     (val) => update('logs', val),
    setShowCertsTab:    (val) => update('certs', val),
    setShowPluginsTab:  (val) => update('plugins', val),
    setShowCrowdSecTab: (val) => update('crowdsec', val),

    setLogLines: async (val) => {
      await SecureStore.setItemAsync(KEY_LOG_LINES, String(val));
      set({ logLines: val });
    },

    load: async () => {
      const [prefsRaw, logLines] = await Promise.all([
        SecureStore.getItemAsync(KEY_PREFS),
        SecureStore.getItemAsync(KEY_LOG_LINES),
      ]);
      let prefsByServer: Record<string, TabPrefs> = {};
      try { prefsByServer = prefsRaw ? JSON.parse(prefsRaw) : {}; } catch { prefsByServer = {}; }

      // one-time migration of the old global toggles into the host entry
      if (!prefsRaw) {
        const [l, c, p, cs] = await Promise.all([
          SecureStore.getItemAsync(KEY_SHOW_LOGS),
          SecureStore.getItemAsync(KEY_SHOW_CERTS),
          SecureStore.getItemAsync(KEY_SHOW_PLUGINS),
          SecureStore.getItemAsync(KEY_SHOW_CROWDSEC),
        ]);
        if (l || c || p || cs) {
          prefsByServer.host = { logs: l === '1', certs: c === '1', plugins: p === '1', crowdsec: cs === '1' };
          await SecureStore.setItemAsync(KEY_PREFS, JSON.stringify(prefsByServer));
        }
      }

      const hostPrefs = prefsByServer.host ?? DEFAULTS;
      set({
        prefsByServer,
        activeServer: 'host',
        ...viewFromPrefs(hostPrefs),
        logLines: logLines ? parseInt(logLines, 10) : 100,
        ready: true,
      });
    },
  };
});
