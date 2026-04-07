import { useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { Animated, FlatList, RefreshControl, StyleSheet, TouchableOpacity, View } from 'react-native';
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
import { font, radius, spacing } from '../../src/theme';

const LINE_OPTIONS = [100, 500, 1000];

function statusColor(line: string, colors: ReturnType<typeof useThemeStore.getState>['colors']): string {
  const m = line.match(/" (\d{3}) /);
  if (!m) return colors.text;
  const code = parseInt(m[1], 10);
  if (code >= 500) return colors.red  ?? '#f87171';
  if (code >= 400) return colors.yellow ?? '#fbbf24';
  return colors.text;
}

export default function LogsScreen() {
  const c          = useThemeStore(s => s.colors);
  const logLines   = useTabsStore(s => s.logLines);
  const setLines   = useTabsStore(s => s.setLogLines);
  const swipe      = useTabSwipe('logs');
  const scrollAnim = useRef(new Animated.Value(0)).current;
  const qc         = useQueryClient();

  const openDrawer = useDrawerStore(s => s.open);
  const { contentPadding, contentMaxWidth } = useLayout();

  const { data, isFetching, isError } = useLogs();
  const [showPicker, setShowPicker] = useState(false);

  const lines = data?.lines ?? [];
  const error = data?.error;

  return (
    <View style={[styles.container, { backgroundColor: c.bg }]} {...swipe}>
      <TopBar title="Logs" scrollAnim={scrollAnim} onMenuPress={openDrawer} />
      <DemoBanner />

      <View style={[styles.toolbar, { borderBottomColor: c.border, paddingHorizontal: contentPadding }]}>
        <TouchableOpacity
          style={[styles.lineBtn, { borderColor: c.border, backgroundColor: c.card }]}
          onPress={() => setShowPicker(v => !v)}
        >
          <MaterialCommunityIcons name="format-list-numbered" size={14} color={c.muted} />
          <Text style={[styles.lineBtnText, { color: c.muted }]}>{logLines} lines</Text>
          <MaterialCommunityIcons name={showPicker ? 'chevron-up' : 'chevron-down'} size={14} color={c.muted} />
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.refreshBtn, { borderColor: c.border, backgroundColor: c.card }]}
          onPress={() => qc.invalidateQueries({ queryKey: ['logs'] })}
          disabled={isFetching}
        >
          <MaterialCommunityIcons
            name="refresh"
            size={16}
            color={isFetching ? c.muted : c.blue}
          />
        </TouchableOpacity>
      </View>

      {showPicker && (
        <View style={[styles.picker, { backgroundColor: c.card, borderColor: c.border, marginHorizontal: contentPadding }]}>
          {LINE_OPTIONS.map(opt => (
            <TouchableOpacity
              key={opt}
              style={[styles.pickerRow, { borderBottomColor: c.border }, opt === LINE_OPTIONS[LINE_OPTIONS.length - 1] && { borderBottomWidth: 0 }]}
              onPress={() => { setLines(opt); setShowPicker(false); qc.invalidateQueries({ queryKey: ['logs'] }); }}
            >
              <Text style={[styles.pickerText, { color: logLines === opt ? c.blue : c.text }]}>{opt} lines</Text>
              {logLines === opt && <MaterialCommunityIcons name="check" size={16} color={c.blue} />}
            </TouchableOpacity>
          ))}
        </View>
      )}

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
          data={[...lines].reverse()}
          keyExtractor={(_, i) => String(i)}
          contentContainerStyle={[styles.listContent, { paddingHorizontal: contentPadding, alignSelf: 'center', width: '100%', maxWidth: contentMaxWidth }]}
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
          renderItem={({ item }) => (
            <Text style={[styles.logLine, { color: statusColor(item, c), borderBottomColor: c.border }]}>
              {item}
            </Text>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container:    { flex: 1 },
  toolbar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
    paddingVertical: 8,
    borderBottomWidth: 1,
  },
  lineBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: radius.sm,
    borderWidth: 1,
  },
  lineBtnText: { fontSize: font.sm },
  refreshBtn: {
    marginLeft: 'auto',
    padding: 7,
    borderRadius: radius.sm,
    borderWidth: 1,
  },
  picker: {
    marginTop: 4,
    borderRadius: radius.md,
    borderWidth: 1,
    overflow: 'hidden',
  },
  pickerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.md,
    paddingVertical: 10,
    borderBottomWidth: 1,
  },
  pickerText: { fontSize: font.md },
  listContent: { paddingBottom: 110, paddingTop: 8 },
  logLine: {
    fontFamily: 'monospace',
    fontSize: 11,
    lineHeight: 18,
    paddingVertical: 4,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  center:     { flex: 1, justifyContent: 'center', alignItems: 'center', gap: 8, padding: spacing.xl },
  errorText:  { fontSize: font.md, textAlign: 'center' },
  errorHint:  { fontSize: font.sm, textAlign: 'center' },
  emptyText:  { fontSize: font.md },
});
