import { useQueryClient } from '@tanstack/react-query';
import { useMemo, useRef, useState } from 'react';
import { Animated, Pressable, RefreshControl, StyleSheet, TouchableOpacity, View } from 'react-native';
import { Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { TopBar } from '../../src/components/TopBar';
import { DemoBanner } from '../../src/components/DemoBanner';
import { useLogs } from '../../src/hooks/useLogs';
import { useLayout } from '../../src/hooks/useLayout';
import { useTabsStore } from '../../src/store/tabs';
import { useThemeStore } from '../../src/store/theme';
import { useDrawerStore } from '../../src/store/drawer';
import { useTabSwipe } from '../../src/hooks/useTabSwipe';
import { useRouter } from 'expo-router';
import { font, radius, spacing } from '../../src/theme';

const LINE_OPTIONS = [100, 150, 200];

interface ParsedLog {
  ip: string;
  date: string;
  method: string;
  path: string;
  status: number;
  size: string;
  duration: string;
  service: string;
  serviceUrl: string;
  raw: string;
}

function parseLogLine(line: string): ParsedLog | null {
  // Full Traefik CLF+extra: ... "referer" "ua" count "router@provider" "serviceURL" duration
  const full = line.match(
    /^(\S+) \S+ \S+ \[([^\]]+)\] "(\w+) (\S+)[^"]*" (\d+) (\S+) "[^"]*" "[^"]*" \S+ "([^"]*)" "([^"]*)" (\S+)/
  );
  if (full) {
    const [, ip, date, method, path, statusStr, size, service, serviceUrl, duration] = full;
    return {
      ip, date: date.split(' ')[0], method, path,
      status: parseInt(statusStr, 10) || 0,
      size: size === '-' ? '—' : size,
      service: service === '-' ? '' : service,
      serviceUrl: serviceUrl === '-' ? '' : serviceUrl,
      duration, raw: line,
    };
  }
  // Fallback: basic CLF without Traefik extras
  const m = line.match(
    /^(\S+) \S+ \S+ \[([^\]]+)\] "(\w+) (\S+)[^"]*" (\d+) (\S+)(?:[^"]*"[^"]*"[^"]*"[^"]*")? ?(\S+)?/
  );
  if (!m) return null;
  const [, ip, date, method, path, statusStr, size, duration] = m;
  return {
    ip, date: date.split(' ')[0].replace(/\[/, ''), method, path,
    status: parseInt(statusStr, 10) || 0,
    size: size === '-' ? '—' : size,
    service: '', serviceUrl: '', duration: duration ?? '', raw: line,
  };
}

function statusColor(status: number, colors: ReturnType<typeof useThemeStore.getState>['colors']): string {
  if (!status)       return colors.muted  ?? '#8b949e';
  if (status >= 500) return colors.red    ?? '#f87171';
  if (status >= 400) return colors.yellow ?? '#fbbf24';
  if (status >= 300) return colors.teal   ?? '#1abc9c';
  if (status >= 200) return colors.green  ?? '#22c55e';
  return colors.muted;
}

function methodColor(method: string, colors: ReturnType<typeof useThemeStore.getState>['colors']): string {
  switch (method) {
    case 'GET':    return colors.blue;
    case 'POST':   return colors.green;
    case 'PUT':    return colors.orange;
    case 'PATCH':  return colors.yellow;
    case 'DELETE': return colors.red;
    default:       return colors.muted;
  }
}

function LogCard({ item, c, onPress }: { item: string; c: ReturnType<typeof useThemeStore.getState>['colors']; onPress: () => void }) {
  const parsed = parseLogLine(item);

  if (!parsed) {
    return (
      <TouchableOpacity onPress={onPress} activeOpacity={0.7} style={[styles.card, { backgroundColor: c.card, borderColor: c.border }]}>
        <Text style={[styles.rawLine, { color: c.muted }]}>{item}</Text>
      </TouchableOpacity>
    );
  }

  const sc = statusColor(parsed.status, c);
  const mc = methodColor(parsed.method, c);

  return (
    <TouchableOpacity onPress={onPress} activeOpacity={0.7} style={[styles.card, { backgroundColor: c.card, borderColor: c.border }]}>
      <View style={styles.cardTop}>
        <View style={[styles.badge, { backgroundColor: sc + '22', borderColor: sc + '55' }]}>
          <Text style={[styles.badgeText, { color: sc }]}>{parsed.status || 'tunnel'}</Text>
        </View>
        <View style={[styles.badge, { backgroundColor: mc + '22', borderColor: mc + '55' }]}>
          <Text style={[styles.badgeText, { color: mc }]}>{parsed.method}</Text>
        </View>
        <Text style={[styles.path, { color: c.text }]} numberOfLines={1} ellipsizeMode="middle">
          {parsed.path}
        </Text>
      </View>
      <View style={styles.cardBottom}>
        <Text style={[styles.meta, { color: c.muted }]}>{parsed.ip}</Text>
        <Text style={[styles.metaDot, { color: c.border }]}>·</Text>
        <Text style={[styles.meta, { color: c.muted }]}>{parsed.date}</Text>
        {!!parsed.size && parsed.size !== '—' && (
          <>
            <Text style={[styles.metaDot, { color: c.border }]}>·</Text>
            <Text style={[styles.meta, { color: c.muted }]}>{parsed.size}B</Text>
          </>
        )}
        {!!parsed.duration && (
          <>
            <Text style={[styles.metaDot, { color: c.border }]}>·</Text>
            <Text style={[styles.meta, { color: c.muted }]}>{parsed.duration}</Text>
          </>
        )}
        {!!parsed.service && (
          <>
            <Text style={[styles.metaDot, { color: c.border }]}>·</Text>
            <Text style={[styles.meta, { color: c.blue }]} numberOfLines={1}>{parsed.service}</Text>
          </>
        )}
      </View>
    </TouchableOpacity>
  );
}


