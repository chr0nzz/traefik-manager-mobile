import { useQueryClient } from '@tanstack/react-query';
import { useRef } from 'react';
import { Animated, NativeScrollEvent, NativeSyntheticEvent, RefreshControl, StyleSheet, View } from 'react-native';
import { Text } from 'react-native-paper';
import { useNavigation } from '@react-navigation/native';
import { EntrypointCard } from '../../src/components/EntrypointCard';
import { StatCard } from '../../src/components/StatCard';
import { TopBar } from '../../src/components/TopBar';
import { useDashboard } from '../../src/hooks/useDashboard';
import { useNavStore } from '../../src/store/nav';
import { useThemeStore } from '../../src/store/theme';
import { useTabSwipe } from '../../src/hooks/useTabSwipe';

export default function DashboardScreen() {
  const { overview, entrypoints } = useDashboard();
  const qc                  = useQueryClient();
  const c                   = useThemeStore(s => s.colors);
  const setNavVis           = useNavStore(s => s.setVisible);
  const setRouteProtoFilter = useNavStore(s => s.setRouteProtoFilter);
  const swipe               = useTabSwipe('index');
  const navigation          = useNavigation<any>();

  const exploreRoutes = (proto: string) => {
    setRouteProtoFilter(proto);
    navigation.navigate('routes');
  };
  const scrollAnim = useRef(new Animated.Value(0)).current;
  const lastY      = useRef(0);

  const onRefresh = () => {
    qc.invalidateQueries({ queryKey: ['overview'] });
    qc.invalidateQueries({ queryKey: ['entrypoints'] });
  };

  const onScrollListener = (e: NativeSyntheticEvent<NativeScrollEvent>) => {
    const y = e.nativeEvent.contentOffset.y;
    const dy = y - lastY.current;
    if (dy > 8)  setNavVis(false);
    if (dy < -8 || y < 10) setNavVis(true);
    lastY.current = y;
  };

  const d = overview.data;

  return (
    <View style={[styles.container, { backgroundColor: c.bg }]} {...swipe}>
      <TopBar title="Dashboard" scrollAnim={scrollAnim} accent={c.blue} icon="view-dashboard" />
      <Animated.ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.content}
        refreshControl={<RefreshControl refreshing={overview.isFetching} onRefresh={onRefresh} tintColor={c.blue} />}
        onScroll={Animated.event(
          [{ nativeEvent: { contentOffset: { y: scrollAnim } } }],
          { useNativeDriver: false, listener: onScrollListener },
        )}
        scrollEventThrottle={16}
      >
        {overview.isError && (
          <View style={[styles.errorBox, { borderColor: c.red + '55' }]}>
            <Text style={{ color: c.red, fontSize: 12 }}>
              {(overview.error as Error)?.message ?? 'Failed to load'}
            </Text>
          </View>
        )}

        <StatCard title="HTTP Routers"  accent={c.blue}   data={d?.http?.routers}     onExplore={() => exploreRoutes('HTTP')} />
        <StatCard title="TCP Routers"   accent={c.orange} data={d?.tcp?.routers}      onExplore={() => exploreRoutes('TCP')} />
        <StatCard title="UDP Routers"   accent={c.purple} data={d?.udp?.routers}      onExplore={() => exploreRoutes('UDP')} />
        <StatCard title="Services"      accent={c.green}  data={d?.http?.services}    onExplore={() => navigation.navigate('live')} />
        <StatCard title="Middlewares"   accent={c.yellow} data={d?.http?.middlewares} onExplore={() => navigation.navigate('middlewares')} />
        <EntrypointCard data={entrypoints.data} />
      </Animated.ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  scroll:    { flex: 1 },
  content:   { padding: 16, paddingBottom: 110 },
  errorBox: {
    backgroundColor: 'rgba(239,68,68,0.1)',
    borderRadius: 8, padding: 12, marginBottom: 12, borderWidth: 1,
  },
});
