import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Tabs, useRouter, useSegments } from 'expo-router';
import { Pressable, StyleSheet, View, useWindowDimensions } from 'react-native';
import { useState } from 'react';
import { Text } from 'react-native-paper';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useThemeStore } from '../../src/store/theme';
import { useTabsStore } from '../../src/store/tabs';
import { useDrawerStore } from '../../src/store/drawer';
import { NavigationDrawer } from '../../src/components/NavigationDrawer';

function useIsWide() {
  const { width } = useWindowDimensions();
  return width >= 600;
}

const BASE_TABS = [
  { name: 'index',       icon: 'view-dashboard-outline',      activeIcon: 'view-dashboard',      label: 'Home'      },
  { name: 'routes',      icon: 'swap-horizontal',             activeIcon: 'swap-horizontal',      label: 'Routes'    },
  { name: 'middlewares', icon: 'shield-link-variant-outline', activeIcon: 'shield-link-variant',  label: 'Middleware'},
  { name: 'live',        icon: 'lightning-bolt-outline',      activeIcon: 'lightning-bolt',       label: 'Services'  },
] as const;

const TABS_WITH_LOGS = [
  { name: 'index',       icon: 'view-dashboard-outline',      activeIcon: 'view-dashboard',      label: 'Home'      },
  { name: 'routes',      icon: 'swap-horizontal',             activeIcon: 'swap-horizontal',      label: 'Routes'    },
  { name: 'middlewares', icon: 'shield-link-variant-outline', activeIcon: 'shield-link-variant',  label: 'Middleware'},
  { name: 'live',        icon: 'lightning-bolt-outline',      activeIcon: 'lightning-bolt',       label: 'Services'  },
  { name: 'logs',        icon: 'text-search',                 activeIcon: 'text-search',          label: 'Logs'      },
] as const;

function useFocused() {
  const segments = useSegments();
  const currentTab: string = segments[segments.length - 1] ?? '';
  return (name: string) =>
    name === 'index'
      ? currentTab === '(tabs)' || currentTab === 'index' || currentTab === ''
      : currentTab === name;
}

function M3NavBar() {
  const insets   = useSafeAreaInsets();
  const c        = useThemeStore(s => s.colors);
  const showLogs = useTabsStore(s => s.showLogsTab);
  const router   = useRouter();
  const focused  = useFocused();
  const TABS     = showLogs ? TABS_WITH_LOGS : BASE_TABS;

  return (
    <View
      style={[
        styles.container,
        {
          backgroundColor: c.card,
          paddingBottom: insets.bottom,
          borderTopColor: c.border,
        },
      ]}
    >
      {TABS.map((tab) => {
        const active = focused(tab.name);
        return (
          <Pressable
            key={tab.name}
            style={styles.item}
            onPress={() => router.navigate(tab.name === 'index' ? '/' : (`/${tab.name}` as any))}
            android_ripple={{ color: 'transparent', borderless: true, radius: 48 }}
          >
            <View style={[
              styles.indicator,
              active && { backgroundColor: c.secondaryContainer },
            ]}>
              <MaterialCommunityIcons
                name={active ? tab.activeIcon : tab.icon}
                size={24}
                color={active ? c.onSecondaryContainer : c.muted}
              />
            </View>
            <Text style={[
              styles.label,
              { color: active ? c.onSecondaryContainer : c.muted },
            ]}>
              {tab.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

function M3NavRail() {
  const insets   = useSafeAreaInsets();
  const c        = useThemeStore(s => s.colors);
  const showLogs = useTabsStore(s => s.showLogsTab);
  const open     = useDrawerStore(s => s.open);
  const router   = useRouter();
  const focused  = useFocused();
  const TABS     = showLogs ? TABS_WITH_LOGS : BASE_TABS;

  return (
    <View style={[
      styles.rail,
      {
        backgroundColor: c.card,
        borderRightColor: c.border,
        paddingTop: insets.top + 8,
        paddingBottom: insets.bottom + 8,
      },
    ]}>
      <Pressable
        style={styles.railMenu}
        onPress={open}
        android_ripple={{ color: 'transparent', borderless: true, radius: 28 }}
      >
        <MaterialCommunityIcons name="cog-outline" size={22} color={c.muted} />
      </Pressable>

      <View style={styles.railItems}>
        {TABS.map((tab) => {
          const active = focused(tab.name);
          return (
            <Pressable
              key={tab.name}
              style={styles.railItem}
              onPress={() => router.navigate(tab.name === 'index' ? '/' : (`/${tab.name}` as any))}
              android_ripple={{ color: 'transparent', borderless: true, radius: 40 }}
            >
              <View style={[
                styles.railIndicator,
                active && { backgroundColor: c.secondaryContainer },
              ]}>
                <MaterialCommunityIcons
                  name={active ? tab.activeIcon : tab.icon}
                  size={24}
                  color={active ? c.onSecondaryContainer : c.muted}
                />
              </View>
              <Text style={[
                styles.railLabel,
                { color: active ? c.onSecondaryContainer : c.muted, fontWeight: active ? '600' : '500' },
              ]}>
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
  const isWide      = layoutWide ?? windowIsWide;
  const showLogs    = useTabsStore(s => s.showLogsTab);
  const setIsWide   = useDrawerStore(s => s.setIsWide);

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
        <Tabs
          screenOptions={{ headerShown: false, tabBarStyle: { display: 'none' } }}
        >
          <Tabs.Screen name="index"       options={{ title: 'Dashboard' }} />
          <Tabs.Screen name="routes"      options={{ title: 'Routes'    }} />
          <Tabs.Screen name="middlewares" options={{ title: 'Middleware' }} />
          <Tabs.Screen name="live"        options={{ title: 'Services'  }} />
          <Tabs.Screen name="logs"        options={{ title: 'Logs', href: showLogs ? undefined : null }} />
          <Tabs.Screen name="backups"     options={{ href: null }}         />
          <Tabs.Screen name="settings"    options={{ href: null }}         />
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
});
