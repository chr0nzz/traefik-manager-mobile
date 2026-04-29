import { Linking, ScrollView, StyleSheet, TouchableOpacity, View } from 'react-native';
import { Surface, Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useThemeStore } from '../../src/store/theme';
import { font, radius, spacing } from '../../src/theme';

type Colors = ReturnType<typeof useThemeStore.getState>['colors'];

const LIBRARIES = [
  { name: 'React Native',                author: 'Meta Platforms, Inc.',   license: 'MIT', url: 'https://github.com/facebook/react-native' },
  { name: 'React',                       author: 'Meta Platforms, Inc.',   license: 'MIT', url: 'https://github.com/facebook/react' },
  { name: 'Expo',                        author: 'Expo, Inc.',             license: 'MIT', url: 'https://github.com/expo/expo' },
  { name: 'Expo Router',                 author: 'Expo, Inc.',             license: 'MIT', url: 'https://github.com/expo/expo' },
  { name: 'React Native Paper',          author: 'Callstack, Inc.',        license: 'MIT', url: 'https://github.com/callstack/react-native-paper' },
  { name: 'TanStack Query',              author: 'TanStack',               license: 'MIT', url: 'https://github.com/TanStack/query' },
  { name: 'Zustand',                     author: 'Paul Henschel et al.',   license: 'MIT', url: 'https://github.com/pmndrs/zustand' },
  { name: 'expo-material3-theme',        author: 'pchmn',                  license: 'MIT', url: 'https://github.com/pchmn/expo-material3-theme' },
  { name: 'expo-secure-store',           author: 'Expo, Inc.',             license: 'MIT', url: 'https://github.com/expo/expo' },
  { name: 'expo-local-authentication',   author: 'Expo, Inc.',             license: 'MIT', url: 'https://github.com/expo/expo' },
  { name: 'react-native-safe-area-context', author: 'Th3rdwave',          license: 'MIT', url: 'https://github.com/th3rdwave/react-native-safe-area-context' },
  { name: 'react-native-screens',        author: 'Software Mansion',       license: 'MIT', url: 'https://github.com/software-mansion/react-native-screens' },
  { name: 'react-native-svg',            author: 'Software Mansion',       license: 'MIT', url: 'https://github.com/software-mansion/react-native-svg' },
  { name: 'react-native-edge-to-edge',   author: 'Zoontek',                license: 'MIT', url: 'https://github.com/zoontek/react-native-edge-to-edge' },
  { name: 'Material Design Icons',       author: 'Pictogrammers',          license: 'Apache 2.0', url: 'https://github.com/Templarian/MaterialDesign' },
  { name: '@expo/vector-icons',          author: 'Expo, Inc.',             license: 'MIT', url: 'https://github.com/expo/vector-icons' },
];

function LibraryRow({ name, author, license, url, isLast, c }: {
  name: string; author: string; license: string; url: string; isLast: boolean; c: Colors;
}) {
  return (
    <TouchableOpacity
      style={[styles.row, !isLast && { borderBottomWidth: 1, borderBottomColor: c.border }]}
      onPress={() => Linking.openURL(url)}
      activeOpacity={0.7}
    >
      <View style={styles.rowBody}>
        <Text style={[styles.rowName, { color: c.text }]}>{name}</Text>
        <Text style={[styles.rowAuthor, { color: c.muted }]}>{author}</Text>
      </View>
      <Text style={[styles.licenseBadge, { color: c.blue, borderColor: c.blue + '40', backgroundColor: c.blue + '12' }]}>
        {license}
      </Text>
      <MaterialCommunityIcons name="open-in-new" size={13} color={c.muted} />
    </TouchableOpacity>
  );
}

export default function AttributionsScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const c      = useThemeStore(s => s.colors);

  return (
    <View style={[styles.screen, { backgroundColor: c.bg }]}>
      <View style={[styles.headerBar, { paddingTop: insets.top + 4, borderBottomColor: c.border, backgroundColor: c.card }]}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={8} style={styles.backBtn}>
          <MaterialCommunityIcons name="arrow-left" size={22} color={c.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: c.text }]}>Attributions</Text>
        <View style={styles.backBtn} />
      </View>

      <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 32 }]}>
        <Text style={[styles.intro, { color: c.muted }]}>
          Traefik Manager Mobile is built with the following open source libraries. Tap any entry to view its source and license.
        </Text>

        <Surface style={[styles.group, { backgroundColor: c.card }]} elevation={1}>
          {LIBRARIES.map((lib, i) => (
            <LibraryRow
              key={lib.name}
              {...lib}
              isLast={i === LIBRARIES.length - 1}
              c={c}
            />
          ))}
        </Surface>
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
  content:     { padding: spacing.lg, gap: spacing.md },
  intro: {
    fontSize: font.sm,
    lineHeight: 19,
    paddingHorizontal: 4,
  },
  group: { borderRadius: radius.md, overflow: 'hidden' },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.md,
    paddingVertical: 11,
    gap: spacing.sm,
  },
  rowBody:   { flex: 1 },
  rowName:   { fontSize: font.sm, fontWeight: '600' },
  rowAuthor: { fontSize: font.xs, marginTop: 1 },
  licenseBadge: {
    fontSize: font.xs,
    fontWeight: '600',
    paddingHorizontal: 7,
    paddingVertical: 3,
    borderRadius: 5,
    borderWidth: 1,
    overflow: 'hidden',
  },
});
