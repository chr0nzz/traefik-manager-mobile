import { Platform, ScrollView, StyleSheet, Switch, TouchableOpacity, View, useColorScheme } from 'react-native';
import { Surface, Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useThemeStore, ThemeMode } from '../../src/store/theme';
import { useOrientationStore } from '../../src/store/orientation';
import { font, radius, spacing } from '../../src/theme';

const BASE_MODES: { key: ThemeMode; label: string; icon: string }[] = [
  { key: 'light',  label: 'Light',  icon: 'weather-sunny' },
  { key: 'system', label: 'System', icon: 'theme-light-dark' },
  { key: 'dark',   label: 'Dark',   icon: 'weather-night' },
];

const DYNAMIC_MODE: { key: ThemeMode; label: string; icon: string } =
  { key: 'dynamic', label: 'Dynamic', icon: 'palette-swatch-variant' };

const MODES = Platform.OS === 'android' && Number(Platform.Version) >= 31
  ? [...BASE_MODES, DYNAMIC_MODE]
  : BASE_MODES;

export default function AppearanceScreen() {
  const router      = useRouter();
  const insets      = useSafeAreaInsets();
  const { mode, setMode } = useThemeStore();
  const c           = useThemeStore(s => s.colors);
  const colorScheme = useColorScheme();
  const systemIsDark = colorScheme === 'dark';
  const { locked: orientationLocked, setLocked: setOrientationLocked } = useOrientationStore();

  return (
    <View style={[styles.screen, { backgroundColor: c.bg }]}>
      {/* Header */}
      <View style={[styles.headerBar, { paddingTop: insets.top + 4, borderBottomColor: c.border, backgroundColor: c.card }]}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={8} style={styles.backBtn}>
          <MaterialCommunityIcons name="arrow-left" size={22} color={c.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: c.text }]}>Appearance</Text>
        <View style={styles.headerSpacer} />
      </View>

      <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 24 }]}>
        {/* Section label */}
        <Text style={[styles.sectionLabel, { color: c.muted }]}>THEME</Text>

        {/* Section body */}
        <Surface style={[styles.sectionBody, { backgroundColor: c.card }]} elevation={1}>
          {MODES.map((m, i) => {
            const active = mode === m.key;
            return (
              <TouchableOpacity
                key={m.key}
                style={[
                  styles.modeRow,
                  i < MODES.length - 1 && { borderBottomWidth: 1, borderBottomColor: c.border },
                ]}
                onPress={() => setMode(m.key, systemIsDark)}
                activeOpacity={0.7}
              >
                <View style={[
                  styles.modeIconWrap,
                  { backgroundColor: active ? c.blue + '20' : c.bg, borderColor: active ? c.blue + '55' : c.border },
                ]}>
                  <MaterialCommunityIcons name={m.icon as any} size={18} color={active ? c.blue : c.muted} />
                </View>
                <Text style={[styles.modeLabel, { color: active ? c.text : c.muted, fontWeight: active ? '600' : '400' }]}>
                  {m.label}
                </Text>
                {active && (
                  <MaterialCommunityIcons name="check-circle" size={18} color={c.blue} />
                )}
              </TouchableOpacity>
            );
          })}
        </Surface>

        <Text style={[styles.hint, { color: c.muted }]}>
          System follows your device's appearance setting.{'\n'}
          {Platform.OS === 'android' && Number(Platform.Version) >= 31
            ? 'Dynamic follows your wallpaper & style colors (Android 12+).'
            : ''}
        </Text>

        <Text style={[styles.sectionLabel, { color: c.muted }]}>ORIENTATION</Text>

        <Surface style={[styles.sectionBody, { backgroundColor: c.card }]} elevation={1}>
          <View style={styles.toggleRow}>
            <MaterialCommunityIcons name="screen-rotation-lock" size={18} color={c.blue} />
            <Text style={[styles.toggleLabel, { color: c.text }]}>Lock to Portrait</Text>
            <Switch
              value={orientationLocked}
              onValueChange={setOrientationLocked}
              trackColor={{ false: c.border, true: c.blue + '80' }}
              thumbColor={orientationLocked ? c.blue : c.muted}
            />
          </View>
        </Surface>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  headerBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.md,
    borderBottomWidth: 1,
    gap: spacing.sm,
  },
  backBtn:       { padding: 2 },
  headerTitle:   { flex: 1, fontSize: font.lg, fontWeight: '700' },
  headerSpacer:  { width: 26 },
  content: { padding: spacing.lg, gap: spacing.sm },
  sectionLabel: {
    fontSize: font.xs, fontWeight: '700', letterSpacing: 0.8,
    textTransform: 'uppercase', paddingHorizontal: 4,
  },
  sectionBody: {
    borderRadius: radius.md, overflow: 'hidden',
  },
  modeRow: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: spacing.md, paddingVertical: 14,
    gap: spacing.md,
  },
  modeIconWrap: {
    width: 36, height: 36, borderRadius: radius.sm,
    borderWidth: 1, alignItems: 'center', justifyContent: 'center',
  },
  modeLabel: { flex: 1, fontSize: font.md },
  hint: { fontSize: font.xs, paddingHorizontal: 4, marginTop: 4 },
  toggleRow: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: spacing.md, paddingVertical: 14,
    gap: spacing.md,
  },
  toggleLabel: { flex: 1, fontSize: font.md },
});
