import { useRef, useState } from 'react';
import { Animated, FlatList, Pressable, RefreshControl, StyleSheet, View } from 'react-native';
import { Surface, Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useQueryClient } from '@tanstack/react-query';
import { TopBar } from '../../src/components/TopBar';
import { DemoBanner } from '../../src/components/DemoBanner';
import { usePlugins } from '../../src/hooks/usePlugins';
import { useLayout } from '../../src/hooks/useLayout';
import { useThemeStore } from '../../src/store/theme';
import { useDrawerStore } from '../../src/store/drawer';
import { useTabSwipe } from '../../src/hooks/useTabSwipe';
import { Plugin } from '../../src/api/traefik';
import { font, radius, spacing } from '../../src/theme';

function PluginCard({ plugin, c }: { plugin: Plugin; c: ReturnType<typeof useThemeStore.getState>['colors'] }) {
  return (
    <Surface style={[styles.card, { backgroundColor: c.card }]} elevation={1}>
      <View style={styles.row}>
        <View style={[styles.iconWrap, { backgroundColor: c.blue + '22' }]}>
          <MaterialCommunityIcons name="puzzle-outline" size={18} color={c.blue} />
        </View>
        <View style={styles.info}>
          <Text style={[styles.name, { color: c.text }]} numberOfLines={1}>{plugin.name}</Text>
          <Text style={[styles.module, { color: c.muted }]} numberOfLines={1}>{plugin.moduleName}</Text>
        </View>
        {!!plugin.version && (
          <View style={[styles.versionBadge, { backgroundColor: c.secondaryContainer ?? c.card, borderColor: c.border }]}>
            <Text style={[styles.versionText, { color: c.muted }]}>{plugin.version}</Text>
          </View>
        )}
      </View>
    </Surface>
  );
}

export default function PluginsScreen() {
  const { data, isFetching, isError } = usePlugins();
  const qc          = useQueryClient();
  const c           = useThemeStore(s => s.colors);
  const openDrawer  = useDrawerStore(s => s.open);
  const swipe       = useTabSwipe('plugins');
  const scrollAnim  = useRef(new Animated.Value(0)).current;
  const { contentPadding, contentMaxWidth, listBottomPadding } = useLayout();
  const [search, setSearch] = useState('');

  const allPlugins = data?.plugins ?? [];
  const plugins = search
    ? allPlugins.filter(p => p.name.toLowerCase().includes(search.toLowerCase()))
    : allPlugins;

  const refresh = () => qc.invalidateQueries({ queryKey: ['plugins'] });

  return (
    <View style={[styles.container, { backgroundColor: c.bg }]} {...swipe}>
      <TopBar
        title="Plugins"
        scrollAnim={scrollAnim}
        accent={c.blue}
        icon="puzzle-outline"
        onMenuPress={openDrawer}
        searchValue={search}
        onSearchChange={setSearch}
        searchPlaceholder="Search plugins..."
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
      {!!data?.error && (
        <View style={[styles.errorBanner, { backgroundColor: c.red + '22' }]}>
          <Text style={[styles.errorText, { color: c.red }]}>{data.error}</Text>
        </View>
      )}
      <Animated.FlatList
        data={plugins}
        keyExtractor={(item, i) => `${item.name}-${i}`}
        renderItem={({ item }) => <PluginCard plugin={item} c={c} />}
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
              <MaterialCommunityIcons name="puzzle-outline" size={48} color={c.muted} style={{ opacity: 0.4 }} />
              <Text style={[styles.emptyText, { color: c.muted }]}>
                {isError ? 'Failed to load plugins' : 'No plugins installed'}
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
  container:    { flex: 1 },
  list:         { gap: spacing.sm, paddingTop: spacing.md },
  card:         { borderRadius: radius.md, overflow: 'hidden' },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: spacing.md,
    gap: spacing.sm,
  },
  iconWrap: {
    width: 36,
    height: 36,
    borderRadius: radius.sm,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  info:         { flex: 1, minWidth: 0 },
  name:         { fontSize: font.md, fontWeight: '600' },
  module:       { fontSize: font.sm, marginTop: 2 },
  versionBadge: {
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
    borderWidth: 1,
    flexShrink: 0,
  },
  versionText:  { fontSize: font.xs, fontWeight: '600' },
  empty:        { alignItems: 'center', paddingTop: 80, gap: spacing.md },
  emptyText:    { fontSize: font.md },
  errorBanner:  { paddingHorizontal: spacing.lg, paddingVertical: 8 },
  errorText:    { fontSize: font.sm, fontWeight: '500' },
  iconBtn: {
    width: 36, height: 36,
    alignItems: 'center', justifyContent: 'center',
    borderRadius: 8, borderWidth: 1,
  },
});
