import React, { useEffect, useRef } from 'react';
import {
  Animated,
  Image,
  Pressable,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  useWindowDimensions,
  View,
} from 'react-native';
import { Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import Constants from 'expo-constants';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useDrawerStore } from '../store/drawer';
import { useThemeStore, ThemeMode } from '../store/theme';
import { useTabsStore } from '../store/tabs';
import { useConnection } from '../store/connection';
import { useAppLock } from '../store/applock';
import { useBackups } from '../hooks/useBackups';
import { font, radius, spacing } from '../theme';

const version = Constants.expoConfig?.version ?? '?';

const THEME_LABEL: Record<ThemeMode, string> = {
  light: 'Light',
  dark: 'Dark',
  system: 'System',
};

type Colors = ReturnType<typeof useThemeStore.getState>['colors'];

function DrawerItem({
  icon, label, value, onPress, c,
}: {
  icon: string; label: string; value?: string; onPress: () => void; c: Colors;
}) {
  return (
    <TouchableOpacity style={styles.item} onPress={onPress} activeOpacity={0.65}>
      <View style={styles.itemIndicator}>
        <MaterialCommunityIcons name={icon as any} size={20} color={c.muted} />
      </View>
      <Text style={[styles.itemLabel, { color: c.text }]}>{label}</Text>
      {!!value && (
        <Text style={[styles.itemValue, { color: c.muted }]} numberOfLines={1}>{value}</Text>
      )}
      <MaterialCommunityIcons name="chevron-right" size={16} color={c.border} />
    </TouchableOpacity>
  );
}

function SectionLabel({ title, c }: { title: string; c: Colors }) {
  return (
    <Text style={[styles.sectionLabel, { color: c.muted }]}>{title}</Text>
  );
}

export function NavigationDrawer() {
  const { width }  = useWindowDimensions();
  const insets     = useSafeAreaInsets();
  const router     = useRouter();
  const c          = useThemeStore(s => s.colors);
  const isDark     = useThemeStore(s => s.isDark);
  const { mode }   = useThemeStore();
  const { isOpen, close } = useDrawerStore();
  const { baseUrl, demoMode } = useConnection();
  const appLockEnabled = useAppLock(s => s.enabled);
  const showLogsTab    = useTabsStore(s => s.showLogsTab);
  const { data: backups } = useBackups();
  const backupCount = backups?.length ?? 0;

  const DRAWER_WIDTH = Math.min(320, width - 56);

  const translateX  = useRef(new Animated.Value(-DRAWER_WIDTH)).current;
  const scrimOpacity = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.spring(translateX, {
        toValue: isOpen ? 0 : -DRAWER_WIDTH,
        useNativeDriver: true,
        tension: 80,
        friction: 14,
      }),
      Animated.timing(scrimOpacity, {
        toValue: isOpen ? 1 : 0,
        duration: 200,
        useNativeDriver: true,
      }),
    ]).start();
  }, [isOpen]);

  const navigate = (path: string) => {
    close();
    setTimeout(() => router.push(path as any), 50);
  };

  if (!isOpen && !translateX.__getValue) return null;

  return (
    <>
      <Animated.View
        style={[styles.scrim, { opacity: scrimOpacity }]}
        pointerEvents={isOpen ? 'auto' : 'none'}
      >
        <Pressable style={StyleSheet.absoluteFill} onPress={close} />
      </Animated.View>

      <Animated.View
        style={[
          styles.drawer,
          {
            width: DRAWER_WIDTH,
            backgroundColor: c.bg,
            paddingTop: insets.top,
            paddingBottom: insets.bottom + spacing.lg,
            transform: [{ translateX }],
          },
        ]}
        pointerEvents={isOpen ? 'auto' : 'none'}
      >
        <View style={[styles.header, { borderBottomColor: c.border }]}>
          <Image source={require('../../assets/icon.png')} style={styles.appIcon} />
          <Text style={[styles.appName, { color: c.text }]}>Traefik Manager</Text>
        </View>

        <ScrollView style={{ flex: 1 }} showsVerticalScrollIndicator={false}>
          <SectionLabel title="General" c={c} />

          <DrawerItem
            icon="theme-light-dark"
            label="Appearance"
            value={THEME_LABEL[mode]}
            onPress={() => navigate('/settings/appearance')}
            c={c}
          />
          <DrawerItem
            icon="lock-outline"
            label="App Lock"
            value={appLockEnabled ? 'On' : 'Off'}
            onPress={() => navigate('/settings/applock')}
            c={c}
          />
          <DrawerItem
            icon="server-network"
            label="Traefik"
            value="Domains & resolvers"
            onPress={() => navigate('/settings/traefik')}
            c={c}
          />
          <DrawerItem
            icon="server"
            label="Server"
            value={demoMode ? 'Demo' : baseUrl ? baseUrl.replace(/^https?:\/\//, '') : 'Not connected'}
            onPress={() => navigate('/settings/server')}
            c={c}
          />

          <SectionLabel title="Tabs" c={c} />

          <DrawerItem
            icon="text-search"
            label="Logs"
            value={showLogsTab ? 'Enabled' : 'Disabled'}
            onPress={() => navigate('/settings/logs')}
            c={c}
          />

          <SectionLabel title="Data" c={c} />

          <DrawerItem
            icon="database-export-outline"
            label="Backups"
            value={backupCount > 0 ? `${backupCount} backup${backupCount !== 1 ? 's' : ''}` : 'None'}
            onPress={() => navigate('/settings/backups')}
            c={c}
          />

          <SectionLabel title="Info" c={c} />

          <DrawerItem
            icon="information-outline"
            label="About"
            value={`v${version}`}
            onPress={() => navigate('/settings/about')}
            c={c}
          />
        </ScrollView>
      </Animated.View>
    </>
  );
}

const styles = StyleSheet.create({
  scrim: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0,0,0,0.4)',
    zIndex: 100,
  },
  drawer: {
    position: 'absolute',
    top: 0,
    left: 0,
    bottom: 0,
    zIndex: 101,
    shadowColor: '#000',
    shadowOffset: { width: 4, height: 0 },
    shadowOpacity: 0.18,
    shadowRadius: 12,
    elevation: 16,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.xl,
    borderBottomWidth: StyleSheet.hairlineWidth,
    marginBottom: spacing.sm,
  },
  appIcon: {
    width: 36,
    height: 36,
    borderRadius: 8,
  },
  appName: {
    fontSize: font.md,
    fontWeight: '700',
    flex: 1,
  },
  sectionLabel: {
    fontSize: font.xs,
    fontWeight: '500',
    letterSpacing: 0.4,
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.md,
    paddingBottom: spacing.xs,
  },
  item: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.lg,
    paddingVertical: 13,
    gap: spacing.md,
  },
  itemIndicator: {
    width: 24,
    alignItems: 'center',
  },
  itemLabel: {
    flex: 1,
    fontSize: font.md,
    fontWeight: '500',
  },
  itemValue: {
    fontSize: font.sm,
    maxWidth: 120,
  },
});
