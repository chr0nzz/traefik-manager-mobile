import React from 'react';
import { ActivityIndicator, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
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

export function PillIconBtn({ icon, color, onPress, loading }: {
  icon: string;
  color: string;
  onPress: () => void;
  loading?: boolean;
}) {
  return (
    <TouchableOpacity
      onPress={onPress}
      hitSlop={4}
      style={[styles.pillBtn, { backgroundColor: color + '18', borderColor: color + '44' }]}
    >
      {loading
        ? <ActivityIndicator size="small" color={color} style={{ width: 14, height: 14 }} />
        : <MaterialCommunityIcons name={icon as any} size={14} color={color} />}
    </TouchableOpacity>
  );
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
  pillBtn: {
    paddingHorizontal: 8,
    paddingVertical: 5,
    borderRadius: radius.full,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
