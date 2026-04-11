import React, { useState } from 'react';
import {
  FlatList,
  Modal,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  TouchableWithoutFeedback,
  View,
} from 'react-native';
import { Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { font, radius, spacing } from '../theme';
import { ConfigEntry } from '../api/routes';

interface Props {
  files: ConfigEntry[];
  configDirSet: boolean;
  value: string;
  onChange: (value: string) => void;
  allowNew?: boolean;
  c: {
    bg: string; card: string; border: string; text: string;
    muted: string; purple: string; green: string;
  };
}

const NEW_FILE = '__new__';

export function ConfigFilePicker({ files, configDirSet, value, onChange, allowNew = false, c }: Props) {
  const [open, setOpen] = useState(false);
  const [newName, setNewName] = useState('');
  const isNew = value === NEW_FILE || (allowNew && configDirSet && !files.find(f => f.label === value) && value !== '');

  const showNewInput = allowNew && configDirSet && value === NEW_FILE;
  const displayLabel = value === NEW_FILE
    ? (newName || 'Enter filename...')
    : (value || 'Select a file...');

  const options: Array<{ label: string; value: string; isNew?: boolean }> = [];
  if (allowNew && configDirSet) {
    options.push({ label: 'New file...', value: NEW_FILE, isNew: true });
  }
  files.forEach(f => options.push({ label: f.label, value: f.label }));

  const handleSelect = (val: string) => {
    setOpen(false);
    if (val === NEW_FILE) {
      onChange(NEW_FILE);
    } else {
      setNewName('');
      onChange(val);
    }
  };

  const handleNewName = (text: string) => {
    setNewName(text);
    onChange(text);
  };

  const handleNewNameBlur = () => {
    if (newName && !/\.ya?ml$/.test(newName)) {
      const normalized = newName + '.yml';
      setNewName(normalized);
      onChange(normalized);
    }
  };

  return (
    <View style={styles.wrapper}>
      <TouchableOpacity
        style={[styles.trigger, { backgroundColor: c.bg, borderColor: value ? c.purple : c.border }]}
        onPress={() => setOpen(true)}
        activeOpacity={0.7}
      >
        <Text
          style={[styles.triggerText, { color: value && value !== NEW_FILE ? c.text : c.muted }]}
          numberOfLines={1}
        >
          {value === NEW_FILE ? (newName || 'Enter filename below...') : displayLabel}
        </Text>
        <MaterialCommunityIcons name="chevron-down" size={18} color={c.muted} />
      </TouchableOpacity>

      {value === NEW_FILE && allowNew && configDirSet && (
        <TextInput
          style={[styles.newInput, { backgroundColor: c.bg, borderColor: c.purple, color: c.text }]}
          value={newName}
          onChangeText={handleNewName}
          onBlur={handleNewNameBlur}
          placeholder="app-myservice.yml"
          placeholderTextColor={c.muted}
          autoCapitalize="none"
          autoCorrect={false}
          autoFocus
        />
      )}

      <Modal visible={open} transparent animationType="fade" onRequestClose={() => setOpen(false)}>
        <TouchableWithoutFeedback onPress={() => setOpen(false)}>
          <View style={styles.overlay}>
            <TouchableWithoutFeedback>
              <View style={[styles.sheet, { backgroundColor: c.card, borderColor: c.border }]}>
                <FlatList
                  data={options}
                  keyExtractor={item => item.value}
                  renderItem={({ item }) => {
                    const selected = item.value === value || (item.value !== NEW_FILE && item.value === value);
                    return (
                      <TouchableOpacity
                        style={[
                          styles.option,
                          { borderBottomColor: c.border },
                          selected && { backgroundColor: c.purple + '18' },
                        ]}
                        onPress={() => handleSelect(item.value)}
                        activeOpacity={0.7}
                      >
                        {item.isNew ? (
                          <MaterialCommunityIcons name="plus" size={14} color={c.green} style={{ marginRight: 6 }} />
                        ) : (
                          <MaterialCommunityIcons name="file-outline" size={14} color={c.muted} style={{ marginRight: 6 }} />
                        )}
                        <Text style={[
                          styles.optionText,
                          { color: item.isNew ? c.green : selected ? c.purple : c.text },
                        ]}>
                          {item.label}
                        </Text>
                        {selected && !item.isNew && (
                          <MaterialCommunityIcons name="check" size={14} color={c.purple} style={{ marginLeft: 'auto' }} />
                        )}
                      </TouchableOpacity>
                    );
                  }}
                />
              </View>
            </TouchableWithoutFeedback>
          </View>
        </TouchableWithoutFeedback>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper:     { gap: 6 },
  trigger: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    borderWidth: 1, borderRadius: radius.sm,
    paddingHorizontal: spacing.sm, paddingVertical: 10,
  },
  triggerText: { flex: 1, fontSize: font.sm },
  newInput: {
    borderWidth: 1, borderRadius: radius.sm,
    paddingHorizontal: spacing.sm, paddingVertical: 9,
    fontSize: font.sm,
  },
  overlay: {
    flex: 1, backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center', paddingHorizontal: spacing.lg,
  },
  sheet: {
    borderRadius: radius.md, borderWidth: 1,
    maxHeight: 360, overflow: 'hidden',
  },
  option: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: spacing.md, paddingVertical: 13,
    borderBottomWidth: 1,
  },
  optionText: { fontSize: font.sm, flex: 1 },
});
