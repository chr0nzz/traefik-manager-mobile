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
import { MiddlewareWizard, WIZARD_TEMPLATES } from '../../src/components/MiddlewareWizard';

interface Template {
  id: string; name: string; icon: string; description: string; yaml: string;
}

const TEMPLATES: Template[] = [
  { id: 'blank', name: 'Blank', icon: 'file-outline', description: 'Start from scratch', yaml: '' },
  { id: 'https-redirect', name: 'HTTPS Redirect', icon: 'lock-outline', description: 'Redirect HTTP to HTTPS', yaml: 'redirectScheme:\n  scheme: https\n  permanent: true' },
  { id: 'basic-auth', name: 'Basic Auth', icon: 'account-key-outline', description: 'Password protect your service', yaml: 'basicAuth:\n  users: []\n  realm: "Authentication Required"' },
  { id: 'digest-auth', name: 'Digest Auth', icon: 'key-outline', description: 'MD5 digest authentication', yaml: 'digestAuth:\n  users:\n    - "user:realm:hash"' },
  { id: 'security-headers', name: 'Security Headers', icon: 'shield-check-outline', description: 'Add HSTS and security headers', yaml: 'headers:\n  forceSTSHeader: true\n  stsSeconds: 315360000\n  stsIncludeSubdomains: true\n  stsPreload: true\n  contentTypeNosniff: true\n  browserXssFilter: true\n  frameDeny: true\n  referrerPolicy: "strict-origin-when-cross-origin"' },
  { id: 'rate-limit', name: 'Rate Limit', icon: 'speedometer', description: 'Limit request rate per source IP', yaml: 'rateLimit:\n  average: 100\n  burst: 50\n  period: 1s' },
  { id: 'forward-auth', name: 'Forward Auth', icon: 'shield-account-outline', description: 'Delegate auth to external service', yaml: 'forwardAuth:\n  address: "http://auth-service:9000/verify"\n  trustForwardHeader: true\n  authResponseHeaders:\n    - X-Auth-User\n    - X-Auth-Role' },
  { id: 'forward-auth-authentik', name: 'Authentik', icon: 'shield-account', description: 'Authentik SSO forward auth', yaml: 'forwardAuth:\n  address: "http://authentik-server:9000/outpost.goauthentik.io/auth/traefik"\n  trustForwardHeader: true\n  authResponseHeaders:\n    - X-authentik-username\n    - X-authentik-groups\n    - X-authentik-email\n    - X-authentik-name' },
  { id: 'forward-auth-authelia', name: 'Authelia', icon: 'shield-lock', description: 'Authelia forward auth', yaml: 'forwardAuth:\n  address: "http://authelia:9091/api/authz/forward-auth"\n  trustForwardHeader: true\n  authResponseHeaders:\n    - Remote-User\n    - Remote-Name\n    - Remote-Groups\n    - Remote-Email' },
  { id: 'forward-auth-gatekeeper', name: 'Gatekeeper', icon: 'gate', description: 'Gatekeeper forward auth', yaml: 'forwardAuth:\n  address: "https://auth.example.com/auth/verify"\n  trustForwardHeader: false\n  authResponseHeaders:\n    - X-Auth-User\n    - X-Auth-Email' },
  { id: 'ip-allowlist', name: 'IP Allowlist', icon: 'ip-network-outline', description: 'Restrict access by IP range', yaml: 'ipAllowList:\n  sourceRange:\n    - "10.0.0.0/8"\n    - "172.16.0.0/12"\n    - "192.168.0.0/16"' },
  { id: 'ip-allowlist-private', name: 'Private IPs', icon: 'home-network-outline', description: 'Allow LAN / private IP ranges', yaml: 'ipAllowList:\n  sourceRange:\n    - "10.0.0.0/8"\n    - "172.16.0.0/12"\n    - "192.168.0.0/16"\n    - "127.0.0.1/32"' },
  { id: 'cors-headers', name: 'CORS', icon: 'web', description: 'Cross-origin resource sharing headers', yaml: 'headers:\n  accessControlAllowMethods:\n    - GET\n    - POST\n    - PUT\n    - DELETE\n    - PATCH\n    - OPTIONS\n  accessControlAllowOriginList:\n    - "*"\n  accessControlAllowHeaders:\n    - "*"\n  accessControlMaxAge: 100\n  addVaryHeader: true' },
  { id: 'redirect-regex', name: 'Redirect Regex', icon: 'arrow-decision-outline', description: 'Redirect using a regex pattern', yaml: 'redirectRegex:\n  regex: "^http://(.*)"\n  replacement: "https://${1}"\n  permanent: true' },
  { id: 'strip-prefix', name: 'Strip Prefix', icon: 'scissors-cutting', description: 'Remove a URL path prefix', yaml: 'stripPrefix:\n  prefixes:\n    - "/api"\n    - "/v1"' },
  { id: 'add-prefix', name: 'Add Prefix', icon: 'plus-box-outline', description: 'Prepend a URL path prefix', yaml: 'addPrefix:\n  prefix: "/api"' },
  { id: 'replace-path', name: 'Replace Path', icon: 'find-replace', description: 'Replace request URL path', yaml: 'replacePath:\n  path: "/foo"' },
  { id: 'compress', name: 'Compress', icon: 'zip-box-outline', description: 'Enable gzip / brotli compression', yaml: 'compress:\n  minResponseBodyBytes: 1200' },
  { id: 'retry', name: 'Retry', icon: 'refresh', description: 'Retry failed requests', yaml: 'retry:\n  attempts: 4\n  initialInterval: "100ms"' },
  { id: 'circuit-breaker', name: 'Circuit Breaker', icon: 'electric-switch-closed', description: 'Stop traffic when error rate is high', yaml: 'circuitBreaker:\n  expression: "NetworkErrorRatio() > 0.5"' },
  { id: 'buffering', name: 'Buffering', icon: 'buffer', description: 'Buffer request and response bodies', yaml: 'buffering:\n  maxRequestBodyBytes: 10485760\n  maxResponseBodyBytes: 10485760' },
  { id: 'in-flight-req', name: 'In-Flight Req', icon: 'counter', description: 'Limit concurrent requests', yaml: 'inFlightReq:\n  amount: 10' },
  { id: 'chain', name: 'Chain', icon: 'link-variant', description: 'Combine multiple middlewares', yaml: 'chain:\n  middlewares:\n    - middleware1@file\n    - middleware2@file' },
];

