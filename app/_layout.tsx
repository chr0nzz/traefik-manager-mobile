import * as LocalAuthentication from 'expo-local-authentication';
import * as SystemUI from 'expo-system-ui';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SplashScreen, Stack, useRouter, useSegments } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { AppState, AppStateStatus, StyleSheet, TouchableOpacity, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { MD3DarkTheme, MD3LightTheme, PaperProvider, Text } from 'react-native-paper';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { useColorScheme } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useConnection } from '../src/store/connection';
import { useThemeStore } from '../src/store/theme';
import { useAppLock } from '../src/store/applock';
import { darkColors, lightColors } from '../src/theme';

SplashScreen.preventAutoHideAsync();

function makePaperTheme(isDark: boolean) {
  const base = isDark ? MD3DarkTheme : MD3LightTheme;
  const c    = isDark ? darkColors   : lightColors;
  return {
    ...base,
    colors: {
      ...base.colors,
      primary:          c.blue,
      secondary:        c.purple,
      background:       c.bg,
      surface:          c.card,
      surfaceVariant:   c.card,
      onSurface:        c.text,
      onSurfaceVariant: c.muted,
      outline:          c.border,
      error:            c.red,
    },
  };
}

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 2, staleTime: 15_000 } },
});

function ConnectionGate() {
  const { baseUrl, apiKey, ready, loadConnection } = useConnection();
  const { load: loadTheme, applySystem }           = useThemeStore();
  const { load: loadAppLock }                      = useAppLock();
  const router     = useRouter();
  const segments   = useSegments();
  const didRoute   = useRef(false);
  const colorScheme = useColorScheme();
  const systemIsDark = colorScheme === 'dark';

  useEffect(() => {
    Promise.all([loadConnection(), loadTheme(systemIsDark), loadAppLock()]).then(() => SplashScreen.hideAsync());
  }, []);

  useEffect(() => {
    applySystem(systemIsDark);
  }, [systemIsDark]);

  useEffect(() => {
    if (!ready) return;
    if (didRoute.current) return;
    const connected  = !!baseUrl && !!apiKey;
    const onConnect  = segments[0] === 'connect';
    if (!connected && !onConnect) {
      didRoute.current = true;
      router.replace('/connect');
    } else if (connected && onConnect) {
      didRoute.current = true;
      router.replace('/(tabs)');
    }
  }, [ready, baseUrl, apiKey]);

  return null;
}

function AppLockGate() {
  const { enabled, ready } = useAppLock();
  const c = useThemeStore(s => s.colors);
  const [locked, setLocked] = useState(true);
  const appState = useRef<AppStateStatus>(AppState.currentState);
  const isAuthenticating = useRef(false);

  const authenticate = async () => {
    if (isAuthenticating.current) return;
    isAuthenticating.current = true;
    try {
      const result = await LocalAuthentication.authenticateAsync({
        promptMessage: 'Unlock Traefik Manager',
        cancelLabel: 'Cancel',
        disableDeviceFallback: false,
      });
      if (result.success) setLocked(false);
    } finally {
      isAuthenticating.current = false;
    }
  };

  useEffect(() => {
    if (!ready || !enabled) return;
    authenticate();
  }, [ready, enabled]);

  useEffect(() => {
    if (!enabled) return;
    const sub = AppState.addEventListener('change', (next: AppStateStatus) => {
      const wasActive = appState.current === 'active';
      const goingBackground = next === 'background' || next === 'inactive';
      const comingBack = next === 'active' && (appState.current === 'background' || appState.current === 'inactive');
      if (wasActive && goingBackground) setLocked(true);
      if (comingBack) authenticate();
      appState.current = next;
    });
    return () => sub.remove();
  }, [enabled]);

  if (!enabled || !locked) return null;

  return (
    <View style={[styles.lockOverlay, { backgroundColor: c.bg }]}>
      <MaterialCommunityIcons name="lock" size={48} color={c.blue} style={{ marginBottom: 16 }} />
      <Text style={[styles.lockTitle, { color: c.text }]}>Traefik Manager</Text>
      <Text style={[styles.lockSub, { color: c.muted }]}>Authentication required</Text>
      <TouchableOpacity
        style={[styles.unlockBtn, { backgroundColor: c.blue }]}
        onPress={authenticate}
        activeOpacity={0.8}
      >
        <Text style={styles.unlockBtnText}>Unlock</Text>
      </TouchableOpacity>
    </View>
  );
}

export default function RootLayout() {
  const isDark = useThemeStore(s => s.isDark);
  const c      = useThemeStore(s => s.colors);
  const theme  = makePaperTheme(isDark);

  useEffect(() => {
    SystemUI.setBackgroundColorAsync(c.bg);
  }, [c.bg]);

  return (
    <SafeAreaProvider>
    <PaperProvider theme={theme}>
      <QueryClientProvider client={queryClient}>
        <StatusBar style={isDark ? 'light' : 'dark'} />
        <ConnectionGate />
        <Stack
          screenOptions={{
            headerShown: false,
            contentStyle: { backgroundColor: c.bg },
          }}
        >
          <Stack.Screen name="connect" />
          <Stack.Screen name="(tabs)" />
          <Stack.Screen name="route/[id]" options={{ presentation: 'modal', headerShown: false }} />
          <Stack.Screen name="route/new" options={{ presentation: 'modal', headerShown: false }} />
          <Stack.Screen name="middleware/[id]" options={{ presentation: 'modal', headerShown: false }} />
          <Stack.Screen name="middleware/new" options={{ presentation: 'modal', headerShown: false }} />
          <Stack.Screen name="service/[name]" options={{ presentation: 'modal', headerShown: false }} />
          <Stack.Screen name="settings/appearance" options={{ presentation: 'modal', headerShown: false }} />
          <Stack.Screen name="settings/applock" options={{ presentation: 'modal', headerShown: false }} />
          <Stack.Screen name="settings/server" options={{ presentation: 'modal', headerShown: false }} />
          <Stack.Screen name="settings/backups" options={{ presentation: 'modal', headerShown: false }} />
          <Stack.Screen name="settings/about" options={{ presentation: 'modal', headerShown: false }} />
          <Stack.Screen name="settings/traefik" options={{ presentation: 'modal', headerShown: false }} />
        </Stack>
        <AppLockGate />
      </QueryClientProvider>
    </PaperProvider>
    </SafeAreaProvider>
  );
}

const styles = StyleSheet.create({
  lockOverlay: {
    ...StyleSheet.absoluteFillObject,
    zIndex: 999,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 32,
  },
  lockTitle: {
    fontSize: 22,
    fontWeight: '700',
    marginBottom: 6,
  },
  lockSub: {
    fontSize: 14,
    marginBottom: 32,
  },
  unlockBtn: {
    paddingHorizontal: 32,
    paddingVertical: 14,
    borderRadius: 999,
  },
  unlockBtnText: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '700',
  },
});
