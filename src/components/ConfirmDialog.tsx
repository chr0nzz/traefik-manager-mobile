import { Modal, StyleSheet, TouchableOpacity, TouchableWithoutFeedback, View } from 'react-native';
import { Text } from 'react-native-paper';
import { useThemeStore } from '../store/theme';
import { font, radius, spacing } from '../theme';

interface Props {
  visible: boolean;
  title: string;
  message?: string;
  confirmLabel?: string;
  confirmDestructive?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmDialog({
  visible, title, message, confirmLabel = 'Confirm',
  confirmDestructive = false, onConfirm, onCancel,
}: Props) {
  const c = useThemeStore(s => s.colors);

  return (
    <Modal transparent visible={visible} animationType="fade" onRequestClose={onCancel}>
      <TouchableWithoutFeedback onPress={onCancel}>
        <View style={styles.backdrop}>
          <TouchableWithoutFeedback>
            <View style={[styles.dialog, { backgroundColor: c.card, borderColor: c.border }]}>
              <Text style={[styles.title, { color: c.text }]}>{title}</Text>
              {!!message && (
                <Text style={[styles.message, { color: c.muted }]}>{message}</Text>
              )}
              <View style={[styles.actions, { borderTopColor: c.border }]}>
                <TouchableOpacity style={[styles.btn, { borderRightColor: c.border }]} onPress={onCancel} activeOpacity={0.6}>
                  <Text style={[styles.btnText, { color: c.muted }]}>Cancel</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.btn} onPress={onConfirm} activeOpacity={0.6}>
                  <Text style={[styles.btnText, { color: confirmDestructive ? c.red : c.blue, fontWeight: '700' }]}>
                    {confirmLabel}
                  </Text>
                </TouchableOpacity>
              </View>
            </View>
          </TouchableWithoutFeedback>
        </View>
      </TouchableWithoutFeedback>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.55)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: spacing.xl,
  },
  dialog: {
    width: '100%',
    maxWidth: 360,
    borderRadius: radius.lg,
    borderWidth: 1,
    overflow: 'hidden',
  },
  title: {
    fontSize: font.lg,
    fontWeight: '700',
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.lg,
    paddingBottom: spacing.sm,
  },
  message: {
    fontSize: font.md,
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.lg,
    lineHeight: 20,
  },
  actions: {
    flexDirection: 'row',
    borderTopWidth: 1,
  },
  btn: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 14,
    borderRightWidth: 0,
  },
  btnText: {
    fontSize: font.md,
  },
});
