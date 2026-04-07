import { MaterialCommunityIcons } from '@expo/vector-icons';
import { BottomTabBarProps } from '@react-navigation/bottom-tabs';
import { Tabs } from 'expo-router';
import { Pressable, StyleSheet, View } from 'react-native';
import { Text } from 'react-native-paper';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useThemeStore } from '../../src/store/theme';
import { useTabsStore } from '../../src/store/tabs';
import { NavigationDrawer } from '../../src/components/NavigationDrawer';

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

function M3NavBar({ state, navigation }: BottomTabBarProps) {
  const insets   = useSafeAreaInsets();
  const c        = useThemeStore(s => s.colors);
  const showLogs = useTabsStore(s => s.showLogsTab);
  const TABS     = showLogs ? TABS_WITH_LOGS : BASE_TABS;

  const currentRouteName = state.routes[state.index]?.name;

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
        const focused = currentRouteName === tab.name;
        return (
          <Pressable
            key={tab.name}
            style={styles.item}
            onPress={() => navigation.navigate(tab.name)}
            android_ripple={{ color: c.secondaryContainer, borderless: false, radius: 64 }}
          >
            <View style={[
              styles.indicator,
              focused && { backgroundColor: c.secondaryContainer },
            ]}>
              <MaterialCommunityIcons
                name={focused ? tab.activeIcon : tab.icon}
                size={24}
                color={focused ? c.onSecondaryContainer : c.muted}
              />
            </View>
            <Text style={[
              styles.label,
              { color: focused ? c.onSecondaryContainer : c.muted },
            ]}>
              {tab.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

export default function TabsLayout() {
  const showLogs = useTabsStore(s => s.showLogsTab);

  return (
    <View style={{ flex: 1 }}>
    <Tabs
      tabBar={(props) => <M3NavBar {...props} />}
      screenOptions={{
        headerShown: false,
      }}
    >
      <Tabs.Screen name="index"       options={{ title: 'Dashboard' }} />
      <Tabs.Screen name="routes"      options={{ title: 'Routes'    }} />
      <Tabs.Screen name="middlewares" options={{ title: 'Middleware' }} />
      <Tabs.Screen name="live"        options={{ title: 'Services'  }} />
      <Tabs.Screen name="logs"        options={{ title: 'Logs', href: showLogs ? undefined : null }} />
      <Tabs.Screen name="backups"     options={{ href: null }}         />
      <Tabs.Screen name="settings"    options={{ href: null }}         />
    </Tabs>
    <NavigationDrawer />
    </View>
  );
}

const styles = StyleSheet.create({
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
});
