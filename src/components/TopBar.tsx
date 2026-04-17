import React, { useRef, useState } from 'react';
import { Animated, Modal, Platform, Pressable, StyleSheet, TextInput, View } from 'react-native';
import { Text } from 'react-native-paper';
import { StatusBar } from 'expo-status-bar';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useThemeStore } from '../store/theme';
import { useDrawerStore } from '../store/drawer';
import { radius } from '../theme';

export interface OverflowMenuItem {
  label: string;
  selected?: boolean;
  onPress: () => void;
  icon?: string;
}

export interface OverflowMenuSection {
  title?: string;
  items: OverflowMenuItem[];
}

interface MenuProps {
  visible: boolean;
  onDismiss: () => void;
  sections: OverflowMenuSection[];
  anchorY: number;
}

function M3OverflowMenu({ visible, onDismiss, sections, anchorY }: MenuProps) {
  const c = useThemeStore(s => s.colors);

  return (
    <Modal visible={visible} transparent animationType="fade" statusBarTranslucent onRequestClose={onDismiss}>
      <Pressable style={StyleSheet.absoluteFill} onPress={onDismiss} />
      <View style={[menu.surface, { backgroundColor: c.card, top: anchorY + 4 }]}>
        {sections.map((section, si) => (
          <React.Fragment key={si}>
            {si > 0 && <View style={[menu.divider, { backgroundColor: c.border }]} />}
            {section.title && (
              <View style={menu.sectionLabel}>
                <Text style={[menu.sectionText, { color: c.muted }]}>{section.title}</Text>
              </View>
            )}
            {section.items.map((item, ii) => (
              <Pressable
                key={ii}
                style={({ pressed }) => [menu.item, pressed && { backgroundColor: c.muted + '20' }]}
                onPress={() => { item.onPress(); onDismiss(); }}
                android_ripple={{ color: c.muted + '30' }}
              >
                {item.icon && !item.selected ? (
                  <MaterialCommunityIcons name={item.icon as any} size={20} color={c.muted} />
                ) : (
                  <View style={{ width: 20 }} />
                )}
                <Text style={[menu.itemLabel, { color: c.text }]}>{item.label}</Text>
                {item.selected && (
                  <MaterialCommunityIcons name="check" size={20} color={c.blue} />
                )}
              </Pressable>
            ))}
          </React.Fragment>
        ))}
      </View>
    </Modal>
  );
}

interface Props {
  title: string;
  scrollAnim: Animated.Value;
  onMenuPress?: () => void;
  right?: React.ReactNode;
  accent?: string;
  icon?: string;
  searchValue?: string;
  onSearchChange?: (v: string) => void;
  searchPlaceholder?: string;
  searchAccent?: string;
  overflowSections?: OverflowMenuSection[];
  wideFilters?: React.ReactNode;
}

