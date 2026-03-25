import React from 'react';
import { ScrollView, StyleSheet, TouchableOpacity, View } from 'react-native';
import { Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { TraefikService } from '../../src/api/traefik';
import { font, radius, spacing } from '../../src/theme';
import { useThemeStore } from '../../src/store/theme';
import { useLive } from '../../src/hooks/useLive';
import { ProtocolBadge } from '../../src/components/StatusBadge';
import { providerOf } from '../../src/utils';

// ── helpers ───────────────────────────────────────────────────────

function svcStatus(s: string): 'ok' | 'warn' | 'err' {
  const l = (s || '').toLowerCase();
  if (l === 'enabled' || l === 'success' || l === 'up') return 'ok';
  if (l === 'warning' || l === 'warn') return 'warn';
  return 'err';
}

// ── sub-components ────────────────────────────────────────────────

type Colors = ReturnType<typeof useThemeStore.getState>['colors'];

function Section({ icon, title, count, children, c }: {
  icon: string; title: string; count?: number; children: React.ReactNode; c: Colors;
}) {
  return (
    <View style={styles.section}>
      <View style={styles.sectionHeader}>
        <MaterialCommunityIcons name={icon as any} size={14} color={c.blue} />
        <Text style={[styles.sectionTitle, { color: c.text }]}>{title}</Text>
        {count !== undefined && (
          <View style={[styles.countBadge, { backgroundColor: c.border }]}>
            <Text style={[styles.countText, { color: c.muted }]}>{count}</Text>
          </View>
        )}
      </View>
      <View style={[styles.sectionBody, { borderColor: c.border, backgroundColor: c.card }]}>
        {children}
      </View>
    </View>
  );
}

function Row({ label, value, isLast, c }: {
  label: string; value: React.ReactNode; isLast: boolean; c: Colors;
}) {
  return (
    <View style={[styles.row, !isLast && { borderBottomWidth: 1, borderBottomColor: c.border }]}>
      <Text style={[styles.rowLabel, { color: c.muted }]}>{label}</Text>
      <View style={styles.rowValue}>{value}</View>
    </View>
  );
}

function BoolChip({ value, c }: { value: boolean; c: Colors }) {
  const col = value ? c.green : c.red;
  return (
    <View style={[styles.chip, { backgroundColor: col + '20', borderColor: col + '55' }]}>
      <Text style={[styles.chipText, { color: col }]}>{value ? 'True' : 'False'}</Text>
    </View>
  );
}

function TextChip({ label, c }: { label: string; c: Colors }) {
  return (
    <View style={[styles.chip, { backgroundColor: c.bg, borderColor: c.border }]}>
      <Text style={[styles.chipText, { color: c.muted }]}>{label}</Text>
    </View>
  );
}

// ── screen ────────────────────────────────────────────────────────

export default function ServiceDetailScreen() {
  const { name } = useLocalSearchParams<{ name: string }>();
  const router   = useRouter();
  const insets   = useSafeAreaInsets();
  const c        = useThemeStore(s => s.colors);
  const { data } = useLive();

  const service  = data?.find(s => s.name === decodeURIComponent(name));

  const provider  = service ? (service.provider ?? providerOf(service.name)) : '—';
  const proto     = service?._proto ?? service?.type ?? 'http';
  const st        = svcStatus(service?.status ?? '');
  const baseName  = service ? (service.name.includes('@') ? service.name.split('@')[0] : service.name) : decodeURIComponent(name);
  const servers   = Object.entries((service as TraefikService | undefined)?.serverStatus ?? {});
  const usedBy    = service?.usedBy ?? [];

  const statusColor = { ok: c.green, warn: c.orange, err: c.red };
  const statusLabel = { ok: 'Success', warn: 'Warning', err: 'Error' };

  return (
    <View style={[styles.screen, { backgroundColor: c.bg }]}>
      {/* Header */}
      <View style={[styles.headerBar, { paddingTop: insets.top + 4, borderBottomColor: c.border, backgroundColor: c.card }]}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={8} style={styles.backBtn}>
          <MaterialCommunityIcons name="close" size={22} color={c.text} />
        </TouchableOpacity>
        <ProtocolBadge protocol={proto} />
        <Text style={[styles.headerTitle, { color: c.text }]} numberOfLines={1}>{baseName}</Text>
      </View>

      {!service ? (
        <View style={styles.notFound}>
          <MaterialCommunityIcons name="alert-circle-outline" size={32} color={c.muted} />
          <Text style={[styles.notFoundTxt, { color: c.muted }]}>Service not found</Text>
        </View>
      ) : (
        <ScrollView
          style={styles.scroll}
          contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 24 }]}
          bounces={false}
        >
          {/* Service Details */}
          <Section icon="information-outline" title="SERVICE DETAILS" c={c}>
            <Row label="TYPE" isLast={false} c={c}
              value={<Text style={[styles.rowText, { color: c.text }]}>{service.type ?? '—'}</Text>} />
            <Row label="PROVIDER" isLast={false} c={c}
              value={<TextChip label={provider} c={c} />} />
            <Row label="FULL NAME" isLast={false} c={c}
              value={<Text style={[styles.rowText, { color: c.text, fontFamily: 'monospace' }]}>{service.name}</Text>} />
            <Row
              label="STATUS"
              isLast={service.loadBalancer?.passHostHeader === undefined}
              c={c}
              value={
                <View style={styles.statusVal}>
                  <View style={[styles.statusDot, { backgroundColor: statusColor[st] }]} />
                  <Text style={[styles.rowText, { color: statusColor[st], fontWeight: '600' }]}>{statusLabel[st]}</Text>
                </View>
              }
            />
            {service.loadBalancer?.passHostHeader !== undefined && (
              <Row label="PASS HOST HEADER" isLast c={c}
                value={<BoolChip value={service.loadBalancer.passHostHeader} c={c} />} />
            )}
          </Section>

          {/* Servers */}
          {servers.length > 0 && (
            <Section icon="server-network" title="SERVERS" count={servers.length} c={c}>
              <View style={[styles.tableHeader, { borderBottomColor: c.border }]}>
                <Text style={[styles.tableHead, { color: c.muted, flex: 1 }]}>STATUS</Text>
                <Text style={[styles.tableHead, { color: c.muted, flex: 2 }]}>URL</Text>
              </View>
              {servers.map(([url, sv], i) => {
                const ss = svcStatus(sv);
                const sc = statusColor[ss];
                return (
                  <View
                    key={url}
                    style={[styles.tableRow, i < servers.length - 1 && { borderBottomWidth: 1, borderBottomColor: c.border }]}
                  >
                    <View style={[styles.serverStatus, { flex: 1 }]}>
                      <View style={[styles.statusDot, { backgroundColor: sc }]} />
                      <Text style={[styles.rowText, { color: sc, fontWeight: '600' }]}>
                        {ss === 'ok' ? 'Active' : ss === 'warn' ? 'Warning' : 'Error'}
                      </Text>
                    </View>
                    <Text style={[styles.rowText, { color: c.text, fontFamily: 'monospace', flex: 2 }]} numberOfLines={2}>
                      {url}
                    </Text>
                  </View>
                );
              })}
            </Section>
          )}

          {/* Used by */}
          {usedBy.length > 0 && (
            <Section icon="source-branch" title="USED BY ROUTERS" c={c}>
              <View style={styles.chipsWrap}>
                {usedBy.map(r => <TextChip key={r} label={r} c={c} />)}
              </View>
            </Section>
          )}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen:  { flex: 1 },
  headerBar: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: spacing.lg, paddingBottom: spacing.md,
    borderBottomWidth: 1, gap: spacing.sm,
  },
  backBtn:     { padding: 2 },
  headerTitle: { flex: 1, fontSize: font.lg, fontWeight: '700' },
  scroll:      { flex: 1 },
  content:     { padding: spacing.md, gap: spacing.md },
  notFound:    { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 8 },
  notFoundTxt: { fontSize: font.sm },
  // Sections
  section:      { gap: 6 },
  sectionHeader:{ flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 2 },
  sectionTitle: { fontSize: font.xs, fontWeight: '800', letterSpacing: 0.8, textTransform: 'uppercase' as const },
  countBadge:   { paddingHorizontal: 6, paddingVertical: 1, borderRadius: radius.full, minWidth: 20, alignItems: 'center' as const },
  countText:    { fontSize: font.xs, fontWeight: '700' },
  sectionBody:  { borderRadius: radius.md, borderWidth: 1, overflow: 'hidden' as const },
  // Rows
  row: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: spacing.md, paddingVertical: 11, gap: spacing.sm,
  },
  rowLabel:  { fontSize: font.xs, fontWeight: '700', letterSpacing: 0.5, width: 130 },
  rowValue:  { flex: 1 },
  rowText:   { fontSize: font.sm },
  statusVal: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  statusDot: { width: 6, height: 6, borderRadius: 3 },
  serverStatus: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  // Table
  tableHeader: { flexDirection: 'row', paddingHorizontal: spacing.md, paddingVertical: 8, borderBottomWidth: 1 },
  tableHead:   { fontSize: font.xs, fontWeight: '700', letterSpacing: 0.5 },
  tableRow:    { flexDirection: 'row', alignItems: 'center', paddingHorizontal: spacing.md, paddingVertical: 10 },
  // Chips
  chip:      { paddingHorizontal: 8, paddingVertical: 3, borderRadius: radius.full, borderWidth: 1, alignSelf: 'flex-start' as const },
  chipText:  { fontSize: font.xs, fontWeight: '600' },
  chipsWrap: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, padding: spacing.md },
});
