import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
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
import { Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { font, radius, spacing } from '../theme';
import { useThemeStore } from '../store/theme';
import { useSaveMiddleware } from '../hooks/useMiddlewares';
import { useKeyboardHeight } from '../hooks/useKeyboardHeight';

interface Props {
  visible: boolean;
  onClose: () => void;
}

interface Template {
  id: string;
  name: string;
  icon: string;
  description: string;
  yaml: string;
}

const TEMPLATES: Template[] = [
  {
    id: 'blank',
    name: 'Blank',
    icon: 'file-outline',
    description: 'Start from scratch',
    yaml: '',
  },
  {
    id: 'https-redirect',
    name: 'HTTPS Redirect',
    icon: 'lock-outline',
    description: 'Redirect HTTP to HTTPS',
    yaml: 'redirectScheme:\n  scheme: https\n  permanent: true',
  },
  {
    id: 'basic-auth',
    name: 'Basic Auth',
    icon: 'account-key-outline',
    description: 'Password protect your service',
    yaml: 'basicAuth:\n  users:\n    - "user:$apr1$replace_me" # htpasswd format\n  realm: "Authentication Required"',
  },
  {
    id: 'security-headers',
    name: 'Security Headers',
    icon: 'shield-check-outline',
    description: 'Add HSTS and security headers',
    yaml: 'headers:\n  stsSeconds: 31536000\n  stsIncludeSubdomains: true\n  stsPreload: true\n  forceSTSHeader: true\n  contentTypeNosniff: true\n  browserXssFilter: true\n  frameDeny: true',
  },
  {
    id: 'rate-limit',
    name: 'Rate Limit',
    icon: 'speedometer',
    description: 'Limit request rate per source IP',
    yaml: 'rateLimit:\n  average: 100\n  burst: 50\n  period: 1s',
  },
  {
    id: 'forward-auth',
    name: 'Forward Auth',
    icon: 'shield-account-outline',
    description: 'Delegate auth to external service',
    yaml: 'forwardAuth:\n  address: "http://auth-service:9000/verify"\n  trustForwardHeader: true\n  authResponseHeaders:\n    - X-Auth-User\n    - X-Auth-Role',
  },
  {
    id: 'strip-prefix',
    name: 'Strip Prefix',
    icon: 'scissors-cutting',
    description: 'Remove a URL path prefix',
    yaml: 'stripPrefix:\n  prefixes:\n    - "/api"',
  },
  {
    id: 'add-prefix',
    name: 'Add Prefix',
    icon: 'plus-box-outline',
    description: 'Prepend a URL path prefix',
    yaml: 'addPrefix:\n  prefix: "/api"',
  },
  {
    id: 'compress',
    name: 'Compress',
    icon: 'zip-box-outline',
    description: 'Enable gzip / brotli compression',
    yaml: 'compress: {}',
  },
  {
    id: 'ip-allowlist',
    name: 'IP Allowlist',
    icon: 'ip-network-outline',
    description: 'Restrict access by IP range',
    yaml: 'ipAllowList:\n  sourceRange:\n    - "10.0.0.0/8"\n    - "172.16.0.0/12"\n    - "192.168.0.0/16"',
  },
  {
    id: 'redirect-regex',
    name: 'Redirect Regex',
    icon: 'arrow-decision-outline',
    description: 'Redirect using a regex pattern',
    yaml: 'redirectRegex:\n  regex: "^http://(.*)"\n  replacement: "https://${1}"\n  permanent: true',
  },
  {
    id: 'chain',
    name: 'Chain',
    icon: 'link-variant',
    description: 'Combine multiple middlewares',
    yaml: 'chain:\n  middlewares:\n    - middleware1@file\n    - middleware2@file',
  },
];

const TEMPLATE_COLORS: Record<string, string> = {
  'blank':           'muted',
  'https-redirect':  'blue',
  'basic-auth':      'yellow',
  'security-headers':'green',
  'rate-limit':      'red',
  'forward-auth':    'purple',
  'strip-prefix':    'orange',
  'add-prefix':      'orange',
  'compress':        'muted',
  'ip-allowlist':    'blue',
  'redirect-regex':  'blue',
  'chain':           'purple',
};

export function AddMiddlewareModal({ visible, onClose }: Props) {
  const c                   = useThemeStore(s => s.colors);
  const { height: screenH } = useWindowDimensions();
  const kbHeight            = useKeyboardHeight();
  const saveMiddleware      = useSaveMiddleware();

  const [step,    setStep]    = useState<'pick' | 'form'>('pick');
  const [fName,   setFName]   = useState('');
  const [fYaml,   setFYaml]   = useState('');
  const [saving,  setSaving]  = useState(false);
  const [saveErr, setSaveErr] = useState('');

  useEffect(() => {
    if (visible) {
      setStep('pick');
      setFName(''); setFYaml(''); setSaveErr('');
    }
  }, [visible]);

  const selectTemplate = (t: Template) => {
    setFYaml(t.yaml);
    setStep('form');
  };

  const handleBack = () => {
    setStep('pick');
    setSaveErr('');
  };

  const handleSave = () => {
    if (!fName.trim()) { setSaveErr('Name is required'); return; }
    setSaving(true);
    setSaveErr('');
    saveMiddleware.mutate(
      { name: fName.trim(), content: fYaml.trim(), isEdit: false, originalId: '' },
      {
        onSuccess: (res) => {
          setSaving(false);
          if (res.ok) { onClose(); }
          else { setSaveErr(res.message ?? 'Save failed'); }
        },
        onError: (e) => { setSaving(false); setSaveErr(e.message); },
      },
    );
  };

  // On iOS offset above keyboard manually; Android adjustResize handles it.
  const sheetBottom = Platform.OS === 'ios' ? kbHeight : 0;

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <Pressable style={StyleSheet.absoluteFill} onPress={onClose} />

        {step === 'pick' ? (
          /* ── Step 1: Template picker ── */
          <View style={[styles.sheet, { backgroundColor: c.bg, bottom: sheetBottom, maxHeight: screenH * 0.88 }]}>
            <View style={[styles.handle, { backgroundColor: c.border }]} />
            <View style={[styles.sheetHeader, { borderBottomColor: c.border, backgroundColor: c.card }]}>
              <MaterialCommunityIcons name="lightning-bolt-outline" size={20} color={c.purple} />
              <Text style={[styles.sheetTitle, { color: c.text }]}>Choose Template</Text>
              <TouchableOpacity onPress={onClose} hitSlop={12} style={{ marginLeft: 'auto' }}>
                <MaterialCommunityIcons name="close" size={20} color={c.muted} />
              </TouchableOpacity>
            </View>

            <ScrollView contentContainerStyle={styles.templateList} bounces={false}>
              {TEMPLATES.map(t => {
                const tcolorKey = TEMPLATE_COLORS[t.id] ?? 'muted';
                const tcolor    = c[tcolorKey as keyof typeof c] as string;
                return (
                  <TouchableOpacity
                    key={t.id}
                    style={[styles.templateRow, { backgroundColor: c.card, borderColor: c.border }]}
                    onPress={() => selectTemplate(t)}
                    activeOpacity={0.7}
                  >
                    <View style={[styles.templateIcon, { backgroundColor: tcolor + '18' }]}>
                      <MaterialCommunityIcons name={t.icon as any} size={22} color={tcolor} />
                    </View>
                    <View style={styles.templateText}>
                      <Text style={[styles.templateName, { color: c.text }]}>{t.name}</Text>
                      <Text style={[styles.templateDesc, { color: c.muted }]}>{t.description}</Text>
                    </View>
                    <MaterialCommunityIcons name="chevron-right" size={18} color={c.border} />
                  </TouchableOpacity>
                );
              })}
            </ScrollView>
          </View>

        ) : (
          /* ── Step 2: Form ── */
          <View style={[styles.sheet, { backgroundColor: c.card, borderColor: c.border, bottom: sheetBottom, maxHeight: screenH * 0.88 }]}>
            <View style={[styles.handle, { backgroundColor: c.border }]} />
            <View style={[styles.sheetHeader, { borderBottomColor: c.border, backgroundColor: c.card }]}>
              <TouchableOpacity onPress={handleBack} hitSlop={8}>
                <MaterialCommunityIcons name="arrow-left" size={20} color={c.muted} />
              </TouchableOpacity>
              <Text style={[styles.sheetTitle, { color: c.text }]}>New Middleware</Text>
              <TouchableOpacity onPress={onClose} hitSlop={12} style={{ marginLeft: 'auto' }}>
                <MaterialCommunityIcons name="close" size={20} color={c.muted} />
              </TouchableOpacity>
            </View>

            <ScrollView
              style={styles.scroll}
              contentContainerStyle={styles.scrollContent}
              keyboardShouldPersistTaps="handled"
              bounces={false}
            >
              <View style={styles.formGroup}>
                <Text style={[styles.formLabel, { color: c.muted }]}>NAME</Text>
                <TextInput
                  style={[styles.formInput, { backgroundColor: c.bg, borderColor: c.border, color: c.text }]}
                  value={fName}
                  onChangeText={setFName}
                  autoCapitalize="none"
                  autoCorrect={false}
                  placeholder="my-middleware"
                  placeholderTextColor={c.muted}
                  autoFocus
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
                  style={[styles.actionBtn, { backgroundColor: c.purple, borderColor: c.purple }]}
                  onPress={handleSave}
                  disabled={saving}
                >
                  {saving
                    ? <ActivityIndicator size="small" color="#fff" />
                    : <Text style={[styles.actionBtnTxt, { color: '#fff' }]}>Create Middleware</Text>}
                </TouchableOpacity>
                <TouchableOpacity style={[styles.actionBtn, { backgroundColor: c.bg, borderColor: c.border }]} onPress={handleBack}>
                  <Text style={[styles.actionBtnTxt, { color: c.muted }]}>Back</Text>
                </TouchableOpacity>
              </View>
            </ScrollView>
          </View>
        )}
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.55)' },
  sheet: {
    position: 'absolute', left: 0, right: 0,
    borderTopLeftRadius: 20, borderTopRightRadius: 20, overflow: 'hidden',
  },
  handle: { width: 36, height: 4, borderRadius: 2, alignSelf: 'center', marginTop: 10, marginBottom: 4 },
  sheetHeader: {
    flexDirection: 'row', alignItems: 'center', gap: spacing.sm,
    paddingHorizontal: spacing.lg, paddingVertical: spacing.md,
    borderBottomWidth: 1,
  },
  sheetTitle: { fontSize: font.lg, fontWeight: '700' },
  // Template list
  templateList: { padding: spacing.md, gap: spacing.sm, paddingBottom: 40 },
  templateRow: {
    flexDirection: 'row', alignItems: 'center', gap: spacing.md,
    borderRadius: radius.md, borderWidth: 1,
    paddingHorizontal: spacing.md, paddingVertical: spacing.md,
  },
  templateIcon: { width: 44, height: 44, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center' },
  templateText: { flex: 1, gap: 2 },
  templateName: { fontSize: font.sm, fontWeight: '700' },
  templateDesc: { fontSize: font.xs },
  // Form
  scroll:        { flexShrink: 1 },
  scrollContent: { padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xl },
  formGroup:   { gap: 4 },
  formLabel:   { fontSize: font.xs, fontWeight: '700', letterSpacing: 0.5 },
  formInput:   { borderWidth: 1, borderRadius: radius.sm, paddingHorizontal: spacing.sm, paddingVertical: 9, fontSize: font.sm },
  yamlInput:   { minHeight: 180, paddingTop: spacing.sm, fontSize: font.xs, lineHeight: 18 },
  errTxt:      { fontSize: font.sm, marginTop: 4 },
  actionRow:   { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.sm },
  actionBtn:   { flex: 1, paddingVertical: 12, borderRadius: radius.sm, borderWidth: 1, alignItems: 'center' },
  actionBtnTxt:{ fontSize: font.sm, fontWeight: '600' },
});
