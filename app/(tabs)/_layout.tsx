import { MaterialCommunityIcons } from '@expo/vector-icons';
import { ArrowsLeftRight, PlugsConnected } from 'phosphor-react-native';
import { Tabs, useRouter, useSegments } from 'expo-router';
import { Pressable, StyleSheet, View, useWindowDimensions } from 'react-native';
import { useState } from 'react';
import { Text } from 'react-native-paper';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useThemeStore } from '../../src/store/theme';
import { useTabsStore } from '../../src/store/tabs';
import { useDrawerStore } from '../../src/store/drawer';
import { NavigationDrawer } from '../../src/components/NavigationDrawer';
import type { ComponentType } from 'react';

type PhosphorProps = { size: number; color: string; weight: 'regular' | 'bold' };

function useIsWide() {
  const { width } = useWindowDimensions();
  return width >= 600;
}

const PRIMARY_TABS = [
  { name: 'index',       icon: 'view-dashboard-outline', activeIcon: 'view-dashboard',   label: 'Home',       PhIcon: undefined as ComponentType<PhosphorProps> | undefined },
  { name: 'routes',      icon: '',                        activeIcon: '',                 label: 'Routes',     PhIcon: ArrowsLeftRight as ComponentType<PhosphorProps> },
  { name: 'middlewares', icon: '',                        activeIcon: '',                 label: 'Middleware', PhIcon: PlugsConnected  as ComponentType<PhosphorProps> },
  { name: 'live',        icon: 'lightning-bolt-outline',  activeIcon: 'lightning-bolt',  label: 'Services',   PhIcon: undefined as ComponentType<PhosphorProps> | undefined },
];

const ALL_OPTIONAL_TABS = [
  { name: 'logs',    icon: 'console-line',        activeIcon: 'console-line',       label: 'Logs'    },
  { name: 'certs',   icon: 'certificate-outline', activeIcon: 'certificate-outline', label: 'Certs'   },
  { name: 'plugins', icon: 'puzzle-outline',      activeIcon: 'puzzle',             label: 'Plugins' },
] as const;


function useFocused() {
  const segments = useSegments();
  const currentTab: string = segments[segments.length - 1] ?? '';
  return (name: string) =>
    name === 'index'
      ? currentTab === '(tabs)' || currentTab === 'index' || currentTab === ''
      : currentTab === name;
}

function useEnabledOptional() {
  const { showLogsTab, showCertsTab, showPluginsTab } = useTabsStore();
  return ALL_OPTIONAL_TABS.filter(t => {
    if (t.name === 'logs')    return showLogsTab;
    if (t.name === 'certs')   return showCertsTab;
    if (t.name === 'plugins') return showPluginsTab;
    return false;
  });
}

function TabItem({
  icon, activeIcon, label, active, onPress, c, PhIcon,
}: {
  icon: string; activeIcon: string; label: string;
  active: boolean; onPress: () => void;
  c: ReturnType<typeof useThemeStore.getState>['colors'];
  PhIcon?: ComponentType<PhosphorProps>;
}) {
  const color = active ? c.onSecondaryContainer : c.muted;
  return (
    <Pressable
      style={styles.item}
      onPress={onPress}
      android_ripple={{ color: 'transparent', borderless: true, radius: 48 }}
    >
      <View style={[styles.indicator, active && { backgroundColor: c.secondaryContainer }]}>
        {PhIcon
          ? <PhIcon size={24} color={color} weight={active ? 'bold' : 'regular'} />
          : <MaterialCommunityIcons name={(active ? activeIcon : icon) as any} size={24} color={color} />
        }
      </View>
      <Text style={[styles.label, { color }]}>
        {label}
      </Text>
    </Pressable>
  );
}

function M3NavBar() {
  const insets          = useSafeAreaInsets();
  const c               = useThemeStore(s => s.colors);
  const router          = useRouter();
  const focused         = useFocused();
  const enabledOptional = useEnabledOptional();
  const [showingMore, setShowingMore] = useState(false);

  const navigate = (name: string) =>
    router.navigate(name === 'index' ? '/' : (`/${name}` as any));

  const singleOptional = enabledOptional.length === 1;
  const multiOptional  = enabledOptional.length >= 2;

  const displayTabs = showingMore ? enabledOptional : PRIMARY_TABS;

  return (
    <View style={[styles.container, { backgroundColor: c.card, paddingBottom: insets.bottom, borderTopColor: c.border }]}>
      {singleOptional ? (
        [...PRIMARY_TABS, ...enabledOptional].map(tab => (
          <TabItem
            key={tab.name}
            {...tab}
            active={focused(tab.name)}
            onPress={() => navigate(tab.name)}
            c={c}
          />
        ))
      ) : (
        <>
          {displayTabs.map(tab => (
            <TabItem
              key={tab.name}
              {...tab}
              active={focused(tab.name)}
              onPress={() => navigate(tab.name)}
              c={c}
            />
          ))}
          {multiOptional && (
            showingMore ? (
              <Pressable
                style={styles.item}
                onPress={() => setShowingMore(false)}
                android_ripple={{ color: 'transparent', borderless: true, radius: 48 }}
              >
                <View style={styles.indicator}>
                  <MaterialCommunityIcons name="chevron-left" size={20} color={c.muted} />
                </View>
                <Text style={[styles.toggleLabel, { color: c.muted }]}>back</Text>
              </Pressable>
            ) : (
              <Pressable
                style={styles.item}
                onPress={() => setShowingMore(true)}
                android_ripple={{ color: 'transparent', borderless: true, radius: 48 }}
              >
                <View style={styles.indicator}>
                  <MaterialCommunityIcons name="chevron-right" size={20} color={c.muted} />
                </View>
                <Text style={[styles.toggleLabel, { color: c.muted }]}>more</Text>
              </Pressable>
            )
          )}
        </>
      )}
    </View>
  );
}

