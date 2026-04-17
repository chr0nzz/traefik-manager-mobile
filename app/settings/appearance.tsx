import { Platform, ScrollView, StyleSheet, Switch, TouchableOpacity, View, useColorScheme } from 'react-native';
import { Surface, Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useThemeStore, ThemeMode } from '../../src/store/theme';
import { font, radius, spacing } from '../../src/theme';

const MODES: { key: ThemeMode; label: string; icon: string }[] = [
  { key: 'light',  label: 'Light',  icon: 'weather-sunny' },
  { key: 'system', label: 'System', icon: 'theme-light-dark' },
  { key: 'dark',   label: 'Dark',   icon: 'weather-night' },
];

const supportsDynamic = Platform.OS === 'android' && Number(Platform.Version) >= 31;

export default function AppearanceScreen() {
  const router         = useRouter();
  const insets         = useSafeAreaInsets();
  const mode           = useThemeStore(s => s.mode);
  const dynamicColors  = useThemeStore(s => s.dynamicColors);
  const setMode        = useThemeStore(s => s.setMode);
  const setDynamic     = useThemeStore(s => s.setDynamicColors);
  const c              = useThemeStore(s => s.colors);
  const colorScheme    = useColorScheme();
  const systemIsDark   = colorScheme === 'dark';

  return (
    <View style={[styles.screen, { backgroundColor: c.bg }]}>
      <View style={[styles.headerBar, { paddingTop: insets.top + 4, borderBottomColor: c.border, backgroundColor: c.card }]}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={8} style={styles.backBtn}>
          <MaterialCommunityIcons name="arrow-left" size={22} color={c.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: c.text }]}>Appearance</Text>
        <View style={styles.headerSpacer} />
      </View>

      <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 24 }]}>
        <Text style={[styles.sectionLabel, { color: c.muted }]}>THEME</Text>

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
          System follows your device's appearance setting.
        </Text>

        {supportsDynamic && (
          <>
            <Text style={[styles.sectionLabel, { color: c.muted, marginTop: spacing.md }]}>COLORS</Text>
            <Surface style={[styles.sectionBody, { backgroundColor: c.card }]} elevation={1}>
              <View style={styles.switchRow}>
                <View style={[
                  styles.modeIconWrap,
                  { backgroundColor: dynamicColors ? c.blue + '20' : c.bg, borderColor: dynamicColors ? c.blue + '55' : c.border },
                ]}>
                  <MaterialCommunityIcons name="palette-swatch-variant" size={18} color={dynamicColors ? c.blue : c.muted} />
                </View>
                <View style={styles.switchLabelWrap}>
                  <Text style={[styles.modeLabel, { color: c.text }]}>Dynamic colors</Text>
                </View>
                <Switch
                  value={dynamicColors}
                  onValueChange={setDynamic}
                  trackColor={{ false: c.border, true: c.blue + '99' }}
                  thumbColor={dynamicColors ? c.blue : c.muted}
                />
              </View>
            </Surface>
            <Text style={[styles.hint, { color: c.muted }]}>
              Uses your wallpaper and style colors (Android 12+). Works with any theme mode.
            </Text>
          </>
        )}
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
  switchRow: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: spacing.md, paddingVertical: 12,
    gap: spacing.md,
  },
  modeIconWrap: {
    width: 36, height: 36, borderRadius: radius.sm,
    borderWidth: 1, alignItems: 'center', justifyContent: 'center',
  },
  modeLabel: { flex: 1, fontSize: font.md },
  switchLabelWrap: { flex: 1 },
  hint: { fontSize: font.xs, paddingHorizontal: 4, marginTop: 4 },
});
