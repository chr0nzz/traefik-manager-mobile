import React, { useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  View,
  useWindowDimensions,
} from 'react-native';
import { Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { RouteFormData } from '../api/routes';
import { font, radius, spacing } from '../theme';
import { useThemeStore } from '../store/theme';
import { useSaveRoute } from '../hooks/useRoutes';
import { useKeyboardHeight } from '../hooks/useKeyboardHeight';

interface Props {
  visible: boolean;
  onClose: () => void;
}

const PROTOCOLS = ['http', 'tcp', 'udp'];

export function AddRouteModal({ visible, onClose }: Props) {
  const c               = useThemeStore(s => s.colors);
  const { height: screenH } = useWindowDimensions();
  const kbHeight        = useKeyboardHeight();
  const saveRoute       = useSaveRoute();

  const [fName,   setFName]   = useState('');
  const [fHost,   setFHost]   = useState('');
  const [fIp,     setFIp]     = useState('');
  const [fPort,   setFPort]   = useState('');
  const [fProto,  setFProto]  = useState('http');
  const [fMws,    setFMws]    = useState('');
  const [saving,  setSaving]  = useState(false);
  const [saveErr, setSaveErr] = useState('');

  useEffect(() => {
    if (visible) {
      setFName(''); setFHost(''); setFIp(''); setFPort('');
      setFProto('http'); setFMws(''); setSaveErr('');
    }
  }, [visible]);

  const handleSave = () => {
    if (!fName.trim() || !fIp.trim()) { setSaveErr('Name and target IP are required'); return; }
    setSaving(true);
    setSaveErr('');
    const data: RouteFormData = {
      serviceName: fName.trim(),
      subdomain:   fHost.trim(),
      targetIp:    fIp.trim(),
      targetPort:  fPort.trim(),
      protocol:    fProto,
      middlewares: fMws.trim(),
    };
    saveRoute.mutate(
      { data, isEdit: false, originalId: '' },
      {
        onSuccess: (res) => {
          setSaving(false);
          if (res.ok) { onClose(); }
          else { setSaveErr(res.message ?? 'Save failed'); }
        },
        onError: (e) => { setSaving(false); setSaveErr(e.message); },
      },
    );
  };

  // On iOS we offset the sheet above the keyboard manually.
  // On Android adjustResize shrinks the window, so bottom:0 is correct.
  const sheetBottom = Platform.OS === 'ios' ? kbHeight : 0;

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <Pressable style={StyleSheet.absoluteFill} onPress={onClose} />
        <View style={[styles.sheet, { backgroundColor: c.card, borderColor: c.border, bottom: sheetBottom, maxHeight: screenH * 0.88 }]}>
          <View style={[styles.handle, { backgroundColor: c.border }]} />
          <View style={[styles.sheetHeader, { borderBottomColor: c.border, backgroundColor: c.card }]}>
            <MaterialCommunityIcons name="plus-circle-outline" size={20} color={c.green} />
            <Text style={[styles.sheetTitle, { color: c.text }]}>New Route</Text>
            <TouchableOpacity onPress={onClose} hitSlop={12} style={{ marginLeft: 'auto' }}>
              <MaterialCommunityIcons name="close" size={20} color={c.muted} />
            </TouchableOpacity>
          </View>

          <ScrollView
            style={styles.scroll}
            contentContainerStyle={styles.scrollContent}
            keyboardShouldPersistTaps="handled"
            bounces={false}
          >
            <FormField label="NAME" value={fName} onChange={setFName} c={c} placeholder="my-service" />
            <FormField label="HOST / DOMAIN" value={fHost} onChange={setFHost} c={c} placeholder="app.example.com" keyboardType="url" />
            <FormField label="TARGET IP / HOST" value={fIp} onChange={setFIp} c={c} placeholder="192.168.1.10" keyboardType="url" />
            <FormField label="TARGET PORT" value={fPort} onChange={setFPort} c={c} placeholder="8080" keyboardType="numeric" />
            <FormField label="MIDDLEWARES (comma-separated)" value={fMws} onChange={setFMws} c={c} placeholder="auth@file, compress" />

            <Text style={[styles.formLabel, { color: c.muted }]}>PROTOCOL</Text>
            <View style={styles.protoRow}>
              {PROTOCOLS.map(p => (
                <TouchableOpacity
                  key={p}
                  style={[styles.protoBtn, { borderColor: c.border, backgroundColor: c.bg }, fProto === p && { backgroundColor: c.green + '20', borderColor: c.green }]}
                  onPress={() => setFProto(p)}
                >
                  <Text style={[styles.protoBtnTxt, { color: fProto === p ? c.green : c.muted }]}>{p.toUpperCase()}</Text>
                </TouchableOpacity>
              ))}
            </View>

            {!!saveErr && <Text style={[styles.errTxt, { color: c.red }]}>{saveErr}</Text>}

            <View style={styles.actionRow}>
              <TouchableOpacity
                style={[styles.actionBtn, { backgroundColor: c.green, borderColor: c.green }]}
                onPress={handleSave}
                disabled={saving}
              >
                {saving
                  ? <ActivityIndicator size="small" color="#fff" />
                  : <Text style={[styles.actionBtnTxt, { color: '#fff' }]}>Create Route</Text>}
              </TouchableOpacity>
              <TouchableOpacity style={[styles.actionBtn, { backgroundColor: c.bg, borderColor: c.border }]} onPress={onClose}>
                <Text style={[styles.actionBtnTxt, { color: c.muted }]}>Cancel</Text>
              </TouchableOpacity>
            </View>
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}

function FormField({
  label, value, onChange, c, placeholder, keyboardType,
}: {
  label: string; value: string; onChange: (v: string) => void;
  c: ReturnType<typeof useThemeStore.getState>['colors'];
  placeholder?: string;
  keyboardType?: 'default' | 'url' | 'numeric';
}) {
  return (
    <View style={styles.formGroup}>
      <Text style={[styles.formLabel, { color: c.muted }]}>{label}</Text>
      <TextInput
        style={[styles.formInput, { backgroundColor: c.bg, borderColor: c.border, color: c.text }]}
        value={value}
        onChangeText={onChange}
        placeholder={placeholder}
        placeholderTextColor={c.muted}
        autoCapitalize="none"
        autoCorrect={false}
        keyboardType={keyboardType ?? 'default'}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.55)' },
  sheet: {
    position: 'absolute', left: 0, right: 0,
    borderTopLeftRadius: 20, borderTopRightRadius: 20, overflow: 'hidden',
  },
  handle: { width: 36, height: 4, borderRadius: 2, alignSelf: 'center', marginTop: 10, marginBottom: 4 },
  sheetHeader: {
    flexDirection: 'row', alignItems: 'center', gap: spacing.sm,
    paddingHorizontal: spacing.lg, paddingVertical: spacing.md,
    borderBottomWidth: 1,
  },
  sheetTitle: { fontSize: font.lg, fontWeight: '700' },
  scroll:        { flexShrink: 1 },
  scrollContent: { padding: spacing.lg, gap: spacing.md, paddingBottom: spacing.xl },
  formGroup:   { gap: 4 },
  formLabel:   { fontSize: font.xs, fontWeight: '700', letterSpacing: 0.5 },
  formInput:   { borderWidth: 1, borderRadius: radius.sm, paddingHorizontal: spacing.sm, paddingVertical: 9, fontSize: font.sm },
  protoRow:    { flexDirection: 'row', gap: spacing.sm },
  protoBtn:    { flex: 1, paddingVertical: 8, borderRadius: radius.sm, borderWidth: 1, alignItems: 'center' },
  protoBtnTxt: { fontSize: font.sm, fontWeight: '700' },
  errTxt:      { fontSize: font.sm, marginTop: 4 },
  actionRow:   { flexDirection: 'row', gap: spacing.sm, marginTop: spacing.sm },
  actionBtn:   { flex: 1, paddingVertical: 12, borderRadius: radius.sm, borderWidth: 1, alignItems: 'center' },
  actionBtnTxt:{ fontSize: font.sm, fontWeight: '600' },
});
