import { MaterialCommunityIcons } from '@expo/vector-icons';
import { BottomTabBarProps } from '@react-navigation/bottom-tabs';
import { Tabs } from 'expo-router';
import { useEffect, useRef } from 'react';
import { Animated, Pressable, StyleSheet, View } from 'react-native';
import { Text } from 'react-native-paper';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useNavStore } from '../../src/store/nav';
import { useThemeStore } from '../../src/store/theme';

const TABS = [
  { name: 'index',       icon: 'view-dashboard-outline', activeIcon: 'view-dashboard',  label: 'Home'        },
  { name: 'routes',      icon: 'swap-horizontal',        activeIcon: 'swap-horizontal', label: 'Routes'      },
  { name: 'middlewares', icon: 'shield-link-variant-outline', activeIcon: 'shield-link-variant', label: 'Middleware' },
  { name: 'live',        icon: 'lightning-bolt-outline', activeIcon: 'lightning-bolt',  label: 'Services'    },
  { name: 'settings',    icon: 'cog-outline',            activeIcon: 'cog',             label: 'Settings'    },
] as const;

function FloatingNav({ state, navigation }: BottomTabBarProps) {
  const insets     = useSafeAreaInsets();
  const c          = useThemeStore(s => s.colors);
  const visible    = useNavStore(s => s.visible);
  const setVisible = useNavStore(s => s.setVisible);
  const translateY = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.spring(translateY, {
      toValue: visible ? 0 : 120,
      useNativeDriver: true,
      tension: 80,
      friction: 12,
    }).start();
  }, [visible]);

  return (
    <Animated.View
      style={[
        styles.wrapper,
        { paddingBottom: insets.bottom + 10, transform: [{ translateY }] },
      ]}
      pointerEvents="box-none"
    >
      <View style={[styles.pill, { backgroundColor: c.card, borderColor: c.border }]}>
        {TABS.map((tab, index) => {
          const focused = state.index === index;
          return (
            <Pressable
              key={tab.name}
              style={styles.tabBtn}
              onPress={() => {
                setVisible(true);
                navigation.navigate(tab.name);
              }}
              android_ripple={{ color: c.blue + '33', borderless: true, radius: 32 }}
            >
              <MaterialCommunityIcons
                name={focused ? tab.activeIcon : tab.icon}
                size={24}
                color={focused ? c.blue : c.muted}
              />
              {focused && (
                <Text style={[styles.label, { color: c.blue }]}>{tab.label}</Text>
              )}
            </Pressable>
          );
        })}
      </View>
    </Animated.View>
  );
}

export default function TabsLayout() {
  const c      = useThemeStore(s => s.colors);
  const insets = useSafeAreaInsets();

  return (
    <Tabs
      tabBar={(props) => <FloatingNav {...props} />}
      screenOptions={{
        headerShown: false,
        contentStyle: { backgroundColor: c.bg },
      }}
    >
      <Tabs.Screen name="index"       options={{ title: 'Dashboard'  }} />
      <Tabs.Screen name="routes"      options={{ title: 'Routes'     }} />
      <Tabs.Screen name="middlewares" options={{ title: 'Middleware'  }} />
      <Tabs.Screen name="live"        options={{ title: 'Services'   }} />
      <Tabs.Screen name="backups"     options={{ href: null }}          />
      <Tabs.Screen name="settings"    options={{ title: 'Settings'   }} />
    </Tabs>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    alignItems: 'center',
    pointerEvents: 'box-none',
  },
  pill: {
    flexDirection: 'row',
    borderRadius: 999,
    borderWidth: 1,
    paddingHorizontal: 8,
    paddingVertical: 6,
    gap: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.4,
    shadowRadius: 16,
    elevation: 16,
  },
  tabBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 999,
    minWidth: 48,
    justifyContent: 'center',
  },
  label: {
    fontSize: 13,
    fontWeight: '700',
  },
});
