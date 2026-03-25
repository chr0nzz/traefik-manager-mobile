import React from 'react';
import {
  ActivityIndicator,
  Alert,
  Linking,
  StyleSheet,
  Switch,
  TouchableOpacity,
  View,
} from 'react-native';
import { Surface, Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Route, domainFromRule } from '../api/routes';
import { font, radius, spacing } from '../theme';
import { useThemeStore } from '../store/theme';
import { useDeleteRoute } from '../hooks/useRoutes';
import { ProtocolBadge, StatusDot } from './StatusBadge';

interface Props {
  route: Route;
  onToggle: (id: string, enable: boolean) => void;
  toggling: boolean;
  editMode?: boolean;
}

export function RouteCard({ route, onToggle, toggling, editMode = false }: Props) {
  const router = useRouter();
  const c      = useThemeStore(s => s.colors);
  const deleteRoute = useDeleteRoute();

  const st     = route.enabled ? 'ok' : 'unknown';
  const domain = domainFromRule(route.rule);
  const mws    = route.middlewares ?? [];

  const openUrl = () => {
    if (!domain) return;
    Linking.openURL(domain.startsWith('http') ? domain : `https://${domain}`);
  };

  const handleDelete = () => {
    Alert.alert(
      'Delete Route',
      `Delete "${route.name}"? This cannot be undone.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete', style: 'destructive',
          onPress: () => deleteRoute.mutate({ id: route.id, configFile: route.configFile }, {
            onError: (e) => Alert.alert('Error', e.message),
          }),
        },
      ],
    );
  };

  return (
    <Surface style={[styles.card, { backgroundColor: c.card, borderColor: c.border }, !route.enabled && styles.cardDisabled]} elevation={1}>
      {/* Header */}
      <View style={styles.header}>
        <View style={styles.badges}>
          <ProtocolBadge protocol={route.protocol} />
          {route.tls && <SmallChip label="🔒 TLS" color={c.green} c={c} />}
          <StatusDot status={st} />
        </View>
        <View style={styles.headerRight}>
          <TouchableOpacity onPress={() => router.push(`/route/${encodeURIComponent(route.id)}`)} hitSlop={8} style={styles.iconBtn}>
            <MaterialCommunityIcons name="information-outline" size={17} color={c.muted} />
          </TouchableOpacity>
          {!!domain && (
            <TouchableOpacity onPress={openUrl} hitSlop={8} style={styles.iconBtn}>
              <MaterialCommunityIcons name="open-in-new" size={17} color={c.muted} />
            </TouchableOpacity>
          )}
          {editMode && (
            <TouchableOpacity onPress={handleDelete} hitSlop={8} style={styles.iconBtn}>
              {deleteRoute.isPending
                ? <ActivityIndicator size="small" color={c.red} />
                : <MaterialCommunityIcons name="trash-can-outline" size={17} color={c.red} />}
            </TouchableOpacity>
          )}
          {editMode && (
            <TouchableOpacity onPress={() => router.push(`/route/${encodeURIComponent(route.id)}?edit=1`)} hitSlop={8} style={styles.iconBtn}>
              <MaterialCommunityIcons name="pencil-outline" size={17} color={c.muted} />
            </TouchableOpacity>
          )}
          {editMode && (toggling
            ? <ActivityIndicator size="small" color={c.blue} style={{ marginLeft: 2 }} />
            : <Switch
                value={route.enabled}
                onValueChange={(v) => onToggle(route.id, v)}
                trackColor={{ false: c.border, true: c.blue + '66' }}
                thumbColor={route.enabled ? c.blue : c.muted}
                style={styles.toggle}
              />
          )}
        </View>
      </View>

      <Text style={[styles.name, { color: c.text }]} numberOfLines={1}>{route.name}</Text>
      <Text style={[styles.service, { color: c.muted }]} numberOfLines={1}>{route.service_name}</Text>

      {!!domain && (
        <View style={[styles.field, { backgroundColor: c.bg }]}>
          <Text style={[styles.fieldLabel, { color: c.muted }]}>DOMAIN</Text>
          <Text style={[styles.fieldValue, { color: c.blue }]} numberOfLines={1}>{domain}</Text>
        </View>
      )}
      {!!route.target && (
        <View style={[styles.field, { backgroundColor: c.bg }]}>
          <Text style={[styles.fieldLabel, { color: c.muted }]}>TARGET</Text>
          <Text style={[styles.fieldValue, { color: c.green }]} numberOfLines={1}>{route.target}</Text>
        </View>
      )}

      {mws.length > 0 && (
        <View style={styles.mwRow}>
          {mws.slice(0, 3).map(m => {
            const label = m.includes('@') ? m.split('@')[0] : m;
            return (
              <View key={m} style={[styles.mwChip, { backgroundColor: c.blue + '18', borderColor: c.blue + '44' }]}>
                <Text style={[styles.mwTxt, { color: c.blue }]} numberOfLines={1}>{label}</Text>
              </View>
            );
          })}
          {mws.length > 3 && (
            <View style={[styles.mwChip, { backgroundColor: c.blue + '18', borderColor: c.blue + '44' }]}>
              <Text style={[styles.mwTxt, { color: c.blue }]}>+{mws.length - 3}</Text>
            </View>
          )}
        </View>
      )}
    </Surface>
  );
}

function SmallChip({ label, color, c }: { label: string; color: string; c: ReturnType<typeof useThemeStore.getState>['colors'] }) {
  return (
    <View style={[styles.chip, { backgroundColor: color + '20', borderColor: color + '55' }]}>
      <Text style={[styles.chipText, { color }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: radius.md, borderWidth: 1,
    marginBottom: spacing.sm, overflow: 'hidden',
    padding: spacing.md, gap: 6,
  },
  cardDisabled: { opacity: 0.5 },
  header:      { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  badges:      { flexDirection: 'row', gap: 6, flexWrap: 'wrap', flex: 1, alignItems: 'center' },
  headerRight: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  iconBtn:     { padding: 2 },
  toggle:      { transform: [{ scaleX: 0.8 }, { scaleY: 0.8 }] },
  name:        { fontSize: font.md, fontWeight: '700' },
  service:     { fontSize: font.sm },
  field:       { borderRadius: radius.sm, padding: spacing.sm },
  fieldLabel:  { fontSize: font.xs, fontWeight: '700', letterSpacing: 0.5, marginBottom: 2 },
  fieldValue:  { fontSize: font.sm, fontFamily: 'monospace' },
  mwRow:       { flexDirection: 'row', flexWrap: 'wrap', gap: 4, marginTop: 2 },
  mwChip:      { paddingHorizontal: 6, paddingVertical: 2, borderRadius: radius.full, borderWidth: 1 },
  mwTxt:       { fontSize: font.xs, fontWeight: '600' },
  chip:        { paddingHorizontal: 8, paddingVertical: 3, borderRadius: radius.full, borderWidth: 1, alignSelf: 'flex-start' },
  chipText:    { fontSize: font.xs, fontWeight: '600' },
});
