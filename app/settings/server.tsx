import { Alert, ScrollView, StyleSheet, TouchableOpacity, View } from 'react-native';
import { Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useQueryClient } from '@tanstack/react-query';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useConnection } from '../../src/store/connection';
import { useThemeStore } from '../../src/store/theme';
import { font, radius, spacing } from '../../src/theme';

export default function ServerScreen() {
  const router   = useRouter();
  const insets   = useSafeAreaInsets();
  const c        = useThemeStore(s => s.colors);
  const { baseUrl, clearConnection } = useConnection();
  const qc       = useQueryClient();

  const handleDisconnect = () => {
    Alert.alert('Disconnect', 'Remove saved connection?', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Disconnect',
        style: 'destructive',
        onPress: async () => {
          await clearConnection();
          qc.clear();
          router.replace('/connect');
        },
      },
    ]);
  };

  return (
    <View style={[styles.screen, { backgroundColor: c.bg }]}>
      {/* Header */}
      <View style={[styles.headerBar, { paddingTop: insets.top + 4, borderBottomColor: c.border, backgroundColor: c.card }]}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={8} style={styles.backBtn}>
          <MaterialCommunityIcons name="arrow-left" size={22} color={c.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: c.text }]}>Server</Text>
        <View style={styles.headerSpacer} />
      </View>

      <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 24 }]}>

        <Text style={[styles.sectionLabel, { color: c.muted }]}>CONNECTION</Text>
        <View style={[styles.sectionBody, { borderColor: c.border, backgroundColor: c.card }]}>
          {/* URL row */}
          <View style={[styles.row, { borderBottomWidth: 1, borderBottomColor: c.border }]}>
            <Text style={[styles.rowLabel, { color: c.muted }]}>URL</Text>
            <Text style={[styles.rowValue, { color: c.text, fontFamily: 'monospace' }]} numberOfLines={2}>
              {baseUrl || '—'}
            </Text>
          </View>
          {/* Status row */}
          <View style={styles.row}>
            <Text style={[styles.rowLabel, { color: c.muted }]}>STATUS</Text>
            <View style={[styles.chip, { backgroundColor: c.green + '20', borderColor: c.green + '55' }]}>
              <View style={[styles.dot, { backgroundColor: c.green }]} />
              <Text style={[styles.chipText, { color: c.green }]}>Connected</Text>
            </View>
          </View>
        </View>

        <Text style={[styles.sectionLabel, { color: c.muted }]}>DANGER ZONE</Text>
        <View style={[styles.sectionBody, { borderColor: c.red + '44', backgroundColor: c.card }]}>
          <TouchableOpacity style={styles.disconnectRow} onPress={handleDisconnect} activeOpacity={0.7}>
            <MaterialCommunityIcons name="logout" size={18} color={c.red} />
            <Text style={[styles.disconnectLabel, { color: c.red }]}>Disconnect</Text>
          </TouchableOpacity>
        </View>
        <Text style={[styles.hint, { color: c.muted }]}>
          Removes the saved server URL and API key from this device.
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
  sectionBody: { borderRadius: radius.md, borderWidth: 1, overflow: 'hidden' },
  row: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: spacing.md, paddingVertical: 12,
    gap: spacing.sm,
  },
  rowLabel: { fontSize: font.xs, fontWeight: '700', letterSpacing: 0.5, width: 70 },
  rowValue: { flex: 1, fontSize: font.sm },
  chip: {
    flexDirection: 'row', alignItems: 'center', gap: 5,
    paddingHorizontal: 8, paddingVertical: 3,
    borderRadius: 999, borderWidth: 1, alignSelf: 'flex-start',
  },
  dot:      { width: 6, height: 6, borderRadius: 3 },
  chipText: { fontSize: font.xs, fontWeight: '600' },
  disconnectRow: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: spacing.md, paddingVertical: 14,
    gap: spacing.sm,
  },
  disconnectLabel: { fontSize: font.md, fontWeight: '600' },
  hint: { fontSize: font.xs, paddingHorizontal: 4, marginTop: 4 },
});
