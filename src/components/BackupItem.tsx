import React from 'react';
import { Alert, StyleSheet, TouchableOpacity, View } from 'react-native';
import { Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Backup } from '../api/backups';
import { font, radius, spacing } from '../theme';
import { useThemeStore } from '../store/theme';

interface Props {
  backup: Backup;
  onRestore: (name: string) => void;
  onDelete: (name: string) => void;
  restoring: boolean;
}

export function BackupItem({ backup, onRestore, onDelete, restoring }: Props) {
  const c      = useThemeStore(s => s.colors);
  const sizeKb = (backup.size / 1024).toFixed(1);

  const confirmRestore = () => {
    Alert.alert(
      'Restore Backup',
      `Restore from ${backup.name}? This will overwrite the current configuration.`,
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Restore', style: 'destructive', onPress: () => onRestore(backup.name) },
      ],
    );
  };

  const confirmDelete = () => {
    Alert.alert(
      'Delete Backup',
      `Delete ${backup.name}?`,
      [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Delete', style: 'destructive', onPress: () => onDelete(backup.name) },
      ],
    );
  };

  return (
    <View style={styles.row}>
      <View style={[styles.iconWrap, { backgroundColor: c.blue + '1A', borderColor: c.blue + '33' }]}>
        <MaterialCommunityIcons name="database-export" size={20} color={c.blue} />
      </View>
      <View style={styles.info}>
        <Text style={[styles.name, { color: c.text }]} numberOfLines={1}>{backup.name}</Text>
        <Text style={[styles.meta, { color: c.muted }]}>{backup.modified} · {sizeKb} KB</Text>
      </View>
      <View style={styles.actions}>
        <TouchableOpacity
          style={[styles.btn, styles.restoreBtn, { backgroundColor: c.blue + '0F', borderColor: c.blue + '59' }, restoring && styles.btnDisabled]}
          onPress={confirmRestore}
          disabled={restoring}
        >
          <MaterialCommunityIcons
            name={restoring ? 'loading' : 'restore'}
            size={15}
            color={c.blue}
          />
          <Text style={[styles.btnTxt, { color: c.blue }]}>Restore</Text>
        </TouchableOpacity>
        <TouchableOpacity style={[styles.btn, styles.deleteBtn, { backgroundColor: c.red + '0F', borderColor: c.red + '4D' }]} onPress={confirmDelete}>
          <MaterialCommunityIcons name="trash-can-outline" size={15} color={c.red} />
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: spacing.sm + 2,
    paddingHorizontal: spacing.md,
    gap: spacing.sm,
  },
  iconWrap: {
    width: 36,
    height: 36,
    borderRadius: radius.sm,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  info:    { flex: 1 },
  name: {
    fontSize: font.sm,
    fontWeight: '600',
    fontFamily: 'monospace',
  },
  meta: {
    fontSize: font.xs,
    marginTop: 1,
  },
  actions: {
    flexDirection: 'row',
    gap: 6,
    alignItems: 'center',
  },
  btn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 8,
    paddingVertical: 6,
    borderRadius: radius.sm,
    borderWidth: 1,
  },
  restoreBtn: {},
  deleteBtn: {
    paddingHorizontal: 7,
  },
  btnDisabled: { opacity: 0.5 },
  btnTxt: { fontSize: font.xs, fontWeight: '700' },
});
