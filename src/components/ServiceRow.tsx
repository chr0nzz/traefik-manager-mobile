import React, { useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, TouchableOpacity, View, useWindowDimensions } from 'react-native';
import { Surface, Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { TraefikService } from '../api/traefik';
import { font, radius, spacing } from '../theme';
import { useThemeStore } from '../store/theme';
import { ProtocolBadge, StatusDot } from './StatusBadge';
import { providerOf } from '../utils';

interface Props { service: TraefikService }

function svcStatus(s: string): 'ok' | 'warn' | 'err' {
  const l = (s || '').toLowerCase();
  if (l === 'enabled' || l === 'success' || l === 'up') return 'ok';
  if (l === 'warning' || l === 'warn')    return 'warn';
  return 'err';
}

export function ServiceRow({ service }: Props) {
  const [showInfo, setShowInfo] = useState(false);
  const c                        = useThemeStore(s => s.colors);
  const { height: screenH }      = useWindowDimensions();

  const provider  = service.provider ?? providerOf(service.name);
  const proto     = service._proto ?? service.type ?? 'http';
  const st        = svcStatus(service.status);
  const baseName  = service.name.includes('@') ? service.name.split('@')[0] : service.name;
  const servers   = Object.entries(service.serverStatus ?? {});
  const usedBy    = service.usedBy ?? [];

  const statusColor = { ok: c.green, warn: c.orange, err: c.red };
  const statusLabel = { ok: 'Success', warn: 'Warning', err: 'Error' };

  // Server health counts
  const activeCount = servers.filter(([, sv]) => svcStatus(sv) === 'ok').length;
  const serverSummary = servers.length > 0
    ? `${activeCount}/${servers.length} active`
    : null;

  return (
    <>
      <Surface style={[styles.card, { backgroundColor: c.card, borderColor: c.border }]} elevation={1}>
        {/* Header row */}
        <View style={styles.header}>
          <View style={styles.badges}>
            <ProtocolBadge protocol={proto} />
            {!!service.type && (
              <View style={[styles.typeBadge, { backgroundColor: c.blue + '18', borderColor: c.blue + '44' }]}>
                <Text style={[styles.typeBadgeText, { color: c.blue }]}>{service.type}</Text>
              </View>
            )}
          </View>
          <View style={styles.headerRight}>
            <View style={[styles.statusChip, { backgroundColor: statusColor[st] + '18', borderColor: statusColor[st] + '44' }]}>
              <View style={[styles.statusDot, { backgroundColor: statusColor[st] }]} />
              <Text style={[styles.statusChipText, { color: statusColor[st] }]}>{statusLabel[st]}</Text>
            </View>
            <TouchableOpacity onPress={() => setShowInfo(true)} hitSlop={8} style={styles.iconBtn}>
              <MaterialCommunityIcons name="information-outline" size={17} color={c.muted} />
            </TouchableOpacity>
          </View>
        </View>

        {/* Name */}
        <Text style={[styles.name, { color: c.text }]} numberOfLines={1}>{baseName}</Text>

        {/* Meta row */}
        <View style={styles.metaRow}>
          <View style={[styles.metaChip, { backgroundColor: c.bg, borderColor: c.border }]}>
            <MaterialCommunityIcons name="database-outline" size={11} color={c.muted} />
            <Text style={[styles.metaChipText, { color: c.muted }]}>{provider}</Text>
          </View>
          {serverSummary && (
            <View style={[styles.metaChip, { backgroundColor: activeCount === servers.length ? c.green + '12' : c.orange + '12', borderColor: activeCount === servers.length ? c.green + '44' : c.orange + '44' }]}>
              <MaterialCommunityIcons name="server-outline" size={11} color={activeCount === servers.length ? c.green : c.orange} />
              <Text style={[styles.metaChipText, { color: activeCount === servers.length ? c.green : c.orange }]}>{serverSummary}</Text>
            </View>
          )}
        </View>

        {/* Used-by chips */}
        {usedBy.length > 0 && (
          <View style={styles.usedByRow}>
            {usedBy.slice(0, 3).map(r => {
              const rName = r.includes('@') ? r.split('@')[0] : r;
              return (
                <View key={r} style={[styles.usedChip, { backgroundColor: c.purple + '14', borderColor: c.purple + '40' }]}>
                  <MaterialCommunityIcons name="source-branch" size={10} color={c.purple} />
                  <Text style={[styles.usedChipText, { color: c.purple }]} numberOfLines={1}>{rName}</Text>
                </View>
              );
            })}
            {usedBy.length > 3 && (
              <View style={[styles.usedChip, { backgroundColor: c.purple + '14', borderColor: c.purple + '40' }]}>
                <Text style={[styles.usedChipText, { color: c.purple }]}>+{usedBy.length - 3}</Text>
              </View>
            )}
          </View>
        )}
      </Surface>

      {/* ── Detail sheet ── */}
      <Modal visible={showInfo} transparent animationType="slide" onRequestClose={() => setShowInfo(false)}>
        <View style={styles.overlay}>
          <Pressable style={StyleSheet.absoluteFill} onPress={() => setShowInfo(false)} />
          <View style={[styles.sheet, { backgroundColor: c.bg, height: screenH * 0.92 }]}>
            <View style={[styles.handle, { backgroundColor: c.border }]} />

            <View style={[styles.sheetHeader, { borderBottomColor: c.border, backgroundColor: c.card }]}>
              <ProtocolBadge protocol={proto} />
              <Text style={[styles.sheetTitle, { color: c.text }]} numberOfLines={1}>{baseName}</Text>
              <TouchableOpacity onPress={() => setShowInfo(false)} hitSlop={12} style={styles.closeBtn}>
                <MaterialCommunityIcons name="close" size={20} color={c.muted} />
              </TouchableOpacity>
            </View>

            <ScrollView style={styles.scroll} contentContainerStyle={styles.scrollContent} bounces={false}>

              <Section icon="information-outline" title="SERVICE DETAILS" c={c}>
                <Row label="TYPE"     isLast={false} c={c}
                  value={<Text style={[styles.rowText, { color: c.text }]}>{service.type ?? '—'}</Text>} />
                <Row label="PROVIDER" isLast={false} c={c}
                  value={<TextChip label={provider} c={c} />} />
                <Row label="STATUS"   isLast={service.loadBalancer?.passHostHeader === undefined} c={c}
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

              {servers.length > 0 && (
                <Section icon="server-network" title="SERVERS" count={servers.length} c={c}>
                  <View style={[styles.tableHeader, { borderBottomColor: c.border }]}>
                    <Text style={[styles.tableHead, { color: c.muted, flex: 1 }]}>STATUS</Text>
                    <Text style={[styles.tableHead, { color: c.muted, flex: 2 }]}>URL</Text>
                  </View>
                  {servers.map(([url, sv], i) => {
                    const ss = svcStatus(sv);
                    const sc = statusColor[ss];
                    const isLast = i === servers.length - 1;
                    return (
                      <View key={url} style={[styles.tableRow, !isLast && { borderBottomWidth: 1, borderBottomColor: c.border }]}>
                        <View style={[styles.serverStatus, { flex: 1 }]}>
                          <View style={[styles.statusDot, { backgroundColor: sc }]} />
                          <Text style={[styles.rowText, { color: sc, fontWeight: '600' }]}>
                            {ss === 'ok' ? 'Active' : ss === 'warn' ? 'Warning' : 'Error'}
                          </Text>
                        </View>
                        <Text style={[styles.rowText, { color: c.text, fontFamily: 'monospace', flex: 2 }]} numberOfLines={2}>{url}</Text>
                      </View>
                    );
                  })}
                </Section>
              )}

              {usedBy.length > 0 && (
                <Section icon="source-branch" title="USED BY ROUTERS" c={c}>
                  <View style={styles.chipsWrap}>
                    {usedBy.map(r => <TextChip key={r} label={r} c={c} />)}
                  </View>
                </Section>
              )}

            </ScrollView>
          </View>
        </View>
      </Modal>
    </>
  );
}

// ── Sub-components ────────────────────────────────────────────────

function Section({ icon, title, count, children, c }: {
  icon: string; title: string; count?: number; children: React.ReactNode;
  c: ReturnType<typeof useThemeStore.getState>['colors'];
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
  label: string; value: React.ReactNode; isLast: boolean;
  c: ReturnType<typeof useThemeStore.getState>['colors'];
}) {
  return (
    <View style={[styles.row, !isLast && { borderBottomWidth: 1, borderBottomColor: c.border }]}>
      <Text style={[styles.rowLabel, { color: c.muted }]}>{label}</Text>
      <View style={styles.rowValue}>{value}</View>
    </View>
  );
}

function BoolChip({ value, c }: { value: boolean; c: ReturnType<typeof useThemeStore.getState>['colors'] }) {
  const col = value ? c.green : c.red;
  return (
    <View style={[styles.chip, { backgroundColor: col + '20', borderColor: col + '55' }]}>
      <Text style={[styles.chipText, { color: col }]}>{value ? 'True' : 'False'}</Text>
    </View>
  );
}

function TextChip({ label, c }: { label: string; c: ReturnType<typeof useThemeStore.getState>['colors'] }) {
  return (
    <View style={[styles.chip, { backgroundColor: c.bg, borderColor: c.border }]}>
      <Text style={[styles.chipText, { color: c.muted }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  // Card
  card: {
    borderRadius: radius.md, borderWidth: 1,
    marginBottom: spacing.sm, overflow: 'hidden',
    padding: spacing.md, gap: 6,
  },
  header:      { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  badges:      { flexDirection: 'row', gap: 6, alignItems: 'center', flex: 1 },
  headerRight: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  typeBadge:   { paddingHorizontal: 7, paddingVertical: 2, borderRadius: radius.full, borderWidth: 1 },
  typeBadgeText:{ fontSize: font.xs, fontWeight: '700' },
  statusChip:  { flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 8, paddingVertical: 3, borderRadius: radius.full, borderWidth: 1 },
  statusDot:   { width: 6, height: 6, borderRadius: 3 },
  statusChipText: { fontSize: font.xs, fontWeight: '600' },
  iconBtn:     { padding: 2 },
  name:        { fontSize: font.md, fontWeight: '700' },
  metaRow:     { flexDirection: 'row', gap: 6, flexWrap: 'wrap' },
  metaChip:    { flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 7, paddingVertical: 3, borderRadius: radius.full, borderWidth: 1 },
  metaChipText:{ fontSize: font.xs, fontWeight: '600' },
  usedByRow:   { flexDirection: 'row', flexWrap: 'wrap', gap: 4 },
  usedChip:    { flexDirection: 'row', alignItems: 'center', gap: 3, paddingHorizontal: 7, paddingVertical: 3, borderRadius: radius.full, borderWidth: 1, maxWidth: 160 },
  usedChipText:{ fontSize: font.xs, fontWeight: '600' },
  // Sheet
  overlay:  { flex: 1, backgroundColor: 'rgba(0,0,0,0.55)', justifyContent: 'flex-end' },
  sheet:    { borderTopLeftRadius: 20, borderTopRightRadius: 20, overflow: 'hidden' },
  handle:   { width: 36, height: 4, borderRadius: 2, alignSelf: 'center', marginTop: 10, marginBottom: 4 },
  sheetHeader: {
    flexDirection: 'row', alignItems: 'center', gap: spacing.sm,
    paddingHorizontal: spacing.lg, paddingVertical: spacing.md,
    borderBottomWidth: 1,
  },
  sheetTitle: { flex: 1, fontSize: font.lg, fontWeight: '700' },
  closeBtn:   { marginLeft: 4 },
  scroll:     { flex: 1 },
  scrollContent: { padding: spacing.md, gap: spacing.md, paddingBottom: spacing.xl },
  // Sections
  section:      { gap: 6 },
  sectionHeader:{ flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 2 },
  sectionTitle: { fontSize: font.xs, fontWeight: '800', letterSpacing: 0.8, textTransform: 'uppercase' },
  countBadge:   { paddingHorizontal: 6, paddingVertical: 1, borderRadius: radius.full, minWidth: 20, alignItems: 'center' },
  countText:    { fontSize: font.xs, fontWeight: '700' },
  sectionBody:  { borderRadius: radius.md, borderWidth: 1, overflow: 'hidden' },
  // Rows
  row: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: spacing.md, paddingVertical: 11, gap: spacing.sm,
  },
  rowLabel: { fontSize: font.xs, fontWeight: '700', letterSpacing: 0.5, width: 120 },
  rowValue: { flex: 1 },
  rowText:  { fontSize: font.sm },
  statusVal:    { flexDirection: 'row', alignItems: 'center', gap: 5 },
  serverStatus: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  // Table
  tableHeader: { flexDirection: 'row', paddingHorizontal: spacing.md, paddingVertical: 8, borderBottomWidth: 1 },
  tableHead:   { fontSize: font.xs, fontWeight: '700', letterSpacing: 0.5 },
  tableRow:    { flexDirection: 'row', alignItems: 'center', paddingHorizontal: spacing.md, paddingVertical: 10 },
  // Chips
  chip:     { paddingHorizontal: 8, paddingVertical: 3, borderRadius: radius.full, borderWidth: 1, alignSelf: 'flex-start' },
  chipText: { fontSize: font.xs, fontWeight: '600' },
  chipsWrap:{ flexDirection: 'row', flexWrap: 'wrap', gap: 6, padding: spacing.md },
});
