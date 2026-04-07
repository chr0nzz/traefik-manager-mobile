import { useState, useEffect } from 'react';
import {
  ActivityIndicator, Alert, KeyboardAvoidingView, Platform,
  ScrollView, StyleSheet, TouchableOpacity, View,
} from 'react-native';
import { Surface, Text, TextInput } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useThemeStore } from '../../src/store/theme';
import { font, radius, spacing } from '../../src/theme';
import { useSettings, useSaveSettings } from '../../src/hooks/useSettings';
import { testTraefikUrl } from '../../src/api/settings';

export default function TraefikSettingsScreen() {
  const router  = useRouter();
  const insets  = useSafeAreaInsets();
  const c       = useThemeStore(s => s.colors);
  const { data: settings, isLoading } = useSettings();
  const { mutate: save, isPending: saving } = useSaveSettings();

  const [domains, setDomains]     = useState('');
  const [resolvers, setResolvers] = useState('');
  const [apiUrl, setApiUrl]       = useState('');
  const [testResult, setTestResult] = useState<{ ok: boolean; msg: string } | null>(null);
  const [testing, setTesting]     = useState(false);

  useEffect(() => {
    if (settings) {
      setDomains(settings.domains.join(', '));
      setResolvers(settings.cert_resolver);
      setApiUrl(settings.traefik_api_url);
    }
  }, [settings]);

  const handleSave = () => {
    const parsedDomains = domains.split(',').map(d => d.trim()).filter(Boolean);
    if (parsedDomains.length === 0) {
      Alert.alert('Validation', 'Enter at least one domain.');
      return;
    }
    if (!apiUrl.startsWith('http://') && !apiUrl.startsWith('https://')) {
      Alert.alert('Validation', 'Traefik API URL must start with http:// or https://');
      return;
    }
    save(
      { domains: parsedDomains, cert_resolver: resolvers.trim(), traefik_api_url: apiUrl.trim() },
      { onSuccess: () => router.back() },
    );
  };

  const handleTest = async () => {
    setTesting(true);
    setTestResult(null);
    try {
      const res = await testTraefikUrl(apiUrl.trim());
      setTestResult(res.ok
        ? { ok: true, msg: `Connected - Traefik ${res.version ?? ''}` }
        : { ok: false, msg: res.error ?? 'Connection failed' });
    } catch (e: any) {
      setTestResult({ ok: false, msg: e.message ?? 'Connection failed' });
    } finally {
      setTesting(false);
    }
  };

  return (
    <View style={[styles.screen, { backgroundColor: c.bg }]}>
      <View style={[styles.headerBar, { paddingTop: insets.top + 4, borderBottomColor: c.border, backgroundColor: c.card }]}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={8} style={styles.backBtn}>
          <MaterialCommunityIcons name="arrow-left" size={22} color={c.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: c.text }]}>Traefik</Text>
        <TouchableOpacity onPress={handleSave} disabled={saving || isLoading} hitSlop={8}>
          {saving
            ? <ActivityIndicator size="small" color={c.blue} />
            : <Text style={[styles.saveBtn, { color: c.blue }]}>Save</Text>}
        </TouchableOpacity>
      </View>

      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        <ScrollView
          keyboardShouldPersistTaps="handled"
          contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 24 }]}
        >
          <TextInput
            label="Domains"
            value={domains}
            onChangeText={setDomains}
            placeholder="example.com, home.lab"
            autoCapitalize="none"
            autoCorrect={false}
            mode="outlined"
            style={{ backgroundColor: c.bg }}
          />
          <Text style={[styles.hint, { color: c.muted }]}>Separate multiple domains with a comma.</Text>

          <TextInput
            label="Cert Resolvers"
            value={resolvers}
            onChangeText={setResolvers}
            placeholder="letsencrypt, cloudflare"
            autoCapitalize="none"
            autoCorrect={false}
            mode="outlined"
            style={{ backgroundColor: c.bg }}
          />
          <Text style={[styles.hint, { color: c.muted }]}>Separate multiple resolvers with a comma. The first is used as the default per route.</Text>

          <TextInput
            label="Traefik API URL"
            value={apiUrl}
            onChangeText={text => { setApiUrl(text); setTestResult(null); }}
            placeholder="http://traefik:8080"
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="url"
            mode="outlined"
            style={{ backgroundColor: c.bg, fontFamily: 'monospace' }}
          />
          <TouchableOpacity
            onPress={handleTest}
            disabled={testing || !apiUrl}
            style={[styles.testBtn, { borderColor: c.border, backgroundColor: c.card }]}
            activeOpacity={0.7}
          >
            {testing
              ? <ActivityIndicator size="small" color={c.blue} />
              : <MaterialCommunityIcons name="lan-connect" size={16} color={c.blue} />}
            <Text style={[styles.testBtnText, { color: c.blue }]}>Test connection</Text>
          </TouchableOpacity>
          {testResult && (
            <Text style={[styles.testResult, { color: testResult.ok ? c.green : c.red }]}>
              {testResult.msg}
            </Text>
          )}
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen:      { flex: 1 },
  headerBar: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: spacing.lg, paddingBottom: spacing.md,
    borderBottomWidth: 1, gap: spacing.sm,
  },
  backBtn:     { padding: 2 },
  headerTitle: { flex: 1, fontSize: font.lg, fontWeight: '700' },
  saveBtn:     { fontSize: font.md, fontWeight: '600' },
  content:     { padding: spacing.lg, gap: spacing.sm },
  hint: { fontSize: font.xs, paddingHorizontal: 4, marginTop: 2 },
  testBtn: {
    flexDirection: 'row', alignItems: 'center', gap: 6,
    borderWidth: 1, borderRadius: radius.md,
    paddingHorizontal: spacing.md, paddingVertical: 10,
    marginTop: spacing.xs, alignSelf: 'flex-start',
  },
  testBtnText: { fontSize: font.sm, fontWeight: '600' },
  testResult:  { fontSize: font.sm, paddingHorizontal: 4, marginTop: 4 },
});
