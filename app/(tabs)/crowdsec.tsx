import { useRef, useState } from 'react';
import { ActivityIndicator, Animated, Pressable, RefreshControl, StyleSheet, TouchableOpacity, View } from 'react-native';
import { Surface, Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useQueryClient } from '@tanstack/react-query';
import { TopBar } from '../../src/components/TopBar';
import { DemoBanner } from '../../src/components/DemoBanner';
import { useCrowdSecDecisions, useCrowdSecAlerts, useDeleteDecision } from '../../src/hooks/useCrowdSec';
import { useLayout } from '../../src/hooks/useLayout';
import { useThemeStore } from '../../src/store/theme';
import { useDrawerStore } from '../../src/store/drawer';
import { useTabSwipe } from '../../src/hooks/useTabSwipe';
import { type CrowdSecDecision, type CrowdSecAlert } from '../../src/api/crowdsec';
import { font, radius, spacing } from '../../src/theme';

type Colors = ReturnType<typeof useThemeStore.getState>['colors'];

const TYPE_COLORS: Record<string, (c: Colors) => string> = {
  ban:     c => c.red,
  captcha: c => c.yellow,
  bypass:  c => c.green,
};

function StatCard({ label, count, color }: { label: string; count: number; color: string }) {
  return (
    <Surface style={styles.statCard} elevation={1}>
      <Text style={[styles.statCount, { color }]}>{count}</Text>
      <Text style={styles.statLabel}>{label}</Text>
    </Surface>
  );
}

function DecisionRow({
  decision, onDelete, deletingId, c,
}: {
  decision: CrowdSecDecision;
  onDelete: (id: number) => void;
  deletingId: number | null;
  c: Colors;
}) {
  const typeColor = TYPE_COLORS[decision.type]?.(c) ?? c.muted;
  const isDeleting = deletingId === decision.id;
  return (
    <Surface style={[styles.decisionCard, { backgroundColor: c.card }]} elevation={1}>
      <View style={styles.decisionMain}>
        <View style={{ flex: 1, minWidth: 0 }}>
          <Text style={[styles.decisionIp, { color: c.text }]} numberOfLines={1}>{decision.value}</Text>
          <Text style={[styles.decisionScenario, { color: c.muted }]} numberOfLines={1}>{decision.scenario}</Text>
        </View>
        <View style={[styles.typeBadge, { backgroundColor: typeColor + '22', borderColor: typeColor + '55' }]}>
          <Text style={[styles.typeBadgeText, { color: typeColor }]}>{decision.type.toUpperCase()}</Text>
        </View>
        <TouchableOpacity
          onPress={() => onDelete(decision.id)}
          disabled={isDeleting}
          hitSlop={8}
          style={styles.deleteBtn}
        >
          {isDeleting
            ? <ActivityIndicator size="small" color={c.red} />
            : <MaterialCommunityIcons name="trash-can-outline" size={18} color={c.red} />}
        </TouchableOpacity>
      </View>
      <Text style={[styles.decisionMeta, { color: c.muted }]}>
        Expires: {decision.duration} · Origin: {decision.origin}
      </Text>
    </Surface>
  );
}

function AlertRow({ alert, c }: { alert: CrowdSecAlert; c: Colors }) {
  const date = new Date(alert.startAt).toLocaleString();
  return (
    <View style={[styles.alertRow, { borderBottomColor: c.border }]}>
      <View style={{ flex: 1, minWidth: 0 }}>
        <Text style={[styles.alertIp, { color: c.text }]} numberOfLines={1}>{alert.source.ip}</Text>
        <Text style={[styles.alertScenario, { color: c.muted }]} numberOfLines={1}>{alert.scenario}</Text>
      </View>
      <Text style={[styles.alertDate, { color: c.muted }]}>{date}</Text>
    </View>
  );
}

const TYPE_FILTERS = ['All', 'Ban', 'Captcha', 'Bypass'];

