import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
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
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { RouteFormData } from '../../src/api/routes';
import { font, radius, spacing } from '../../src/theme';
import { useThemeStore } from '../../src/store/theme';
import { useSaveRoute } from '../../src/hooks/useRoutes';
import { useConfigs } from '../../src/hooks/useConfigs';
import { useSettings } from '../../src/hooks/useSettings';

const PROTOCOLS = ['http', 'tcp', 'udp'] as const;
type Colors = ReturnType<typeof useThemeStore.getState>['colors'];

function FormField({ label, value, onChange, c, placeholder, keyboardType }: {
  label: string; value: string; onChange: (v: string) => void; c: Colors;
  placeholder?: string; keyboardType?: 'default' | 'url' | 'numeric';
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

export default function NewRouteScreen() {
  const router  = useRouter();
  const insets  = useSafeAreaInsets();
  const c       = useThemeStore(s => s.colors);

  const saveRoute          = useSaveRoute();
  const configs            = useConfigs();
  const { data: settings } = useSettings();
  const resolvers   = (settings?.cert_resolver ?? '').split(',').map(r => r.trim()).filter(Boolean);
  const multiConfig = (configs.data?.length ?? 0) > 1;

  const [fName,         setFName]         = useState('');
  const [fHost,         setFHost]         = useState('');
  const [fIp,           setFIp]           = useState('');
  const [fPort,         setFPort]         = useState('');
  const [fProto,        setFProto]        = useState('http');
  const [fMws,          setFMws]          = useState('');
  const [fScheme,       setFScheme]       = useState('http');
  const [fPassHost,     setFPassHost]     = useState(true);
  const [fCertResolver, setFCertResolver] = useState('');
  const [fConfigFile,   setFConfigFile]   = useState('');
  const [saving,        setSaving]        = useState(false);
  const [saveErr,       setSaveErr]       = useState('');

  useEffect(() => {
    if (resolvers.length > 0 && !fCertResolver) setFCertResolver(resolvers[0]);
  }, [settings]);

  useEffect(() => {
    if (configs.data?.length && !fConfigFile) setFConfigFile(configs.data[0].label);
  }, [configs.data]);

  const handleSave = () => {
    if (!fName.trim() || !fIp.trim()) { setSaveErr('Name and target IP are required'); return; }
    setSaving(true);
    setSaveErr('');
    const data: RouteFormData = {
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
      { data, isEdit: false, originalId: '' },
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

  return (
    <View style={[styles.screen, { backgroundColor: c.bg, paddingTop: insets.top }]}>

      {/* Header */}
      <View style={[styles.headerBar, { borderBottomColor: c.border, backgroundColor: c.card }]}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={12} style={styles.headerBtn}>
          <MaterialCommunityIcons name="chevron-down" size={26} color={c.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: c.text }]}>New Route</Text>
        <View style={styles.headerActions}>
          <TouchableOpacity
            onPress={handleSave}
            disabled={saving}
            style={[styles.headerActionBtn, { borderColor: c.green, backgroundColor: c.green }]}
          >
            {saving
              ? <ActivityIndicator size="small" color="#fff" />
              : <Text style={[styles.headerActionTxt, { color: '#fff' }]}>Create</Text>}
          </TouchableOpacity>
        </View>
      </View>

      {/* Form */}
      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        <ScrollView
          style={{ flex: 1 }}
          contentContainerStyle={[styles.scrollContent, { paddingBottom: insets.bottom + 24 }]}
          keyboardShouldPersistTaps="handled"
        >
          <FormField label="NAME" value={fName} onChange={setFName} c={c} placeholder="my-service" />
          <FormField label="HOST / DOMAIN" value={fHost} onChange={setFHost} c={c}
            placeholder="app.example.com" keyboardType="url" />
          <FormField label="TARGET IP / HOST" value={fIp} onChange={setFIp} c={c}
            placeholder="192.168.1.10" keyboardType="url" />
          <FormField label="TARGET PORT" value={fPort} onChange={setFPort} c={c}
            placeholder="8080" keyboardType="numeric" />
          <FormField label="MIDDLEWARES (comma-separated)" value={fMws} onChange={setFMws} c={c}
            placeholder="auth@file, compress" />

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
  headerActionBtn: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: radius.sm, borderWidth: 1 },
  headerActionTxt: { fontSize: font.sm, fontWeight: '600' },
  // Scroll
  scrollContent: { padding: spacing.md, gap: spacing.md },
  // Form
  formGroup:    { gap: 4 },
  formLabel:    { fontSize: font.xs, fontWeight: '700', letterSpacing: 0.5 },
  formHint:     { fontSize: font.xs, opacity: 0.7 },
  formInput:    { borderWidth: 1, borderRadius: radius.sm, paddingHorizontal: spacing.sm, paddingVertical: 9, fontSize: font.sm },
  toggleRow:    { flexDirection: 'row', gap: spacing.sm, flexWrap: 'wrap' },
  toggleBtn:    { flex: 1, paddingVertical: 8, borderRadius: radius.sm, borderWidth: 1, alignItems: 'center', minWidth: 60 },
  toggleBtnTxt: { fontSize: font.sm, fontWeight: '700' },
  switchRow:    { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.sm },
  errTxt:       { fontSize: font.sm },
});
