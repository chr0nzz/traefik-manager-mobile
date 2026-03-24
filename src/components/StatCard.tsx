import React from 'react';
import { StyleSheet, TouchableOpacity, View } from 'react-native';
import { Surface, Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { font, radius, spacing } from '../theme';
import { useThemeStore } from '../store/theme';
import { StatRing } from './StatRing';

interface StatData {
  total: number;
  warnings: number;
  errors: number;
}

interface Props {
  title: string;
  accent: string;
  data?: StatData;
  onExplore?: () => void;
}

export function StatCard({ title, accent, data, onExplore }: Props) {
  const c     = useThemeStore(s => s.colors);
  const total = data?.total    ?? 0;
  const warn  = data?.warnings ?? 0;
  const err   = data?.errors   ?? 0;
  const ok    = Math.max(0, total - warn - err);

  const pct = (n: number) => total > 0 ? `${Math.round((n / total) * 100)}%` : '0%';

  return (
    <Surface style={[styles.card, { backgroundColor: c.card, borderColor: c.border }]} elevation={1}>
      {/* Ring */}
      <View style={styles.ringWrap}>
        <StatRing
          size={72}
          strokeWidth={11}
          segments={[
            { value: ok,   color: c.green  },
            { value: warn, color: c.yellow },
            { value: err,  color: c.red    },
          ]}
        />
      </View>

      {/* Right column */}
      <View style={styles.right}>
        {/* Title + Explore */}
        <View style={styles.titleRow}>
          <Text style={[styles.title, { color: c.text }]} numberOfLines={1}>{title}</Text>
          {onExplore && (
            <TouchableOpacity onPress={onExplore} hitSlop={8}>
              <Text style={[styles.explore, { color: accent }]}>Explore →</Text>
            </TouchableOpacity>
          )}
        </View>

        {/* Rows */}
        <StatRow
          icon="check-circle"
          label="Success"
          count={ok}
          pct={pct(ok)}
          color={c.green}
          tc={c.text}
          mc={c.muted}
        />
        <StatRow
          icon="alert-circle"
          label="Warnings"
          count={warn}
          pct={pct(warn)}
          color={c.yellow}
          tc={c.text}
          mc={c.muted}
        />
        <StatRow
          icon="close-circle"
          label="Errors"
          count={err}
          pct={pct(err)}
          color={c.red}
          tc={c.text}
          mc={c.muted}
        />
      </View>
    </Surface>
  );
}

function StatRow({
  icon, label, count, pct, color, tc, mc,
}: {
  icon: string; label: string; count: number;
  pct: string; color: string; tc: string; mc: string;
}) {
  return (
    <View style={styles.row}>
      <MaterialCommunityIcons name={icon as any} size={16} color={color} />
      <Text style={[styles.rowLabel, { color: tc }]}>{label}</Text>
      <Text style={[styles.rowPct, { color: mc }]}>{pct}</Text>
      <Text style={[styles.rowCount, { color: tc }]}>{count}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: radius.md,
    borderWidth: 1,
    padding: spacing.md,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    marginBottom: spacing.sm,
  },
  ringWrap: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  right: {
    flex: 1,
    gap: 4,
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 4,
  },
  title: {
    fontSize: font.md,
    fontWeight: '700',
    flex: 1,
  },
  explore: {
    fontSize: font.sm,
    fontWeight: '600',
    marginLeft: spacing.sm,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  rowLabel: {
    flex: 1,
    fontSize: font.sm,
  },
  rowPct: {
    fontSize: font.sm,
    minWidth: 36,
    textAlign: 'right',
  },
  rowCount: {
    fontSize: font.sm,
    fontWeight: '700',
    minWidth: 24,
    textAlign: 'right',
  },
});
