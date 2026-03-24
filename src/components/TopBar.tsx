import React from 'react';
import { Animated, Platform, StyleSheet, View } from 'react-native';
import { Text } from 'react-native-paper';
import { StatusBar } from 'expo-status-bar';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useThemeStore } from '../store/theme';
import { font } from '../theme';

interface Props {
  title: string;
  scrollAnim: Animated.Value;
  accent?: string;
  right?: React.ReactNode;
}

export function TopBar({ title, scrollAnim, accent, right }: Props) {
  const c      = useThemeStore(s => s.colors);
  const isDark = useThemeStore(s => s.isDark);
  const insets = useSafeAreaInsets();
  const topPad = Platform.OS === 'android' ? (insets.top || 24) : insets.top;

  // Fade in card background + border on scroll (elevation effect)
  const scrollOpacity = scrollAnim.interpolate({
    inputRange:  [0, 30],
    outputRange: [0, 1],
    extrapolate: 'clamp',
  });

  return (
    <View style={{ position: 'relative' }}>
      <StatusBar style={isDark ? 'light' : 'dark'} />

      {/* Base bar — always c.bg */}
      <View style={[styles.bar, { backgroundColor: c.bg, paddingTop: topPad }]}>
        <View style={styles.inner}>
          {/* Accent dot + title */}
          <View style={styles.titleRow}>
            {accent && <View style={[styles.accentDot, { backgroundColor: accent }]} />}
            <Text style={[styles.title, { color: c.text }]}>{title}</Text>
          </View>
          {right && <View style={styles.rightSlot}>{right}</View>}
        </View>
        {/* Accent bottom border */}
        {accent && <View style={[styles.accentLine, { backgroundColor: accent }]} />}
      </View>

      {/* Scroll elevation overlay — c.card fades in */}
      <Animated.View
        style={[
          StyleSheet.absoluteFill,
          { backgroundColor: c.card, opacity: scrollOpacity },
        ]}
        pointerEvents="none"
      />
      {/* Scroll border bottom */}
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
    justifyContent: 'space-between',
    height: 46,
    paddingHorizontal: 16,
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    flex: 1,
  },
  accentDot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  title: {
    fontSize: font.lg,
    fontWeight: '700',
    letterSpacing: 0.1,
  },
  rightSlot: {
    alignItems: 'flex-end',
    justifyContent: 'center',
  },
  accentLine: {
    height: 2,
    width: '100%',
    opacity: 0.7,
  },
  scrollBorder: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    borderBottomWidth: 1,
  },
});
