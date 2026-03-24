import React, { useState } from 'react';
import { Modal, Pressable, StyleSheet, TouchableOpacity, View } from 'react-native';
import { Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { font, radius, spacing } from '../theme';
import { useThemeStore } from '../store/theme';

interface Option {
  value: string;
  label: string;
}

interface Props {
  value: string;
  options: Option[];
  onChange: (value: string) => void;
  placeholder?: string;
  label?: string;
}

export function FilterDropdown({ value, options, onChange, placeholder = 'All', label }: Props) {
  const [open, setOpen] = useState(false);
  const c          = useThemeStore(s => s.colors);
  const current    = options.find(o => o.value === value);
  const isFiltered = value !== 'all' && value !== '';

  const displayLabel = current?.label ?? placeholder;

  return (
    <>
      <TouchableOpacity
        style={[
          styles.chip,
          { borderColor: c.border, backgroundColor: c.card },
          isFiltered && { borderColor: c.blue + '88', backgroundColor: c.blue + '18' },
        ]}
        onPress={() => setOpen(true)}
        activeOpacity={0.7}
      >
        <Text style={[styles.chipText, { color: isFiltered ? c.blue : c.muted }]} numberOfLines={1}>
          {displayLabel}
        </Text>
        <MaterialCommunityIcons
          name="chevron-down"
          size={14}
          color={isFiltered ? c.blue : c.muted}
        />
      </TouchableOpacity>

      <Modal visible={open} transparent animationType="slide" onRequestClose={() => setOpen(false)}>
        <View style={styles.overlay}>
          <Pressable style={StyleSheet.absoluteFill} onPress={() => setOpen(false)} />

          <View style={[styles.sheet, { backgroundColor: c.card, borderColor: c.border }]}>
            {/* Handle */}
            <View style={[styles.handle, { backgroundColor: c.border }]} />

            {/* Header */}
            <View style={[styles.header, { borderBottomColor: c.border }]}>
              <Text style={[styles.headerTitle, { color: c.text }]}>
                {label ?? placeholder}
              </Text>
              <TouchableOpacity onPress={() => setOpen(false)} hitSlop={12}>
                <MaterialCommunityIcons name="close" size={20} color={c.muted} />
              </TouchableOpacity>
            </View>

            {/* Options */}
            {options.map((opt, i) => {
              const active = opt.value === value;
              const isLast = i === options.length - 1;
              return (
                <TouchableOpacity
                  key={opt.value}
                  style={[
                    styles.option,
                    !isLast && { borderBottomWidth: 1, borderBottomColor: c.border },
                    active && { backgroundColor: c.blue + '12' },
                  ]}
                  onPress={() => { onChange(opt.value); setOpen(false); }}
                  activeOpacity={0.6}
                >
                  <View style={[
                    styles.radio,
                    { borderColor: active ? c.blue : c.border },
                    active && { backgroundColor: c.blue },
                  ]}>
                    {active && <View style={styles.radioDot} />}
                  </View>
                  <Text style={[
                    styles.optionText,
                    { color: active ? c.text : c.muted },
                    active && { fontWeight: '600' },
                  ]}>
                    {opt.label}
                  </Text>
                  {active && (
                    <MaterialCommunityIcons name="check" size={16} color={c.blue} />
                  )}
                </TouchableOpacity>
              );
            })}
          </View>
        </View>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 7,
    borderRadius: radius.full,
    borderWidth: 1,
  },
  chipText: {
    fontSize: font.sm,
    fontWeight: '600',
    maxWidth: 90,
  },
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.55)',
    justifyContent: 'flex-end',
  },
  sheet: {
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    borderTopWidth: 1,
    paddingBottom: 32,
  },
  handle: {
    width: 36, height: 4, borderRadius: 2,
    alignSelf: 'center',
    marginTop: 10, marginBottom: 6,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    borderBottomWidth: 1,
    marginBottom: 4,
  },
  headerTitle: {
    fontSize: font.lg,
    fontWeight: '700',
  },
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
    paddingHorizontal: spacing.lg,
    paddingVertical: 14,
  },
  radio: {
    width: 18, height: 18,
    borderRadius: 9,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  radioDot: {
    width: 6, height: 6,
    borderRadius: 3,
    backgroundColor: '#fff',
  },
  optionText: {
    flex: 1,
    fontSize: font.md,
  },
});
