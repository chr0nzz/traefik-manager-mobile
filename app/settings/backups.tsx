import { ActivityIndicator, FlatList, RefreshControl, StyleSheet, TouchableOpacity, View } from 'react-native';
import { Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useState } from 'react';
import { BackupItem } from '../../src/components/BackupItem';
import { useBackupMutations, useBackups } from '../../src/hooks/useBackups';
import { useThemeStore } from '../../src/store/theme';
import { font, radius, spacing } from '../../src/theme';

export default function BackupsScreen() {
  const router  = useRouter();
  const insets  = useSafeAreaInsets();
  const c       = useThemeStore(s => s.colors);

  const { data: backups, isFetching, isError, refetch } = useBackups();
  const { create, restore, remove } = useBackupMutations();
  const [restoringFile, setRestoringFile] = useState<string | null>(null);

  const backupList = backups ?? [];

  const handleRestore = async (name: string) => {
    setRestoringFile(name);
    try { await restore.mutateAsync(name); }
    finally { setRestoringFile(null); }
  };

  const handleDelete = (name: string) => remove.mutateAsync(name);

  return (
    <View style={[styles.screen, { backgroundColor: c.bg }]}>
      {/* Header */}
      <View style={[styles.headerBar, { paddingTop: insets.top + 4, borderBottomColor: c.border, backgroundColor: c.card }]}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={8} style={styles.backBtn}>
          <MaterialCommunityIcons name="arrow-left" size={22} color={c.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: c.text }]}>
          Backups{backupList.length > 0 ? ` (${backupList.length})` : ''}
        </Text>
        <TouchableOpacity
          style={[styles.createBtn, { backgroundColor: c.blue + '18', borderColor: c.blue + '55' }]}
          onPress={() => create.mutateAsync()}
          disabled={create.isPending}
          hitSlop={8}
        >
          {create.isPending
            ? <ActivityIndicator size="small" color={c.blue} />
            : <MaterialCommunityIcons name="plus" size={18} color={c.blue} />}
          <Text style={[styles.createTxt, { color: c.blue }]}>Create</Text>
        </TouchableOpacity>
      </View>

      {isError && (
        <View style={[styles.message, { backgroundColor: c.card, borderColor: c.border }]}>
          <MaterialCommunityIcons name="alert-circle-outline" size={20} color={c.red} />
          <Text style={[styles.messageTxt, { color: c.red }]}>Failed to load backups</Text>
        </View>
      )}

      {backupList.length === 0 && !isFetching && !isError && (
        <View style={styles.emptyState}>
          <MaterialCommunityIcons name="database-export-outline" size={36} color={c.border} />
          <Text style={[styles.emptyTxt, { color: c.muted }]}>No backups yet</Text>
          <Text style={[styles.emptyHint, { color: c.muted }]}>Tap Create to make your first backup</Text>
        </View>
      )}

      <FlatList
        data={backupList}
        keyExtractor={b => b.name}
        contentContainerStyle={[styles.list, { paddingBottom: insets.bottom + 24 }]}
        ItemSeparatorComponent={() => <View style={[styles.separator, { backgroundColor: c.border }]} />}
        renderItem={({ item }) => (
          <View style={[styles.itemWrap, { backgroundColor: c.card, borderColor: c.border }]}>
            <BackupItem
              backup={item}
              onRestore={handleRestore}
              onDelete={handleDelete}
              restoring={restoringFile === item.name}
            />
          </View>
        )}
        refreshControl={
          <RefreshControl refreshing={isFetching} onRefresh={() => refetch()} tintColor={c.blue} />
        }
      />
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
  backBtn:    { padding: 2 },
  headerTitle:{ flex: 1, fontSize: font.lg, fontWeight: '700' },
  createBtn: {
    flexDirection: 'row', alignItems: 'center', gap: 4,
    paddingHorizontal: 10, paddingVertical: 6,
    borderRadius: radius.sm, borderWidth: 1,
  },
  createTxt:  { fontSize: font.xs, fontWeight: '700' },
  list:       { padding: spacing.lg, gap: spacing.sm },
  itemWrap:   { borderRadius: radius.md, borderWidth: 1, overflow: 'hidden' },
  separator:  { height: 1, marginVertical: 2 },
  message: {
    flexDirection: 'row', alignItems: 'center', gap: spacing.sm,
    margin: spacing.lg, padding: spacing.md,
    borderRadius: radius.md, borderWidth: 1,
  },
  messageTxt: { fontSize: font.sm },
  emptyState: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 8, paddingBottom: 60 },
  emptyTxt:   { fontSize: font.md, fontWeight: '600' },
  emptyHint:  { fontSize: font.sm },
});