export default function CrowdSecScreen() {
  const c           = useThemeStore(s => s.colors);
  const openDrawer  = useDrawerStore(s => s.open);
  const swipe       = useTabSwipe('crowdsec');
  const scrollAnim  = useRef(new Animated.Value(0)).current;
  const qc          = useQueryClient();
  const { contentPadding, contentMaxWidth, listBottomPadding } = useLayout();

  const [search, setSearch]           = useState('');
  const [typeFilter, setTypeFilter]   = useState('All');
  const [alertsExpanded, setAlertsExpanded] = useState(true);
  const [deletingId, setDeletingId]   = useState<number | null>(null);

  const decisionsQuery = useCrowdSecDecisions();
  const alertsQuery    = useCrowdSecAlerts();
  const deleteDecision = useDeleteDecision();

  const decisions = decisionsQuery.data ?? [];
  const alerts    = alertsQuery.data ?? [];

  const filtered = decisions.filter(d => {
    const matchesType   = typeFilter === 'All' || d.type === typeFilter.toLowerCase();
    const q = search.trim().toLowerCase();
    const matchesSearch = !q || d.value.includes(q) || d.scenario.toLowerCase().includes(q);
    return matchesType && matchesSearch;
  });

  const bans     = decisions.filter(d => d.type === 'ban').length;
  const captchas = decisions.filter(d => d.type === 'captcha').length;
  const bypasses = decisions.filter(d => d.type === 'bypass').length;

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['crowdsec-decisions'] });
    qc.invalidateQueries({ queryKey: ['crowdsec-alerts'] });
  };

  const handleDelete = async (id: number) => {
    setDeletingId(id);
    try {
      await deleteDecision.mutateAsync(id);
    } finally {
      setDeletingId(null);
    }
  };

  const ListHeader = (
    <View style={{ paddingTop: spacing.md, gap: spacing.sm }}>
      <View style={styles.statsRow}>
        <StatCard label="Bans"     count={bans}     color={c.red} />
        <StatCard label="Captchas" count={captchas} color={c.yellow} />
        <StatCard label="Bypasses" count={bypasses} color={c.green} />
      </View>
      <View style={styles.filterRow}>
        {TYPE_FILTERS.map(f => {
          const active = typeFilter === f;
          return (
            <TouchableOpacity
              key={f}
              onPress={() => setTypeFilter(f)}
              style={[
                styles.filterChip,
                { borderColor: active ? c.blue + '88' : c.border, backgroundColor: active ? c.blue + '18' : 'transparent' },
              ]}
            >
              <Text style={[styles.filterChipText, { color: active ? c.blue : c.muted }]}>{f}</Text>
            </TouchableOpacity>
          );
        })}
      </View>
    </View>
  );

  const ListFooter = (
    <View style={[styles.alertsSection, { borderTopColor: c.border }]}>
      <TouchableOpacity style={styles.alertsHeader} onPress={() => setAlertsExpanded(v => !v)}>
        <Text style={[styles.alertsTitle, { color: c.text }]}>Recent Alerts</Text>
        <Text style={[styles.alertsCount, { color: c.muted }]}>{alerts.length}</Text>
        <MaterialCommunityIcons
          name={alertsExpanded ? 'chevron-up' : 'chevron-down'}
          size={18}
          color={c.muted}
        />
      </TouchableOpacity>
      {alertsExpanded && alerts.map((alert, i) => (
        <AlertRow key={i} alert={alert} c={c} />
      ))}
    </View>
  );

  const isLoading = decisionsQuery.isFetching && decisions.length === 0;

  return (
    <View style={[styles.container, { backgroundColor: c.bg }]} {...swipe}>
      <TopBar
        title="CrowdSec"
        scrollAnim={scrollAnim}
        accent={c.red}
        icon="shield-bug-outline"
        onMenuPress={openDrawer}
        searchValue={search}
        onSearchChange={setSearch}
        searchPlaceholder="Filter by IP or scenario..."
        right={
          <Pressable
            onPress={refresh}
            style={[styles.iconBtn, { borderColor: c.border }]}
            android_ripple={{ color: c.muted + '40' }}
            hitSlop={8}
          >
            <MaterialCommunityIcons name="refresh" size={20} color={c.red} />
          </Pressable>
        }
      />
      <DemoBanner />
      {decisionsQuery.isError ? (
        <View style={styles.notConfigured}>
          <MaterialCommunityIcons name="shield-bug-outline" size={56} color={c.muted} style={{ opacity: 0.35 }} />
          <Text style={[styles.notConfiguredTitle, { color: c.text }]}>CrowdSec Not Available</Text>
          <Text style={[styles.notConfiguredText, { color: c.muted }]}>
            Configure CrowdSec credentials in Settings on the Traefik Manager web panel.
          </Text>
        </View>
      ) : (
        <Animated.FlatList
          data={filtered}
          keyExtractor={item => String(item.id)}
          renderItem={({ item }) => (
            <DecisionRow
              decision={item}
              onDelete={handleDelete}
              deletingId={deletingId}
              c={c}
            />
          )}
          contentContainerStyle={[
            styles.list,
            { paddingHorizontal: contentPadding, paddingBottom: listBottomPadding, maxWidth: contentMaxWidth, alignSelf: 'center', width: '100%' },
          ]}
          ListHeaderComponent={ListHeader}
          ListFooterComponent={ListFooter}
          refreshControl={
            <RefreshControl refreshing={isLoading} onRefresh={refresh} tintColor={c.red} />
          }
          ListEmptyComponent={
            !isLoading ? (
              <View style={styles.empty}>
                <MaterialCommunityIcons name="shield-check-outline" size={48} color={c.muted} style={{ opacity: 0.4 }} />
                <Text style={[styles.emptyText, { color: c.muted }]}>No active decisions</Text>
              </View>
            ) : null
          }
          onScroll={Animated.event([{ nativeEvent: { contentOffset: { y: scrollAnim } } }], { useNativeDriver: false })}
          scrollEventThrottle={16}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container:  { flex: 1 },
  list:       { gap: spacing.sm },

  statsRow: {
    flexDirection: 'row',
    gap: spacing.sm,
  },
  statCard: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: spacing.md,
    borderRadius: radius.md,
  },
  statCount:  { fontSize: 28, fontWeight: '700', lineHeight: 34 },
  statLabel:  { fontSize: font.xs, fontWeight: '500', letterSpacing: 0.5, opacity: 0.6, marginTop: 2 },

  filterRow:      { flexDirection: 'row', gap: 6 },
  filterChip: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 7,
    borderRadius: radius.sm,
    borderWidth: 1,
  },
  filterChipText: { fontSize: font.xs, fontWeight: '600' },

  decisionCard:   { borderRadius: radius.md, overflow: 'hidden', marginBottom: 0 },
  decisionMain: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: spacing.md,
    gap: spacing.sm,
  },
  decisionIp:       { fontSize: font.md, fontWeight: '600' },
  decisionScenario: { fontSize: font.sm, marginTop: 2 },
  typeBadge: {
    paddingHorizontal: 7,
    paddingVertical: 3,
    borderRadius: 5,
    borderWidth: 1,
    flexShrink: 0,
  },
  typeBadgeText: { fontSize: 10, fontWeight: '700' },
  deleteBtn:     { padding: 4, flexShrink: 0 },
  decisionMeta: {
    fontSize: font.sm,
    paddingHorizontal: spacing.md,
    paddingBottom: 10,
  },

  alertsSection: {
    marginTop: spacing.md,
    borderTopWidth: StyleSheet.hairlineWidth,
    paddingTop: spacing.sm,
  },
  alertsHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: spacing.sm,
    gap: spacing.sm,
  },
  alertsTitle:  { flex: 1, fontSize: font.md, fontWeight: '600' },
  alertsCount:  { fontSize: font.sm },
  alertRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
    gap: spacing.sm,
  },
  alertIp:       { fontSize: font.sm, fontWeight: '500' },
  alertScenario: { fontSize: font.xs, marginTop: 2 },
  alertDate:     { fontSize: font.xs, flexShrink: 0 },

  empty:    { alignItems: 'center', paddingTop: 60, gap: spacing.md },
  emptyText: { fontSize: font.md },

  notConfigured: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing.xl,
    gap: spacing.md,
  },
  notConfiguredTitle: { fontSize: font.lg, fontWeight: '600', textAlign: 'center' },
  notConfiguredText:  { fontSize: font.md, textAlign: 'center', lineHeight: 22 },

  iconBtn: {
    width: 36, height: 36,
    alignItems: 'center', justifyContent: 'center',
    borderRadius: 8, borderWidth: 1,
  },
});
