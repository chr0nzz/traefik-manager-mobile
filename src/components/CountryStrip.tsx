import { ScrollView, StyleSheet, TouchableOpacity, View } from 'react-native';
import { Text } from 'react-native-paper';
import { CountryCount, flagEmoji } from '../api/geoip';
import { useThemeStore } from '../store/theme';
import { font, radius, spacing } from '../theme';

interface Props {
  countries: CountryCount[];
  active?: string | null;
  onSelect?: (code: string | null) => void;
  max?: number;
}

export default function CountryStrip({ countries, active, onSelect, max = 12 }: Props) {
  const c = useThemeStore(s => s.colors);
  if (countries.length === 0) return null;

  const shown = countries.slice(0, max);
  const rest  = countries.length - shown.length;

  return (
    <View style={styles.wrap}>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.row}>
        {shown.map(entry => {
          const on = active === entry.code;
          return (
            <TouchableOpacity
              key={entry.code}
              disabled={!onSelect}
              onPress={() => onSelect?.(on ? null : entry.code)}
              activeOpacity={0.7}
              style={[
                styles.chip,
                { borderColor: c.border, backgroundColor: c.card },
                on && { borderColor: c.blue + '66', backgroundColor: c.blue + '18' },
              ]}
            >
              <Text style={styles.flag}>{flagEmoji(entry.code)}</Text>
              <Text style={[styles.code, { color: on ? c.blue : c.text }]}>{entry.code}</Text>
              <Text style={[styles.count, { color: on ? c.blue : c.muted }]}>{entry.count}</Text>
            </TouchableOpacity>
          );
        })}
        {rest > 0 && (
          <View style={[styles.chip, { borderColor: c.border, backgroundColor: c.card }]}>
            <Text style={[styles.count, { color: c.muted }]}>+{rest} more</Text>
          </View>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap:  { paddingBottom: spacing.xs },
  row:   { flexDirection: 'row', gap: 6, paddingHorizontal: spacing.md },
  chip:  { flexDirection: 'row', alignItems: 'center', gap: 5, borderWidth: 1,
           borderRadius: radius.sm, paddingHorizontal: 9, paddingVertical: 5 },
  flag:  { fontSize: 13 },
  code:  { fontSize: font.xs, fontWeight: '700', letterSpacing: 0.3 },
  count: { fontSize: font.xs, fontWeight: '600' },
});
