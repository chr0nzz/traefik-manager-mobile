import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Linking,
  Platform,
  ScrollView,
  StyleSheet,
  Switch,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { RouteFormData, domainFromRule } from '../../src/api/routes';
import { font, radius, spacing } from '../../src/theme';
import { useThemeStore } from '../../src/store/theme';
import { useRoutes, useDeleteRoute, useSaveRoute, useToggleRoute } from '../../src/hooks/useRoutes';
import { useConfigs } from '../../src/hooks/useConfigs';
import { useSettings } from '../../src/hooks/useSettings';
import { ProtocolBadge } from '../../src/components/StatusBadge';

// ── helpers ──────────────────────────────────────────────────────

function parseTarget(target: string): { ip: string; port: string } {
  if (!target) return { ip: '', port: '' };
  try {
    const u = new URL(target);
    return { ip: u.hostname, port: u.port || '' };
  } catch {
    return { ip: target, port: '' };
  }
}

const PROTOCOLS = ['http', 'tcp', 'udp'] as const;

// ── sub-components ────────────────────────────────────────────────

type Colors = ReturnType<typeof useThemeStore.getState>['colors'];

function Section({ icon, title, count, children, c }: {
  icon: string; title: string; count?: number; children: React.ReactNode; c: Colors;
}) {
  return (
    <View style={styles.section}>
      <View style={styles.sectionHeader}>
        <MaterialCommunityIcons name={icon as any} size={14} color={c.blue} />
        <Text style={[styles.sectionTitle, { color: c.text }]}>{title}</Text>
        {count !== undefined && (
          <View style={[styles.countBadge, { backgroundColor: c.border }]}>
            <Text style={[styles.countText, { color: c.muted }]}>{count}</Text>
          </View>
        )}
      </View>
      <View style={[styles.sectionBody, { borderColor: c.border, backgroundColor: c.card }]}>
        {children}
      </View>
    </View>
  );
}

function Row({ label, value, isLast, c }: {
  label: string; value: React.ReactNode; isLast: boolean; c: Colors;
}) {
  return (
    <View style={[styles.row, !isLast && { borderBottomWidth: 1, borderBottomColor: c.border }]}>
      <Text style={[styles.rowLabel, { color: c.muted }]}>{label}</Text>
      <View style={styles.rowValue}>{value}</View>
    </View>
  );
}

function Chip({ label, c }: { label: string; c: Colors }) {
  return (
    <View style={[styles.chip, { backgroundColor: c.bg, borderColor: c.border }]}>
      <Text style={[styles.chipText, { color: c.muted }]}>{label}</Text>
    </View>
  );
}

function BoolChip({ value, c }: { value: boolean; c: Colors }) {
  const col = value ? c.green : c.red;
  return (
    <View style={[styles.chip, { backgroundColor: col + '20', borderColor: col + '55' }]}>
      <Text style={[styles.chipText, { color: col }]}>{value ? 'True' : 'False'}</Text>
    </View>
  );
}

function FormField({ label, value, onChange, c, placeholder, keyboardType, multiline }: {
  label: string; value: string; onChange: (v: string) => void; c: Colors;
  placeholder?: string; keyboardType?: 'default' | 'url' | 'numeric'; multiline?: boolean;
}) {
  return (
    <View style={styles.formGroup}>
      <Text style={[styles.formLabel, { color: c.muted }]}>{label}</Text>
      <TextInput
        style={[styles.formInput, { backgroundColor: c.bg, borderColor: c.border, color: c.text },
          multiline && styles.multilineInput]}
        value={value}
        onChangeText={onChange}
        placeholder={placeholder}
        placeholderTextColor={c.muted}
        autoCapitalize="none"
        autoCorrect={false}
        keyboardType={keyboardType ?? 'default'}
        multiline={multiline}
        textAlignVertical={multiline ? 'top' : undefined}
      />
    </View>
  );
}

// ── main screen ───────────────────────────────────────────────────

