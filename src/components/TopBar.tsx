import React from 'react';
import { Animated, Platform, Pressable, StyleSheet, useWindowDimensions, View } from 'react-native';
import { Text } from 'react-native-paper';
import { StatusBar } from 'expo-status-bar';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useThemeStore } from '../store/theme';

interface Props {
  title: string;
  scrollAnim: Animated.Value;
  onMenuPress?: () => void;
  right?: React.ReactNode;
  accent?: string;
  icon?: string;
}

function useIsWide() {
  const { width } = useWindowDimensions();
  return width >= 600;
}

export function TopBar({ title, scrollAnim, onMenuPress, right, accent, icon }: Props) {
  const c        = useThemeStore(s => s.colors);
  const isDark   = useThemeStore(s => s.isDark);
  const insets   = useSafeAreaInsets();
  const isWide   = useIsWide();
  const topPad   = Platform.OS === 'android' ? (insets.top || 24) : insets.top;
  const showMenu = !!onMenuPress && !isWide;

  const scrollOpacity = scrollAnim.interpolate({
    inputRange:  [0, 30],
    outputRange: [0, 1],
    extrapolate: 'clamp',
  });

  return (
    <View style={{ position: 'relative', backgroundColor: c.bg }}>
      <StatusBar style={isDark ? 'light' : 'dark'} />

      <Animated.View
        style={[
          StyleSheet.absoluteFill,
          { backgroundColor: c.card, opacity: scrollOpacity },
        ]}
        pointerEvents="none"
      />

      <View style={[styles.bar, { paddingTop: topPad }]}>
        <View style={styles.inner}>
          {showMenu ? (
            <Pressable
              onPress={onMenuPress}
              style={styles.menuBtn}
              hitSlop={8}
              android_ripple={{ color: c.border, borderless: true, radius: 20 }}
            >
              <MaterialCommunityIcons name="menu" size={24} color={c.text} />
            </Pressable>
          ) : icon ? (
            <View style={styles.iconSlot}>
              <MaterialCommunityIcons name={icon as any} size={22} color={accent ?? c.text} />
            </View>
          ) : null}

          <Text style={[styles.title, { color: c.text }]}>{title}</Text>

          {right && <View style={styles.rightSlot}>{right}</View>}
        </View>
      </View>

      <Animated.View
        style={[
          styles.scrollBorder,
          { borderBottomColor: c.border, opacity: scrollOpacity },
        ]}
        pointerEvents="none"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  bar: { width: '100%' },
  inner: {
    flexDirection: 'row',
    alignItems: 'center',
    height: 56,
    paddingHorizontal: 4,
    gap: 4,
  },
  menuBtn: {
    width: 48,
    height: 48,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 24,
  },
  iconSlot: {
    width: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    flex: 1,
    fontSize: 22,
    fontWeight: '700',
    letterSpacing: 0,
    paddingHorizontal: 8,
  },
  rightSlot: {
    paddingRight: 12,
    alignItems: 'flex-end',
    justifyContent: 'center',
  },
  scrollBorder: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    borderBottomWidth: 1,
  },
});