export function TopBar({ title, scrollAnim, onMenuPress, right, accent, icon, searchValue, onSearchChange, searchPlaceholder, searchAccent, overflowSections, wideFilters }: Props) {
  const c        = useThemeStore(s => s.colors);
  const isDark   = useThemeStore(s => s.isDark);
  const insets   = useSafeAreaInsets();
  const isWide   = useDrawerStore(s => s.isWide);
  const topPad   = Platform.OS === 'android' ? (insets.top || 24) : insets.top;
  const showMenu = !!onMenuPress && !isWide;

  const [searchOpen, setSearchOpen]   = useState(false);
  const [menuVisible, setMenuVisible] = useState(false);
  const [menuAnchorY, setMenuAnchorY] = useState(0);
  const searchRef    = useRef<TextInput>(null);
  const containerRef = useRef<View>(null);

  const hasSearch   = !!onSearchChange;
  const hasOverflow = !isWide && !!overflowSections?.length;

  const scrollOpacity = scrollAnim.interpolate({
    inputRange:  [0, 30],
    outputRange: [0, 1],
    extrapolate: 'clamp',
  });

  const openSearch = () => {
    setSearchOpen(true);
    setTimeout(() => searchRef.current?.focus(), 50);
  };

  const closeSearch = () => {
    onSearchChange?.('');
    setSearchOpen(false);
  };

  const openOverflow = () => {
    containerRef.current?.measure((_x, _y, _w, h, _px, pageY) => {
      setMenuAnchorY(pageY + h);
      setMenuVisible(true);
    });
  };

  return (
    <View ref={containerRef} collapsable={false} style={{ position: 'relative', backgroundColor: c.bg }}>
      <StatusBar style={isDark ? 'light' : 'dark'} />

      <Animated.View
        style={[StyleSheet.absoluteFill, { backgroundColor: c.card, opacity: scrollOpacity }]}
        pointerEvents="none"
      />

      <View style={[styles.bar, { paddingTop: topPad }]}>
        {searchOpen ? (
          <View style={styles.searchRow}>
            <Pressable
              onPress={closeSearch}
              hitSlop={8}
              style={[styles.iconBtn, { borderColor: c.border }]}
              android_ripple={{ color: c.muted + '40' }}
            >
              <MaterialCommunityIcons name="arrow-left" size={20} color={c.muted} />
            </Pressable>
            <TextInput
              ref={searchRef}
              style={[styles.searchInput, { color: c.text, backgroundColor: c.card, borderColor: (searchAccent ?? c.blue) + '88' }]}
              value={searchValue}
              onChangeText={onSearchChange}
              placeholder={searchPlaceholder ?? 'Search...'}
              placeholderTextColor={c.muted}
              autoCorrect={false}
              returnKeyType="search"
            />
            {!!searchValue && (
              <Pressable
                onPress={() => onSearchChange?.('')}
                hitSlop={8}
                style={[styles.iconBtn, { borderColor: c.border }]}
                android_ripple={{ color: c.muted + '40' }}
              >
                <MaterialCommunityIcons name="close-circle" size={18} color={c.muted} />
              </Pressable>
            )}
          </View>
        ) : (
          <View style={styles.inner}>
            {showMenu ? (
              <Pressable
                onPress={onMenuPress}
                style={styles.gearBtn}
                hitSlop={8}
                android_ripple={{ color: c.muted + '40' }}
              >
                <MaterialCommunityIcons name="cog-outline" size={20} color={c.muted} />
              </Pressable>
            ) : icon ? (
              <View style={styles.gearBtn}>
                <MaterialCommunityIcons name={icon as any} size={20} color={accent ?? c.muted} />
              </View>
            ) : null}

            <Text style={[styles.title, { color: c.text }]} numberOfLines={1}>{title}</Text>

            <View style={{ flex: 1 }} />

            {isWide && wideFilters && (
              <View style={styles.wideInline}>{wideFilters}</View>
            )}
            {right}
            {hasSearch && (
              <Pressable
                onPress={openSearch}
                style={[styles.iconBtn, { borderColor: c.border }]}
                android_ripple={{ color: c.muted + '40' }}
              >
                <MaterialCommunityIcons name="magnify" size={20} color={c.blue} />
              </Pressable>
            )}
            {hasOverflow && (
              <Pressable
                onPress={openOverflow}
                style={[styles.iconBtn, { borderColor: c.border }]}
                android_ripple={{ color: c.muted + '40' }}
              >
                <MaterialCommunityIcons name="dots-vertical" size={20} color={c.muted} />
              </Pressable>
            )}
          </View>
        )}
      </View>

      {!isWide && (
        <Animated.View
          style={[styles.scrollBorder, { borderBottomColor: c.border, opacity: scrollOpacity }]}
          pointerEvents="none"
        />
      )}

      {hasOverflow && (
        <M3OverflowMenu
          visible={menuVisible}
          onDismiss={() => setMenuVisible(false)}
          sections={overflowSections!}
          anchorY={menuAnchorY}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: { width: '100%' },
  inner: {
    flexDirection: 'row',
    alignItems: 'center',
    height: 56,
    paddingLeft: 8,
    paddingRight: 8,
    gap: 6,
  },
  searchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    height: 56,
    paddingHorizontal: 8,
    gap: 6,
  },
  gearBtn: {
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.sm,
  },
  iconBtn: {
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: radius.sm,
    borderWidth: 1,
  },
  title: {
    fontSize: 22,
    fontWeight: '700',
    letterSpacing: 0,
    paddingHorizontal: 10,
  },
  wideInline: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginRight: 6,
  },
  searchInput: {
    flex: 1,
    borderRadius: radius.sm,
    borderWidth: 1.5,
    paddingHorizontal: 14,
    paddingVertical: 8,
    fontSize: 15,
  },
  scrollBorder: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    borderBottomWidth: 1,
  },
});

const menu = StyleSheet.create({
  surface: {
    position: 'absolute',
    right: 8,
    minWidth: 180,
    maxWidth: 280,
    borderRadius: radius.sm,
    overflow: 'hidden',
    elevation: 4,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.15,
    shadowRadius: 8,
  },
  divider: {
    height: 1,
    marginVertical: 4,
  },
  sectionLabel: {
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 4,
  },
  sectionText: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.5,
  },
  item: {
    flexDirection: 'row',
    alignItems: 'center',
    height: 48,
    paddingHorizontal: 16,
    gap: 12,
  },
  itemLabel: {
    flex: 1,
    fontSize: 14,
  },
});