export default function LogsScreen() {
  const c          = useThemeStore(s => s.colors);
  const logLines   = useTabsStore(s => s.logLines);
  const setLines   = useTabsStore(s => s.setLogLines);
  const swipe      = useTabSwipe('logs');
  const scrollAnim = useRef(new Animated.Value(0)).current;
  const qc         = useQueryClient();
  const router     = useRouter();
  const [search, setSearch] = useState('');

  const openDrawer = useDrawerStore(s => s.open);
  const { contentPadding, contentMaxWidth, listBottomPadding } = useLayout();

  const { data, isFetching, isError } = useLogs();

  const lines = data?.lines ?? [];
  const error = data?.error;

  const displayLines = useMemo(() => {
    const reversed = [...lines].reverse();
    if (!search.trim()) return reversed;
    const q = search.toLowerCase();
    return reversed.filter(l => l.toLowerCase().includes(q));
  }, [lines, search]);

  return (
    <View style={[styles.container, { backgroundColor: c.bg }]} {...swipe}>
      <TopBar
        title="Logs"
        scrollAnim={scrollAnim}
        onMenuPress={openDrawer}
        searchValue={search}
        onSearchChange={setSearch}
        searchPlaceholder="Search logs..."
        searchAccent={c.blue}
        overflowSections={[{
          title: 'Log lines',
          items: LINE_OPTIONS.map(opt => ({
            label: `${opt} lines`,
            selected: logLines === opt,
            onPress: () => { setLines(opt); qc.invalidateQueries({ queryKey: ['logs'] }); },
          })),
        }]}
        wideFilters={
          <View style={styles.wideRow}>
            {LINE_OPTIONS.map(opt => (
              <TouchableOpacity
                key={opt}
                style={[styles.lineChip, { borderColor: c.border, backgroundColor: c.card }, logLines === opt && { borderColor: c.blue + '66', backgroundColor: c.blue + '18' }]}
                onPress={() => { setLines(opt); qc.invalidateQueries({ queryKey: ['logs'] }); }}
              >
                <Text style={[styles.lineChipText, { color: logLines === opt ? c.blue : c.muted }]}>{opt} lines</Text>
              </TouchableOpacity>
            ))}
          </View>
        }
        right={
          <Pressable
            style={[styles.refreshBtn, { borderColor: c.border }]}
            onPress={() => qc.invalidateQueries({ queryKey: ['logs'] })}
            disabled={isFetching}
            android_ripple={{ color: c.muted + '40' }}
          >
            <MaterialCommunityIcons name="refresh" size={20} color={isFetching ? c.muted : c.blue} />
          </Pressable>
        }
      />
      <DemoBanner />

      {isError || error ? (
        <View style={styles.center}>
          <MaterialCommunityIcons name="alert-circle-outline" size={36} color={c.red ?? '#f87171'} />
          <Text style={[styles.errorText, { color: c.muted }]}>{error ?? 'Failed to load logs'}</Text>
          <Text style={[styles.errorHint, { color: c.muted }]}>Check ACCESS_LOG_PATH on the server</Text>
        </View>
      ) : lines.length === 0 && !isFetching ? (
        <View style={styles.center}>
          <MaterialCommunityIcons name="text-search" size={36} color={c.muted} />
          <Text style={[styles.emptyText, { color: c.muted }]}>No log entries found</Text>
        </View>
      ) : (
        <Animated.FlatList
          data={displayLines}
          keyExtractor={(_, i) => String(i)}
          contentContainerStyle={[styles.listContent, { paddingHorizontal: contentPadding, paddingBottom: listBottomPadding, alignSelf: 'center', width: '100%', maxWidth: contentMaxWidth }]}
          onScroll={Animated.event(
            [{ nativeEvent: { contentOffset: { y: scrollAnim } } }],
            { useNativeDriver: false },
          )}
          scrollEventThrottle={16}
          refreshControl={
            <RefreshControl
              refreshing={isFetching}
              onRefresh={() => qc.invalidateQueries({ queryKey: ['logs'] })}
              tintColor={c.blue}
            />
          }
          renderItem={({ item }) => <LogCard item={item} c={c} onPress={() => router.push({ pathname: '/log-detail', params: { raw: item } })} />}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  wideRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  lineChip: {
    height: 36,
    paddingHorizontal: 12,
    borderRadius: radius.sm,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  lineChipText: { fontSize: font.xs, fontWeight: '700' },
  refreshBtn: {
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.sm,
    borderWidth: 1,
  },
  listContent: { paddingTop: spacing.sm, gap: spacing.sm },
  card: {
    borderRadius: radius.md,
    borderWidth: 1,
    padding: spacing.md,
    gap: 6,
  },
  cardTop: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
  },
  badge: {
    borderRadius: radius.sm,
    borderWidth: 1,
    paddingHorizontal: 6,
    paddingVertical: 2,
  },
  badgeText: {
    fontSize: font.xs,
    fontWeight: '700',
    letterSpacing: 0.3,
  },
  path: {
    flex: 1,
    fontSize: font.sm,
    fontFamily: 'monospace',
  },
  cardBottom: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 4,
  },
  meta:    { fontSize: font.xs },
  metaDot: { fontSize: font.xs },
  rawLine: { fontSize: 11, fontFamily: 'monospace' },
  center:     { flex: 1, justifyContent: 'center', alignItems: 'center', gap: 8, padding: spacing.xl },
  errorText:  { fontSize: font.md, textAlign: 'center' },
  errorHint:  { fontSize: font.sm, textAlign: 'center' },
  emptyText:  { fontSize: font.md },
});
