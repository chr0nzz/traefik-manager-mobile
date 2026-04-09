import { Animated, StyleSheet, TouchableOpacity, View } from 'react-native';
import { Surface, Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import Constants from 'expo-constants';
import { useRef } from 'react';
import { TopBar } from '../../src/components/TopBar';
import { useConnection } from '../../src/store/connection';
import { useThemeStore, ThemeMode } from '../../src/store/theme';
import { useTabsStore } from '../../src/store/tabs';
import { useAppLock } from '../../src/store/applock';
import { useTabSwipe } from '../../src/hooks/useTabSwipe';
import { useBackups } from '../../src/hooks/useBackups';
import { DemoBanner } from '../../src/components/DemoBanner';
import { useLayout } from '../../src/hooks/useLayout';
import { font, radius, spacing } from '../../src/theme';

const version = Constants.expoConfig?.version ?? '—';

const THEME_LABEL: Record<ThemeMode, string> = {
  light: '☀ Light',
  dark: '🌙 Dark',
  system: '⚙ System',
};

type Colors = ReturnType<typeof useThemeStore.getState>['colors'];

function SectionLabel({ title, c }: { title: string; c: Colors }) {
  return (
    <Text style={[styles.sectionLabel, { color: c.muted }]}>{title}</Text>
  );
}

function NavGroup({ children, c }: { children: React.ReactNode; c: Colors }) {
  return (
    <Surface style={[styles.group, { backgroundColor: c.card }]} elevation={1}>
      {children}
    </Surface>
  );
}

function NavRow({
  icon, iconColor, label, value, onPress, isLast, c,
}: {
  icon: string; iconColor?: string; label: string; value?: string;
  onPress: () => void; isLast: boolean; c: Colors;
}) {
  return (
    <TouchableOpacity
      style={[styles.navRow, !isLast && { borderBottomWidth: 1, borderBottomColor: c.border }]}
      onPress={onPress}
      activeOpacity={0.6}
    >
      <MaterialCommunityIcons name={icon as any} size={18} color={iconColor ?? c.muted} style={styles.navIcon} />
      <Text style={[styles.navLabel, { color: c.text }]}>{label}</Text>
      <View style={styles.navRight}>
        {!!value && (
          <Text style={[styles.navValue, { color: c.muted }]} numberOfLines={1}>{value}</Text>
        )}
        <MaterialCommunityIcons name="chevron-right" size={18} color={c.border} />
      </View>
    </TouchableOpacity>
  );
}


export default function SettingsScreen() {
  const router      = useRouter();
  const { baseUrl } = useConnection();
  const { mode }    = useThemeStore();
  const showLogsTab = useTabsStore(s => s.showLogsTab);
  const c           = useThemeStore(s => s.colors);
  const appLockEnabled = useAppLock(s => s.enabled);
  const swipe       = useTabSwipe('settings');
  const scrollAnim  = useRef(new Animated.Value(0)).current;
  const { listBottomPadding } = useLayout();

  const { data: backups } = useBackups();
  const backupCount = backups?.length ?? 0;

  return (
    <View style={[styles.container, { backgroundColor: c.bg }]} {...swipe}>
      <TopBar title="Settings" scrollAnim={scrollAnim} accent={c.blue} icon="cog" />
      <DemoBanner />
      <Animated.ScrollView
        style={styles.scroll}
        contentContainerStyle={[styles.content, { paddingBottom: listBottomPadding }]}
        onScroll={Animated.event(
          [{ nativeEvent: { contentOffset: { y: scrollAnim } } }],
          { useNativeDriver: false },
        )}
        scrollEventThrottle={16}
      >
        <SectionLabel title="GENERAL" c={c} />
        <NavGroup c={c}>
          <NavRow
            icon="theme-light-dark"
            iconColor={c.blue}
            label="Appearance"
            value={THEME_LABEL[mode]}
            onPress={() => router.push('/settings/appearance')}
            isLast={false}
            c={c}
          />
          <NavRow
            icon="lock-outline"
            iconColor={c.blue}
            label="App Lock"
            value={appLockEnabled ? 'Enabled' : 'Disabled'}
            onPress={() => router.push('/settings/applock')}
            isLast={false}
            c={c}
          />
          <NavRow
            icon="server-network"
            iconColor={c.blue}
            label="Traefik"
            value="Domains & resolvers"
            onPress={() => router.push('/settings/traefik')}
            isLast={false}
            c={c}
          />
          <NavRow
            icon="server"
            label="Server"
            value={baseUrl ? baseUrl.replace(/^https?:\/\//, '') : 'Not connected'}
            onPress={() => router.push('/settings/server')}
            isLast
            c={c}
          />
        </NavGroup>

        <SectionLabel title="TABS" c={c} />
        <NavGroup c={c}>
          <NavRow
            icon="text-search"
            iconColor={c.blue}
            label="Logs"
            value={showLogsTab ? 'Enabled' : 'Disabled'}
            onPress={() => router.push('/settings/logs')}
            isLast
            c={c}
          />
        </NavGroup>

        <SectionLabel title="DATA" c={c} />
        <NavGroup c={c}>
          <NavRow
            icon="database-export-outline"
            label="Backups"
            value={backupCount > 0 ? `${backupCount} backup${backupCount !== 1 ? 's' : ''}` : 'No backups'}
            onPress={() => router.push('/settings/backups')}
            isLast
            c={c}
          />
        </NavGroup>

        <SectionLabel title="INFO" c={c} />
        <NavGroup c={c}>
          <NavRow
            icon="information-outline"
            label="About"
            value={`v${version}`}
            onPress={() => router.push('/settings/about')}
            isLast
            c={c}
          />
        </NavGroup>
      </Animated.ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  scroll:    { flex: 1 },
  content:   { padding: spacing.lg, gap: spacing.sm },
  sectionLabel: {
    fontSize: font.sm,
    fontWeight: '500',
    letterSpacing: 0.1,
    marginBottom: 4,
    marginTop: spacing.sm,
    paddingHorizontal: 4,
  },
  group: {
    borderRadius: radius.md,
    overflow: 'hidden',
  },
  navRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.md,
    paddingVertical: 14,
    gap: spacing.sm,
  },
  navIcon:  { width: 22 },
  navLabel: { flex: 1, fontSize: font.md, fontWeight: '500' },
  navRight: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  navValue: { fontSize: font.sm, maxWidth: 160 },
});
