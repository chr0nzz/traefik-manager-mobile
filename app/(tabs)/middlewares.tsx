import { useQueryClient } from '@tanstack/react-query';
import { useMemo, useRef, useState } from 'react';
import { Animated, FlatList, RefreshControl, StyleSheet, TextInput, TouchableOpacity, View } from 'react-native';
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
const PAD = spacing.md;

export default function MiddlewaresScreen() {
  const router                      = useRouter();
  const [search, setSearch] = useState('');
  const [proto, setProto]   = useState('All');
  const [searchOpen, setSearchOpen] = useState(false);
  const [editMode, setEditMode]     = useState(false);
  const searchRef           = useRef<TextInput>(null);

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

  const openSearch = () => {
    setSearchOpen(true);
    setTimeout(() => searchRef.current?.focus(), 50);
  };

  const closeSearch = () => {
    setSearch('');
    setSearchOpen(false);
  };

  return (
    <View style={[styles.container, { backgroundColor: c.bg }]} {...swipe}>
      <TopBar
        title="Middleware"
        scrollAnim={scrollAnim}
        onMenuPress={openDrawer}
        right={
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
            <TouchableOpacity
              onPress={() => setEditMode(v => !v)}
              hitSlop={8}
              style={[styles.topBarBtn, editMode && { backgroundColor: c.orange + '22', borderColor: c.orange + '55' }]}
            >
              <MaterialCommunityIcons name={editMode ? 'pencil' : 'pencil-outline'} size={18} color={editMode ? c.orange : c.muted} />
            </TouchableOpacity>
            <TouchableOpacity onPress={() => router.push('/middleware/new')} hitSlop={8} style={[styles.topBarBtn, { borderColor: c.purple + '55' }]}>
              <MaterialCommunityIcons name="plus" size={18} color={c.purple} />
            </TouchableOpacity>
          </View>
        }
      />
      <DemoBanner />
      <View style={[styles.filterBar, { borderBottomColor: c.border, paddingHorizontal: contentPadding }]}>
        {searchOpen ? (
          <View style={styles.searchRow}>
            <TouchableOpacity onPress={closeSearch} hitSlop={8} style={styles.backBtn}>
              <MaterialCommunityIcons name="arrow-left" size={20} color={c.muted} />
            </TouchableOpacity>
            <TextInput
              ref={searchRef}
              style={[styles.searchExpanded, { backgroundColor: c.card, borderColor: c.purple + '88', color: c.text }]}
              value={search}
              onChangeText={setSearch}
              placeholder="Search middlewares..."
              placeholderTextColor={c.muted}
              autoCorrect={false}
              returnKeyType="search"
            />
            {search.length > 0 && (
              <TouchableOpacity onPress={() => setSearch('')} hitSlop={8} style={styles.clearBtn}>
                <MaterialCommunityIcons name="close-circle" size={18} color={c.muted} />
              </TouchableOpacity>
            )}
          </View>
        ) : (
          <View style={styles.filterRow}>
            <TouchableOpacity
              style={[styles.searchBtn, { backgroundColor: c.card, borderColor: c.border }]}
              onPress={openSearch}
              activeOpacity={0.7}
            >
              <MaterialCommunityIcons name="magnify" size={15} color={c.muted} />
              <Text style={[styles.searchBtnText, { color: c.muted }]}>Search middlewares...</Text>
            </TouchableOpacity>

            <View style={styles.protoRow}>
              {PROTOS.map(p => (
                <TouchableOpacity
                  key={p}
                  style={[styles.protoBtn, { borderColor: c.border, backgroundColor: c.card }, proto === p && { backgroundColor: c.purple + '20', borderColor: c.purple + '66' }]}
                  onPress={() => setProto(p)}
                >
                  <Text style={[styles.protoBtnText, { color: proto === p ? c.purple : c.muted }]}>{p}</Text>
                </TouchableOpacity>
              ))}
            </View>
          </View>
        )}
      </View>

      {isError && (
        <View style={[styles.errorBox, { borderColor: c.red + '55' }]}>
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
  filterBar: {
    paddingVertical: spacing.sm,
    borderBottomWidth: 1,
  },
  filterRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
  },
  searchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.sm,
  },
  backBtn:  { padding: 2 },
  clearBtn: { padding: 2 },
  searchBtn: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    borderRadius: radius.full,
    borderWidth: 1,
    paddingHorizontal: spacing.md,
    paddingVertical: 8,
  },
  searchBtnText: { fontSize: font.sm, flex: 1 },
  searchExpanded: {
    flex: 1,
    borderRadius: radius.full,
    borderWidth: 1.5,
    paddingHorizontal: spacing.md,
    paddingVertical: 8,
    fontSize: font.sm,
  },
  protoRow: { flexDirection: 'row', gap: spacing.xs },
  protoBtn: {
    paddingHorizontal: spacing.sm,
    paddingVertical: 6,
    borderRadius: radius.full,
    borderWidth: 1,
  },
  protoBtnText: { fontSize: font.xs, fontWeight: '700' },
  errorBox: {
    margin: spacing.md, padding: spacing.md,
    borderRadius: spacing.sm, borderWidth: 1,
    backgroundColor: 'rgba(239,68,68,0.08)',
  },
  list: {},
  topBarBtn: {
    padding: 5,
    borderRadius: 6,
    borderWidth: 1,
    borderColor: 'transparent',
  },
});
