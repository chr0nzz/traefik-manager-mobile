import * as LocalAuthentication from 'expo-local-authentication';
import { ScrollView, StyleSheet, TouchableOpacity, View } from 'react-native';
import { Surface, Switch, Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useAppLock } from '../../src/store/applock';
import { useThemeStore } from '../../src/store/theme';
import { font, radius, spacing } from '../../src/theme';

export default function AppLockScreen() {
  const router  = useRouter();
  const insets  = useSafeAreaInsets();
  const c       = useThemeStore(s => s.colors);
  const { enabled, setEnabled } = useAppLock();

  const handleToggle = async (value: boolean) => {
    if (value) {
      const result = await LocalAuthentication.authenticateAsync({
        promptMessage: 'Confirm your identity to enable App Lock',
        cancelLabel: 'Cancel',
        disableDeviceFallback: false,
      });
      if (result.success) {
        await setEnabled(true);
      }
    } else {
      await setEnabled(false);
    }
  };

  return (
    <View style={[styles.screen, { backgroundColor: c.bg }]}>
      <View style={[styles.headerBar, { paddingTop: insets.top + 4, borderBottomColor: c.border, backgroundColor: c.card }]}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={8} style={styles.backBtn}>
          <MaterialCommunityIcons name="arrow-left" size={22} color={c.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: c.text }]}>App Lock</Text>
        <View style={styles.headerSpacer} />
      </View>

      <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 24 }]}>
        <Text style={[styles.sectionLabel, { color: c.muted }]}>SECURITY</Text>

        <Surface style={[styles.sectionBody, { backgroundColor: c.card }]} elevation={1}>
          <View style={styles.toggleRow}>
            <MaterialCommunityIcons
              name={enabled ? 'lock' : 'lock-outline'}
              size={18}
              color={enabled ? c.blue : c.muted}
              style={styles.rowIcon}
            />
            <Text style={[styles.rowLabel, { color: c.text }]}>Require biometrics to open</Text>
            <Switch
              value={enabled}
              onValueChange={handleToggle}
            />
          </View>
        </Surface>

        <Text style={[styles.hint, { color: c.muted }]}>
          Uses your device fingerprint, face unlock, or PIN. You will be prompted each time you open the app or return from the background.
        </Text>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  headerBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.md,
    borderBottomWidth: 1,
    gap: spacing.sm,
  },
  backBtn:      { padding: 2 },
  headerTitle:  { flex: 1, fontSize: font.lg, fontWeight: '700' },
  headerSpacer: { width: 26 },
  content: { padding: spacing.lg, gap: spacing.sm },
  sectionLabel: {
    fontSize: font.xs, fontWeight: '700', letterSpacing: 0.8,
    textTransform: 'uppercase', paddingHorizontal: 4,
  },
  sectionBody: {
    borderRadius: radius.md, overflow: 'hidden',
  },
  toggleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.md,
    paddingVertical: 14,
    gap: spacing.sm,
  },
  rowIcon:  { width: 22 },
  rowLabel: { flex: 1, fontSize: font.md, fontWeight: '500' },
  hint: { fontSize: font.xs, paddingHorizontal: 4, marginTop: 4, lineHeight: 18 },
});
