import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import { Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { font, radius, spacing } from '../../src/theme';
import { useThemeStore } from '../../src/store/theme';
import { useMiddlewares, useDeleteMiddleware, useSaveMiddleware } from '../../src/hooks/useMiddlewares';
import { useConfigs } from '../../src/hooks/useConfigs';
import { ConfigFilePicker } from '../../src/components/ConfigFilePicker';
import { ProtocolBadge, Badge } from '../../src/components/StatusBadge';
import { providerOf } from '../../src/utils';

// ── config formatter ──────────────────────────────────────────────

const SKIP_KEYS = new Set(['name', 'type', 'status', 'provider', '_proto']);

function formatConfig(mw: Record<string, unknown>): string {
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
          return typeof v === 'object' && v !== null && !Array.isArray(v)
            ? `\n${pad}${k}:${rendered}`
            : `\n${pad}${k}: ${rendered}`;
        }).join('');
    }
    return String(val);
  }
  return entries.map(([k, v]) =>
    typeof v === 'object' && v !== null && !Array.isArray(v)
      ? `${k}:${toYaml(v, 1)}`
      : `${k}: ${toYaml(v)}`
  ).join('\n');
}

const TYPE_COLORS: Record<string, string> = {
  redirectscheme: 'blue', stripprefix: 'purple', headers: 'orange',
  basicauth: 'yellow', forwardauth: 'green', ratelimit: 'red',
  compress: 'muted', chain: 'blue', redirectregex: 'blue',
};

// ── sub-components ────────────────────────────────────────────────

type Colors = ReturnType<typeof useThemeStore.getState>['colors'];

