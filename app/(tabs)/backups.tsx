import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { FlatList, RefreshControl, StyleSheet, View } from 'react-native';
import { Button, Text } from 'react-native-paper';
import { BackupItem } from '../../src/components/BackupItem';
import { useBackupMutations, useBackups } from '../../src/hooks/useBackups';
import { font, radius, spacing } from '../../src/theme';
import { useThemeStore } from '../../src/store/theme';
import { useLayout } from '../../src/hooks/useLayout';

export default function BackupsScreen() {
  const [restoringFile, setRestoringFile] = useState<string | null>(null);
  const { data, isFetching, isError, error } = useBackups();
  const { create, restore, remove } = useBackupMutations();
  const qc        = useQueryClient();
  const c         = useThemeStore(s => s.colors);
  const { listBottomPadding } = useLayout();

  const backups = data?.backups ?? [];

  const handleCreate = async () => {
    await create.mutateAsync();
  };

  const handleRestore = async (filename: string) => {
    setRestoringFile(filename);
    try {
      await restore.mutateAsync(filename);
    } finally {
      setRestoringFile(null);
    }
  };

  const handleDelete = async (filename: string) => {
    await remove.mutateAsync(filename);
  };

  return (
    <View style={[styles.container, { backgroundColor: c.bg }]}>
      <View style={[styles.header, { borderBottomColor: c.border }]}>
        <Text style={[styles.count, { color: c.muted }]}>{backups.length} backup{backups.length !== 1 ? 's' : ''}</Text>
        <Button
          mode="contained"
          onPress={handleCreate}
          loading={create.isPending}
          disabled={create.isPending}
          compact
          icon="plus"
          labelStyle={{ fontSize: font.sm }}
        >
          Create Backup
        </Button>
      </View>

      {isError && (
        <View style={[styles.errorBox, { backgroundColor: c.red + '1A', borderColor: c.red + '4D' }]}>
          <Text style={[styles.errorText, { color: c.red }]}>{(error as Error)?.message ?? 'Failed to load'}</Text>
        </View>
      )}

      <FlatList
        data={backups}
        keyExtractor={b => b.filename}
        renderItem={({ item }) => (
          <BackupItem
            backup={item}
            onRestore={handleRestore}
            onDelete={handleDelete}
            restoring={restoringFile === item.filename}
          />
        )}
        scrollEventThrottle={16}
        contentContainerStyle={{ paddingBottom: listBottomPadding }}
        refreshControl={
          <RefreshControl
            refreshing={isFetching}
            onRefresh={() => qc.invalidateQueries({ queryKey: ['backups'] })}
            tintColor={c.blue}
          />
        }
        ListEmptyComponent={
          <Text style={[styles.empty, { color: c.muted }]}>{isFetching ? 'Loading…' : 'No backups yet'}</Text>
        }
        style={styles.list}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: spacing.md,
    borderBottomWidth: 1,
  },
  count: { fontSize: font.sm },
  list:  { flex: 1 },
  empty: { textAlign: 'center', fontSize: font.base, marginTop: spacing.xxl },
  errorBox: {
    borderRadius: radius.sm,
    margin: spacing.md, padding: spacing.md, borderWidth: 1,
  },
  errorText: { fontSize: font.sm },
});
