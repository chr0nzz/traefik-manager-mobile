import { useQueryClient } from '@tanstack/react-query';
import { useMemo, useRef, useState } from 'react';
import { Animated, FlatList, NativeScrollEvent, NativeSyntheticEvent, RefreshControl, ScrollView, StyleSheet, TextInput, TouchableOpacity, View } from 'react-native';
import { Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { FilterDropdown } from '../../src/components/FilterDropdown';
import { ServiceRow } from '../../src/components/ServiceRow';
import { TopBar } from '../../src/components/TopBar';
import { useLive } from '../../src/hooks/useLive';
import { useNavStore } from '../../src/store/nav';
import { useThemeStore } from '../../src/store/theme';
import { useTabSwipe } from '../../src/hooks/useTabSwipe';
import { font, radius, spacing } from '../../src/theme';
import { providerOf } from '../../src/utils';

const PAD = spacing.md;

function statusOf(s: string) {
  const l = (s || '').toLowerCase();
  if (l === 'enabled' || l === 'success') return 'ok';
  if (l === 'warning' || l === 'warn')    return 'warn';
  return 'err';
}

export default function LiveScreen() {
  const [search, setSearch]         = useState('');
  const [status, setStatus]         = useState('all');
  const [proto, setProto]           = useState('all');
  const [provider, setProvider]     = useState('all');
  const [searchOpen, setSearchOpen] = useState(false);
  const searchRef                   = useRef<TextInput>(null);

  const { data, isFetching, isError, error } = useLive();
  const qc        = useQueryClient();
  const setNavVis = useNavStore(s => s.setVisible);
  const c         = useThemeStore(s => s.colors);
  const swipe     = useTabSwipe('live');
  const scrollAnim = useRef(new Animated.Value(0)).current;
  const lastY      = useRef(0);

  const onScrollListener = (e: NativeSyntheticEvent<NativeScrollEvent>) => {
    const y = e.nativeEvent.contentOffset.y;
    const dy = y - lastY.current;
    if (dy > 8)  setNavVis(false);
    if (dy < -8 || y < 10) setNavVis(true);
    lastY.current = y;
  };

  const allServices = data ?? [];

  const uniqueProtos = useMemo(
    () => [...new Set(allServices.map(s => s._proto ?? s.type ?? 'http').filter(Boolean))].sort(),
    [allServices],
  );
  const uniqueProviders = useMemo(
    () => [...new Set(allServices.map(s => providerOf(s.name)))].sort(),
    [allServices],
  );

  const filtered = useMemo(() => {
    return allServices.filter(s => {
      if (status !== 'all') {
        const st = statusOf(s.status);
        if (status === 'success' && st !== 'ok')   return false;
        if (status === 'warning' && st !== 'warn') return false;
        if (status === 'error'   && st !== 'err')  return false;
      }
      if (proto !== 'all' && (s._proto ?? s.type ?? 'http') !== proto) return false;
      if (provider !== 'all' && providerOf(s.name) !== provider) return false;
      if (search.trim() && !s.name?.toLowerCase().includes(search.toLowerCase())) return false;
      return true;
    });
  }, [allServices, status, proto, provider, search]);

  const hasFilters = status !== 'all' || proto !== 'all' || provider !== 'all';

  const statusOpts = [
    { value: 'all',     label: 'All Status' },
    { value: 'success', label: 'Success'    },
    { value: 'warning', label: 'Warnings'   },
    { value: 'error',   label: 'Errors'     },
  ];
  const protoOpts = [
    { value: 'all', label: 'All Protocols' },
    ...uniqueProtos.map(p => ({ value: p, label: p.toUpperCase() })),
  ];
  const providerOpts = [
    { value: 'all', label: 'All Providers' },
    ...uniqueProviders.map(p => ({ value: p, label: p })),
  ];

  const clearFilters = () => { setStatus('all'); setProto('all'); setProvider('all'); setSearch(''); };

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
      <TopBar title="Services" scrollAnim={scrollAnim} accent={c.green} icon="lightning-bolt" />

      <View style={[styles.filterBar, { borderBottomColor: c.border }]}>
        {searchOpen ? (
          /* ── Expanded search ── */
          <View style={styles.searchRow}>
            <TouchableOpacity onPress={closeSearch} hitSlop={8} style={styles.backBtn}>
              <MaterialCommunityIcons name="arrow-left" size={20} color={c.muted} />
            </TouchableOpacity>
            <TextInput
              ref={searchRef}
              style={[styles.searchExpanded, { backgroundColor: c.card, borderColor: c.muted + '88', color: c.text }]}
              value={search}
              onChangeText={setSearch}
              placeholder="Search services..."
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
          /* ── Filter row ── */
          <View style={styles.filterRow}>
            {/* Search icon pill */}
            <TouchableOpacity
              style={[styles.searchIconBtn, { backgroundColor: c.card, borderColor: c.border }]}
              onPress={openSearch}
              hitSlop={6}
            >
              <MaterialCommunityIcons name="magnify" size={18} color={c.muted} />
            </TouchableOpacity>

            {/* Dropdowns */}
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.dropRow}>
              <FilterDropdown value={status}   options={statusOpts}   onChange={setStatus}   placeholder="Status"   label="Filter by Status" />
              <FilterDropdown value={proto}    options={protoOpts}    onChange={setProto}    placeholder="Protocol" label="Filter by Protocol" />
              <FilterDropdown value={provider} options={providerOpts} onChange={setProvider} placeholder="Provider" label="Filter by Provider" />
              {hasFilters && (
                <TouchableOpacity
                  style={[styles.clearChip, { borderColor: c.red + '55', backgroundColor: c.red + '12' }]}
                  onPress={clearFilters}
                >
                  <MaterialCommunityIcons name="close" size={12} color={c.red} />
                  <Text style={[styles.clearChipText, { color: c.red }]}>Clear</Text>
                </TouchableOpacity>
              )}
            </ScrollView>
          </View>
        )}
      </View>

      {isError && (
        <View style={[styles.errorBox, { borderColor: c.red + '55' }]}>
          <Text style={{ color: c.red, fontSize: font.sm }}>{(error as Error)?.message ?? 'Failed to load'}</Text>
        </View>
      )}

      <FlatList
        data={filtered}
        keyExtractor={s => s.name}
        renderItem={({ item }) => <ServiceRow service={item} />}
        contentContainerStyle={styles.list}
        onScroll={Animated.event(
          [{ nativeEvent: { contentOffset: { y: scrollAnim } } }],
          { useNativeDriver: false, listener: onScrollListener },
        )}
        scrollEventThrottle={16}
        refreshControl={
          <RefreshControl
            refreshing={isFetching}
            onRefresh={() => qc.invalidateQueries({ queryKey: ['live-services'] })}
            tintColor={c.muted}
          />
        }
        ListEmptyComponent={
          <Text style={{ textAlign: 'center', color: c.muted, fontSize: font.base, marginTop: spacing.xxl, padding: spacing.md }}>
            {isFetching ? 'Loading…' : 'No services found'}
          </Text>
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container:  { flex: 1 },
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
  searchIconBtn: {
    width: 36, height: 36,
    borderRadius: radius.full,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  searchExpanded: {
    flex: 1,
    borderRadius: radius.full,
    borderWidth: 1.5,
    paddingHorizontal: spacing.md,
    paddingVertical: 8,
    fontSize: font.sm,
  },
  dropRow: {
    flexDirection: 'row',
    gap: spacing.xs,
    alignItems: 'center',
  },
  clearChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 7,
    borderRadius: radius.full,
    borderWidth: 1,
  },
  clearChipText: { fontSize: font.xs, fontWeight: '700' },
  errorBox: {
    margin: spacing.md, padding: spacing.md,
    borderRadius: spacing.sm, borderWidth: 1,
    backgroundColor: 'rgba(239,68,68,0.08)',
  },
  list: { padding: PAD, paddingBottom: 110 },
});