function M3NavRail() {
  const insets          = useSafeAreaInsets();
  const c               = useThemeStore(s => s.colors);
  const open            = useDrawerStore(s => s.open);
  const router          = useRouter();
  const focused         = useFocused();
  const enabledOptional = useEnabledOptional();

  const allTabs = [...PRIMARY_TABS, ...enabledOptional];

  const navigate = (name: string) =>
    router.navigate(name === 'index' ? '/' : (`/${name}` as any));

  return (
    <View style={[styles.rail, { backgroundColor: c.card, borderRightColor: c.border, paddingTop: insets.top + 8, paddingBottom: insets.bottom + 8 }]}>
      <Pressable
        style={styles.railMenu}
        onPress={open}
        android_ripple={{ color: 'transparent', borderless: true, radius: 28 }}
      >
        <MaterialCommunityIcons name="cog-outline" size={22} color={c.muted} />
      </Pressable>
      <View style={styles.railItems}>
        {allTabs.map(tab => {
          const active = focused(tab.name);
          return (
            <Pressable
              key={tab.name}
              style={styles.railItem}
              onPress={() => navigate(tab.name)}
              android_ripple={{ color: 'transparent', borderless: true, radius: 40 }}
            >
              <View style={[styles.railIndicator, active && { backgroundColor: c.secondaryContainer }]}>
                {(() => { const Ph = (tab as any).PhIcon as ComponentType<PhosphorProps> | undefined; return Ph
                  ? <Ph size={24} color={active ? c.onSecondaryContainer : c.muted} weight={active ? 'bold' : 'regular'} />
                  : <MaterialCommunityIcons name={(active ? tab.activeIcon : tab.icon) as any} size={24} color={active ? c.onSecondaryContainer : c.muted} />;
                })()}
              </View>
              <Text style={[styles.railLabel, { color: active ? c.onSecondaryContainer : c.muted, fontWeight: active ? '600' : '500' }]}>
                {tab.label}
              </Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

export default function TabsLayout() {
  const windowIsWide = useIsWide();
  const [layoutWide, setLayoutWide] = useState<boolean | null>(null);
  const isWide       = layoutWide ?? windowIsWide;
  const { showLogsTab, showCertsTab, showPluginsTab } = useTabsStore();
  const setIsWide    = useDrawerStore(s => s.setIsWide);

  return (
    <View
      style={styles.root}
      onLayout={(e) => {
        const wide = e.nativeEvent.layout.width >= 600;
        setLayoutWide(wide);
        setIsWide(wide);
      }}
    >
      {isWide && <M3NavRail />}
      <View style={{ flex: 1 }}>
        <Tabs screenOptions={{ headerShown: false, tabBarStyle: { display: 'none' } }}>
          <Tabs.Screen name="index"       options={{ title: 'Dashboard' }} />
          <Tabs.Screen name="routes"      options={{ title: 'Routes'    }} />
          <Tabs.Screen name="middlewares" options={{ title: 'Middleware' }} />
          <Tabs.Screen name="live"        options={{ title: 'Services'  }} />
          <Tabs.Screen name="logs"     options={{ title: 'Logs',         href: showLogsTab    ? undefined : null }} />
          <Tabs.Screen name="certs"    options={{ title: 'Certificates', href: showCertsTab   ? undefined : null }} />
          <Tabs.Screen name="plugins"  options={{ title: 'Plugins',      href: showPluginsTab ? undefined : null }} />
          <Tabs.Screen name="backups"  options={{ href: null }} />
          <Tabs.Screen name="settings" options={{ href: null }} />
        </Tabs>
        {!isWide && <M3NavBar />}
      </View>
      <NavigationDrawer />
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, flexDirection: 'row' },
  container: {
    flexDirection: 'row',
    borderTopWidth: StyleSheet.hairlineWidth,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -1 },
    shadowOpacity: 0.08,
    shadowRadius: 4,
    elevation: 3,
  },
  item: {
    flex: 1,
    alignItems: 'center',
    paddingTop: 12,
    paddingBottom: 4,
    gap: 4,
  },
  indicator: {
    width: 64,
    height: 32,
    borderRadius: 999,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  label: {
    fontSize: 12,
    fontWeight: '500',
    letterSpacing: 0.5,
  },
  rail: {
    width: 80,
    alignSelf: 'stretch',
    borderRightWidth: StyleSheet.hairlineWidth,
    alignItems: 'center',
  },
  railMenu: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8,
  },
  railItems: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
  },
  railItem: {
    alignItems: 'center',
    gap: 4,
    paddingVertical: 4,
    width: 72,
  },
  railIndicator: {
    width: 56,
    height: 32,
    borderRadius: 999,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  railLabel: {
    fontSize: 11,
    letterSpacing: 0.3,
    textAlign: 'center',
  },
  toggleLabel: {
    fontSize: 12,
    fontWeight: '500',
    letterSpacing: 0.5,
  },
});
