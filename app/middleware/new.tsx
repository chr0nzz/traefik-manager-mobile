import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  View,
} from 'react-native';
import { Button, Surface, Text, TextInput } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { font, radius, spacing } from '../../src/theme';
import { useThemeStore } from '../../src/store/theme';
import { useSaveMiddleware } from '../../src/hooks/useMiddlewares';
import { useConfigs } from '../../src/hooks/useConfigs';
import { ConfigFilePicker } from '../../src/components/ConfigFilePicker';

interface Template {
  id: string; name: string; icon: string; description: string; yaml: string;
}

const TEMPLATES: Template[] = [
  { id: 'blank', name: 'Blank', icon: 'file-outline', description: 'Start from scratch', yaml: '' },
  { id: 'https-redirect', name: 'HTTPS Redirect', icon: 'lock-outline', description: 'Redirect HTTP to HTTPS', yaml: 'redirectScheme:\n  scheme: https\n  permanent: true' },
  { id: 'basic-auth', name: 'Basic Auth', icon: 'account-key-outline', description: 'Password protect your service', yaml: 'basicAuth:\n  users:\n    - "user:$apr1$replace_me" # htpasswd format\n  realm: "Authentication Required"' },
  { id: 'security-headers', name: 'Security Headers', icon: 'shield-check-outline', description: 'Add HSTS and security headers', yaml: 'headers:\n  stsSeconds: 31536000\n  stsIncludeSubdomains: true\n  stsPreload: true\n  forceSTSHeader: true\n  contentTypeNosniff: true\n  browserXssFilter: true\n  frameDeny: true' },
  { id: 'rate-limit', name: 'Rate Limit', icon: 'speedometer', description: 'Limit request rate per source IP', yaml: 'rateLimit:\n  average: 100\n  burst: 50\n  period: 1s' },
  { id: 'forward-auth', name: 'Forward Auth', icon: 'shield-account-outline', description: 'Delegate auth to external service', yaml: 'forwardAuth:\n  address: "http://auth-service:9000/verify"\n  trustForwardHeader: true\n  authResponseHeaders:\n    - X-Auth-User\n    - X-Auth-Role' },
  { id: 'strip-prefix', name: 'Strip Prefix', icon: 'scissors-cutting', description: 'Remove a URL path prefix', yaml: 'stripPrefix:\n  prefixes:\n    - "/api"' },
  { id: 'add-prefix', name: 'Add Prefix', icon: 'plus-box-outline', description: 'Prepend a URL path prefix', yaml: 'addPrefix:\n  prefix: "/api"' },
  { id: 'compress', name: 'Compress', icon: 'zip-box-outline', description: 'Enable gzip / brotli compression', yaml: 'compress: {}' },
  { id: 'ip-allowlist', name: 'IP Allowlist', icon: 'ip-network-outline', description: 'Restrict access by IP range', yaml: 'ipAllowList:\n  sourceRange:\n    - "10.0.0.0/8"\n    - "172.16.0.0/12"\n    - "192.168.0.0/16"' },
  { id: 'redirect-regex', name: 'Redirect Regex', icon: 'arrow-decision-outline', description: 'Redirect using a regex pattern', yaml: 'redirectRegex:\n  regex: "^http://(.*)"\n  replacement: "https://${1}"\n  permanent: true' },
  { id: 'chain', name: 'Chain', icon: 'link-variant', description: 'Combine multiple middlewares', yaml: 'chain:\n  middlewares:\n    - middleware1@file\n    - middleware2@file' },
];

const TEMPLATE_COLORS: Record<string, string> = {
  'blank': 'muted', 'https-redirect': 'blue', 'basic-auth': 'yellow',
  'security-headers': 'green', 'rate-limit': 'red', 'forward-auth': 'purple',
  'strip-prefix': 'orange', 'add-prefix': 'orange', 'compress': 'muted',
  'ip-allowlist': 'blue', 'redirect-regex': 'blue', 'chain': 'purple',
};

type Colors = ReturnType<typeof useThemeStore.getState>['colors'];