function Section({ icon, title, children, c }: {
  icon: string; title: string; children: React.ReactNode; c: Colors;
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

// ── main screen ───────────────────────────────────────────────────

export default function MiddlewareDetailScreen() {
  const { id, edit } = useLocalSearchParams<{ id: string; edit?: string }>();
  const router       = useRouter();
  const insets       = useSafeAreaInsets();
  const c            = useThemeStore(s => s.colors);

  const { data }       = useMiddlewares();
  const deleteMiddleware = useDeleteMiddleware();
  const saveMiddleware   = useSaveMiddleware();
  const configs          = useConfigs();
  const configFiles      = configs.data?.files ?? [];
  const configDirSet     = configs.data?.configDirSet ?? false;
  const showConfigPicker = configFiles.length > 1 || configDirSet;

  const decodedName  = decodeURIComponent(id ?? '');
  const middleware   = data?.find(m => m.name === decodedName);

  const baseName   = middleware ? (middleware.name.includes('@') ? middleware.name.split('@')[0] : middleware.name) : decodedName;
  const provider   = middleware ? (middleware.provider ?? providerOf(middleware.name)) : '';
  const proto      = middleware?._proto ?? 'http';
  const type       = middleware?.type ?? '';
  const tkey       = TYPE_COLORS[(type || '').toLowerCase()] ?? 'muted';
  const tc         = c[tkey as keyof typeof c] as string;
  const config     = middleware ? formatConfig(middleware as Record<string, unknown>) : '';

  const statusColor = { ok: c.green, warn: c.orange, err: c.red };
  const statusLabel = { ok: 'Enabled', warn: 'Warning', err: 'Error' };
  const st = (() => {
    const l = ((middleware?.status) || '').toLowerCase();
    if (l === 'enabled' || l === 'success') return 'ok' as const;
    if (l === 'warning' || l === 'warn')    return 'warn' as const;
    return 'err' as const;
  })();

  const [editMode,    setEditMode]    = useState(edit === '1');
  const [fName,       setFName]       = useState('');
  const [fYaml,       setFYaml]       = useState('');
  const [fConfigFile, setFConfigFile] = useState('');
  const [saving,      setSaving]      = useState(false);
  const [saveErr,     setSaveErr]     = useState('');

  const populateForm = () => {
    setFName(baseName);
    setFYaml(config);
    setFConfigFile(middleware?.configFile || configFiles[0]?.label || '');
    setSaveErr('');
  };

  useEffect(() => {
    if (middleware && editMode) populateForm();
  }, [editMode]);

  useEffect(() => {
    if (middleware) populateForm();
  }, [middleware?.name]);

  const handleDelete = () => {
    if (!middleware) return;
    Alert.alert(
      'Delete Middleware',
      `Delete "${baseName}"? This cannot be undone.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Delete', style: 'destructive',
          onPress: () => deleteMiddleware.mutate(
            { name: baseName },
            { onSuccess: () => router.back(), onError: (e) => Alert.alert('Error', e.message) },
          ),
        },
      ],
    );
  };

  const handleSave = () => {
    if (!fName.trim()) { setSaveErr('Name is required'); return; }
    setSaving(true);
    setSaveErr('');
    saveMiddleware.mutate(
      { name: fName.trim(), content: fYaml.trim(), isEdit: true, originalId: baseName, configFile: fConfigFile },
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
  if (data && !middleware) {
    return (
      <View style={[styles.screen, { backgroundColor: c.bg, paddingTop: insets.top }]}>
        <View style={[styles.headerBar, { borderBottomColor: c.border, backgroundColor: c.card }]}>
          <TouchableOpacity onPress={() => router.back()} hitSlop={12} style={styles.headerBtn}>
            <MaterialCommunityIcons name="close" size={22} color={c.text} />
          </TouchableOpacity>
          <Text style={[styles.headerTitle, { color: c.text }]}>Middleware</Text>
          <View style={styles.headerBtn} />
        </View>
        <View style={styles.centered}>
          <Text style={[styles.rowText, { color: c.muted }]}>Middleware not found.</Text>
        </View>
      </View>
    );
  }

  if (!middleware) {
    return (
      <View style={[styles.screen, { backgroundColor: c.bg, paddingTop: insets.top }]}>
        <View style={styles.centered}><ActivityIndicator color={c.blue} /></View>
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
          {editMode ? 'Edit Middleware' : baseName}
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
                disabled={deleteMiddleware.isPending}
              >
                {deleteMiddleware.isPending
                  ? <ActivityIndicator size="small" color={c.red} />
                  : <MaterialCommunityIcons name="trash-can-outline" size={20} color={c.red} />}
              </TouchableOpacity>
              <TouchableOpacity onPress={() => setEditMode(true)} hitSlop={8} style={styles.headerIconBtn}>
                <MaterialCommunityIcons name="pencil-outline" size={20} color={c.muted} />
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

            {showConfigPicker && (
              <View style={styles.formGroup}>
                <Text style={[styles.formLabel, { color: c.muted }]}>CONFIG FILE</Text>
                <ConfigFilePicker
                  files={configFiles}
                  configDirSet={configDirSet}
                  value={fConfigFile}
                  onChange={setFConfigFile}
                  c={c}
                />
              </View>
            )}

            {!!saveErr && <Text style={[styles.errTxt, { color: c.red }]}>{saveErr}</Text>}
          </ScrollView>
        </KeyboardAvoidingView>
      ) : (
        <ScrollView
          style={{ flex: 1 }}
          contentContainerStyle={[styles.scrollContent, { paddingBottom: insets.bottom + 24 }]}
        >
          {/* Badge row */}
          <View style={styles.badgeRow}>
            <ProtocolBadge protocol={proto} />
            {!!type && <Badge label={type} color={tc} bg={tc + '20'} />}
            <Badge label={statusLabel[st]} color={statusColor[st]} bg={statusColor[st] + '20'} />
          </View>

          {/* Middleware Details */}
          <Section icon="information-outline" title="MIDDLEWARE DETAILS" c={c}>
            <Row label="FULL NAME" isLast={false} c={c}
              value={<Text style={[styles.rowText, { color: c.text, fontFamily: 'monospace' }]}>{middleware.name}</Text>} />
            <Row label="PROVIDER" isLast={false} c={c}
              value={<Chip label={provider} c={c} />} />
            {!!type && (
              <Row label="TYPE" isLast={false} c={c}
                value={<Text style={[styles.rowText, { color: tc }]}>{type}</Text>} />
            )}
            <Row label="STATUS" isLast c={c}
              value={
                <View style={styles.statusVal}>
                  <View style={[styles.statusDot, { backgroundColor: statusColor[st] }]} />
                  <Text style={[styles.rowText, { color: statusColor[st], fontWeight: '600' }]}>{statusLabel[st]}</Text>
                </View>
              }
            />
          </Section>

          {/* Config */}
          {!!config && (
            <Section icon="code-braces" title="CONFIG" c={c}>
              <View style={[styles.codeWrap, { backgroundColor: c.bg }]}>
                <Text style={[styles.codeText, { color: c.green }]}>{config}</Text>
              </View>
            </Section>
          )}
        </ScrollView>
      )}
    </View>
  );
}

// ── styles ────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  screen:   { flex: 1 },
  centered: { flex: 1, alignItems: 'center', justifyContent: 'center' },
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
  statusVal: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  // Sections
  section:      { gap: 6 },
  sectionHeader:{ flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 2 },
  sectionTitle: { fontSize: font.xs, fontWeight: '800', letterSpacing: 0.8, textTransform: 'uppercase' as const, flex: 1 },
  sectionBody:  { borderRadius: radius.md, borderWidth: 1, overflow: 'hidden' as const },
  // Rows
  row:      { flexDirection: 'row', alignItems: 'center', paddingHorizontal: spacing.md, paddingVertical: 11, gap: spacing.sm },
  rowLabel: { fontSize: font.xs, fontWeight: '700', letterSpacing: 0.5, width: 120 },
  rowValue: { flex: 1 },
  rowText:  { fontSize: font.sm },
  // Chips
  chip:     { paddingHorizontal: 8, paddingVertical: 3, borderRadius: radius.full, borderWidth: 1, alignSelf: 'flex-start' as const },
  chipText: { fontSize: font.xs, fontWeight: '600' },
  // Code block
  codeWrap: { padding: spacing.md },
  codeText: { fontSize: font.xs, fontFamily: 'monospace', lineHeight: 18 },
  // Form
  formGroup:     { gap: 4 },
  formLabel:     { fontSize: font.xs, fontWeight: '700', letterSpacing: 0.5 },
  formInput:     { borderWidth: 1, borderRadius: radius.sm, paddingHorizontal: spacing.sm, paddingVertical: 9, fontSize: font.sm },
  yamlInput:     { minHeight: 220, paddingTop: spacing.sm, fontSize: font.xs, lineHeight: 18 },
  toggleRow:     { flexDirection: 'row', gap: spacing.sm, flexWrap: 'wrap' },
  toggleBtn:     { flex: 1, paddingVertical: 8, borderRadius: radius.sm, borderWidth: 1, alignItems: 'center', minWidth: 60 },
  toggleBtnTxt:  { fontSize: font.sm, fontWeight: '700' },
  errTxt:        { fontSize: font.sm },
});
