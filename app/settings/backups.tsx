import { ActivityIndicator, RefreshControl, ScrollView, StyleSheet, TouchableOpacity, View } from 'react-native';
import { Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useState } from 'react';
import { BackupItem } from '../../src/components/BackupItem';
import { useBackupMutations, useBackups } from '../../src/hooks/useBackups';
import { useThemeStore } from '../../src/store/theme';
import { Backup } from '../../src/api/backups';
import { font, radius, spacing } from '../../src/theme';

function SectionHeader({
  title, icon, onCreate, creating, c,
}: {
  title: string; icon: string;
  onCreate: () => void; creating: boolean;
  c: ReturnType<typeof useThemeStore.getState>['colors'];
}) {
  return (
    <View style={[styles.sectionHeader, { borderBottomColor: c.border }]}>
      <View style={styles.sectionTitle}>
        <MaterialCommunityIcons name={icon as any} size={15} color={c.muted} />
        <Text style={[styles.sectionLabel, { color: c.muted }]}>{title}</Text>
      </View>
      <TouchableOpacity
        style={[styles.createBtn, { backgroundColor: c.blue + '18', borderColor: c.blue + '55' }]}
        onPress={onCreate}
        disabled={creating}
        hitSlop={8}
      >
        {creating
          ? <ActivityIndicator size="small" color={c.blue} />
          : <MaterialCommunityIcons name="plus" size={16} color={c.blue} />}
        <Text style={[styles.createTxt, { color: c.blue }]}>Create</Text>
      </TouchableOpacity>
    </View>
  );
}

function BackupSection({
  backups, label, icon, onCreate, creating, onRestore, onDelete, restoringFile, emptyHint, c,
}: {
  backups: Backup[]; label: string; icon: string;
  onCreate: () => void; creating: boolean;
  onRestore: (n: string) => void; onDelete: (n: string) => void;
  restoringFile: string | null; emptyHint: string;
  c: ReturnType<typeof useThemeStore.getState>['colors'];
}) {
  return (
    <View style={[styles.section, { backgroundColor: c.card, borderColor: c.border }]}>
      <SectionHeader title={label} icon={icon} onCreate={onCreate} creating={creating} c={c} />
      {backups.length === 0 ? (
        <View style={styles.emptySection}>
          <Text style={[styles.emptySectionTxt, { color: c.muted }]}>{emptyHint}</Text>
        </View>
      ) : (
        backups.map((item, i) => (
          <View key={item.name} style={[styles.itemWrap, i > 0 && { borderTopWidth: 1, borderTopColor: c.border }]}>
            <BackupItem
              backup={item}
              onRestore={onRestore}
              onDelete={onDelete}
              restoring={restoringFile === item.name}
            />
          </View>
        ))
      )}
    </View>
  );
}

export default function BackupsScreen() {
  const router  = useRouter();
  const insets  = useSafeAreaInsets();
  const c       = useThemeStore(s => s.colors);

  const { data, isFetching, isError, refetch } = useBackups();
  const { create, createStatic, restore, remove } = useBackupMutations();
  const [restoringFile, setRestoringFile] = useState<string | null>(null);

  const allBackups: Backup[] = data ?? [];
  const routeBackups  = allBackups.filter(b => b.kind !== 'static');
  const staticBackups = allBackups.filter(b => b.kind === 'static');

  const handleRestore = async (name: string) => {
    setRestoringFile(name);
    try { await restore.mutateAsync(name); }
    finally { setRestoringFile(null); }
  };

  const handleDelete = (name: string) => remove.mutateAsync(name);

  return (
    <View style={[styles.screen, { backgroundColor: c.bg }]}>
      <View style={[styles.headerBar, { paddingTop: insets.top + 4, borderBottomColor: c.border, backgroundColor: c.card }]}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={8} style={styles.backBtn}>
          <MaterialCommunityIcons name="arrow-left" size={22} color={c.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: c.text }]}>
          Backups{allBackups.length > 0 ? ` (${allBackups.length})` : ''}
        </Text>
      </View>

      {isError && (
        <View style={[styles.message, { backgroundColor: c.card, borderColor: c.border }]}>
          <MaterialCommunityIcons name="alert-circle-outline" size={20} color={c.red} />
          <Text style={[styles.messageTxt, { color: c.red }]}>Failed to load backups</Text>
        </View>
      )}

      <ScrollView
        contentContainerStyle={[styles.list, { paddingBottom: insets.bottom + 24 }]}
        refreshControl={<RefreshControl refreshing={isFetching} onRefresh={() => refetch()} tintColor={c.blue} />}
      >
        <BackupSection
          label="Route Config"
          icon="file-code-outline"
          backups={routeBackups}
          onCreate={() => create.mutateAsync()}
          creating={create.isPending}
          onRestore={handleRestore}
          onDelete={handleDelete}
          restoringFile={restoringFile}
          emptyHint="No route config backups yet"
          c={c}
        />
        <BackupSection
          label="Static Config"
          icon="cog-outline"
          backups={staticBackups}
          onCreate={() => createStatic.mutateAsync()}
          creating={createStatic.isPending}
          onRestore={handleRestore}
          onDelete={handleDelete}
          restoringFile={restoringFile}
          emptyHint="No static config backups yet"
          c={c}
        />
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen:      { flex: 1 },
  headerBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.md,
    borderBottomWidth: 1,
    gap: spacing.sm,
  },
  backBtn:     { padding: 2 },
  headerTitle: { flex: 1, fontSize: font.lg, fontWeight: '700' },
  list:        { padding: spacing.lg, gap: spacing.lg },
  section: {
    borderRadius: radius.md,
    borderWidth: 1,
    overflow: 'hidden',
  },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.md,
    paddingVertical: 10,
    borderBottomWidth: 1,
  },
  sectionTitle: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  sectionLabel: { fontSize: font.xs, fontWeight: '700', textTransform: 'uppercase', letterSpacing: 0.5 },
  createBtn: {
    flexDirection: 'row', alignItems: 'center', gap: 4,
    paddingHorizontal: 10, paddingVertical: 5,
    borderRadius: radius.sm, borderWidth: 1,
  },
  createTxt:   { fontSize: font.xs, fontWeight: '700' },
  itemWrap:    {},
  emptySection: { padding: spacing.lg, alignItems: 'center' },
  emptySectionTxt: { fontSize: font.sm },
  message: {
    flexDirection: 'row', alignItems: 'center', gap: spacing.sm,
    margin: spacing.lg, padding: spacing.md,
    borderRadius: radius.md, borderWidth: 1,
  },
  messageTxt:  { fontSize: font.sm },
});
