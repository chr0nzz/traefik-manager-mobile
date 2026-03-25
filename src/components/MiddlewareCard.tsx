import React from 'react';
import {
  ActivityIndicator,
  Alert,
  StyleSheet,
  TouchableOpacity,
  View,
} from 'react-native';
import { Surface, Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Middleware } from '../api/middlewares';
import { font, radius, spacing } from '../theme';
import { useThemeStore } from '../store/theme';
import { useDeleteMiddleware } from '../hooks/useMiddlewares';
import { Badge, ProtocolBadge } from './StatusBadge';

interface Props { middleware: Middleware; editMode?: boolean }

const SKIP_KEYS = new Set(['name', 'type', 'status', 'provider', '_proto']);

function formatConfig(mw: Middleware): string {
  const entries = Object.entries(mw).filter(([k]) => !SKIP_KEYS.has(k));
  if (entries.length === 0) return '';
  function toYaml(val: unknown, indent = 0): string {
    const pad = '  '.repeat(indent);
    if (val === null || val === undefined) return 'null';
    if (typeof val === 'boolean' || typeof val === 'number') return String(val);
    if (typeof val === 'string') return val.includes(':') || val.includes(' ') ? `"${val}"` : val;
    if (Array.isArray(val)) return val.map(v => `\n${pad}- ${toYaml(v, indent + 1)}`).join('');
    if (typeof val === 'object') {
      return Object.entries(val as Record<string, unknown>)
        .map(([k, v]) => {
          const rendered = toYaml(v, indent + 1);
          if (typeof v === 'object' && v !== null && !Array.isArray(v)) {
            return `\n${pad}${k}:${rendered}`;
          }
          return `\n${pad}${k}: ${rendered}`;
        }).join('');
    }
    return String(val);
  }
  return entries.map(([k, v]) => {
    if (typeof v === 'object' && v !== null && !Array.isArray(v)) {
      return `${k}:${toYaml(v, 1)}`;
    }
    return `${k}: ${toYaml(v)}`;
  }).join('\n');
}

const TYPE_KEY: Record<string, string> = {
  redirectscheme: 'blue', stripprefix: 'purple', headers: 'orange',
  basicauth: 'yellow', forwardauth: 'green', ratelimit: 'red',
  compress: 'muted', chain: 'blue', redirectregex: 'blue',
};

export function MiddlewareCard({ middleware, editMode = false }: Props) {
  const router = useRouter();
  const c      = useThemeStore(s => s.colors);
  const deleteMiddleware = useDeleteMiddleware();

  const proto    = middleware._proto ?? 'http';
  const baseName = middleware.name.includes('@') ? middleware.name.split('@')[0] : middleware.name;
  const type     = middleware.type ?? '';
  const tkey     = TYPE_KEY[(type || '').toLowerCase()] ?? 'muted';
  const tc       = c[tkey as keyof typeof c] as string;
  const config   = formatConfig(middleware);

  const handleDelete = () => {
    Alert.alert(
      'Delete Middleware',
      `Delete "${baseName}"? This cannot be undone.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete', style: 'destructive',
          onPress: () => deleteMiddleware.mutate({ name: baseName, configFile: middleware.configFile as string | undefined }, {
            onError: (e) => Alert.alert('Error', e.message),
          }),
        },
      ],
    );
  };

  return (
    <Surface style={[styles.card, { backgroundColor: c.card, borderColor: c.border }]} elevation={1}>
      {/* Header */}
      <View style={styles.header}>
        <View style={styles.badges}>
          <ProtocolBadge protocol={proto} />
          {!!type && <Badge label={type} color={tc} bg={tc + '18'} />}
        </View>
        <View style={styles.headerRight}>
          <TouchableOpacity onPress={() => router.push(`/middleware/${encodeURIComponent(middleware.name)}`)} hitSlop={8} style={styles.iconBtn}>
            <MaterialCommunityIcons name="information-outline" size={17} color={c.muted} />
          </TouchableOpacity>
          {editMode && (
            <TouchableOpacity onPress={handleDelete} hitSlop={8} style={styles.iconBtn}>
              {deleteMiddleware.isPending
                ? <ActivityIndicator size="small" color={c.red} />
                : <MaterialCommunityIcons name="trash-can-outline" size={17} color={c.red} />}
            </TouchableOpacity>
          )}
          {editMode && (
            <TouchableOpacity onPress={() => router.push(`/middleware/${encodeURIComponent(middleware.name)}?edit=1`)} hitSlop={8} style={styles.iconBtn}>
              <MaterialCommunityIcons name="pencil-outline" size={17} color={c.muted} />
            </TouchableOpacity>
          )}
        </View>
      </View>

      <Text style={[styles.name, { color: c.text }]} numberOfLines={1}>{baseName}</Text>

      {!!config && (
        <View style={[styles.codeBlock, { backgroundColor: c.bg, borderColor: c.border }]}>
          <Text style={[styles.codeText, { color: c.green }]}>{config}</Text>
        </View>
      )}
    </Surface>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: radius.md,
    borderWidth: 1,
    marginBottom: spacing.sm,
    overflow: 'hidden',
    padding: spacing.md,
    gap: 8,
  },
  header:      { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  badges:      { flexDirection: 'row', gap: 6, flexWrap: 'wrap', flex: 1, alignItems: 'center' },
  headerRight: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  iconBtn:     { padding: 2 },
  name:        { fontSize: font.md, fontWeight: '700' },
  codeBlock:   { borderRadius: radius.sm, borderWidth: 1, padding: spacing.sm },
  codeText:    { fontSize: font.xs, fontFamily: 'monospace', lineHeight: 18 },
});
