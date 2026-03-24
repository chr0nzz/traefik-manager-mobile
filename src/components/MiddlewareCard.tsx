import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  View,
  useWindowDimensions,
} from 'react-native';
import { Surface, Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Middleware } from '../api/middlewares';
import { font, radius, spacing } from '../theme';
import { useThemeStore } from '../store/theme';
import { useDeleteMiddleware, useSaveMiddleware } from '../hooks/useMiddlewares';
import { useKeyboardHeight } from '../hooks/useKeyboardHeight';
import { Badge, ProtocolBadge, StatusDot } from './StatusBadge';
import { providerOf } from '../utils';

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

function mwStatus(s: string): 'ok' | 'warn' | 'err' {
  const l = (s || '').toLowerCase();
  if (l === 'enabled' || l === 'success') return 'ok';
  if (l === 'warning' || l === 'warn')    return 'warn';
  return 'err';
}

const TYPE_KEY: Record<string, string> = {
  redirectscheme: 'blue', stripprefix: 'purple', headers: 'orange',
  basicauth: 'yellow', forwardauth: 'green', ratelimit: 'red',
  compress: 'muted', chain: 'blue', redirectregex: 'blue',
};

export function MiddlewareCard({ middleware, editMode = false }: Props) {
  const [showInfo, setShowInfo] = useState(false);
  const [showEdit, setShowEdit] = useState(false);
  const c                        = useThemeStore(s => s.colors);
  const { height: screenH }      = useWindowDimensions();
  const kbHeight                 = useKeyboardHeight();
  const deleteMiddleware         = useDeleteMiddleware();
  const saveMiddleware           = useSaveMiddleware();
  const editSheetBottom = Platform.OS === 'ios' ? kbHeight : 0;

  const provider = middleware.provider ?? providerOf(middleware.name);
  const proto    = middleware._proto ?? 'http';
  const st       = mwStatus(middleware.status);
  const baseName = middleware.name.includes('@') ? middleware.name.split('@')[0] : middleware.name;
  const type     = middleware.type ?? '';
  const tkey     = TYPE_KEY[(type || '').toLowerCase()] ?? 'muted';
  const tc       = c[tkey as keyof typeof c] as string;
  const config   = formatConfig(middleware);

  const statusColor = { ok: c.green, warn: c.orange, err: c.red };
  const statusLabel = { ok: 'Enabled', warn: 'Warning', err: 'Error' };

  // ── Edit state ──────────────────────────────────────────────────
  const [fName,   setFName]   = useState(baseName);
  const [fYaml,   setFYaml]   = useState(config);
  const [saving,  setSaving]  = useState(false);
  const [saveErr, setSaveErr] = useState('');

  useEffect(() => {
    if (showEdit) {
      setFName(baseName);
      setFYaml(config);
      setSaveErr('');
    }
  }, [showEdit]);

  const handleDelete = () => {
    Alert.alert(
      'Delete Middleware',
      `Delete "${baseName}"? This cannot be undone.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete', style: 'destructive',
          onPress: () => deleteMiddleware.mutate(baseName, {
            onError: (e) => Alert.alert('Error', e.message),
          }),
        },
      ],
    );
  };

  const handleSave = () => {
    if (!fName.trim()) { setSaveErr('Name is required'); return; }
    setSaving(true);
    setSaveErr('');
    saveMiddleware.mutate(
      { name: fName.trim(), content: fYaml.trim(), isEdit: true, originalId: baseName },
      {
        onSuccess: (res) => {
          setSaving(false);
          if (res.ok) { setShowEdit(false); }
          else { setSaveErr(res.message ?? 'Save failed'); }
        },
        onError: (e) => { setSaving(false); setSaveErr(e.message); },
      },
    );
  };

  return (
    <>
      <Surface style={[styles.card, { backgroundColor: c.card, borderColor: c.border }]} elevation={1}>
        {/* Header */}
        <View style={styles.header}>
          <View style={styles.badges}>
            <ProtocolBadge protocol={proto} />
            {!!type && <Badge label={type} color={tc} bg={tc + '18'} />}
          </View>
          <View style={styles.headerRight}>
            <TouchableOpacity onPress={() => setShowInfo(true)} hitSlop={8} style={styles.iconBtn}>
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
              <TouchableOpacity onPress={() => setShowEdit(true)} hitSlop={8} style={styles.iconBtn}>
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

      {/* ── Detail sheet ── */}
      <Modal visible={showInfo} transparent animationType="slide" onRequestClose={() => setShowInfo(false)}>
        <View style={styles.overlay}>
          <Pressable style={StyleSheet.absoluteFill} onPress={() => setShowInfo(false)} />
          <View style={[styles.sheet, { backgroundColor: c.card, borderColor: c.border, height: screenH * 0.75 }]}>
            <View style={[styles.handle, { backgroundColor: c.border }]} />
            <View style={[styles.sheetHeader, { borderBottomColor: c.border }]}>
              <View style={{ flex: 1 }}>
                <Text style={[styles.sheetTitle, { color: c.text }]}>{baseName}</Text>
                <View style={styles.badgeRow}>
                  <ProtocolBadge protocol={proto} />
                  {!!type && <Badge label={type} color={tc} bg={tc + '20'} />}
                  <Badge label={statusLabel[st]} color={statusColor[st]} bg={statusColor[st] + '20'} />
                </View>
              </View>
              <TouchableOpacity onPress={() => setShowInfo(false)} style={styles.closeBtn}>
                <Text style={[styles.closeTxt, { color: c.muted }]}>✕</Text>
              </TouchableOpacity>
            </View>
            <ScrollView style={styles.scroll} contentContainerStyle={styles.scrollContent} bounces={false}>
              <View style={[styles.field, { backgroundColor: c.bg }]}>
                <Text style={[styles.label, { color: c.muted }]}>FULL NAME</Text>
                <Text style={[styles.value, { color: c.text, fontFamily: 'monospace' }]}>{middleware.name}</Text>
              </View>
              <View style={[styles.field, { backgroundColor: c.bg }]}>
                <Text style={[styles.label, { color: c.muted }]}>PROVIDER</Text>
                <Text style={[styles.value, { color: c.text }]}>{provider}</Text>
              </View>
              {!!type && (
                <View style={[styles.field, { backgroundColor: c.bg }]}>
                  <Text style={[styles.label, { color: c.muted }]}>TYPE</Text>
                  <Text style={[styles.value, { color: tc }]}>{type}</Text>
                </View>
              )}
              {!!config && (
                <View style={[styles.field, { backgroundColor: c.bg }]}>
                  <Text style={[styles.label, { color: c.muted }]}>CONFIG</Text>
                  <Text style={[styles.value, { color: c.green, fontFamily: 'monospace' }]}>{config}</Text>
                </View>
              )}
            </ScrollView>
            <View style={[styles.footer, { borderTopColor: c.border }]}>
              <TouchableOpacity style={[styles.footerBtn, { backgroundColor: c.bg, borderColor: c.border }]} onPress={() => setShowInfo(false)}>
                <Text style={[styles.footerBtnTxt, { color: c.muted }]}>Close</Text>
              </TouchableOpacity>
            </View>
          </View>
        </View>
      </Modal>

      {/* ── Edit modal ── */}
      <Modal visible={showEdit} transparent animationType="slide" onRequestClose={() => setShowEdit(false)}>
        <View style={styles.overlay}>
          <Pressable style={StyleSheet.absoluteFill} onPress={() => setShowEdit(false)} />
          <View style={[styles.sheet, { backgroundColor: c.card, borderColor: c.border, bottom: editSheetBottom, maxHeight: screenH * 0.88 }]}>
            <View style={[styles.handle, { backgroundColor: c.border }]} />
            <View style={[styles.sheetHeader, { borderBottomColor: c.border }]}>
              <Text style={[styles.sheetTitle, { color: c.text, flex: 1 }]}>Edit Middleware</Text>
              <TouchableOpacity onPress={() => setShowEdit(false)} style={styles.closeBtn}>
                <Text style={[styles.closeTxt, { color: c.muted }]}>✕</Text>
              </TouchableOpacity>
            </View>

            <ScrollView style={styles.editScroll} contentContainerStyle={styles.editScrollContent} keyboardShouldPersistTaps="handled" bounces={false}>
              <View style={styles.formGroup}>
                <Text style={[styles.formLabel, { color: c.muted }]}>NAME</Text>
                <TextInput
                  style={[styles.formInput, { backgroundColor: c.bg, borderColor: c.border, color: c.text }]}
                  value={fName}
                  onChangeText={setFName}
                  autoCapitalize="none"
                  autoCorrect={false}
                  placeholder="middleware-name"
                  placeholderTextColor={c.muted}
                />
              </View>

              <View style={styles.formGroup}>
                <Text style={[styles.formLabel, { color: c.muted }]}>CONFIG (YAML)</Text>
                <TextInput
                  style={[styles.formInput, styles.yamlInput, { backgroundColor: c.bg, borderColor: c.border, color: c.green, fontFamily: 'monospace' }]}
                  value={fYaml}
                  onChangeText={setFYaml}
                  multiline
                  autoCapitalize="none"
                  autoCorrect={false}
                  placeholder={'redirectScheme:\n  scheme: https\n  permanent: true'}
                  placeholderTextColor={c.muted}
                  textAlignVertical="top"
                />
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

const styles = StyleSheet.create({
  card: {
    borderRadius: radius.md,
    borderWidth: 1,
    marginBottom: spacing.sm,
    overflow: 'hidden',
    padding: spacing.md,
    gap: 8,
  },
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  badges:      { flexDirection: 'row', gap: 6, flexWrap: 'wrap', flex: 1, alignItems: 'center' },
  headerRight: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  iconBtn:     { padding: 2 },
  name:        { fontSize: font.md, fontWeight: '700' },
  codeBlock:   { borderRadius: radius.sm, borderWidth: 1, padding: spacing.sm },
  codeText:    { fontSize: font.xs, fontFamily: 'monospace', lineHeight: 18 },
  // Sheet
  overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)' },
  sheet:   { position: 'absolute', left: 0, right: 0, borderTopLeftRadius: 20, borderTopRightRadius: 20, borderTopWidth: 1, flexDirection: 'column' },
  handle:  { width: 36, height: 4, borderRadius: 2, alignSelf: 'center', marginTop: 10, marginBottom: 4 },
  sheetHeader: {
    flexDirection: 'row', alignItems: 'flex-start',
    paddingHorizontal: spacing.lg, paddingTop: spacing.sm, paddingBottom: spacing.md,
    borderBottomWidth: 1,
  },
  sheetTitle:    { fontSize: font.lg, fontWeight: '800', marginBottom: 6 },
  badgeRow:      { flexDirection: 'row', alignItems: 'center', gap: 6, flexWrap: 'wrap' },
  closeBtn:      { padding: 6, marginLeft: spacing.sm },
  closeTxt:      { fontSize: font.md },
  scroll:        { flex: 1 },
  scrollContent: { padding: spacing.lg, gap: 8 },
  editScroll:        { flexShrink: 1 },
  editScrollContent: { padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xl },
  actionRow:   { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.sm },
  actionBtn:   { flex: 1, paddingVertical: 12, borderRadius: radius.sm, borderWidth: 1, alignItems: 'center' },
  actionBtnTxt:{ fontSize: font.sm, fontWeight: '600' },
  field:         { borderRadius: radius.sm, padding: spacing.sm },
  label:         { fontSize: font.xs, fontWeight: '700', letterSpacing: 0.5, marginBottom: 4 },
  value:         { fontSize: font.sm },
  footer:        { flexDirection: 'row', gap: spacing.sm, padding: spacing.lg, borderTopWidth: 1 },
  footerBtn:     { flex: 1, flexDirection: 'row', gap: 6, paddingVertical: 10, borderRadius: radius.sm, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  footerBtnTxt:  { fontSize: font.sm, fontWeight: '600' },
  // Form
  formGroup: { gap: 4 },
  formLabel: { fontSize: font.xs, fontWeight: '700', letterSpacing: 0.5 },
  formInput: { borderWidth: 1, borderRadius: radius.sm, paddingHorizontal: spacing.sm, paddingVertical: 9, fontSize: font.sm },
  yamlInput: { minHeight: 200, paddingTop: spacing.sm, fontSize: font.xs, lineHeight: 18 },
  errTxt:    { fontSize: font.sm, marginTop: 4 },
});
