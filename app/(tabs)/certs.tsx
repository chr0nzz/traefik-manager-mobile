import { useRef, useState } from 'react';
import { Animated, FlatList, Pressable, RefreshControl, StyleSheet, View } from 'react-native';
import { Surface, Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useQueryClient } from '@tanstack/react-query';
import { TopBar } from '../../src/components/TopBar';
import { DemoBanner } from '../../src/components/DemoBanner';
import { useCerts } from '../../src/hooks/useCerts';
import { useLayout } from '../../src/hooks/useLayout';
import { useThemeStore } from '../../src/store/theme';
import { useDrawerStore } from '../../src/store/drawer';
import { useTabSwipe } from '../../src/hooks/useTabSwipe';
import { Cert } from '../../src/api/traefik';
import { font, radius, spacing } from '../../src/theme';

function daysUntil(iso: string | null): number | null {
  if (!iso) return null;
  const diff = new Date(iso).getTime() - Date.now();
  return Math.floor(diff / (1000 * 60 * 60 * 24));
}

function expiryColor(days: number | null, colors: ReturnType<typeof useThemeStore.getState>['colors']): string {
  if (days === null) return colors.muted;
  if (days < 0)   return colors.red;
  if (days < 7)   return colors.red;
  if (days < 30)  return colors.amber ?? '#f0883e';
  return colors.green;
}

function expiryLabel(days: number | null): string {
  if (days === null) return 'Unknown';
  if (days < 0)  return 'Expired';
  if (days === 0) return 'Expires today';
  return `${days}d`;
}

function CertCard({ cert, c }: { cert: Cert; c: ReturnType<typeof useThemeStore.getState>['colors'] }) {
  const days  = daysUntil(cert.not_after);
  const color = expiryColor(days, c);
  const label = expiryLabel(days);

  return (
    <Surface style={[styles.card, { backgroundColor: c.card }]} elevation={1}>
      <View style={styles.cardTop}>
        <View style={styles.cardLeft}>
          <MaterialCommunityIcons name="certificate-outline" size={18} color={c.blue} style={styles.certIcon} />
          <View style={{ flex: 1, minWidth: 0 }}>
            <Text style={[styles.domain, { color: c.text }]} numberOfLines={1}>{cert.main}</Text>
            {cert.sans.length > 0 && (
              <Text style={[styles.sans, { color: c.muted }]} numberOfLines={1}>
                +{cert.sans.length} SAN{cert.sans.length !== 1 ? 's' : ''}
              </Text>
            )}
          </View>
        </View>
        <View style={[styles.badge, { backgroundColor: color + '22', borderColor: color + '55' }]}>
          <Text style={[styles.badgeText, { color }]}>{label}</Text>
        </View>
      </View>
      <View style={[styles.cardMeta, { borderTopColor: c.border }]}>
        <Text style={[styles.metaText, { color: c.muted }]}>
          Resolver: {cert.resolver}
        </Text>
        {cert.not_after && (
          <Text style={[styles.metaText, { color: c.muted }]}>
            Expires: {new Date(cert.not_after).toLocaleDateString()}
          </Text>
        )}
      </View>
    </Surface>
  );
}

export default function CertsScreen() {
  const { data, isFetching, isError } = useCerts();
  const qc          = useQueryClient();
  const c           = useThemeStore(s => s.colors);
  const openDrawer  = useDrawerStore(s => s.open);
  const swipe       = useTabSwipe('certs');
  const scrollAnim  = useRef(new Animated.Value(0)).current;
  const { contentPadding, contentMaxWidth, listBottomPadding } = useLayout();
  const [search, setSearch] = useState('');

  const allCerts = data?.certs ?? [];
  const certs = search
    ? allCerts.filter(c => c.main.toLowerCase().includes(search.toLowerCase()))
    : allCerts;

  const refresh = () => qc.invalidateQueries({ queryKey: ['certs'] });

  return (
    <View style={[styles.container, { backgroundColor: c.bg }]} {...swipe}>
      <TopBar
        title="Certificates"
        scrollAnim={scrollAnim}
        accent={c.blue}
        icon="certificate-outline"
        onMenuPress={openDrawer}
        searchValue={search}
        onSearchChange={setSearch}
        searchPlaceholder="Search certificates..."
        right={
          <Pressable
            onPress={refresh}
            style={[styles.iconBtn, { borderColor: c.border }]}
            android_ripple={{ color: c.muted + '40' }}
            hitSlop={8}
          >
            <MaterialCommunityIcons name="refresh" size={20} color={c.blue} />
          </Pressable>
        }
      />
      <DemoBanner />
      {isError && (
        <View style={[styles.errorBanner, { backgroundColor: c.red + '22' }]}>
          <Text style={[styles.errorText, { color: c.red }]}>Could not load certificates</Text>
        </View>
      )}
      <Animated.FlatList
        data={certs}
        keyExtractor={(item, i) => `${item.resolver}-${item.main}-${i}`}
        renderItem={({ item }) => <CertCard cert={item} c={c} />}
        contentContainerStyle={[
          styles.list,
          { paddingHorizontal: contentPadding, paddingBottom: listBottomPadding, maxWidth: contentMaxWidth, alignSelf: 'center', width: '100%' },
        ]}
        refreshControl={
          <RefreshControl refreshing={isFetching} onRefresh={refresh} tintColor={c.blue} />
        }
        ListEmptyComponent={
          !isFetching ? (
            <View style={styles.empty}>
              <MaterialCommunityIcons name="certificate-outline" size={48} color={c.muted} style={{ opacity: 0.4 }} />
              <Text style={[styles.emptyText, { color: c.muted }]}>
                {isError ? 'Failed to load certificates' : 'No certificates found'}
              </Text>
            </View>
          ) : null
        }
        onScroll={Animated.event([{ nativeEvent: { contentOffset: { y: scrollAnim } } }], { useNativeDriver: false })}
        scrollEventThrottle={16}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container:  { flex: 1 },
  list:       { gap: spacing.sm, paddingTop: spacing.md },
  card:       { borderRadius: radius.md, overflow: 'hidden' },
  cardTop: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: spacing.md,
    gap: spacing.sm,
  },
  cardLeft:   { flexDirection: 'row', alignItems: 'center', flex: 1, minWidth: 0, gap: spacing.sm },
  certIcon:   { flexShrink: 0 },
  domain:     { fontSize: font.md, fontWeight: '600' },
  sans:       { fontSize: font.sm, marginTop: 2 },
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
    borderWidth: 1,
    flexShrink: 0,
  },
  badgeText:  { fontSize: font.xs, fontWeight: '700' },
  cardMeta: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: spacing.md,
    paddingVertical: 8,
    borderTopWidth: StyleSheet.hairlineWidth,
    gap: spacing.sm,
  },
  metaText:   { fontSize: font.sm },
  empty:      { alignItems: 'center', paddingTop: 80, gap: spacing.md },
  emptyText:  { fontSize: font.md },
  errorBanner: { paddingHorizontal: spacing.lg, paddingVertical: 8 },
  errorText:   { fontSize: font.sm, fontWeight: '500' },
  iconBtn: {
    width: 36, height: 36,
    alignItems: 'center', justifyContent: 'center',
    borderRadius: 8, borderWidth: 1,
  },
});