export default function NewMiddlewareScreen() {
  const router  = useRouter();
  const insets  = useSafeAreaInsets();
  const c       = useThemeStore(s => s.colors);

  const saveMiddleware = useSaveMiddleware();
  const configs        = useConfigs();
  const configFiles      = configs.data?.files ?? [];
  const configDirSet     = configs.data?.configDirSet ?? false;
  const showConfigPicker = configFiles.length > 1 || configDirSet;

  const [step,        setStep]        = useState<'pick' | 'form'>('pick');
  const [fName,       setFName]       = useState('');
  const [fYaml,       setFYaml]       = useState('');
  const [fConfigFile, setFConfigFile] = useState('');
  const [saving,      setSaving]      = useState(false);
  const [saveErr,     setSaveErr]     = useState('');


  const selectTemplate = (t: Template) => {
    setFYaml(t.yaml);
    setStep('form');
  };

  const handleSave = () => {
    if (!fName.trim()) { setSaveErr('Name is required'); return; }
    setSaving(true);
    setSaveErr('');
    saveMiddleware.mutate(
      { name: fName.trim(), content: fYaml.trim(), isEdit: false, originalId: '', configFile: fConfigFile },
      {
        onSuccess: (res) => {
          setSaving(false);
          if (res.ok) router.back();
          else setSaveErr(res.message ?? 'Save failed');
        },
        onError: (e) => { setSaving(false); setSaveErr(e.message); },
      },
    );
  };

  // ── Step 1: Template picker ──────────────────────────────────────
  if (step === 'pick') {
    return (
      <View style={[styles.screen, { backgroundColor: c.bg, paddingTop: insets.top }]}>
        <View style={[styles.headerBar, { borderBottomColor: c.border, backgroundColor: c.card }]}>
          <TouchableOpacity onPress={() => router.back()} hitSlop={12} style={styles.headerBtn}>
            <MaterialCommunityIcons name="chevron-down" size={26} color={c.text} />
          </TouchableOpacity>
          <Text style={[styles.headerTitle, { color: c.text }]}>Choose Template</Text>
          <View style={styles.headerBtn} />
        </View>
        <ScrollView contentContainerStyle={[styles.templateList, { paddingBottom: insets.bottom + 24 }]}>
          {TEMPLATES.map(t => {
            const tcolorKey = TEMPLATE_COLORS[t.id] ?? 'muted';
            const tcolor    = c[tcolorKey as keyof typeof c] as string;
            return (
              <TouchableOpacity
                key={t.id}
                style={[styles.templateRow, { backgroundColor: c.card }]}
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
    );
  }

  // ── Step 2: Form ─────────────────────────────────────────────────
  return (
    <View style={[styles.screen, { backgroundColor: c.bg, paddingTop: insets.top }]}>
      <View style={[styles.headerBar, { borderBottomColor: c.border, backgroundColor: c.card }]}>
        <TouchableOpacity onPress={() => { setStep('pick'); setSaveErr(''); }} hitSlop={12} style={styles.headerBtn}>
          <MaterialCommunityIcons name="arrow-left" size={22} color={c.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: c.text }]}>New Middleware</Text>
        <View style={styles.headerActions}>
          <Button
            mode="contained"
            onPress={handleSave}
            loading={saving}
            disabled={saving}
            compact
          >
            Create
          </Button>
        </View>
      </View>

      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        <ScrollView
          style={{ flex: 1 }}
          contentContainerStyle={[styles.scrollContent, { paddingBottom: insets.bottom + 24 }]}
          keyboardShouldPersistTaps="handled"
        >
          <TextInput
            label="Name"
            value={fName}
            onChangeText={setFName}
            autoCapitalize="none"
            autoCorrect={false}
            placeholder="my-middleware"
            autoFocus
            mode="outlined"
            style={{ backgroundColor: c.bg }}
          />

          <TextInput
            label="Config (YAML)"
            value={fYaml}
            onChangeText={setFYaml}
            multiline
            numberOfLines={10}
            autoCapitalize="none"
            autoCorrect={false}
            placeholder={'redirectScheme:\n  scheme: https\n  permanent: true'}
            mode="outlined"
            style={{ backgroundColor: c.bg, fontFamily: 'monospace', minHeight: 200 }}
          />

          {showConfigPicker && (
            <View style={styles.formGroup}>
              <Text style={{ fontSize: 12, fontWeight: '500', color: c.muted, marginBottom: 4 }}>Config File</Text>
              <ConfigFilePicker
                files={configFiles}
                configDirSet={configDirSet}
                value={fConfigFile}
                onChange={setFConfigFile}
                allowNew
                c={c}
              />
            </View>
          )}

          {!!saveErr && <Text style={[styles.errTxt, { color: c.red }]}>{saveErr}</Text>}
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen:  { flex: 1 },
  // Header
  headerBar: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: spacing.md, paddingVertical: spacing.sm,
    borderBottomWidth: 1, gap: spacing.sm,
  },
  headerBtn:       { width: 36, alignItems: 'flex-start' },
  headerTitle:     { flex: 1, fontSize: font.lg, fontWeight: '700', textAlign: 'center' },
  headerActions:   { flexDirection: 'row', alignItems: 'center', gap: 6 },
  // Templates
  templateList: { padding: spacing.md, gap: spacing.sm },
  templateRow: {
    flexDirection: 'row', alignItems: 'center', gap: spacing.md,
    borderRadius: radius.md,
    paddingHorizontal: spacing.md, paddingVertical: spacing.md,
  },
  templateIcon: { width: 44, height: 44, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center' },
  templateText: { flex: 1, gap: 2 },
  templateName: { fontSize: font.sm, fontWeight: '700' },
  templateDesc: { fontSize: font.xs },
  // Form
  scrollContent: { padding: spacing.md, gap: spacing.md },
  errTxt:        { fontSize: font.sm },
});
