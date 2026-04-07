import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  Switch,
  TouchableOpacity,
  View,
} from 'react-native';
import { Button, SegmentedButtons, Text, TextInput } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { RouteFormData } from '../../src/api/routes';
import { font, radius, spacing } from '../../src/theme';
import { useThemeStore } from '../../src/store/theme';
import { useSaveRoute } from '../../src/hooks/useRoutes';
import { useConfigs } from '../../src/hooks/useConfigs';
import { useSettings } from '../../src/hooks/useSettings';
import { ConfigFilePicker } from '../../src/components/ConfigFilePicker';

const PROTOCOLS = ['http', 'tcp', 'udp'] as const;
type Colors = ReturnType<typeof useThemeStore.getState>['colors'];

export default function NewRouteScreen() {
  const router  = useRouter();
  const insets  = useSafeAreaInsets();
  const c       = useThemeStore(s => s.colors);

  const saveRoute          = useSaveRoute();
  const configs            = useConfigs();
  const { data: settings } = useSettings();
  const resolvers   = (settings?.cert_resolver ?? '').split(',').map(r => r.trim()).filter(Boolean);
  const configFiles    = configs.data?.files ?? [];
  const configDirSet   = configs.data?.configDirSet ?? false;
  const showConfigPicker = configFiles.length > 1 || configDirSet;

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

      <View style={[styles.headerBar, { borderBottomColor: c.border, backgroundColor: c.card }]}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={12} style={styles.headerBtn}>
          <MaterialCommunityIcons name="chevron-down" size={26} color={c.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: c.text }]}>New Route</Text>
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
            placeholder="my-service"
            autoCapitalize="none"
            autoCorrect={false}
            mode="outlined"
            style={{ backgroundColor: c.bg }}
          />
          <TextInput
            label="Host / Domain"
            value={fHost}
            onChangeText={setFHost}
            placeholder="app.example.com"
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="url"
            mode="outlined"
            style={{ backgroundColor: c.bg }}
          />
          <TextInput
            label="Target IP / Host"
            value={fIp}
            onChangeText={setFIp}
            placeholder="192.168.1.10"
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="url"
            mode="outlined"
            style={{ backgroundColor: c.bg }}
          />
          <TextInput
            label="Target Port"
            value={fPort}
            onChangeText={setFPort}
            placeholder="8080"
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="numeric"
            mode="outlined"
            style={{ backgroundColor: c.bg }}
          />
          <TextInput
            label="Middlewares (comma-separated)"
            value={fMws}
            onChangeText={setFMws}
            placeholder="auth@file, compress"
            autoCapitalize="none"
            autoCorrect={false}
            mode="outlined"
            style={{ backgroundColor: c.bg }}
          />

          <Text style={[styles.fieldLabel, { color: c.muted }]}>Backend Scheme</Text>
          <SegmentedButtons
            value={fScheme}
            onValueChange={setFScheme}
            buttons={[
              { value: 'http',  label: 'HTTP'  },
              { value: 'https', label: 'HTTPS' },
            ]}
          />

          <View style={styles.switchRow}>
            <View style={{ flex: 1, gap: 2 }}>
              <Text style={[styles.fieldLabel, { color: c.text }]}>Pass Host Header</Text>
              <Text style={[styles.fieldHint, { color: c.muted }]}>Forward original Host to backend</Text>
            </View>
            <Switch
              value={fPassHost}
              onValueChange={setFPassHost}
              trackColor={{ false: c.border, true: c.blue }}
              thumbColor={fPassHost ? '#fff' : c.muted}
            />
          </View>

          {resolvers.length > 0 && (fProto === 'http' || fProto === 'tcp') && (
            <>
              <Text style={[styles.fieldLabel, { color: c.muted }]}>Cert Resolver</Text>
              <View style={styles.chipRow}>
                {resolvers.map(r => (
                  <TouchableOpacity
                    key={r}
                    onPress={() => setFCertResolver(r)}
                    style={[
                      styles.chip,
                      {
                        backgroundColor: fCertResolver === r ? c.secondaryContainer : 'transparent',
                        borderColor:     fCertResolver === r ? c.blue : c.border,
                      },
                    ]}
                    activeOpacity={0.7}
                  >
                    <Text style={{ color: fCertResolver === r ? c.onSecondaryContainer : c.text, fontSize: font.sm, fontWeight: '500' }}>
                      {r}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </>
          )}

          <Text style={[styles.fieldLabel, { color: c.muted }]}>Protocol</Text>
          <SegmentedButtons
            value={fProto}
            onValueChange={setFProto}
            buttons={PROTOCOLS.map(p => ({ value: p, label: p.toUpperCase() }))}
          />

          {showConfigPicker && (
            <View style={styles.formGroup}>
              <Text style={[styles.fieldLabel, { color: c.muted }]}>Config File</Text>
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
  headerBar: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: spacing.md, paddingVertical: spacing.sm,
    borderBottomWidth: 1, gap: spacing.sm,
  },
  headerBtn:     { width: 36, alignItems: 'flex-start' },
  headerTitle:   { flex: 1, fontSize: font.lg, fontWeight: '700', textAlign: 'center' },
  headerActions: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  scrollContent: { padding: spacing.md, gap: spacing.md },
  formGroup:     { gap: 4 },
  fieldLabel:    { fontSize: font.sm, fontWeight: '500', marginBottom: 4 },
  fieldHint:     { fontSize: font.xs, opacity: 0.8 },
  switchRow:     { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.sm, paddingVertical: spacing.xs },
  chipRow:       { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  chip:          { borderWidth: 1, borderRadius: radius.full, paddingHorizontal: spacing.md, paddingVertical: 7 },
  errTxt:        { fontSize: font.sm },
});
