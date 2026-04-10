import { ScrollView, StyleSheet, Switch, TouchableOpacity, View } from 'react-native';
import { Surface, Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTabsStore } from '../../src/store/tabs';
import { useThemeStore } from '../../src/store/theme';
import { font, radius, spacing } from '../../src/theme';

const LINE_OPTIONS = [100, 150, 200];

export default function LogsSettingsScreen() {
  const router        = useRouter();
  const insets        = useSafeAreaInsets();
  const c             = useThemeStore(s => s.colors);
  const showLogsTab   = useTabsStore(s => s.showLogsTab);
  const logLines      = useTabsStore(s => s.logLines);
  const setShowLogs   = useTabsStore(s => s.setShowLogsTab);
  const setLogLines   = useTabsStore(s => s.setLogLines);

  return (
    <View style={[styles.screen, { backgroundColor: c.bg }]}>
      <View style={[styles.headerBar, { paddingTop: insets.top + 4, borderBottomColor: c.border, backgroundColor: c.card }]}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={8} style={styles.backBtn}>
          <MaterialCommunityIcons name="arrow-left" size={22} color={c.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: c.text }]}>Logs</Text>
        <View style={styles.backBtn} />
      </View>

      <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 32 }]}>
        <Surface style={[styles.group, { backgroundColor: c.card }]} elevation={1}>
          <View style={styles.row}>
            <View style={styles.rowLeft}>
              <Text style={[styles.rowLabel, { color: c.text }]}>Show Logs Tab</Text>
              <Text style={[styles.rowHint, { color: c.muted }]}>Adds an Access Logs tab to the navigation bar</Text>
            </View>
            <Switch
              value={showLogsTab}
              onValueChange={setShowLogs}
              trackColor={{ false: c.border, true: c.blue }}
              thumbColor={showLogsTab ? '#fff' : c.muted}
            />
          </View>
        </Surface>

        {showLogsTab && (
          <>
            <Text style={[styles.sectionLabel, { color: c.muted }]}>DEFAULT LINE COUNT</Text>
            <Surface style={[styles.group, { backgroundColor: c.card }]} elevation={1}>
              {LINE_OPTIONS.map((opt, i) => (
                <TouchableOpacity
                  key={opt}
                  style={[styles.row, i < LINE_OPTIONS.length - 1 && { borderBottomWidth: 1, borderBottomColor: c.border }]}
                  onPress={() => setLogLines(opt)}
                >
                  <Text style={[styles.rowLabel, { color: c.text }]}>{opt} lines</Text>
                  {logLines === opt && (
                    <MaterialCommunityIcons name="check" size={18} color={c.blue} />
                  )}
                </TouchableOpacity>
              ))}
            </Surface>

            <Text style={[styles.hint, { color: c.muted }]}>
              Requires ACCESS_LOG_PATH to be set on the Traefik Manager server and the log file to be readable.
            </Text>
          </>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen:      { flex: 1 },
  headerBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.md,
    paddingBottom: 12,
    borderBottomWidth: 1,
  },
  backBtn:     { width: 32 },
  headerTitle: { fontSize: font.lg, fontWeight: '700' },
  content:     { padding: spacing.lg, gap: spacing.sm },
  sectionLabel: {
    fontSize: font.xs,
    fontWeight: '700',
    letterSpacing: 0.8,
    textTransform: 'uppercase',
    marginBottom: 2,
    marginTop: spacing.sm,
    paddingHorizontal: 4,
  },
  group: {
    borderRadius: radius.md,
    overflow: 'hidden',
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.md,
    paddingVertical: 14,
  },
  rowLeft:  { flex: 1, marginRight: spacing.md },
  rowLabel: { fontSize: font.md, fontWeight: '500' },
  rowHint:  { fontSize: font.sm, marginTop: 2 },
  hint: {
    fontSize: font.sm,
    paddingHorizontal: 4,
    lineHeight: 18,
  },
});