export default function RouteDetailScreen() {
  const { id, edit } = useLocalSearchParams<{ id: string; edit?: string }>();
  const router       = useRouter();
  const insets       = useSafeAreaInsets();
  const c            = useThemeStore(s => s.colors);

  const { data }   = useRoutes();
  const toggleRoute  = useToggleRoute();
  const deleteRoute  = useDeleteRoute();
  const saveRoute    = useSaveRoute();
  const configs      = useConfigs();
  const { data: settings } = useSettings();
  const multiConfig  = (configs.data?.length ?? 0) > 1;
  const resolvers    = (settings?.cert_resolver ?? '').split(',').map(r => r.trim()).filter(Boolean);

  const decodedId = decodeURIComponent(id ?? '');
  const route     = data?.apps.find(r => r.id === decodedId);

  const [editMode,    setEditMode]    = useState(edit === '1');
  const [toggling,    setToggling]    = useState(false);

  // ── edit form state ──────────────────────────────────────────
  const [fName,       setFName]       = useState('');
  const [fHost,       setFHost]       = useState('');
  const [fIp,         setFIp]         = useState('');
  const [fPort,       setFPort]       = useState('');
  const [fProto,      setFProto]      = useState('http');
  const [fMws,        setFMws]        = useState('');
  const [fScheme,       setFScheme]       = useState('http');
  const [fPassHost,     setFPassHost]     = useState(true);
  const [fCertResolver, setFCertResolver] = useState('');
  const [fConfigFile,   setFConfigFile]   = useState('');
  const [saving,      setSaving]      = useState(false);
  const [saveErr,     setSaveErr]     = useState('');

  const populateForm = (r: NonNullable<typeof route>) => {
    const p = parseTarget(r.target);
    setFName(r.name);
    setFHost(domainFromRule(r.rule) || r.rule);
    setFIp(p.ip);
    setFPort(p.port);
    setFProto((r.protocol ?? 'http').toLowerCase());
    setFMws((r.middlewares ?? []).join(', '));
    setFScheme((r.target || '').startsWith('https://') ? 'https' : 'http');
    setFPassHost(r.passHostHeader !== false);
    setFCertResolver(r.certResolver ?? resolvers[0] ?? '');
    setFConfigFile(r.configFile ?? '');
    setSaveErr('');
  };

  useEffect(() => {
    if (route && editMode) populateForm(route);
  }, [editMode]);

  useEffect(() => {
    if (route) populateForm(route);
  }, [route?.id]);

  const domain = route ? domainFromRule(route.rule) : '';
  const mws    = route?.middlewares ?? [];

  const handleToggle = () => {
    if (!route) return;
    setToggling(true);
    toggleRoute.mutate(
      { id: route.id, enable: !route.enabled },
      { onSettled: () => setToggling(false) },
    );
  };

  const handleDelete = () => {
    if (!route) return;
    Alert.alert(
      'Delete Route',
      `Delete "${route.name}"? This cannot be undone.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete', style: 'destructive',
          onPress: () => deleteRoute.mutate(
            { id: route.id, configFile: route.configFile },
            { onSuccess: () => router.back(), onError: (e) => Alert.alert('Error', e.message) },
          ),
        },
      ],
    );
  };

  const handleSave = () => {
    if (!route) return;
    if (!fName.trim() || !fIp.trim()) { setSaveErr('Name and target IP are required'); return; }
    setSaving(true);
    setSaveErr('');
    const formData: RouteFormData = {
      serviceName:    fName.trim(),
      subdomain:      fHost.trim(),
      targetIp:       fIp.trim(),
      targetPort:     fPort.trim(),
      protocol:       fProto,
      middlewares:    fMws.trim(),
      scheme:         fScheme,
      passHostHeader: fPassHost,
      certResolver:   fCertResolver,
      configFile:     fConfigFile,
    };
    saveRoute.mutate(
      { data: formData, isEdit: true, originalId: route.id },
      {
        onSuccess: (res) => {
          setSaving(false);
          if (res.ok) setEditMode(false);
          else setSaveErr(res.message ?? 'Save failed');
        },
        onError: (e) => { setSaving(false); setSaveErr(e.message); },
      },
    );
  };

  // ── not found ────────────────────────────────────────────────
  if (data && !route) {
    return (
      <View style={[styles.screen, { backgroundColor: c.bg, paddingTop: insets.top }]}>
        <View style={[styles.headerBar, { borderBottomColor: c.border, backgroundColor: c.card }]}>
          <TouchableOpacity onPress={() => router.back()} hitSlop={12} style={styles.headerBtn}>
            <MaterialCommunityIcons name="close" size={22} color={c.text} />
          </TouchableOpacity>
          <Text style={[styles.headerTitle, { color: c.text }]}>Route</Text>
          <View style={styles.headerBtn} />
        </View>
        <View style={styles.centered}>
          <Text style={[styles.rowText, { color: c.muted }]}>Route not found.</Text>
        </View>
      </View>
    );
  }

  // ── loading ──────────────────────────────────────────────────
  if (!route) {
    return (
      <View style={[styles.screen, { backgroundColor: c.bg, paddingTop: insets.top }]}>
        <View style={styles.centered}>
          <ActivityIndicator color={c.blue} />
        </View>
      </View>
    );
  }

  // ── render ───────────────────────────────────────────────────
  return (
    <View style={[styles.screen, { backgroundColor: c.bg, paddingTop: insets.top }]}>

      {/* Header */}
      <View style={[styles.headerBar, { borderBottomColor: c.border, backgroundColor: c.card }]}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={12} style={styles.headerBtn}>
          <MaterialCommunityIcons name="close" size={22} color={c.text} />
        </TouchableOpacity>

        <Text style={[styles.headerTitle, { color: c.text }]} numberOfLines={1}>
          {editMode ? 'Edit Route' : route.name}
        </Text>

        <View style={styles.headerActions}>
          {editMode ? (
            <>
              <TouchableOpacity
                onPress={() => { setEditMode(false); setSaveErr(''); }}
                style={[styles.headerActionBtn, { borderColor: c.border }]}
              >
                <Text style={[styles.headerActionTxt, { color: c.muted }]}>Cancel</Text>
              </TouchableOpacity>
              <TouchableOpacity
                onPress={handleSave}
                disabled={saving}
                style={[styles.headerActionBtn, { borderColor: c.blue, backgroundColor: c.blue }]}
              >
                {saving
                  ? <ActivityIndicator size="small" color="#fff" />
                  : <Text style={[styles.headerActionTxt, { color: '#fff' }]}>Save</Text>}
              </TouchableOpacity>
            </>
          ) : (
            <>
              <TouchableOpacity
                onPress={handleDelete}
                hitSlop={8}
                style={styles.headerIconBtn}
                disabled={deleteRoute.isPending}
              >
                {deleteRoute.isPending
                  ? <ActivityIndicator size="small" color={c.red} />
                  : <MaterialCommunityIcons name="trash-can-outline" size={20} color={c.red} />}
              </TouchableOpacity>
              <TouchableOpacity
                onPress={() => setEditMode(true)}
                hitSlop={8}
                style={styles.headerIconBtn}
              >
                <MaterialCommunityIcons name="pencil-outline" size={20} color={c.muted} />
              </TouchableOpacity>
              <TouchableOpacity onPress={handleToggle} hitSlop={8} style={styles.headerIconBtn} disabled={toggling}>
                {toggling
                  ? <ActivityIndicator size="small" color={c.blue} />
                  : <MaterialCommunityIcons
                      name={route.enabled ? 'toggle-switch' : 'toggle-switch-off-outline'}
                      size={24}
                      color={route.enabled ? c.blue : c.muted}
                    />}
              </TouchableOpacity>
            </>
          )}
        </View>
      </View>

      {/* Content */}
      {editMode ? (
        <KeyboardAvoidingView
          style={{ flex: 1 }}
          behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        >
          <ScrollView
            style={{ flex: 1 }}
            contentContainerStyle={[styles.scrollContent, { paddingBottom: insets.bottom + 24 }]}
            keyboardShouldPersistTaps="handled"
          >
            <FormField label="NAME" value={fName} onChange={setFName} c={c} />
            <FormField label="HOST / DOMAIN" value={fHost} onChange={setFHost} c={c}
              placeholder="e.g. app.example.com" keyboardType="url" />
            <FormField label="TARGET IP / HOST" value={fIp} onChange={setFIp} c={c}
              placeholder="e.g. 192.168.1.10" keyboardType="url" />
            <FormField label="TARGET PORT" value={fPort} onChange={setFPort} c={c}
              placeholder="e.g. 8080" keyboardType="numeric" />
            <FormField label="MIDDLEWARES (comma-separated)" value={fMws} onChange={setFMws} c={c}
              placeholder="e.g. auth@file, compress" />

            <Text style={[styles.formLabel, { color: c.muted }]}>BACKEND SCHEME</Text>
            <View style={styles.toggleRow}>
              {(['http', 'https'] as const).map(s => (
                <TouchableOpacity
                  key={s}
                  style={[styles.toggleBtn, { borderColor: c.border, backgroundColor: c.bg },
                    fScheme === s && { backgroundColor: c.blue + '20', borderColor: c.blue }]}
                  onPress={() => setFScheme(s)}
                >
                  <Text style={[styles.toggleBtnTxt, { color: fScheme === s ? c.blue : c.muted }]}>
                    {s.toUpperCase()}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>

            <View style={styles.switchRow}>
              <View style={{ flex: 1, gap: 2 }}>
                <Text style={[styles.formLabel, { color: c.muted }]}>PASS HOST HEADER</Text>
                <Text style={[styles.formHint, { color: c.muted }]}>Forward original Host to backend</Text>
              </View>
              <Switch
                value={fPassHost}
                onValueChange={setFPassHost}
                trackColor={{ false: c.border, true: c.blue + '66' }}
                thumbColor={fPassHost ? c.blue : c.muted}
              />
            </View>

            {resolvers.length > 0 && (fProto === 'http' || fProto === 'tcp') && (
              <>
                <Text style={[styles.formLabel, { color: c.muted }]}>CERT RESOLVER</Text>
                <View style={styles.toggleRow}>
                  {resolvers.map(r => (
                    <TouchableOpacity
                      key={r}
                      style={[styles.toggleBtn, { borderColor: c.border, backgroundColor: c.bg },
                        fCertResolver === r && { backgroundColor: c.blue + '20', borderColor: c.blue }]}
                      onPress={() => setFCertResolver(r)}
                    >
                      <Text style={[styles.toggleBtnTxt, { color: fCertResolver === r ? c.blue : c.muted }]}>
                        {r}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </View>
              </>
            )}

            <Text style={[styles.formLabel, { color: c.muted }]}>PROTOCOL</Text>
            <View style={styles.toggleRow}>
              {PROTOCOLS.map(p => (
                <TouchableOpacity
                  key={p}
                  style={[styles.toggleBtn, { borderColor: c.border, backgroundColor: c.bg },
                    fProto === p && { backgroundColor: c.blue + '20', borderColor: c.blue }]}
                  onPress={() => setFProto(p)}
                >
                  <Text style={[styles.toggleBtnTxt, { color: fProto === p ? c.blue : c.muted }]}>
                    {p.toUpperCase()}
                  </Text>
                </TouchableOpacity>
              ))}
            </View>

            {multiConfig && (
              <>
                <Text style={[styles.formLabel, { color: c.muted }]}>CONFIG FILE</Text>
                <View style={styles.toggleRow}>
                  {(configs.data ?? []).map(cfg => (
                    <TouchableOpacity
                      key={cfg.label}
                      style={[styles.toggleBtn, { borderColor: c.border, backgroundColor: c.bg },
                        fConfigFile === cfg.label && { backgroundColor: c.purple + '20', borderColor: c.purple }]}
                      onPress={() => setFConfigFile(cfg.label)}
                    >
                      <Text style={[styles.toggleBtnTxt, { color: fConfigFile === cfg.label ? c.purple : c.muted }]}>
                        {cfg.label}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </View>
              </>
            )}

            {!!saveErr && <Text style={[styles.errTxt, { color: c.red }]}>{saveErr}</Text>}
          </ScrollView>
        </KeyboardAvoidingView>
      ) : (
        <ScrollView
          style={{ flex: 1 }}
          contentContainerStyle={[styles.scrollContent, { paddingBottom: insets.bottom + 24 }]}
        >
          {/* Protocol + status badges */}
          <View style={styles.badgeRow}>
            <ProtocolBadge protocol={route.protocol} />
            {route.tls && (
              <View style={[styles.chip, { backgroundColor: c.green + '20', borderColor: c.green + '55' }]}>
                <Text style={[styles.chipText, { color: c.green }]}>TLS</Text>
              </View>
            )}
            <View style={[styles.chip,
              route.enabled
                ? { backgroundColor: c.green + '20', borderColor: c.green + '55' }
                : { backgroundColor: c.muted + '20', borderColor: c.muted + '55' }
            ]}>
              <View style={[styles.statusDot, { backgroundColor: route.enabled ? c.green : c.muted }]} />
              <Text style={[styles.chipText, { color: route.enabled ? c.green : c.muted }]}>
                {route.enabled ? 'Enabled' : 'Disabled'}
              </Text>
            </View>
            {!!domain && (
              <TouchableOpacity
                onPress={() => Linking.openURL(domain.startsWith('http') ? domain : `https://${domain}`)}
                style={[styles.chip, { backgroundColor: c.blue + '14', borderColor: c.blue + '44' }]}
              >
                <MaterialCommunityIcons name="open-in-new" size={11} color={c.blue} />
                <Text style={[styles.chipText, { color: c.blue }]}>Open</Text>
              </TouchableOpacity>
            )}
          </View>

          {/* Router Details */}
          <Section icon="information-outline" title="ROUTER DETAILS" c={c}>
            <Row label="RULE" isLast={false} c={c}
              value={<Text style={[styles.rowText, { color: c.text, fontFamily: 'monospace' }]}>{route.rule}</Text>} />
            {(route.entryPoints?.length ?? 0) > 0 && (
              <Row label="ENTRY POINTS" isLast={false} c={c}
                value={
                  <View style={styles.chipsRow}>
                    {route.entryPoints!.map(ep => <Chip key={ep} label={ep} c={c} />)}
                  </View>
                }
              />
            )}
            <Row label="SERVICE" isLast c={c}
              value={<Text style={[styles.rowText, { color: c.text }]}>{route.service_name}</Text>} />
          </Section>

          {/* TLS */}
          {route.tls && (
            <Section icon="shield-check-outline" title="TLS" c={c}>
              <Row label="TLS" isLast c={c} value={<BoolChip value={true} c={c} />} />
            </Section>
          )}

          {/* Service */}
          {!!route.target && (
            <Section icon="flash-outline" title="SERVICE" c={c}>
              {!!domain && (
                <Row label="DOMAIN" isLast={false} c={c}
                  value={<Text style={[styles.rowText, { color: c.blue }]}>{domain}</Text>} />
              )}
              <Row label="TARGET" isLast={route.passHostHeader === undefined} c={c}
                value={<Text style={[styles.rowText, { color: c.green, fontFamily: 'monospace' }]}>{route.target}</Text>} />
              {route.passHostHeader !== undefined && (
                <Row label="PASS HOST HEADER" isLast c={c}
                  value={<BoolChip value={route.passHostHeader !== false} c={c} />} />
              )}
            </Section>
          )}

          {/* Middlewares */}
          <Section icon="lightning-bolt-outline" title="MIDDLEWARES" count={mws.length} c={c}>
            {mws.length > 0 ? (
              <View style={styles.chipsWrap}>
                {mws.map(m => <Chip key={m} label={m} c={c} />)}
              </View>
            ) : (
              <View style={styles.emptyRow}>
                <Text style={[styles.rowText, { color: c.muted }]}>None configured</Text>
              </View>
            )}
          </Section>

          {/* Config file badge */}
          {!!route.configFile && (
            <Section icon="file-outline" title="SOURCE" c={c}>
              <Row label="CONFIG FILE" isLast c={c}
                value={<Text style={[styles.rowText, { color: c.text }]}>{route.configFile}</Text>} />
            </Section>
          )}
        </ScrollView>
      )}
    </View>
  );
}

