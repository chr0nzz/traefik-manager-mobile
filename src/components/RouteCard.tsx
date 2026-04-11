import React, { useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Image,
  Linking,
  StyleSheet,
  View,
} from 'react-native';
import { Surface, Switch, Text } from 'react-native-paper';
import { useRouter } from 'expo-router';
import { Route, domainFromRule } from '../api/routes';
import { font, radius, spacing } from '../theme';
import { useThemeStore } from '../store/theme';
import { useConnection } from '../store/connection';
import { useDeleteRoute } from '../hooks/useRoutes';
import { PillIconBtn, ProtocolBadge, StatusDot } from './StatusBadge';
import { useDashboardConfig } from '../hooks/useDashboard';

interface Props {
  route: Route;
  onToggle: (id: string, enable: boolean) => void;
  toggling: boolean;
  editMode?: boolean;
}

export function RouteCard({ route, onToggle, toggling, editMode = false }: Props) {
  const router = useRouter();
  const c      = useThemeStore(s => s.colors);
  const { baseUrl, apiKey, demoMode } = useConnection();
  const deleteRoute = useDeleteRoute();
  const { data: dashConfig } = useDashboardConfig();
  const isFileManagedRoute = !route.provider || route.provider === 'file';
  const [iconVisible, setIconVisible] = useState(true);

  const override   = dashConfig?.route_overrides?.[route.id] ?? dashConfig?.route_overrides?.[route.name];
  const autoSlug   = (route.name || '').split('@')[0].toLowerCase().replace(/[^a-z0-9-]/g, '');
  const iconUri    = demoMode ? null
                   : override?.icon_type === 'url'  ? override.icon_url!
                   : override?.icon_type === 'slug' ? `${baseUrl}/api/dashboard/icon/${override.icon_slug}`
                   : `${baseUrl}/api/dashboard/icon/${autoSlug}`;
  const iconHeaders = { 'X-Api-Key': apiKey, 'X-Requested-With': 'fetch' };

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
    <Surface style={[styles.card, { backgroundColor: c.card }, !route.enabled && styles.cardDisabled]} elevation={1}>
      {/* Header */}
      <View style={styles.header}>
        <View style={styles.badges}>
          <ProtocolBadge protocol={route.protocol} />
          {route.tls && <SmallChip label="🔒 TLS" color={c.green} c={c} />}
          {route.insecureSkipVerify && <SmallChip label="⚠ TLS skip" color="#d4a017" c={c} />}
          <StatusDot status={st} />
        </View>
        <View style={styles.headerRight}>
          <PillIconBtn icon="information-outline" color={c.muted} onPress={() => router.push(`/route/${encodeURIComponent(route.id)}`)} />
          {!!domain && (
            <PillIconBtn icon="open-in-new" color={c.muted} onPress={openUrl} />
          )}
          {editMode && isFileManagedRoute && (
            <PillIconBtn icon="trash-can-outline" color={c.red} onPress={handleDelete} loading={deleteRoute.isPending} />
          )}
          {editMode && isFileManagedRoute && (
            <PillIconBtn icon="pencil-outline" color={c.muted} onPress={() => router.push(`/route/${encodeURIComponent(route.id)}?edit=1`)} />
          )}
          {editMode && isFileManagedRoute && (toggling
            ? <ActivityIndicator size="small" color={c.blue} style={{ marginLeft: 2 }} />
            : <Switch
                value={route.enabled}
                onValueChange={(v) => onToggle(route.id, v)}
              />
          )}
        </View>
      </View>

      <View style={styles.nameRow}>
        {iconUri && iconVisible && (
          <Image
            source={{ uri: iconUri, headers: iconHeaders }}
            style={styles.appIcon}
            onError={() => setIconVisible(false)}
          />
        )}
        <Text style={[styles.name, { color: c.text, flexShrink: 1 }]} numberOfLines={1}>{route.name}</Text>
      </View>
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
    borderRadius: radius.md,
    marginBottom: spacing.sm, overflow: 'hidden',
    padding: spacing.md, gap: 6,
  },
  cardDisabled: { opacity: 0.5 },
  header:      { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  badges:      { flexDirection: 'row', gap: 6, flexWrap: 'wrap', flex: 1, alignItems: 'center' },
  headerRight: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  nameRow:     { flexDirection: 'row', alignItems: 'center', gap: 8 },
  appIcon:     { width: 28, height: 28, borderRadius: 6, flexShrink: 0 },
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
