import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { font, radius, spacing } from '../theme';
import { useThemeStore } from '../store/theme';

type Status = 'ok' | 'warn' | 'err' | 'unknown';

interface Props {
  status: Status;
  size?: number;
}

export function StatusDot({ status, size = 8 }: Props) {
  const c = useThemeStore(s => s.colors);
  const colorMap: Record<Status, string> = {
    ok:      c.green,
    warn:    c.yellow,
    err:     c.red,
    unknown: c.muted,
  };
  const color = colorMap[status] ?? c.muted;
  return (
    <View style={{ width: size, height: size, borderRadius: size / 2, backgroundColor: color }} />
  );
}

interface BadgeProps {
  label: string;
  color: string;
  bg: string;
}

export function Badge({ label, color, bg }: BadgeProps) {
  return (
    <View style={[styles.badge, { backgroundColor: bg, borderColor: color + '55' }]}>
      <Text style={[styles.badgeText, { color }]}>{label}</Text>
    </View>
  );
}

export function ProtocolBadge({ protocol }: { protocol: string }) {
  const c = useThemeStore(s => s.colors);
  const p = (protocol ?? '').toLowerCase();
  const colorMap: Record<string, string> = {
    http:  c.blue,
    https: c.blue,
    tcp:   c.purple,
    udp:   c.orange,
  };
  const col = colorMap[p] ?? c.muted;
  return <Badge label={(protocol ?? 'http').toUpperCase()} color={col} bg={col + '22'} />;
}

const styles = StyleSheet.create({
  badge: {
    paddingHorizontal: spacing.sm,
    paddingVertical: 2,
    borderRadius: radius.full,
    borderWidth: 1,
    alignSelf: 'flex-start',
  },
  badgeText: {
    fontSize: font.xs,
    fontWeight: '700',
    letterSpacing: 0.5,
  },
});
