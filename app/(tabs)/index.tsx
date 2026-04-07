import { useQueryClient } from '@tanstack/react-query';
import { useRef } from 'react';
import { Animated, RefreshControl, StyleSheet, View } from 'react-native';
import { Text } from 'react-native-paper';
import { useNavigation } from '@react-navigation/native';
import { EntrypointCard } from '../../src/components/EntrypointCard';
import { StatCard } from '../../src/components/StatCard';
import { TopBar } from '../../src/components/TopBar';
import { DemoBanner } from '../../src/components/DemoBanner';
import { useDashboard } from '../../src/hooks/useDashboard';
import { useLayout } from '../../src/hooks/useLayout';
import { useNavStore } from '../../src/store/nav';
import { useThemeStore } from '../../src/store/theme';
import { useTabSwipe } from '../../src/hooks/useTabSwipe';
import { useDrawerStore } from '../../src/store/drawer';

export default function DashboardScreen() {
  const { overview, entrypoints } = useDashboard();
  const qc                  = useQueryClient();
  const c                   = useThemeStore(s => s.colors);
  const setRouteProtoFilter = useNavStore(s => s.setRouteProtoFilter);
  const openDrawer  = useDrawerStore(s => s.open);
  const swipe       = useTabSwipe('index');
  const navigation  = useNavigation<any>();
  const { contentPadding, contentMaxWidth } = useLayout();
  const twoCol = true;

  const exploreRoutes = (proto: string) => {
    setRouteProtoFilter(proto);
    navigation.navigate('routes');
  };
  const scrollAnim = useRef(new Animated.Value(0)).current;

  const onRefresh = () => {
    qc.invalidateQueries({ queryKey: ['overview'] });
    qc.invalidateQueries({ queryKey: ['entrypoints'] });
  };

  const d = overview.data;

  return (
    <View style={[styles.container, { backgroundColor: c.bg }]} {...swipe}>
      <TopBar
        title="Dashboard"
        scrollAnim={scrollAnim}
        onMenuPress={openDrawer}
      />
      <DemoBanner />
      <Animated.ScrollView
        style={styles.scroll}
        contentContainerStyle={[styles.content, { padding: contentPadding, alignSelf: 'center', width: '100%', maxWidth: contentMaxWidth }]}
        refreshControl={<RefreshControl refreshing={overview.isFetching} onRefresh={onRefresh} tintColor={c.blue} />}
        onScroll={Animated.event(
          [{ nativeEvent: { contentOffset: { y: scrollAnim } } }],
          { useNativeDriver: false },
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

        <View style={twoCol ? styles.grid : null}>
          <View style={twoCol ? styles.gridCol : null}>
            <StatCard title="HTTP Routers"  accent={c.blue}   data={d?.http?.routers}     vertical={twoCol} onExplore={() => exploreRoutes('HTTP')} />
            <StatCard title="TCP Routers"   accent={c.orange} data={d?.tcp?.routers}      vertical={twoCol} onExplore={() => exploreRoutes('TCP')} />
            <StatCard title="UDP Routers"   accent={c.purple} data={d?.udp?.routers}      vertical={twoCol} onExplore={() => exploreRoutes('UDP')} />
          </View>
          <View style={twoCol ? styles.gridCol : null}>
            <StatCard title="Services"    accent={c.green}  data={d?.http?.services}    vertical={twoCol} onExplore={() => navigation.navigate('live')} />
            <StatCard title="Middlewares" accent={c.yellow} data={d?.http?.middlewares} vertical={twoCol} onExplore={() => navigation.navigate('middlewares')} />
            <EntrypointCard data={entrypoints.data} />
          </View>
        </View>
      </Animated.ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  scroll:    { flex: 1 },
  content:   { paddingBottom: 110 },
  errorBox: {
    backgroundColor: 'rgba(239,68,68,0.1)',
    borderRadius: 8, padding: 12, marginBottom: 12, borderWidth: 1,
  },
  grid: {
    flexDirection: 'row',
    gap: 12,
    alignItems: 'flex-start',
  },
  gridCol: {
    flex: 1,
  },
});
