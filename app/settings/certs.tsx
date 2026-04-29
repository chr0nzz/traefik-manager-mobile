import { ScrollView, StyleSheet, View } from 'react-native';
import { Surface, Switch, Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { TouchableOpacity } from 'react-native';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTabsStore } from '../../src/store/tabs';
import { useThemeStore } from '../../src/store/theme';
import { font, radius, spacing } from '../../src/theme';

export default function CertsSettingsScreen() {
  const router          = useRouter();
  const insets          = useSafeAreaInsets();
  const c               = useThemeStore(s => s.colors);
  const showCertsTab    = useTabsStore(s => s.showCertsTab);
  const setShowCertsTab = useTabsStore(s => s.setShowCertsTab);

  return (
    <View style={[styles.screen, { backgroundColor: c.bg }]}>
      <View style={[styles.headerBar, { paddingTop: insets.top + 4, borderBottomColor: c.border, backgroundColor: c.card }]}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={8} style={styles.backBtn}>
          <MaterialCommunityIcons name="arrow-left" size={22} color={c.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: c.text }]}>Certificates</Text>
        <View style={styles.backBtn} />
      </View>

      <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 32 }]}>
        <Surface style={[styles.group, { backgroundColor: c.card }]} elevation={1}>
          <View style={styles.row}>
            <View style={styles.rowLeft}>
              <Text style={[styles.rowLabel, { color: c.text }]}>Show Certificates Tab</Text>
              <Text style={[styles.rowHint, { color: c.muted }]}>Adds a Certificates tab to the More menu</Text>
            </View>
            <Switch value={showCertsTab} onValueChange={setShowCertsTab} />
          </View>
        </Surface>
        <Text style={[styles.hint, { color: c.muted }]}>
          Shows TLS certificates managed by Traefik. Requires ACME (Let's Encrypt) to be configured.
        </Text>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen:    { flex: 1 },
  headerBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.md,
    paddingBottom: 12,
    borderBottomWidth: 1,
  },
  backBtn:     { width: 32 },
  headerTitle: { fontSize: font.lg, fontWeight: '700' },
  content:     { padding: spacing.lg, gap: spacing.sm },
  group:       { borderRadius: radius.md, overflow: 'hidden' },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.md,
    paddingVertical: 14,
  },
  rowLeft:  { flex: 1, marginRight: spacing.md },
  rowLabel: { fontSize: font.md, fontWeight: '500' },
  rowHint:  { fontSize: font.sm, marginTop: 2 },
  hint:     { fontSize: font.sm, paddingHorizontal: 4, lineHeight: 18 },
});
