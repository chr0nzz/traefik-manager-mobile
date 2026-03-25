import { useQueryClient } from '@tanstack/react-query';
import { useMemo, useEffect, useRef, useState } from 'react';
import { Animated, FlatList, NativeScrollEvent, NativeSyntheticEvent, RefreshControl, StyleSheet, TextInput, TouchableOpacity, View } from 'react-native';
import { Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { RouteCard } from '../../src/components/RouteCard';
import { TopBar } from '../../src/components/TopBar';
import { domainFromRule } from '../../src/api/routes';
import { useRoutes, useToggleRoute } from '../../src/hooks/useRoutes';
import { useNavStore } from '../../src/store/nav';
import { useThemeStore } from '../../src/store/theme';
import { useTabSwipe } from '../../src/hooks/useTabSwipe';
import { font, radius, spacing } from '../../src/theme';

const PROTOS = ['All', 'HTTP', 'TCP', 'UDP'];
const PAD = spacing.md;

export default function RoutesScreen() {
  const router                      = useRouter();
  const [search, setSearch]         = useState('');
  const [proto, setProto]           = useState('All');
  const [togglingId, setTogglingId] = useState<string | null>(null);
  const [searchOpen, setSearchOpen] = useState(false);
  const [editMode, setEditMode]     = useState(false);
  const searchRef                   = useRef<TextInput>(null);

  const setNavVis          = useNavStore(s => s.setVisible);
  const routeProtoFilter   = useNavStore(s => s.routeProtoFilter);
  const setRouteProtoFilter = useNavStore(s => s.setRouteProtoFilter);
  const c                  = useThemeStore(s => s.colors);

  // Apply filter set by Dashboard Explore button
  useEffect(() => {
    if (routeProtoFilter && routeProtoFilter !== 'All') {
      setProto(routeProtoFilter);
      setRouteProtoFilter('All'); // reset after consuming
    }
  }, [routeProtoFilter]);
  const swipe      = useTabSwipe('routes');
  const scrollAnim = useRef(new Animated.Value(0)).current;
  const lastY      = useRef(0);

  const onScrollListener = (e: NativeSyntheticEvent<NativeScrollEvent>) => {
    const y = e.nativeEvent.contentOffset.y;
    const dy = y - lastY.current;
    if (dy > 8)  setNavVis(false);
    if (dy < -8 || y < 10) setNavVis(true);
    lastY.current = y;
  };

  const { data, isFetching, isError, error } = useRoutes();
  const toggle = useToggleRoute();
  const qc     = useQueryClient();

  const routes = useMemo(() => {
    let list = data?.apps ?? [];
    if (proto !== 'All') list = list.filter(r => r.protocol?.toLowerCase() === proto.toLowerCase());
    if (search.trim()) {
      const q = search.toLowerCase();
      list = list.filter(r =>
        r.name?.toLowerCase().includes(q) ||
        domainFromRule(r.rule).toLowerCase().includes(q) ||
        r.target?.toLowerCase().includes(q),
      );
    }
    return list;
  }, [data, proto, search]);

  const handleToggle = async (id: string, enable: boolean) => {
    setTogglingId(id);
    try { await toggle.mutateAsync({ id, enable }); }
    finally { setTogglingId(null); }
  };

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
        title="Routes"
        scrollAnim={scrollAnim}
        accent={c.green}
        right={
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 4 }}>
            <TouchableOpacity
              onPress={() => setEditMode(v => !v)}
              hitSlop={8}
              style={[styles.topBarBtn, editMode && { backgroundColor: c.orange + '22', borderColor: c.orange + '55' }]}
            >
              <MaterialCommunityIcons name={editMode ? 'pencil' : 'pencil-outline'} size={18} color={editMode ? c.orange : c.muted} />
            </TouchableOpacity>
            <TouchableOpacity onPress={() => router.push('/route/new')} hitSlop={8} style={[styles.topBarBtn, { borderColor: c.green + '55' }]}>
              <MaterialCommunityIcons name="plus" size={18} color={c.green} />
            </TouchableOpacity>
          </View>
        }
      />


      <View style={[styles.filterBar, { borderBottomColor: c.border }]}>
        {searchOpen ? (
          /* ── Expanded search row ── */
          <View style={styles.searchRow}>
            <TouchableOpacity onPress={closeSearch} hitSlop={8} style={styles.backBtn}>
              <MaterialCommunityIcons name="arrow-left" size={20} color={c.muted} />
            </TouchableOpacity>
            <TextInput
              ref={searchRef}
              style={[styles.searchExpanded, { backgroundColor: c.card, borderColor: c.blue + '88', color: c.text }]}
              value={search}
              onChangeText={setSearch}
              placeholder="Search routes..."
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
          /* ── Collapsed filter row ── */
          <View style={styles.filterRow}>
            <TouchableOpacity
              style={[styles.searchBtn, { backgroundColor: c.card, borderColor: c.border }]}
              onPress={openSearch}
              activeOpacity={0.7}
            >
              <MaterialCommunityIcons name="magnify" size={15} color={c.muted} />
              <Text style={[styles.searchBtnText, { color: c.muted }]}>Search routes...</Text>
            </TouchableOpacity>

            <View style={styles.protoRow}>
              {PROTOS.map(p => (
                <TouchableOpacity
                  key={p}
                  style={[styles.protoBtn, { borderColor: c.border, backgroundColor: c.card }, proto === p && { backgroundColor: c.green + '20', borderColor: c.green + '66' }]}
                  onPress={() => setProto(p)}
                >
                  <Text style={[styles.protoBtnText, { color: proto === p ? c.green : c.muted }]}>{p}</Text>
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
        data={routes}
        keyExtractor={r => r.id}
        renderItem={({ item }) => (
          <RouteCard route={item} onToggle={handleToggle} toggling={togglingId === item.id} editMode={editMode} />
        )}
        contentContainerStyle={styles.list}
        onScroll={Animated.event(
          [{ nativeEvent: { contentOffset: { y: scrollAnim } } }],
          { useNativeDriver: false, listener: onScrollListener },
        )}
        scrollEventThrottle={16}
        refreshControl={
          <RefreshControl
            refreshing={isFetching}
            onRefresh={() => qc.invalidateQueries({ queryKey: ['routes'] })}
            tintColor={c.green}
          />
        }
        ListEmptyComponent={
          <Text style={{ textAlign: 'center', color: c.muted, fontSize: font.base, marginTop: spacing.xxl }}>
            {isFetching ? 'Loading…' : 'No routes found'}
          </Text>
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  filterBar: {
    paddingHorizontal: spacing.md,
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
  list: { padding: PAD, paddingBottom: 110 },
  topBarBtn: {
    padding: 5,
    borderRadius: radius.sm,
    borderWidth: 1,
    borderColor: 'transparent',
  },
});
