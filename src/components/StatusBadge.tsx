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
    <View style={[styles.badge, { backgroundColor: bg, borderColor: color + '40' }]}>
      <Text style={[styles.badgeText, { color }]}>{label}</Text>
    </View>
  );
}

export function ProtocolBadge({ protocol }: { protocol: string }) {
  const c = useThemeStore(s => s.colors);
  const p = (protocol ?? '').toLowerCase();
  const variants: Record<string, { color: string; bg: string }> = {
    http:  { color: c.blue,                bg: c.blue   + '18' },
    https: { color: c.blue,                bg: c.blue   + '18' },
    tcp:   { color: c.purple,              bg: c.purple + '18' },
    udp:   { color: c.onSecondaryContainer, bg: c.secondaryContainer },
  };
  const v = variants[p] ?? { color: c.muted, bg: c.muted + '18' };
  return <Badge label={(protocol ?? 'http').toUpperCase()} color={v.color} bg={v.bg} />;
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
      style={[styles.iconBtn, { backgroundColor: color + '14' }]}
    >
      {loading
        ? <ActivityIndicator size="small" color={color} style={{ width: 16, height: 16 }} />
        : <MaterialCommunityIcons name={icon as any} size={16} color={color} />}
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: radius.sm,
    borderWidth: 1,
    alignSelf: 'flex-start',
  },
  badgeText: {
    fontSize: font.xs,
    fontWeight: '600',
    letterSpacing: 0.4,
  },
  iconBtn: {
    width: 32,
    height: 32,
    borderRadius: radius.sm,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
