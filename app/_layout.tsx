import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { SplashScreen, Stack, useRouter, useSegments } from 'expo-router';
import { useEffect, useRef } from 'react';
import { StatusBar } from 'expo-status-bar';
import { MD3DarkTheme, MD3LightTheme, PaperProvider } from 'react-native-paper';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { useColorScheme } from 'react-native';
import { useConnection } from '../src/store/connection';
import { useThemeStore } from '../src/store/theme';
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
  const router     = useRouter();
  const segments   = useSegments();
  const didRoute   = useRef(false);
  const colorScheme = useColorScheme();
  const systemIsDark = colorScheme === 'dark';

  useEffect(() => {
    Promise.all([loadConnection(), loadTheme(systemIsDark)]).then(() => SplashScreen.hideAsync());
  }, []);

  // Keep theme in sync when system theme changes
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

export default function RootLayout() {
  const isDark = useThemeStore(s => s.isDark);
  const c      = useThemeStore(s => s.colors);
  const theme  = makePaperTheme(isDark);

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
        </Stack>
      </QueryClientProvider>
    </PaperProvider>
    </SafeAreaProvider>
  );
}
