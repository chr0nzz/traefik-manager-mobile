import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Surface, Text } from 'react-native-paper';
import { TraefikEntrypoint } from '../api/traefik';
import { font, radius, spacing } from '../theme';
import { useThemeStore } from '../store/theme';

interface Props {
  data?: TraefikEntrypoint[];
}

const MAX_VISIBLE = 5;

export function EntrypointCard({ data }: Props) {
  const c       = useThemeStore(s => s.colors);
  const all     = data ?? [];
  const visible = all.slice(0, MAX_VISIBLE);
  const extra   = all.length - MAX_VISIBLE;

  return (
    <Surface style={[styles.card, { backgroundColor: c.card }]} elevation={1}>
      <View style={styles.header}>
        <Text style={[styles.title, { color: c.teal }]}>ENTRY POINTS</Text>
        <Text style={[styles.count, { color: c.teal }]}>{all.length}</Text>
      </View>
      <View style={styles.list}>
        {visible.map(ep => (
          <View key={ep.name} style={styles.row}>
            <View style={[styles.dot, { backgroundColor: c.teal }]} />
            <Text style={[styles.name, { color: c.text }]} numberOfLines={1}>{ep.name}</Text>
            <Text style={[styles.address, { color: c.muted }]} numberOfLines={1}>{ep.address}</Text>
          </View>
        ))}
        {extra > 0 && (
          <Text style={[styles.more, { color: c.muted }]}>+{extra} more</Text>
        )}
        {!data && (
          <Text style={[styles.placeholder, { color: c.muted }]}>Loading…</Text>
        )}
      </View>
    </Surface>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: radius.md,
    padding: spacing.md,
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: spacing.sm,
  },
  title: {
    fontSize: font.xs,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 0.8,
  },
  count: {
    fontSize: font.lg,
    fontWeight: '800',
  },
  list: { gap: 5 },
  more: { fontSize: font.xs, marginTop: 2 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    flexShrink: 0,
  },
  name: {
    flex: 1,
    fontSize: font.sm,
    fontWeight: '600',
  },
  address: {
    fontSize: font.xs,
    fontFamily: 'monospace',
  },
  placeholder: {
    fontSize: font.xs,
  },
});
