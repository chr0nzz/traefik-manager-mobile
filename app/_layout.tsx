import * as LocalAuthentication from 'expo-local-authentication';
import { useMaterial3Theme } from '@pchmn/expo-material3-theme';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SplashScreen, Stack, useRouter, useSegments } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { Appearance, AppState, AppStateStatus, StyleSheet, TouchableOpacity, View } from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { MD3DarkTheme, MD3LightTheme, PaperProvider, Text } from 'react-native-paper';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { useColorScheme } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useConnection } from '../src/store/connection';
import { useThemeStore } from '../src/store/theme';
import { useAppLock } from '../src/store/applock';
import { useTabsStore } from '../src/store/tabs';
import { darkColors, lightColors, dynamicColorsFromM3 } from '../src/theme';

SplashScreen.preventAutoHideAsync();

function makePaperTheme(isDark: boolean) {
  const base = isDark ? MD3DarkTheme : MD3LightTheme;
  const c    = isDark ? darkColors   : lightColors;
  return {
    ...base,
    colors: {
      ...base.colors,
      primary:                c.blue,
      onPrimary:              '#ffffff',
      primaryContainer:       c.secondaryContainer,
      onPrimaryContainer:     c.onSecondaryContainer,
      secondary:              c.purple,
      onSecondary:            '#ffffff',
      secondaryContainer:     c.secondaryContainer,
      onSecondaryContainer:   c.onSecondaryContainer,
      tertiary:               c.teal,
      background:             c.bg,
      onBackground:           c.text,
      surface:                c.card,
      onSurface:              c.text,
      surfaceVariant:         isDark ? '#1e2a36' : '#e8f0f8',
      onSurfaceVariant:       c.muted,
      outline:                c.border,
      outlineVariant:         c.border,
      error:                  c.red,
      onError:                '#ffffff',
      inverseSurface:         isDark ? c.text : c.card,
      inverseOnSurface:       isDark ? c.bg : c.text,
      inversePrimary:         c.blue,
      elevation: {
        ...base.colors.elevation,
        level0: 'transparent',
        level1: isDark ? '#1a2130' : '#f0f6ff',
        level2: isDark ? '#1e2638' : '#e8f0fa',
        level3: isDark ? '#222d40' : '#e0eaf6',
      },
    },
  };
}

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 2, staleTime: 15_000 } },
});

function ConnectionGate() {
  const { baseUrl, apiKey, ready, demoMode, loadConnection } = useConnection();
  const { load: loadTheme, applySystem }                    = useThemeStore();
  const { load: loadAppLock }                               = useAppLock();
  const { load: loadTabs }                                  = useTabsStore();
  const router     = useRouter();
  const segments   = useSegments();
  const didRoute   = useRef(false);
  const colorScheme = useColorScheme();
  const systemIsDark = colorScheme === 'dark';

  useEffect(() => {
    const initDark = Appearance.getColorScheme() === 'dark';
    Promise.all([loadConnection(), loadTheme(initDark), loadAppLock(), loadTabs()]).then(() => SplashScreen.hideAsync());
  }, []);

  useEffect(() => {
    applySystem(systemIsDark);
  }, [systemIsDark]);

  useEffect(() => {
    if (!ready) return;
    if (didRoute.current) return;
    const connected  = (!!baseUrl && !!apiKey) || demoMode;
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
  const isDark        = useThemeStore(s => s.isDark);
  const dynamicColors = useThemeStore(s => s.dynamicColors);
  const setColors     = useThemeStore(s => s.setColors);
  const c             = useThemeStore(s => s.colors);
  const demoMode      = useConnection(s => s.demoMode);
  const { theme: m3Theme } = useMaterial3Theme({ fallbackSourceColor: '#24a1de' });
  const [screenKey, setScreenKey] = useState('init');

  const paperTheme = dynamicColors && m3Theme
    ? (isDark
        ? { ...MD3DarkTheme, colors: m3Theme.dark }
        : { ...MD3LightTheme, colors: m3Theme.light })
    : makePaperTheme(isDark);

  const onRootLayout = (e: { nativeEvent: { layout: { width: number } } }) => {
    const w = String(Math.round(e.nativeEvent.layout.width));
    setScreenKey(prev => prev === w ? prev : w);
  };

  useEffect(() => {
    if (dynamicColors && m3Theme) {
      const scheme = isDark ? m3Theme.dark : m3Theme.light;
      setColors(dynamicColorsFromM3(scheme as Record<string, any>, isDark));
    }
  }, [dynamicColors, isDark, m3Theme]);

  useEffect(() => {
    if (!demoMode) return;
    const sub = AppState.addEventListener('change', (next: AppStateStatus) => {
      if (next === 'background' || next === 'inactive') {
        queryClient.removeQueries({ queryKey: ['routes'] });
        queryClient.removeQueries({ queryKey: ['middlewares'] });
        queryClient.removeQueries({ queryKey: ['live-services'] });
        queryClient.removeQueries({ queryKey: ['overview'] });
        queryClient.removeQueries({ queryKey: ['entrypoints'] });
      }
    });
    return () => sub.remove();
  }, [demoMode]);

  return (
    <SafeAreaProvider>
    <View style={{ flex: 1 }} onLayout={onRootLayout}>
    <PaperProvider theme={paperTheme}>
      <QueryClientProvider client={queryClient}>
        <StatusBar style={isDark ? 'light' : 'dark'} />
        <ConnectionGate />
        <Stack
          key={screenKey}
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
          <Stack.Screen name="log-detail" options={{ presentation: 'modal', headerShown: false }} />
        </Stack>
        <AppLockGate />
      </QueryClientProvider>
    </PaperProvider>
    </View>
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
