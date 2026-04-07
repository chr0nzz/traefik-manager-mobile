import { Image, Linking, ScrollView, StyleSheet, TouchableOpacity, View } from 'react-native';
import { Surface, Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery } from '@tanstack/react-query';
import Constants from 'expo-constants';
import { getVersion, getManagerVersion } from '../../src/api/traefik';
import { useThemeStore } from '../../src/store/theme';
import { font, radius, spacing } from '../../src/theme';

const version = Constants.expoConfig?.version ?? '—';

type Colors = ReturnType<typeof useThemeStore.getState>['colors'];

function Section({ title, children, c }: { title: string; children: React.ReactNode; c: Colors }) {
  return (
    <View style={styles.section}>
      <Text style={[styles.sectionLabel, { color: c.muted }]}>{title}</Text>
      <Surface style={[styles.sectionBody, { backgroundColor: c.card }]} elevation={1}>
        {children}
      </Surface>
    </View>
  );
}

function Row({ icon, label, value, onPress, isLast, c }: {
  icon?: string; label: string; value?: string;
  onPress?: () => void; isLast: boolean; c: Colors;
}) {
  const content = (
    <View style={[styles.row, !isLast && { borderBottomWidth: 1, borderBottomColor: c.border }]}>
      {icon && <MaterialCommunityIcons name={icon as any} size={16} color={c.muted} style={styles.rowIcon} />}
      <Text style={[styles.rowLabel, { color: c.text }]}>{label}</Text>
      {!!value && <Text style={[styles.rowValue, { color: c.muted }]}>{value}</Text>}
      {!!onPress && <MaterialCommunityIcons name="open-in-new" size={14} color={c.muted} />}
    </View>
  );

  if (onPress) {
    return (
      <TouchableOpacity onPress={onPress} activeOpacity={0.7}>
        {content}
      </TouchableOpacity>
    );
  }
  return content;
}

export default function AboutScreen() {
  const router  = useRouter();
  const insets  = useSafeAreaInsets();
  const c       = useThemeStore(s => s.colors);

  const traefikVersion = useQuery({ queryKey: ['version'],         queryFn: getVersion,        staleTime: 60_000, retry: 1 });
  const managerVersion = useQuery({ queryKey: ['manager-version'], queryFn: getManagerVersion, staleTime: 60_000, retry: 1 });

  const tv = traefikVersion.data?.Version;
  const mv = managerVersion.data?.version;

  return (
    <View style={[styles.screen, { backgroundColor: c.bg }]}>
      {/* Header */}
      <View style={[styles.headerBar, { paddingTop: insets.top + 4, borderBottomColor: c.border, backgroundColor: c.card }]}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={8} style={styles.backBtn}>
          <MaterialCommunityIcons name="arrow-left" size={22} color={c.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: c.text }]}>About</Text>
        <View style={styles.headerSpacer} />
      </View>

      <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 24 }]}>
        {/* App identity */}
        <View style={styles.identity}>
          <Image
            source={require('../../assets/AppIcons/appstore.png')}
            style={styles.appIcon}
          />
          <Text style={[styles.appName, { color: c.text }]}>Traefik Manager</Text>
          <Text style={[styles.appVersion, { color: c.muted }]}>Mobile v{version}</Text>
        </View>

        <Section title="LINKS" c={c}>
          <Row
            icon="github"
            label="GitHub"
            onPress={() => Linking.openURL('https://github.com/chr0nzz/traefik-manager')}
            isLast={false}
            c={c}
          />
          <Row
            icon="book-open-outline"
            label="Documentation"
            onPress={() => Linking.openURL('https://traefik-manager.xyzlab.dev')}
            isLast
            c={c}
          />
        </Section>

        <Section title="APP" c={c}>
          <Row label="Mobile" value={`v${version}`} isLast={false} c={c} />
          <Row label="Traefik Manager" value={mv ? `v${mv}` : '—'} isLast={false} c={c} />
          <Row label="Traefik" value={tv ?? '—'} isLast={false} c={c} />
          <Row label="Built by" value="chr0nzz" isLast c={c} />
        </Section>
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
  content: { padding: spacing.lg, gap: spacing.xl },
  identity: {
    alignItems: 'center',
    gap: 8,
    paddingVertical: spacing.xl,
  },
  appIcon: {
    width: 84, height: 84,
    borderRadius: 20,
    marginBottom: 4,
  },
  appName:    { fontSize: font.xl, fontWeight: '800' },
  appVersion: { fontSize: font.sm },
  section:    { gap: spacing.sm },
  sectionLabel: {
    fontSize: font.xs, fontWeight: '700', letterSpacing: 0.8,
    textTransform: 'uppercase', paddingHorizontal: 4,
  },
  sectionBody: {
    borderRadius: radius.md, overflow: 'hidden',
  },
  row: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: spacing.md, paddingVertical: 13,
    gap: spacing.sm,
  },
  rowIcon:  { width: 20 },
  rowLabel: { flex: 1, fontSize: font.sm, fontWeight: '500' },
  rowValue: { fontSize: font.sm },
});