// ── styles ────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  screen:  { flex: 1 },
  centered:{ flex: 1, alignItems: 'center', justifyContent: 'center' },
  // Header
  headerBar: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: spacing.md, paddingVertical: spacing.sm,
    borderBottomWidth: 1, gap: spacing.sm,
  },
  headerBtn:       { width: 36, alignItems: 'flex-start' },
  headerTitle:     { flex: 1, fontSize: font.lg, fontWeight: '700', textAlign: 'center' },
  headerActions:   { flexDirection: 'row', alignItems: 'center', gap: 6 },
  headerIconBtn:   { padding: 4 },
  headerActionBtn: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: radius.sm, borderWidth: 1 },
  headerActionTxt: { fontSize: font.sm, fontWeight: '600' },
  // Scroll
  scrollContent: { padding: spacing.md, gap: spacing.md },
  // Badge row
  badgeRow:  { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  statusDot: { width: 6, height: 6, borderRadius: 3 },
  // Sections
  section:      { gap: 6 },
  sectionHeader:{ flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 2 },
  sectionTitle: { fontSize: font.xs, fontWeight: '800', letterSpacing: 0.8, textTransform: 'uppercase' as const, flex: 1 },
  countBadge:   { paddingHorizontal: 6, paddingVertical: 1, borderRadius: radius.full, minWidth: 20, alignItems: 'center' },
  countText:    { fontSize: font.xs, fontWeight: '700' },
  sectionBody:  { borderRadius: radius.md, borderWidth: 1, overflow: 'hidden' as const },
  // Rows
  row:      { flexDirection: 'row', alignItems: 'center', paddingHorizontal: spacing.md, paddingVertical: 11, gap: spacing.sm },
  rowLabel: { fontSize: font.xs, fontWeight: '700', letterSpacing: 0.5, width: 130 },
  rowValue: { flex: 1 },
  rowText:  { fontSize: font.sm },
  emptyRow: { paddingHorizontal: spacing.md, paddingVertical: 11 },
  // Chips
  chip:      { flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 8, paddingVertical: 3, borderRadius: radius.full, borderWidth: 1, alignSelf: 'flex-start' as const },
  chipText:  { fontSize: font.xs, fontWeight: '600' },
  chipsRow:  { flexDirection: 'row', flexWrap: 'wrap', gap: 4 },
  chipsWrap: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, padding: spacing.md },
  // Form
  formGroup:     { gap: 4 },
  formLabel:     { fontSize: font.xs, fontWeight: '700', letterSpacing: 0.5 },
  formHint:      { fontSize: font.xs, opacity: 0.7 },
  formInput:     { borderWidth: 1, borderRadius: radius.sm, paddingHorizontal: spacing.sm, paddingVertical: 9, fontSize: font.sm },
  multilineInput:{ minHeight: 80, paddingTop: spacing.sm },
  toggleRow:     { flexDirection: 'row', gap: spacing.sm, flexWrap: 'wrap' },
  toggleBtn:     { flex: 1, paddingVertical: 8, borderRadius: radius.sm, borderWidth: 1, alignItems: 'center', minWidth: 60 },
  toggleBtnTxt:  { fontSize: font.sm, fontWeight: '700' },
  switchRow:     { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.sm },
  errTxt:        { fontSize: font.sm, color: 'red' },
});
