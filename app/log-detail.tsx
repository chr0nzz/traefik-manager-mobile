import { ScrollView, StyleSheet, TouchableOpacity, View } from 'react-native';
import { Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useThemeStore } from '../src/store/theme';
import { font, radius, spacing } from '../src/theme';

function parseLogLine(line: string) {
  const full = line.match(
    /^(\S+) \S+ \S+ \[([^\]]+)\] "(\w+) (\S+)[^"]*" (\d+) (\S+) "[^"]*" "[^"]*" \S+ "([^"]*)" "([^"]*)" (\S+)/
  );
  if (full) {
    const [, ip, date, method, path, statusStr, size, service, serviceUrl, duration] = full;
    return {
      ip, date: date.split(' ')[0], method, path,
      status: parseInt(statusStr, 10),
      size: size === '-' ? '—' : size,
      service: service === '-' ? '' : service,
      serviceUrl: serviceUrl === '-' ? '' : serviceUrl,
      duration,
    };
  }
  const m = line.match(
    /^(\S+) \S+ \S+ \[([^\]]+)\] "(\w+) (\S+)[^"]*" (\d+) (\S+)(?:[^"]*"[^"]*"[^"]*"[^"]*")? ?(\S+)?/
  );
  if (!m) return null;
  const [, ip, date, method, path, statusStr, size, duration] = m;
  return {
    ip, date: date.split(' ')[0].replace(/\[/, ''), method, path,
    status: parseInt(statusStr, 10),
    size: size === '-' ? '—' : size,
    service: '', serviceUrl: '', duration: duration ?? '',
  };
}

function statusColor(status: number, c: ReturnType<typeof useThemeStore.getState>['colors']) {
  if (!status)       return c.muted  ?? '#8b949e';
  if (status >= 500) return c.red    ?? '#f87171';
  if (status >= 400) return c.yellow ?? '#fbbf24';
  if (status >= 300) return c.teal   ?? '#1abc9c';
  if (status >= 200) return c.green  ?? '#22c55e';
  return c.muted;
}

function methodColor(method: string, c: ReturnType<typeof useThemeStore.getState>['colors']) {
  switch (method) {
    case 'GET':    return c.blue;
    case 'POST':   return c.green;
    case 'PUT':    return c.orange;
    case 'PATCH':  return c.yellow;
    case 'DELETE': return c.red;
    default:       return c.muted;
  }
}

function MetaRow({ label, value, c }: { label: string; value: string; c: ReturnType<typeof useThemeStore.getState>['colors'] }) {
  return (
    <View style={styles.metaRow}>
      <Text style={[styles.metaLabel, { color: c.muted }]}>{label}</Text>
      <Text selectable style={[styles.metaValue, { color: c.text }]}>{value}</Text>
    </View>
  );
}

export default function LogDetailScreen() {
  const router  = useRouter();
  const insets  = useSafeAreaInsets();
  const c       = useThemeStore(s => s.colors);
  const { raw } = useLocalSearchParams<{ raw: string }>();

  const parsed = raw ? parseLogLine(raw) : null;
  const sc = parsed ? statusColor(parsed.status, c) : null;
  const mc = parsed ? methodColor(parsed.method, c) : null;

  return (
    <View style={[styles.screen, { backgroundColor: c.bg }]}>
      <View style={[styles.headerBar, { paddingTop: insets.top + 4, borderBottomColor: c.border, backgroundColor: c.card }]}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={8} style={styles.backBtn}>
          <MaterialCommunityIcons name="arrow-left" size={22} color={c.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: c.text }]}>Log Entry</Text>
        <View style={styles.headerSpacer} />
      </View>

      <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 24 }]}>
        {parsed && (
          <>
            <View style={styles.badges}>
              <View style={[styles.badge, { backgroundColor: sc! + '22', borderColor: sc! + '55' }]}>
                <Text style={[styles.badgeText, { color: sc! }]}>{parsed.status || 'tunnel'}</Text>
              </View>
              <View style={[styles.badge, { backgroundColor: mc! + '22', borderColor: mc! + '55' }]}>
                <Text style={[styles.badgeText, { color: mc! }]}>{parsed.method}</Text>
              </View>
            </View>

            <View style={[styles.section, { backgroundColor: c.card, borderColor: c.border }]}>
              <MetaRow label="Path"     value={parsed.path}     c={c} />
              <View style={[styles.divider, { backgroundColor: c.border }]} />
              <MetaRow label="IP"       value={parsed.ip}       c={c} />
              <View style={[styles.divider, { backgroundColor: c.border }]} />
              <MetaRow label="Date"     value={parsed.date}     c={c} />
              {!!parsed.size && parsed.size !== '—' && (
                <>
                  <View style={[styles.divider, { backgroundColor: c.border }]} />
                  <MetaRow label="Size" value={`${parsed.size} B`} c={c} />
                </>
              )}
              {!!parsed.duration && (
                <>
                  <View style={[styles.divider, { backgroundColor: c.border }]} />
                  <MetaRow label="Duration" value={parsed.duration} c={c} />
                </>
              )}
              {!!parsed.service && (
                <>
                  <View style={[styles.divider, { backgroundColor: c.border }]} />
                  <MetaRow label="Service" value={parsed.service} c={c} />
                </>
              )}
              {!!parsed.serviceUrl && (
                <>
                  <View style={[styles.divider, { backgroundColor: c.border }]} />
                  <MetaRow label="URL" value={parsed.serviceUrl} c={c} />
                </>
              )}
            </View>
          </>
        )}

        <Text style={[styles.sectionLabel, { color: c.muted }]}>RAW</Text>
        <View style={[styles.rawBox, { backgroundColor: c.card, borderColor: c.border }]}>
          <ScrollView horizontal showsHorizontalScrollIndicator>
            <Text selectable style={[styles.rawText, { color: c.text }]}>{raw}</Text>
          </ScrollView>
        </View>
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
  backBtn:      { padding: 2 },
  headerTitle:  { flex: 1, fontSize: font.lg, fontWeight: '700' },
  headerSpacer: { width: 26 },
  content: { padding: spacing.lg, gap: spacing.md },
  badges: { flexDirection: 'row', gap: spacing.sm },
  badge: {
    borderRadius: radius.sm,
    borderWidth: 1,
    paddingHorizontal: 10,
    paddingVertical: 4,
  },
  badgeText: { fontSize: font.sm, fontWeight: '700', letterSpacing: 0.3 },
  section: {
    borderRadius: radius.md,
    borderWidth: 1,
    overflow: 'hidden',
  },
  metaRow: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    paddingHorizontal: spacing.md,
    paddingVertical: 12,
    gap: spacing.md,
  },
  metaLabel: { fontSize: font.sm, width: 72, flexShrink: 0 },
  metaValue:  { fontSize: font.sm, flex: 1 },
  divider: { height: 1, marginHorizontal: spacing.md },
  sectionLabel: {
    fontSize: font.xs, fontWeight: '700', letterSpacing: 0.8,
    textTransform: 'uppercase', paddingHorizontal: 4,
  },
  rawBox: {
    borderRadius: radius.md,
    borderWidth: 1,
    padding: spacing.md,
  },
  rawText: {
    fontSize: 12,
    fontFamily: 'monospace',
    lineHeight: 20,
  },
});
