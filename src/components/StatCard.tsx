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
  vertical?: boolean;
}

export function StatCard({ title, accent, data, onExplore, vertical }: Props) {
  const c     = useThemeStore(s => s.colors);
  const total = data?.total    ?? 0;
  const warn  = data?.warnings ?? 0;
  const err   = data?.errors   ?? 0;
  const ok    = Math.max(0, total - warn - err);

  const pct = (n: number) => total > 0 ? `${Math.round((n / total) * 100)}%` : '0%';

  if (vertical) {
    return (
      <Surface style={[styles.cardVertical, { backgroundColor: c.card }]} elevation={1}>
        <View style={styles.titleRow}>
          <Text style={[styles.title, { color: c.text }]} numberOfLines={1}>{title}</Text>
          {onExplore && (
            <TouchableOpacity onPress={onExplore} hitSlop={8}>
              <Text style={[styles.explore, { color: accent }]}>→</Text>
            </TouchableOpacity>
          )}
        </View>
        <View style={styles.ringWrapVertical}>
          <StatRing
            size={60}
            strokeWidth={9}
            segments={[
              { value: ok,   color: c.green  },
              { value: warn, color: c.yellow },
              { value: err,  color: c.red    },
            ]}
          />
          <View style={StyleSheet.absoluteFillObject} pointerEvents="none">
            <View style={styles.ringCenter}>
              <Text style={[styles.ringCountVertical, { color: c.text }]}>{ok}</Text>
            </View>
          </View>
        </View>
        <StatRow icon="check-circle" label="OK"   count={ok}   pct={pct(ok)}   color={c.green}  tc={c.text} mc={c.muted} compact />
        <StatRow icon="alert-circle" label="Warn" count={warn} pct={pct(warn)} color={c.yellow} tc={c.text} mc={c.muted} compact />
        <StatRow icon="close-circle" label="Err"  count={err}  pct={pct(err)}  color={c.red}    tc={c.text} mc={c.muted} compact />
      </Surface>
    );
  }

  return (
    <Surface style={[styles.card, { backgroundColor: c.card }]} elevation={1}>
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
        <View style={StyleSheet.absoluteFillObject} pointerEvents="none">
          <View style={styles.ringCenter}>
            <Text style={[styles.ringCount, { color: c.text }]}>{ok}</Text>
          </View>
        </View>
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
        <StatRow icon="check-circle" label="Success"  count={ok}   pct={pct(ok)}   color={c.green}  tc={c.text} mc={c.muted} />
        <StatRow icon="alert-circle" label="Warnings" count={warn} pct={pct(warn)} color={c.yellow} tc={c.text} mc={c.muted} />
        <StatRow icon="close-circle" label="Errors"   count={err}  pct={pct(err)}  color={c.red}    tc={c.text} mc={c.muted} />
      </View>
    </Surface>
  );
}

function StatRow({
  icon, label, count, pct, color, tc, mc, compact,
}: {
  icon: string; label: string; count: number;
  pct: string; color: string; tc: string; mc: string; compact?: boolean;
}) {
  return (
    <View style={styles.row}>
      <MaterialCommunityIcons name={icon as any} size={compact ? 13 : 16} color={color} />
      <Text style={[styles.rowLabel, { color: tc, fontSize: compact ? font.xs : font.sm }]}>{label}</Text>
      <Text style={[styles.rowPct,   { color: mc, fontSize: compact ? font.xs : font.sm }]}>{pct}</Text>
      <Text style={[styles.rowCount, { color: tc, fontSize: compact ? font.xs : font.sm }]}>{count}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: radius.md,
    padding: spacing.md,
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    marginBottom: spacing.sm,
  },
  cardVertical: {
    borderRadius: radius.md,
    padding: spacing.md,
    flexDirection: 'column',
    marginBottom: spacing.sm,
    gap: 4,
  },
  ringWrapVertical: {
    alignItems: 'center',
    justifyContent: 'center',
    width: 60,
    height: 60,
    alignSelf: 'center',
    marginVertical: spacing.xs,
  },
  ringCountVertical: {
    fontSize: font.sm,
    fontWeight: '800',
    lineHeight: font.sm,
  },
  ringWrap: {
    alignItems: 'center',
    justifyContent: 'center',
    width: 72,
    height: 72,
  },
  ringCenter: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  ringCount: {
    fontSize: font.md,
    fontWeight: '800',
    lineHeight: font.md,
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
