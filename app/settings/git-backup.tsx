import { ActivityIndicator, Alert, FlatList, StyleSheet, TouchableOpacity, View } from 'react-native';
import { Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useGitCommits, useGitMutations, useGitStatus } from '../../src/hooks/useBackups';
import { useThemeStore } from '../../src/store/theme';
import { GitCommit } from '../../src/api/backups';
import { font, radius, spacing } from '../../src/theme';

function relativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return `${Math.floor(hrs / 24)}d ago`;
}

export default function GitBackupScreen() {
  const router  = useRouter();
  const insets  = useSafeAreaInsets();
  const c       = useThemeStore(s => s.colors);

  const { data: status, isFetching: statusFetching, refetch: refetchStatus } = useGitStatus();
  const { data: commits, isFetching: commitsFetching, refetch: refetchCommits } = useGitCommits();
  const { push, restore } = useGitMutations();

  const isFetching = statusFetching || commitsFetching;

  const handlePush = async () => {
    await push.mutateAsync();
    refetchStatus();
    refetchCommits();
  };

  const handleRestore = (commit: GitCommit) => {
    Alert.alert(
      'Restore from Git',
      `Restore config from commit ${commit.sha.slice(0, 7)}?\n\n"${commit.message.slice(0, 80)}"`,
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Restore', style: 'destructive', onPress: () => restore.mutateAsync(commit.sha) },
      ],
    );
  };

  const renderCommit = ({ item }: { item: GitCommit }) => (
    <TouchableOpacity
      style={[styles.commitRow, { borderBottomColor: c.border }]}
      onPress={() => handleRestore(item)}
      activeOpacity={0.6}
    >
      <View style={[styles.shaChip, { backgroundColor: c.blue + '18', borderColor: c.blue + '44' }]}>
        <Text style={[styles.sha, { color: c.blue }]}>{item.sha.slice(0, 7)}</Text>
      </View>
      <View style={styles.commitInfo}>
        <Text style={[styles.commitMsg, { color: c.text }]} numberOfLines={2}>{item.message}</Text>
        <Text style={[styles.commitTime, { color: c.muted }]}>{relativeTime(item.timestamp)}</Text>
      </View>
      {restore.isPending ? (
        <ActivityIndicator size="small" color={c.muted} />
      ) : (
        <MaterialCommunityIcons name="restore" size={18} color={c.muted} />
      )}
    </TouchableOpacity>
  );

  return (
    <View style={[styles.screen, { backgroundColor: c.bg }]}>
      <View style={[styles.headerBar, { paddingTop: insets.top + 4, borderBottomColor: c.border, backgroundColor: c.card }]}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={8} style={styles.backBtn}>
          <MaterialCommunityIcons name="arrow-left" size={22} color={c.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: c.text }]}>Git Backup</Text>
        {isFetching && <ActivityIndicator size="small" color={c.muted} style={{ marginLeft: 'auto' }} />}
      </View>

      {!status?.enabled ? (
        <View style={styles.notConfigured}>
          <MaterialCommunityIcons name="source-branch-remove" size={40} color={c.muted} />
          <Text style={[styles.notConfiguredTitle, { color: c.text }]}>Git backup not configured</Text>
          <Text style={[styles.notConfiguredHint, { color: c.muted }]}>
            Set up git backup in Settings - Git on the web app
          </Text>
        </View>
      ) : (
        <FlatList
          data={commits ?? []}
          keyExtractor={item => item.sha}
          renderItem={renderCommit}
          contentContainerStyle={[styles.list, { paddingBottom: insets.bottom + 24 }]}
          ListHeaderComponent={
            <View style={styles.header}>
              <View style={[styles.statusCard, { backgroundColor: c.card, borderColor: c.border }]}>
                <View style={styles.statusRow}>
                  <MaterialCommunityIcons name="source-branch" size={16} color={c.blue} />
                  <Text style={[styles.statusLabel, { color: c.muted }]}>Branch</Text>
                  <Text style={[styles.statusValue, { color: c.text }]}>{status.branch ?? 'main'}</Text>
                </View>
                {status.repo && (
                  <View style={[styles.statusRow, { borderTopWidth: 1, borderTopColor: c.border }]}>
                    <MaterialCommunityIcons name="github" size={16} color={c.muted} />
                    <Text style={[styles.statusLabel, { color: c.muted }]}>Repo</Text>
                    <Text style={[styles.statusValue, { color: c.text }]} numberOfLines={1}>{status.repo}</Text>
                  </View>
                )}
                {status.last_push && (
                  <View style={[styles.statusRow, { borderTopWidth: 1, borderTopColor: c.border }]}>
                    <MaterialCommunityIcons name="clock-outline" size={16} color={c.muted} />
                    <Text style={[styles.statusLabel, { color: c.muted }]}>Last push</Text>
                    <Text style={[styles.statusValue, { color: c.text }]}>{relativeTime(status.last_push)}</Text>
                  </View>
                )}
              </View>

              <TouchableOpacity
                style={[styles.pushBtn, { backgroundColor: c.blue, opacity: push.isPending ? 0.6 : 1 }]}
                onPress={handlePush}
                disabled={push.isPending}
                activeOpacity={0.8}
              >
                {push.isPending
                  ? <ActivityIndicator size="small" color="#fff" />
                  : <MaterialCommunityIcons name="upload" size={18} color="#fff" />}
                <Text style={styles.pushBtnText}>Push Now</Text>
              </TouchableOpacity>

              <Text style={[styles.commitsLabel, { color: c.muted }]}>COMMIT HISTORY</Text>
            </View>
          }
          ListEmptyComponent={
            <View style={styles.empty}>
              <Text style={[styles.emptyText, { color: c.muted }]}>No commits yet</Text>
            </View>
          }
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen:    { flex: 1 },
  headerBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.md,
    borderBottomWidth: 1,
    gap: spacing.sm,
  },
  backBtn:     { padding: 2 },
  headerTitle: { fontSize: font.lg, fontWeight: '700' },
  list:        { padding: spacing.lg },
  header:      { gap: spacing.md, marginBottom: spacing.md },
  statusCard: {
    borderRadius: radius.md, borderWidth: 1, overflow: 'hidden',
  },
  statusRow: {
    flexDirection: 'row', alignItems: 'center', gap: spacing.sm,
    paddingHorizontal: spacing.md, paddingVertical: 12,
  },
  statusLabel: { fontSize: font.sm, width: 70 },
  statusValue: { flex: 1, fontSize: font.sm, fontWeight: '500' },
  pushBtn: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'center',
    gap: spacing.sm, padding: 14, borderRadius: radius.md,
  },
  pushBtnText: { color: '#fff', fontSize: font.md, fontWeight: '700' },
  commitsLabel: {
    fontSize: font.xs, fontWeight: '700', letterSpacing: 0.8,
    textTransform: 'uppercase', paddingHorizontal: 4,
  },
  commitRow: {
    flexDirection: 'row', alignItems: 'center', gap: spacing.sm,
    paddingVertical: 14, borderBottomWidth: 1,
  },
  shaChip: {
    paddingHorizontal: 8, paddingVertical: 3,
    borderRadius: radius.sm, borderWidth: 1, flexShrink: 0,
  },
  sha: { fontSize: font.xs, fontWeight: '700', fontFamily: 'monospace' },
  commitInfo: { flex: 1 },
  commitMsg:  { fontSize: font.sm, lineHeight: 18 },
  commitTime: { fontSize: font.xs, marginTop: 3 },
  notConfigured: {
    flex: 1, alignItems: 'center', justifyContent: 'center', padding: spacing.xl, gap: spacing.md,
  },
  notConfiguredTitle: { fontSize: font.lg, fontWeight: '700' },
  notConfiguredHint:  { fontSize: font.sm, textAlign: 'center' },
  empty: { paddingTop: spacing.xl, alignItems: 'center' },
  emptyText: { fontSize: font.sm },
});