const TEMPLATE_COLORS: Record<string, string> = {
  'blank': 'muted', 'https-redirect': 'blue', 'basic-auth': 'yellow',
  'digest-auth': 'yellow', 'security-headers': 'green', 'rate-limit': 'red',
  'forward-auth': 'purple', 'forward-auth-authentik': 'purple',
  'forward-auth-authelia': 'purple', 'forward-auth-gatekeeper': 'purple',
  'ip-allowlist': 'blue', 'ip-allowlist-private': 'blue', 'cors-headers': 'green',
  'redirect-regex': 'blue', 'strip-prefix': 'orange', 'add-prefix': 'orange',
  'replace-path': 'orange', 'compress': 'muted', 'retry': 'orange',
  'circuit-breaker': 'red', 'buffering': 'muted', 'in-flight-req': 'red',
  'chain': 'purple',
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

  const [step,             setStep]             = useState<'pick' | 'form'>('pick');
  const [fName,            setFName]            = useState('');
  const [fYaml,            setFYaml]            = useState('');
  const [fConfigFile,      setFConfigFile]      = useState('');
  const [saving,           setSaving]           = useState(false);
  const [saveErr,          setSaveErr]          = useState('');
  const [selectedTemplate, setSelectedTemplate] = useState('');
  const [wizardMode,       setWizardMode]       = useState(true);

  const selectTemplate = (t: Template) => {
    setFYaml(t.yaml);
    setSelectedTemplate(t.id);
    setWizardMode(true);
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

          {WIZARD_TEMPLATES.has(selectedTemplate) && (
            <View style={[styles.modeToggle, { backgroundColor: c.card, borderColor: c.border }]}>
              <TouchableOpacity
                style={[styles.modeBtn, wizardMode && { backgroundColor: c.blue + '18' }]}
                onPress={() => setWizardMode(true)}
              >
                <Text style={[styles.modeBtnText, { color: wizardMode ? c.blue : c.muted }]}>Simple</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.modeBtn, !wizardMode && { backgroundColor: c.blue + '18' }]}
                onPress={() => setWizardMode(false)}
              >
                <Text style={[styles.modeBtnText, { color: !wizardMode ? c.blue : c.muted }]}>YAML</Text>
              </TouchableOpacity>
            </View>
          )}

          {WIZARD_TEMPLATES.has(selectedTemplate) && wizardMode ? (
            <MiddlewareWizard
              key={selectedTemplate}
              template={selectedTemplate}
              onYamlChange={setFYaml}
              c={c}
            />
          ) : (
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
          )}

          {showConfigPicker && (
            <View>
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
  modeToggle: {
    flexDirection: 'row',
    borderRadius: radius.sm,
    borderWidth: 1,
    overflow: 'hidden',
  },
  modeBtn: {
    flex: 1,
    paddingVertical: 8,
    alignItems: 'center',
  },
  modeBtnText: { fontSize: font.sm, fontWeight: '600' },
});
