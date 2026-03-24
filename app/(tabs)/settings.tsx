import { useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { Alert, Animated, FlatList, Modal, RefreshControl, ScrollView, StyleSheet, TouchableOpacity, View, useColorScheme } from 'react-native';
import { Button, Divider, List, Surface, Text } from 'react-native-paper';
import { BackupItem } from '../../src/components/BackupItem';
import { TopBar } from '../../src/components/TopBar';
import { useConnection } from '../../src/store/connection';
import { useThemeStore, ThemeMode } from '../../src/store/theme';
import { useTabSwipe } from '../../src/hooks/useTabSwipe';
import { useBackupMutations, useBackups } from '../../src/hooks/useBackups';
import { useRef, useState } from 'react';

const PREVIEW = 5;

export default function SettingsScreen() {
  const { baseUrl, clearConnection }  = useConnection();
  const { mode, setMode }             = useThemeStore();
  const c                             = useThemeStore(s => s.colors);
  const colorScheme                   = useColorScheme();
  const systemIsDark                  = colorScheme === 'dark';
  const qc                            = useQueryClient();
  const router                        = useRouter();
  const swipe                         = useTabSwipe('settings');
  const scrollAnim                    = useRef(new Animated.Value(0)).current;

  const { data: backups, isFetching, isError, refetch } = useBackups();
  const { create, restore, remove } = useBackupMutations();
  const [restoringFile, setRestoringFile] = useState<string | null>(null);
  const [showAll, setShowAll] = useState(false);

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

  const handleRestore = async (name: string) => {
    setRestoringFile(name);
    try { await restore.mutateAsync(name); }
    finally { setRestoringFile(null); }
  };

  const handleDelete = (name: string) => remove.mutateAsync(name);

  const backupList  = backups ?? [];
  const preview     = backupList.slice(0, PREVIEW);
  const hasMore     = backupList.length > PREVIEW;

  return (
    <View style={[styles.container, { backgroundColor: c.bg }]} {...swipe}>
      <TopBar title="Settings" scrollAnim={scrollAnim} accent={c.blue} />
    <Animated.ScrollView
      style={styles.scroll}
      contentContainerStyle={styles.content}
      onScroll={Animated.event(
        [{ nativeEvent: { contentOffset: { y: scrollAnim } } }],
        { useNativeDriver: false },
      )}
      scrollEventThrottle={16}
    >
      <Surface style={[styles.section, { backgroundColor: c.card, borderColor: c.border }]} elevation={1}>
        <List.Item
          title="Theme"
          description="Choose light, dark, or follow system"
          titleStyle={{ color: c.text }}
          descriptionStyle={{ color: c.muted }}
          left={() => <List.Icon icon="theme-light-dark" color={c.blue} />}
        />
        <View style={[styles.themeRow, { borderTopColor: c.border }]}>
          {(['light', 'system', 'dark'] as ThemeMode[]).map(m => (
            <TouchableOpacity
              key={m}
              style={[styles.themeBtn, { borderColor: c.border, backgroundColor: c.bg }, mode === m && { borderColor: c.blue, backgroundColor: c.blue + '18' }]}
              onPress={() => setMode(m, systemIsDark)}
            >
              <Text style={[styles.themeBtnTxt, { color: mode === m ? c.blue : c.muted }]}>
                {m === 'light' ? '☀ Light' : m === 'dark' ? '🌙 Dark' : '⚙ System'}
              </Text>
            </TouchableOpacity>
          ))}
        </View>
      </Surface>

      <Surface style={[styles.section, { backgroundColor: c.card, borderColor: c.border }]} elevation={1}>
        <List.Item
          title="Server"
          description={baseUrl || 'Not connected'}
          titleStyle={{ color: c.text }}
          descriptionStyle={{ color: c.muted, fontFamily: 'monospace' }}
          descriptionNumberOfLines={2}
          left={() => <List.Icon icon="server" color={c.muted} />}
        />
        <Divider style={{ backgroundColor: c.border, marginLeft: 56 }} />
        <View style={styles.disconnectWrap}>
          <Button
            mode="outlined"
            onPress={handleDisconnect}
            textColor={c.red}
            style={[styles.disconnectBtn, { borderColor: c.red + '55' }]}
            icon="logout"
          >
            Disconnect
          </Button>
        </View>
      </Surface>

      <Surface style={[styles.section, { backgroundColor: c.card, borderColor: c.border }]} elevation={1}>
        <View style={[styles.sectionHeader, { borderBottomColor: c.border }]}>
          <View>
            <Text style={[styles.sectionTitle, { color: c.text }]}>Backups</Text>
            <Text style={[styles.sectionSub, { color: c.muted }]}>
              {isFetching ? 'Loading…' : `${backupList.length} backup${backupList.length !== 1 ? 's' : ''}`}
            </Text>
          </View>
          <Button
            mode="contained"
            onPress={() => create.mutateAsync()}
            loading={create.isPending}
            disabled={create.isPending}
            compact
            icon="plus"
            labelStyle={{ fontSize: 12 }}
          >
            Create
          </Button>
        </View>

        {isError && (
          <Text style={[styles.msgText, { color: c.red }]}>Failed to load backups</Text>
        )}
        {backupList.length === 0 && !isFetching && !isError && (
          <Text style={[styles.msgText, { color: c.muted }]}>No backups yet</Text>
        )}

        {preview.map((b, i) => (
          <View key={b.name}>
            <BackupItem
              backup={b}
              onRestore={handleRestore}
              onDelete={handleDelete}
              restoring={restoringFile === b.name}
            />
            {i < preview.length - 1 && <Divider style={{ backgroundColor: c.border }} />}
          </View>
        ))}

        {hasMore && (
          <TouchableOpacity
            style={[styles.viewAllBtn, { borderTopColor: c.border }]}
            onPress={() => setShowAll(true)}
          >
            <Text style={[styles.viewAllText, { color: c.blue }]}>
              View all {backupList.length} backups
            </Text>
          </TouchableOpacity>
        )}
      </Surface>

      <Text style={[styles.version, { color: c.muted }]}>Traefik Manager Mobile v0.1.0</Text>

      <Modal visible={showAll} animationType="slide" onRequestClose={() => setShowAll(false)}>
        <View style={[styles.modalRoot, { backgroundColor: c.bg }]}>
          <View style={[styles.modalHeader, { borderBottomColor: c.border, backgroundColor: c.card }]}>
            <Text style={[styles.modalTitle, { color: c.text }]}>
              All Backups ({backupList.length})
            </Text>
            <TouchableOpacity onPress={() => setShowAll(false)} style={styles.modalClose}>
              <Text style={[styles.modalCloseTxt, { color: c.muted }]}>✕</Text>
            </TouchableOpacity>
          </View>

          <FlatList
            data={backupList}
            keyExtractor={b => b.name}
            renderItem={({ item, index }) => (
              <View key={item.name}>
                <BackupItem
                  backup={item}
                  onRestore={handleRestore}
                  onDelete={handleDelete}
                  restoring={restoringFile === item.name}
                />
                {index < backupList.length - 1 && <Divider style={{ backgroundColor: c.border }} />}
              </View>
            )}
            contentContainerStyle={{ paddingBottom: 40 }}
            refreshControl={
              <RefreshControl refreshing={isFetching} onRefresh={() => refetch()} tintColor={c.blue} />
            }
          />
        </View>
      </Modal>
    </Animated.ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  scroll:    { flex: 1 },
  content:   { padding: 16, gap: 12, paddingBottom: 110 },
  section: {
    borderRadius: 12,
    borderWidth: 1,
    overflow: 'hidden',
  },
  themeRow: {
    flexDirection: 'row',
    gap: 8,
    padding: 12,
    borderTopWidth: 1,
  },
  themeBtn: {
    flex: 1,
    paddingVertical: 8,
    borderRadius: 8,
    borderWidth: 1,
    alignItems: 'center',
  },
  themeBtnTxt: { fontSize: 12, fontWeight: '700' },
  disconnectWrap: { padding: 16 },
  disconnectBtn:  { borderRadius: 8 },
  sectionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    borderBottomWidth: 1,
  },
  sectionTitle: { fontSize: 15, fontWeight: '700' },
  sectionSub:   { fontSize: 12, marginTop: 2 },
  msgText: {
    textAlign: 'center',
    fontSize: 13,
    padding: 20,
  },
  viewAllBtn: {
    padding: 14,
    borderTopWidth: 1,
    alignItems: 'center',
  },
  viewAllText: {
    fontSize: 13,
    fontWeight: '700',
  },
  version: {
    textAlign: 'center',
    fontSize: 11,
    marginTop: 4,
  },
  modalRoot: { flex: 1 },
  modalHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 16,
    borderBottomWidth: 1,
  },
  modalTitle:    { fontSize: 16, fontWeight: '800' },
  modalClose:    { padding: 8 },
  modalCloseTxt: { fontSize: 18, fontWeight: '600' },
});
