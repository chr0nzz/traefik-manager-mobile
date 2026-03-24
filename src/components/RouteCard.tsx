import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Linking,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  TextInput,
  TouchableOpacity,
  View,
  useWindowDimensions,
} from 'react-native';
import { Surface, Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Route, RouteFormData, domainFromRule } from '../api/routes';
import { font, radius, spacing } from '../theme';
import { useThemeStore } from '../store/theme';
import { useDeleteRoute, useSaveRoute } from '../hooks/useRoutes';
import { useKeyboardHeight } from '../hooks/useKeyboardHeight';
import { ProtocolBadge, StatusDot } from './StatusBadge';

interface Props {
  route: Route;
  onToggle: (id: string, enable: boolean) => void;
  toggling: boolean;
  editMode?: boolean;
}

function parseTarget(target: string): { ip: string; port: string } {
  if (!target) return { ip: '', port: '' };
  try {
    const u = new URL(target);
    return { ip: u.hostname, port: u.port || '' };
  } catch {
    return { ip: target, port: '' };
  }
}

const PROTOCOLS = ['http', 'tcp', 'udp'];

export function RouteCard({ route, onToggle, toggling, editMode = false }: Props) {
  const [showInfo, setShowInfo] = useState(false);
  const [showEdit, setShowEdit] = useState(false);
  const c                        = useThemeStore(s => s.colors);
  const { height: screenH }      = useWindowDimensions();
  const kbHeight                 = useKeyboardHeight();
  const deleteRoute              = useDeleteRoute();
  const saveRoute                = useSaveRoute();
  const editSheetBottom = Platform.OS === 'ios' ? kbHeight : 0;

  const st     = route.enabled ? 'ok' : 'unknown';
  const domain = domainFromRule(route.rule);
  const mws    = route.middlewares ?? [];

  const openUrl = () => {
    if (!domain) return;
    Linking.openURL(domain.startsWith('http') ? domain : `https://${domain}`);
  };

  // ── Edit form state ──────────────────────────────────────────────
  const parsed = parseTarget(route.target);
  const [fName,   setFName]   = useState(route.name);
  const [fHost,   setFHost]   = useState(domain || route.rule);
  const [fIp,     setFIp]     = useState(parsed.ip);
  const [fPort,   setFPort]   = useState(parsed.port);
  const [fProto,  setFProto]  = useState((route.protocol ?? 'http').toLowerCase());
  const [fMws,    setFMws]    = useState(mws.join(', '));
  const [saving,  setSaving]  = useState(false);
  const [saveErr, setSaveErr] = useState('');

  useEffect(() => {
    if (showEdit) {
      const p = parseTarget(route.target);
      setFName(route.name);
      setFHost(domainFromRule(route.rule) || route.rule);
      setFIp(p.ip);
      setFPort(p.port);
      setFProto((route.protocol ?? 'http').toLowerCase());
      setFMws((route.middlewares ?? []).join(', '));
      setSaveErr('');
    }
  }, [showEdit]);

  const handleDelete = () => {
    Alert.alert(
      'Delete Route',
      `Delete "${route.name}"? This cannot be undone.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete', style: 'destructive',
          onPress: () => deleteRoute.mutate(route.id, {
            onError: (e) => Alert.alert('Error', e.message),
          }),
        },
      ],
    );
  };

  const handleSave = () => {
    if (!fName.trim() || !fIp.trim()) { setSaveErr('Name and target IP are required'); return; }
    setSaving(true);
    setSaveErr('');
    const data: RouteFormData = {
      serviceName: fName.trim(),
      subdomain: fHost.trim(),
      targetIp: fIp.trim(),
      targetPort: fPort.trim(),
      protocol: fProto,
      middlewares: fMws.trim(),
    };
    saveRoute.mutate(
      { data, isEdit: true, originalId: route.id },
      {
        onSuccess: (res) => { setSaving(false); if (res.ok) setShowEdit(false); else setSaveErr(res.message ?? 'Save failed'); },
        onError:   (e)   => { setSaving(false); setSaveErr(e.message); },
      },
    );
  };

  return (
    <>
      <Surface style={[styles.card, { backgroundColor: c.card, borderColor: c.border }, !route.enabled && styles.cardDisabled]} elevation={1}>
        {/* Header */}
        <View style={styles.header}>
          <View style={styles.badges}>
            <ProtocolBadge protocol={route.protocol} />
            {route.tls && <SmallChip label="🔒 TLS" color={c.green} c={c} />}
            <StatusDot status={st} />
          </View>
          <View style={styles.headerRight}>
            <TouchableOpacity onPress={() => setShowInfo(true)} hitSlop={8} style={styles.iconBtn}>
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
              <TouchableOpacity onPress={() => setShowEdit(true)} hitSlop={8} style={styles.iconBtn}>
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

      {/* ── Detail sheet ── */}
      <Modal visible={showInfo} transparent animationType="slide" onRequestClose={() => setShowInfo(false)}>
        <View style={styles.overlay}>
          <Pressable style={StyleSheet.absoluteFill} onPress={() => setShowInfo(false)} />
          <View style={[styles.sheet, { backgroundColor: c.bg, height: screenH * 0.92 }]}>
            <View style={[styles.handle, { backgroundColor: c.border }]} />

            {/* Header */}
            <View style={[styles.sheetHeader, { borderBottomColor: c.border, backgroundColor: c.card }]}>
              <ProtocolBadge protocol={route.protocol} />
              <Text style={[styles.sheetTitle, { color: c.text }]} numberOfLines={1}>{route.name}</Text>
              {editMode && (
                <TouchableOpacity
                  onPress={() => { setShowInfo(false); setShowEdit(true); }}
                  style={[styles.editBtn, { borderColor: c.border, backgroundColor: c.bg }]}
                >
                  <MaterialCommunityIcons name="pencil-outline" size={14} color={c.muted} />
                  <Text style={[styles.editBtnTxt, { color: c.muted }]}>Edit</Text>
                </TouchableOpacity>
              )}
              <TouchableOpacity onPress={() => setShowInfo(false)} hitSlop={12}>
                <MaterialCommunityIcons name="close" size={20} color={c.muted} />
              </TouchableOpacity>
            </View>

            <ScrollView style={styles.scroll} contentContainerStyle={styles.scrollContent} bounces={false}>

              {/* Router Details */}
              <DetailSection icon="information-outline" title="Router Details" c={c}>
                <DetailRow label="STATUS" isLast={false} c={c}
                  value={
                    <View style={styles.statusVal}>
                      <View style={[styles.statusDot, { backgroundColor: route.enabled ? c.green : c.muted }]} />
                      <StatusChip
                        label={route.enabled ? 'Enabled' : 'Disabled'}
                        color={route.enabled ? c.green : c.muted}
                        c={c}
                      />
                    </View>
                  }
                />
                {!!route.rule && (
                  <DetailRow label="RULE" isLast={false} c={c}
                    value={<Text style={[styles.rowText, { color: c.text, fontFamily: 'monospace' }]}>{route.rule}</Text>} />
                )}
                {!!route.name && (
                  <DetailRow label="NAME" isLast={false} c={c}
                    value={<Text style={[styles.rowText, { color: c.text, fontFamily: 'monospace' }]}>{route.name}</Text>} />
                )}
                {(route.entryPoints?.length ?? 0) > 0 && (
                  <DetailRow label="ENTRY POINTS" isLast={false} c={c}
                    value={
                      <View style={styles.chipsRow}>
                        {route.entryPoints!.map(ep => <TextChip key={ep} label={ep} c={c} />)}
                      </View>
                    }
                  />
                )}
                <DetailRow label="SERVICE" isLast c={c}
                  value={<Text style={[styles.rowText, { color: c.text }]}>{route.service_name}</Text>} />
              </DetailSection>

              {/* TLS */}
              {route.tls && (
                <DetailSection icon="shield-check-outline" title="TLS" c={c}>
                  <DetailRow label="TLS" isLast c={c} value={<BoolChip value={true} c={c} />} />
                </DetailSection>
              )}

              {/* Middlewares */}
              <DetailSection icon="lightning-bolt-outline" title="Middlewares" c={c}>
                {mws.length > 0 ? (
                  <View style={styles.chipsWrap}>
                    {mws.map(m => <TextChip key={m} label={m} c={c} />)}
                  </View>
                ) : (
                  <View style={styles.emptyState}>
                    <MaterialCommunityIcons name="layers-outline" size={28} color={c.border} />
                    <Text style={[styles.emptyText, { color: c.muted }]}>No middlewares configured</Text>
                  </View>
                )}
              </DetailSection>

              {/* Service */}
              {!!route.target && (
                <DetailSection icon="flash-outline" title="Service" c={c}>
                  {!!domain && (
                    <DetailRow label="DOMAIN" isLast={false} c={c}
                      value={<Text style={[styles.rowText, { color: c.blue }]}>{domain}</Text>} />
                  )}
                  <DetailRow label="TARGET" isLast c={c}
                    value={<Text style={[styles.rowText, { color: c.green, fontFamily: 'monospace' }]}>{route.target}</Text>} />
                </DetailSection>
              )}

            </ScrollView>
          </View>
        </View>
      </Modal>

      {/* ── Edit modal ── */}
      <Modal visible={showEdit} transparent animationType="slide" onRequestClose={() => setShowEdit(false)}>
        <View style={styles.overlay}>
          <Pressable style={StyleSheet.absoluteFill} onPress={() => setShowEdit(false)} />
          <View style={[styles.sheet, { backgroundColor: c.card, borderColor: c.border, bottom: editSheetBottom, maxHeight: screenH * 0.88 }]}>
            <View style={[styles.handle, { backgroundColor: c.border }]} />
            <View style={[styles.sheetHeader, { borderBottomColor: c.border, backgroundColor: c.card }]}>
              <Text style={[styles.sheetTitle, { color: c.text, flex: 1 }]}>Edit Route</Text>
              <TouchableOpacity onPress={() => setShowEdit(false)} hitSlop={12}>
                <MaterialCommunityIcons name="close" size={20} color={c.muted} />
              </TouchableOpacity>
            </View>

            <ScrollView style={styles.editScroll} contentContainerStyle={styles.editScrollContent} keyboardShouldPersistTaps="handled" bounces={false}>
              <FormField label="NAME" value={fName} onChange={setFName} c={c} />
              <FormField label="HOST / DOMAIN" value={fHost} onChange={setFHost} c={c} placeholder="e.g. app.example.com" keyboardType="url" />
              <FormField label="TARGET IP / HOST" value={fIp} onChange={setFIp} c={c} placeholder="e.g. 192.168.1.10" keyboardType="url" />
              <FormField label="TARGET PORT" value={fPort} onChange={setFPort} c={c} placeholder="e.g. 8080" keyboardType="numeric" />
              <FormField label="MIDDLEWARES (comma-separated)" value={fMws} onChange={setFMws} c={c} placeholder="e.g. auth@file, compress" />

              <Text style={[styles.formLabel, { color: c.muted }]}>PROTOCOL</Text>
              <View style={styles.protoRow}>
                {PROTOCOLS.map(p => (
                  <TouchableOpacity
                    key={p}
                    style={[styles.protoBtn, { borderColor: c.border, backgroundColor: c.bg }, fProto === p && { backgroundColor: c.blue + '20', borderColor: c.blue }]}
                    onPress={() => setFProto(p)}
                  >
                    <Text style={[styles.protoBtnTxt, { color: fProto === p ? c.blue : c.muted }]}>{p.toUpperCase()}</Text>
                  </TouchableOpacity>
                ))}
              </View>

              {!!saveErr && <Text style={[styles.errTxt, { color: c.red }]}>{saveErr}</Text>}

              <View style={styles.actionRow}>
                <TouchableOpacity
                  style={[styles.actionBtn, { backgroundColor: c.blue, borderColor: c.blue }]}
                  onPress={handleSave}
                  disabled={saving}
                >
                  {saving
                    ? <ActivityIndicator size="small" color="#fff" />
                    : <Text style={[styles.actionBtnTxt, { color: '#fff' }]}>Save</Text>}
                </TouchableOpacity>
                <TouchableOpacity style={[styles.actionBtn, { backgroundColor: c.bg, borderColor: c.border }]} onPress={() => setShowEdit(false)}>
                  <Text style={[styles.actionBtnTxt, { color: c.muted }]}>Cancel</Text>
                </TouchableOpacity>
              </View>
            </ScrollView>
          </View>
        </View>
      </Modal>
    </>
  );
}

// ── Shared sub-components ──────────────────────────────────────────

function DetailSection({
  icon, title, children, c,
}: {
  icon: string; title: string; children: React.ReactNode;
  c: ReturnType<typeof useThemeStore.getState>['colors'];
}) {
  return (
    <View style={styles.section}>
      <View style={styles.sectionHeader}>
        <MaterialCommunityIcons name={icon as any} size={14} color={c.blue} />
        <Text style={[styles.sectionTitle, { color: c.text }]}>{title}</Text>
      </View>
      <View style={[styles.sectionBody, { borderColor: c.border, backgroundColor: c.card }]}>
        {children}
      </View>
    </View>
  );
}

function DetailRow({
  label, value, isLast, c,
}: {
  label: string; value: React.ReactNode; isLast: boolean;
  c: ReturnType<typeof useThemeStore.getState>['colors'];
}) {
  return (
    <View style={[styles.row, !isLast && { borderBottomWidth: 1, borderBottomColor: c.border }]}>
      <Text style={[styles.rowLabel, { color: c.muted }]}>{label}</Text>
      <View style={styles.rowValue}>{value}</View>
    </View>
  );
}

function BoolChip({ value, c }: { value: boolean; c: ReturnType<typeof useThemeStore.getState>['colors'] }) {
  const col = value ? c.green : c.red;
  return (
    <View style={[styles.chip, { backgroundColor: col + '20', borderColor: col + '55' }]}>
      <Text style={[styles.chipText, { color: col }]}>{value ? 'True' : 'False'}</Text>
    </View>
  );
}

function StatusChip({ label, color, c }: { label: string; color: string; c: ReturnType<typeof useThemeStore.getState>['colors'] }) {
  return (
    <View style={[styles.chip, { backgroundColor: color + '20', borderColor: color + '55' }]}>
      <Text style={[styles.chipText, { color }]}>{label}</Text>
    </View>
  );
}

function TextChip({ label, c }: { label: string; c: ReturnType<typeof useThemeStore.getState>['colors'] }) {
  return (
    <View style={[styles.chip, { backgroundColor: c.bg, borderColor: c.border }]}>
      <Text style={[styles.chipText, { color: c.muted }]}>{label}</Text>
    </View>
  );
}

function SmallChip({ label, color, c }: { label: string; color: string; c: ReturnType<typeof useThemeStore.getState>['colors'] }) {
  return (
    <View style={[styles.chip, { backgroundColor: color + '20', borderColor: color + '55' }]}>
      <Text style={[styles.chipText, { color }]}>{label}</Text>
    </View>
  );
}

function FormField({
  label, value, onChange, c, placeholder, keyboardType,
}: {
  label: string; value: string; onChange: (v: string) => void;
  c: ReturnType<typeof useThemeStore.getState>['colors'];
  placeholder?: string;
  keyboardType?: 'default' | 'url' | 'numeric';
}) {
  return (
    <View style={styles.formGroup}>
      <Text style={[styles.formLabel, { color: c.muted }]}>{label}</Text>
      <TextInput
        style={[styles.formInput, { backgroundColor: c.bg, borderColor: c.border, color: c.text }]}
        value={value}
        onChangeText={onChange}
        placeholder={placeholder}
        placeholderTextColor={c.muted}
        autoCapitalize="none"
        autoCorrect={false}
        keyboardType={keyboardType ?? 'default'}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  // Card
  card: {
    borderRadius: radius.md, borderWidth: 1,
    marginBottom: spacing.sm, overflow: 'hidden',
    padding: spacing.md, gap: 6,
  },
  cardDisabled: { opacity: 0.5 },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
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
  // Sheet
  overlay:  { flex: 1, backgroundColor: 'rgba(0,0,0,0.55)' },
  sheet:    { position: 'absolute', left: 0, right: 0, borderTopLeftRadius: 20, borderTopRightRadius: 20, overflow: 'hidden' },
  handle:   { width: 36, height: 4, borderRadius: 2, alignSelf: 'center', marginTop: 10, marginBottom: 4 },
  sheetHeader: {
    flexDirection: 'row', alignItems: 'center', gap: spacing.sm,
    paddingHorizontal: spacing.lg, paddingVertical: spacing.md,
    borderBottomWidth: 1,
  },
  sheetTitle: { flex: 1, fontSize: font.lg, fontWeight: '700' },
  editBtn:    { flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 10, paddingVertical: 5, borderRadius: radius.sm, borderWidth: 1 },
  editBtnTxt: { fontSize: font.xs, fontWeight: '600' },
  scroll:        { flex: 1 },
  scrollContent: { padding: spacing.md, gap: spacing.md, paddingBottom: spacing.xl },
  // Sections
  section:       { gap: 6 },
  sectionHeader: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 2 },
  sectionTitle:  { fontSize: font.sm, fontWeight: '700' },
  sectionBody:   { borderRadius: radius.md, borderWidth: 1, overflow: 'hidden' },
  // Rows
  row:       { flexDirection: 'row', alignItems: 'center', paddingHorizontal: spacing.md, paddingVertical: 11, gap: spacing.sm },
  rowLabel:  { fontSize: font.xs, fontWeight: '700', letterSpacing: 0.5, width: 110, color: '#888' },
  rowValue:  { flex: 1 },
  rowText:   { fontSize: font.sm },
  statusVal: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  statusDot: { width: 7, height: 7, borderRadius: 3.5 },
  chipsRow:  { flexDirection: 'row', flexWrap: 'wrap', gap: 4 },
  chipsWrap: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, padding: spacing.md },
  emptyState:{ alignItems: 'center', gap: 6, paddingVertical: spacing.xl },
  emptyText: { fontSize: font.sm },
  // Chips
  chip:     { paddingHorizontal: 8, paddingVertical: 3, borderRadius: radius.full, borderWidth: 1, alignSelf: 'flex-start' },
  chipText: { fontSize: font.xs, fontWeight: '600' },
  // Form
  editScroll:        { flexShrink: 1 },
  editScrollContent: { padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xl },
  actionRow:    { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.sm },
  actionBtn:    { flex: 1, paddingVertical: 12, borderRadius: radius.sm, borderWidth: 1, alignItems: 'center' },
  actionBtnTxt: { fontSize: font.sm, fontWeight: '600' },
  formGroup:   { gap: 4 },
  formLabel:   { fontSize: font.xs, fontWeight: '700', letterSpacing: 0.5 },
  formInput:   { borderWidth: 1, borderRadius: radius.sm, paddingHorizontal: spacing.sm, paddingVertical: 9, fontSize: font.sm },
  protoRow:    { flexDirection: 'row', gap: spacing.sm },
  protoBtn:    { flex: 1, paddingVertical: 8, borderRadius: radius.sm, borderWidth: 1, alignItems: 'center' },
  protoBtnTxt: { fontSize: font.sm, fontWeight: '700' },
  errTxt:      { fontSize: font.sm, marginTop: 4 },
});
