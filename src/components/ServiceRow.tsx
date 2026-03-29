import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Surface, Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { TraefikService } from '../api/traefik';
import { font, radius, spacing } from '../theme';
import { useThemeStore } from '../store/theme';
import { PillIconBtn, ProtocolBadge } from './StatusBadge';
import { providerOf } from '../utils';

interface Props { service: TraefikService }

function svcStatus(s: string): 'ok' | 'warn' | 'err' {
  const l = (s || '').toLowerCase();
  if (l === 'enabled' || l === 'success' || l === 'up') return 'ok';
  if (l === 'warning' || l === 'warn') return 'warn';
  return 'err';
}

export function ServiceRow({ service }: Props) {
  const router = useRouter();
  const c      = useThemeStore(s => s.colors);

  const provider  = service.provider ?? providerOf(service.name);
  const proto     = service._proto ?? service.type ?? 'http';
  const st        = svcStatus(service.status);
  const baseName  = service.name.includes('@') ? service.name.split('@')[0] : service.name;
  const servers   = Object.entries(service.serverStatus ?? {});
  const usedBy    = service.usedBy ?? [];

  const statusColor = { ok: c.green, warn: c.orange, err: c.red };
  const statusLabel = { ok: 'Success', warn: 'Warning', err: 'Error' };

  const activeCount   = servers.filter(([, sv]) => svcStatus(sv) === 'ok').length;
  const serverSummary = servers.length > 0 ? `${activeCount}/${servers.length} active` : null;

  return (
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
          <PillIconBtn
            icon="information-outline"
            color={c.muted}
            onPress={() => router.push(`/service/${encodeURIComponent(service.name)}`)}
          />
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
          <View style={[styles.metaChip, {
            backgroundColor: activeCount === servers.length ? c.green + '12' : c.orange + '12',
            borderColor:     activeCount === servers.length ? c.green + '44' : c.orange + '44',
          }]}>
            <MaterialCommunityIcons name="server-outline" size={11} color={activeCount === servers.length ? c.green : c.orange} />
            <Text style={[styles.metaChipText, { color: activeCount === servers.length ? c.green : c.orange }]}>
              {serverSummary}
            </Text>
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
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: radius.md, borderWidth: 1,
    marginBottom: spacing.sm, overflow: 'hidden',
    padding: spacing.md, gap: 6,
  },
  header:      { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  badges:      { flexDirection: 'row', gap: 6, alignItems: 'center', flex: 1 },
  headerRight: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  typeBadge:    { paddingHorizontal: 7, paddingVertical: 2, borderRadius: radius.full, borderWidth: 1 },
  typeBadgeText:{ fontSize: font.xs, fontWeight: '700' },
  statusChip:   { flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 8, paddingVertical: 3, borderRadius: radius.full, borderWidth: 1 },
  statusDot:    { width: 6, height: 6, borderRadius: 3 },
  statusChipText:{ fontSize: font.xs, fontWeight: '600' },
  name:         { fontSize: font.md, fontWeight: '700' },
  metaRow:      { flexDirection: 'row', gap: 6, flexWrap: 'wrap' },
  metaChip:     { flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 7, paddingVertical: 3, borderRadius: radius.full, borderWidth: 1 },
  metaChipText: { fontSize: font.xs, fontWeight: '600' },
  usedByRow:    { flexDirection: 'row', flexWrap: 'wrap', gap: 4 },
  usedChip:     { flexDirection: 'row', alignItems: 'center', gap: 3, paddingHorizontal: 7, paddingVertical: 3, borderRadius: radius.full, borderWidth: 1, maxWidth: 160 },
  usedChipText: { fontSize: font.xs, fontWeight: '600' },
});
