import { useQueryClient } from '@tanstack/react-query';
import { useMemo, useEffect, useRef, useState } from 'react';
import { Animated, FlatList, Pressable, RefreshControl, StyleSheet, TouchableOpacity, View } from 'react-native';
import { Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { RouteCard } from '../../src/components/RouteCard';
import { TopBar } from '../../src/components/TopBar';
import { DemoBanner } from '../../src/components/DemoBanner';
import { domainFromRule } from '../../src/api/routes';
import { useRoutes, useToggleRoute } from '../../src/hooks/useRoutes';
import { useLayout } from '../../src/hooks/useLayout';
import { useNavStore } from '../../src/store/nav';
import { useThemeStore } from '../../src/store/theme';
import { useDrawerStore } from '../../src/store/drawer';
import { useTabSwipe } from '../../src/hooks/useTabSwipe';
import { font, radius, spacing } from '../../src/theme';

const PROTOS = ['All', 'HTTP', 'TCP', 'UDP'];

export default function RoutesScreen() {
  const router                      = useRouter();
  const [search, setSearch]         = useState('');
  const [proto, setProto]           = useState('All');
  const [togglingId, setTogglingId] = useState<string | null>(null);
  const [editMode, setEditMode]     = useState(false);

  const routeProtoFilter   = useNavStore(s => s.routeProtoFilter);
  const setRouteProtoFilter = useNavStore(s => s.setRouteProtoFilter);
  const c                  = useThemeStore(s => s.colors);
  const openDrawer         = useDrawerStore(s => s.open);

  useEffect(() => {
    if (routeProtoFilter && routeProtoFilter !== 'All') {
      setProto(routeProtoFilter);
      setRouteProtoFilter('All');
    }
  }, [routeProtoFilter]);

  const swipe      = useTabSwipe('routes');
  const scrollAnim = useRef(new Animated.Value(0)).current;

  const { data, isFetching, isError, error } = useRoutes();
  const toggle = useToggleRoute();
  const qc     = useQueryClient();
  const { contentPadding, contentMaxWidth, listBottomPadding } = useLayout();

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
    catch { }
    finally { setTogglingId(null); }
  };

  return (
    <View style={[styles.container, { backgroundColor: c.bg }]} {...swipe}>
      <TopBar
        title="Routes"
        scrollAnim={scrollAnim}
        onMenuPress={openDrawer}
        searchValue={search}
        onSearchChange={setSearch}
        searchPlaceholder="Search routes..."
        searchAccent={c.green}
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
                style={[styles.chip, { borderColor: c.border, backgroundColor: c.card }, proto === p && { backgroundColor: c.green + '20', borderColor: c.green + '66' }]}
                onPress={() => setProto(p)}
              >
                <Text style={[styles.chipText, { color: proto === p ? c.green : c.muted }]}>{p}</Text>
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
              onPress={() => router.push('/route/new')}
              style={[styles.trailingBtn, { borderColor: c.green + '66' }]}
              android_ripple={{ color: c.muted + '40' }}
            >
              <MaterialCommunityIcons name="plus" size={20} color={c.green} />
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
        data={routes}
        keyExtractor={r => r.id}
        renderItem={({ item }) => (
          <RouteCard route={item} onToggle={handleToggle} toggling={togglingId === item.id} editMode={editMode} />
        )}
        contentContainerStyle={[styles.list, { padding: contentPadding, paddingBottom: listBottomPadding, alignSelf: 'center', width: '100%', maxWidth: contentMaxWidth }]}
        onScroll={Animated.event(
          [{ nativeEvent: { contentOffset: { y: scrollAnim } } }],
          { useNativeDriver: false },
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
