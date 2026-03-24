import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Image, KeyboardAvoidingView, ScrollView, StyleSheet, View } from 'react-native';
import { Button, Text, TextInput } from 'react-native-paper';
import { Surface } from 'react-native-paper';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useConnection } from '../src/store/connection';
import { useThemeStore } from '../src/store/theme';
import { font, radius, spacing } from '../src/theme';
import { normalizeUrl } from '../src/utils';

export default function ConnectScreen() {
  const [url, setUrl]         = useState('');
  const [apiKey, setApiKey]   = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState('');
  const [showKey, setShowKey] = useState(false);
  const { setConnection }     = useConnection();
  const router                = useRouter();
  const c                     = useThemeStore(s => s.colors);
  const insets                = useSafeAreaInsets();

  const handleConnect = async () => {
    setError('');
    const base = normalizeUrl(url);
    if (!base) { setError('Enter a URL'); return; }
    if (!apiKey.trim()) { setError('Enter an API key'); return; }

    setLoading(true);
    try {
      const res = await fetch(`${base}/api/auth/apikey/status`, {
        headers: { 'X-Api-Key': apiKey.trim(), 'X-Requested-With': 'fetch' },
      });
      if (res.status === 401) throw new Error('Invalid API key');
      if (!res.ok)            throw new Error(`Server error ${res.status}`);
      const data = await res.json();
      if (!data.enabled)      throw new Error('API key auth is not enabled on this server');

      await setConnection(base, apiKey.trim());
      router.replace('/(tabs)');
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Connection failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={[styles.flex, { backgroundColor: c.bg, paddingTop: insets.top }]}
      behavior="padding"
    >
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <View style={styles.hero}>
          <Image
            source={require('../assets/icon.png')}
            style={styles.logo}
            resizeMode="contain"
          />
          <Text style={[styles.title, { color: c.text }]}>Traefik <Text style={{ color: c.blue }}>Manager</Text></Text>
          <Text style={[styles.subtitle, { color: c.muted }]}>Connect to your instance</Text>
        </View>

        <Surface style={[styles.card, { backgroundColor: c.card, borderColor: c.border }]} elevation={1}>
          <TextInput
            label="Instance URL"
            value={url}
            onChangeText={setUrl}
            placeholder="manager.example.com"
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="url"
            mode="outlined"
            style={{ backgroundColor: c.bg }}
          />

          <TextInput
            label="API Key"
            value={apiKey}
            onChangeText={setApiKey}
            placeholder="Your API key from Settings → Auth"
            secureTextEntry={!showKey}
            autoCapitalize="none"
            autoCorrect={false}
            mode="outlined"
            style={{ backgroundColor: c.bg }}
            right={
              <TextInput.Icon
                icon={showKey ? 'eye-off' : 'eye'}
                onPress={() => setShowKey(v => !v)}
              />
            }
          />

          {!!error && <Text style={[styles.error, { color: c.red }]}>{error}</Text>}

          <Button
            mode="contained"
            onPress={handleConnect}
            loading={loading}
            disabled={loading}
            style={styles.btn}
            contentStyle={styles.btnContent}
            labelStyle={styles.btnLabel}
          >
            Connect
          </Button>
        </Surface>

        <Text style={[styles.hint, { color: c.muted }]}>
          Generate an API key in Settings → Authentication
        </Text>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  container: {
    flexGrow: 1,
    justifyContent: 'center',
    padding: spacing.xl,
  },
  hero: {
    alignItems: 'center',
    marginBottom: spacing.xxl,
  },
  logo: {
    width: 80,
    height: 80,
    borderRadius: radius.lg,
    marginBottom: spacing.md,
  },
  title: {
    fontSize: font.xxl,
    fontWeight: '800',
    marginBottom: spacing.xs,
  },
  subtitle: {
    fontSize: font.base,
  },
  card: {
    borderRadius: radius.lg,
    borderWidth: 1,
    padding: spacing.xl,
    marginBottom: spacing.lg,
    gap: spacing.md,
  },
  error: {
    fontSize: font.sm,
    textAlign: 'center',
  },
  btn: {
    marginTop: spacing.xs,
    borderRadius: radius.sm,
  },
  btnContent: {
    paddingVertical: 4,
  },
  btnLabel: {
    fontSize: font.md,
    fontWeight: '700',
    letterSpacing: 0.5,
  },
  hint: {
    fontSize: font.xs,
    textAlign: 'center',
    lineHeight: 18,
  },
});
