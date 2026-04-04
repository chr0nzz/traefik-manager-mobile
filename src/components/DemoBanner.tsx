import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { StyleSheet, TouchableOpacity } from 'react-native';
import { Text } from 'react-native-paper';
import { useConnection } from '../store/connection';
import { useThemeStore } from '../store/theme';
import { font, spacing } from '../theme';

export function DemoBanner() {
  const demoMode = useConnection(s => s.demoMode);
  const c        = useThemeStore(s => s.colors);
  const router   = useRouter();

  if (!demoMode) return null;

  return (
    <TouchableOpacity
      style={[styles.banner, { backgroundColor: c.blue + '18', borderBottomColor: c.blue + '44' }]}
      onPress={() => router.push('/settings/server')}
      activeOpacity={0.7}
    >
      <MaterialCommunityIcons name="flask-outline" size={13} color={c.blue} />
      <Text style={[styles.text, { color: c.blue }]}>Demo Mode - tap to connect to your server</Text>
      <MaterialCommunityIcons name="chevron-right" size={13} color={c.blue} />
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  banner: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 5,
    paddingVertical: 6,
    borderBottomWidth: 1,
  },
  text: {
    fontSize: font.xs,
    fontWeight: '600',
  },
});
