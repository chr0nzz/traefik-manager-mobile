import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Surface, Text } from 'react-native-paper';
import { TraefikEntrypoint } from '../api/traefik';
import { font, radius, spacing } from '../theme';
import { useThemeStore } from '../store/theme';

interface Props {
  data?: TraefikEntrypoint[];
}

export function EntrypointCard({ data }: Props) {
  const c     = useThemeStore(s => s.colors);
  const count = data?.length ?? 0;

  return (
    <Surface style={[styles.card, { backgroundColor: c.card, borderColor: c.border }]} elevation={1}>
      <View style={styles.header}>
        <Text style={[styles.title, { color: c.teal }]}>ENTRY POINTS</Text>
        <Text style={[styles.count, { color: c.teal }]}>{count}</Text>
      </View>
      <View style={styles.list}>
        {(data ?? []).map(ep => (
          <View key={ep.name} style={styles.row}>
            <View style={[styles.dot, { backgroundColor: c.teal }]} />
            <Text style={[styles.name, { color: c.text }]} numberOfLines={1}>{ep.name}</Text>
            <Text style={[styles.address, { color: c.muted }]}>{ep.address}</Text>
          </View>
        ))}
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
    borderWidth: 1,
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
    fontSize: 28,
    fontWeight: '800',
    lineHeight: 32,
  },
  list: { gap: 5 },
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
