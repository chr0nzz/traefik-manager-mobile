import { StyleSheet, TouchableOpacity, View } from 'react-native';
import { IconButton, Text, TextInput } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Backend } from '../api/routes';
import { useThemeStore } from '../store/theme';
import { font, radius, spacing } from '../theme';

interface Props {
  backends: Backend[];
  onChange: (rows: Backend[]) => void;
  protocol: string;
  upper?: boolean;
}

export default function BackendRows({ backends, onChange, protocol, upper }: Props) {
  const c    = useThemeStore(s => s.colors);
  const http = protocol === 'http';
  const cap  = (t: string) => (upper ? t.toUpperCase() : t);

  const update = (i: number, patch: Partial<Backend>) =>
    onChange(backends.map((b, n) => (n === i ? { ...b, ...patch } : b)));

  const remove = (i: number) => onChange(backends.filter((_, n) => n !== i));

  const add = () => onChange([...backends, { scheme: 'http', host: '', port: '' }]);

  return (
    <View style={styles.wrap}>
      <Text style={[styles.label, { color: c.muted }]}>
        {backends.length > 0 ? cap('Additional Backends') : ''}
      </Text>

      {backends.map((b, i) => (
        <View key={i} style={styles.row}>
          {http && (
            <TouchableOpacity
              onPress={() => update(i, { scheme: b.scheme === 'https' ? 'http' : 'https' })}
              style={[styles.scheme, { borderColor: c.border, backgroundColor: c.bg }]}
            >
              <Text style={[styles.schemeText, { color: c.text }]}>{b.scheme ?? 'http'}</Text>
            </TouchableOpacity>
          )}
          <TextInput
            label={cap(`Backend ${i + 2} Host`)}
            value={b.host}
            onChangeText={t => update(i, { host: t })}
            placeholder="192.168.1.11"
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="url"
            mode="outlined"
            dense
            style={[styles.host, { backgroundColor: c.bg }]}
          />
          <TextInput
            label={cap('Port')}
            value={b.port}
            onChangeText={t => update(i, { port: t })}
            placeholder="8080"
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="numeric"
            mode="outlined"
            dense
            style={[styles.port, { backgroundColor: c.bg }]}
          />
          <IconButton icon="close" size={18} iconColor={c.muted} onPress={() => remove(i)} />
        </View>
      ))}

      <TouchableOpacity
        onPress={add}
        style={[styles.addBtn, { borderColor: c.border }]}
      >
        <MaterialCommunityIcons name="plus" size={16} color={c.muted} />
        <Text style={[styles.addText, { color: c.muted }]}>Add backend</Text>
      </TouchableOpacity>

      {backends.length > 0 && (
        <Text style={[styles.hint, { color: c.muted }]}>
          Traefik load balances across all backends. Sticky sessions, health checks and
          priority are kept as configured on the web app.
        </Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap:       { gap: 6 },
  label:      { fontSize: font.sm, fontWeight: '500' },
  row:        { flexDirection: 'row', alignItems: 'center', gap: 4 },
  scheme:     { borderWidth: 1, borderRadius: radius.sm, paddingHorizontal: 8, paddingVertical: 10 },
  schemeText: { fontSize: font.xs, fontWeight: '700' },
  host:       { flex: 1 },
  port:       { width: 78 },
  addBtn:     { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, borderWidth: 1, borderStyle: 'dashed', borderRadius: radius.sm, paddingVertical: 9 },
  addText:    { fontSize: font.sm, fontWeight: '500' },
  hint:       { fontSize: font.xs, opacity: 0.8, lineHeight: 15 },
});
