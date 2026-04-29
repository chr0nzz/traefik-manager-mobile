import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Surface, Text } from 'react-native-paper';
import { TraefikEntrypoint } from '../api/traefik';
import { font, radius, spacing } from '../theme';
import { useThemeStore } from '../store/theme';

interface Props {
  data?: TraefikEntrypoint[];
}

function epColor(address: string, c: ReturnType<typeof useThemeStore.getState>['colors']): string {
  const port = (address || '').split(':').pop() || '';
  if (['443', '8443'].includes(port)) return c.green;
  if (['80', '8080'].includes(port))  return c.blue;
  return c.muted;
}

export function EntrypointCard({ data }: Props) {
  const c   = useThemeStore(s => s.colors);
  const all = data ?? [];

  return (
    <Surface style={[styles.card, { backgroundColor: c.card }]} elevation={1}>
      <View style={styles.header}>
        <Text style={[styles.title, { color: c.teal }]}>ENTRY POINTS</Text>
        <Text style={[styles.count, { color: c.teal }]}>{data ? all.length : '—'}</Text>
      </View>

      {!data ? (
        <Text style={[styles.placeholder, { color: c.muted }]}>Loading…</Text>
      ) : all.length === 0 ? (
        <Text style={[styles.placeholder, { color: c.muted }]}>No entry points</Text>
      ) : (
        <View style={styles.chips}>
          {all.map(ep => {
            const color = epColor(ep.address, c);
            return (
              <View key={ep.name} style={[styles.chip, { borderColor: color + '55', backgroundColor: color + '14' }]}>
                <Text style={[styles.chipName, { color }]} numberOfLines={1}>{ep.name}</Text>
                {!!ep.address && (
                  <Text style={[styles.chipAddr, { color: c.muted }]} numberOfLines={1}>{ep.address}</Text>
                )}
              </View>
            );
          })}
        </View>
      )}
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
    letterSpacing: 0.8,
  },
  count: {
    fontSize: font.lg,
    fontWeight: '800',
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
  },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    paddingHorizontal: 9,
    paddingVertical: 5,
    borderRadius: 7,
    borderWidth: 1,
    maxWidth: '100%',
    overflow: 'hidden',
  },
  chipName: {
    fontSize: font.xs,
    fontWeight: '700',
    flexShrink: 0,
  },
  chipAddr: {
    fontSize: font.xs,
    fontFamily: 'monospace',
    flexShrink: 1,
  },
  placeholder: {
    fontSize: font.xs,
  },
});
