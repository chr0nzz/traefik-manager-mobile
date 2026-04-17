import { useQueryClient } from '@tanstack/react-query';
import { useMemo, useRef, useState } from 'react';
import { Animated, FlatList, Pressable, RefreshControl, StyleSheet, TouchableOpacity, View } from 'react-native';
import { Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { MiddlewareCard } from '../../src/components/MiddlewareCard';
import { TopBar } from '../../src/components/TopBar';
import { DemoBanner } from '../../src/components/DemoBanner';
import { useMiddlewares } from '../../src/hooks/useMiddlewares';
import { useLayout } from '../../src/hooks/useLayout';
import { useThemeStore } from '../../src/store/theme';
import { useDrawerStore } from '../../src/store/drawer';
import { useTabSwipe } from '../../src/hooks/useTabSwipe';
import { font, radius, spacing } from '../../src/theme';
import { providerOf } from '../../src/utils';

const PROTOS = ['All', 'HTTP', 'TCP'];

export default function MiddlewaresScreen() {
  const router                      = useRouter();
  const [search, setSearch]         = useState('');
  const [proto, setProto]           = useState('All');
  const [editMode, setEditMode]     = useState(false);

  const c          = useThemeStore(s => s.colors);
  const openDrawer = useDrawerStore(s => s.open);
  const qc         = useQueryClient();
  const swipe      = useTabSwipe('middlewares');
  const scrollAnim = useRef(new Animated.Value(0)).current;

  const { data, isFetching, isError, error } = useMiddlewares();
  const { contentPadding, contentMaxWidth, listBottomPadding } = useLayout();

  const middlewares = useMemo(() => {
    let list = data ?? [];
    if (proto !== 'All') list = list.filter(m => (m._proto ?? 'http').toLowerCase() === proto.toLowerCase());
    if (search.trim()) {
      const q = search.toLowerCase();
      list = list.filter(m =>
        m.name?.toLowerCase().includes(q) ||
        (m.type ?? '').toLowerCase().includes(q) ||
        providerOf(m.name).toLowerCase().includes(q),
      );
    }
    return list;
  }, [data, proto, search]);

  return (
    <View style={[styles.container, { backgroundColor: c.bg }]} {...swipe}>
      <TopBar
        title="Middleware"
        scrollAnim={scrollAnim}
        onMenuPress={openDrawer}
        searchValue={search}
        onSearchChange={setSearch}
        searchPlaceholder="Search middlewares..."
        searchAccent={c.purple}
        overflowSections={[{
          title: 'Protocol',
          items: PROTOS.map(p => ({
            label: p,
            selected: proto === p,
            onPress: () => setProto(p),
          })),
        }]}
        wideFilters={
          <View style={styles.wideRow}>
            {PROTOS.map(p => (
              <TouchableOpacity
                key={p}
                style={[styles.chip, { borderColor: c.border, backgroundColor: c.card }, proto === p && { backgroundColor: c.purple + '20', borderColor: c.purple + '66' }]}
                onPress={() => setProto(p)}
              >
                <Text style={[styles.chipText, { color: proto === p ? c.purple : c.muted }]}>{p}</Text>
              </TouchableOpacity>
            ))}
          </View>
        }
        right={
          <View style={{ flexDirection: 'row', gap: 6 }}>
            <Pressable
              onPress={() => setEditMode(v => !v)}
              style={[styles.trailingBtn, { borderColor: editMode ? c.orange + '66' : c.border }]}
              android_ripple={{ color: c.muted + '40' }}
            >
              <MaterialCommunityIcons name={editMode ? 'pencil' : 'pencil-outline'} size={20} color={editMode ? c.orange : c.muted} />
            </Pressable>
            <Pressable
              onPress={() => router.push('/middleware/new')}
              style={[styles.trailingBtn, { borderColor: c.purple + '66' }]}
              android_ripple={{ color: c.muted + '40' }}
            >
              <MaterialCommunityIcons name="plus" size={20} color={c.purple} />
            </Pressable>
          </View>
        }
      />

      <DemoBanner />

      {isError && (
        <View style={[styles.errorBox, { backgroundColor: c.red + '14', borderColor: c.red + '55' }]}>
          <Text style={{ color: c.red, fontSize: font.sm }}>{(error as Error)?.message ?? 'Failed to load'}</Text>
        </View>
      )}

      <FlatList
        data={middlewares}
        keyExtractor={m => m.name}
        renderItem={({ item }) => <MiddlewareCard middleware={item} editMode={editMode} />}
        contentContainerStyle={[styles.list, { padding: contentPadding, paddingBottom: listBottomPadding, alignSelf: 'center', width: '100%', maxWidth: contentMaxWidth }]}
        onScroll={Animated.event(
          [{ nativeEvent: { contentOffset: { y: scrollAnim } } }],
          { useNativeDriver: false },
        )}
        scrollEventThrottle={16}
        refreshControl={
          <RefreshControl
            refreshing={isFetching}
            onRefresh={() => qc.invalidateQueries({ queryKey: ['middlewares'] })}
            tintColor={c.purple}
          />
        }
        ListEmptyComponent={
          <Text style={{ textAlign: 'center', color: c.muted, fontSize: font.base, marginTop: spacing.xxl }}>
            {isFetching ? 'Loading…' : 'No middlewares found'}
          </Text>
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  wideRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  chip: {
    height: 36,
    paddingHorizontal: 12,
    borderRadius: radius.sm,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  chipText: { fontSize: font.xs, fontWeight: '700' },
  errorBox: {
    margin: spacing.md, padding: spacing.md,
    borderRadius: radius.sm, borderWidth: 1,
  },
  list: {},
  trailingBtn: {
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.sm,
    borderWidth: 1,
  },
});
