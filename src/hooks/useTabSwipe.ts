import { useRef } from 'react';
import { PanResponder } from 'react-native';
import { useNavigation } from '@react-navigation/native';

const TABS = ['index', 'routes', 'middlewares', 'live', 'settings'];

export function useTabSwipe(tabName: string) {
  const navigation = useNavigation<any>();
  const tabRef = useRef(tabName);
  tabRef.current = tabName;

  const panResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder:        () => false,
      onMoveShouldSetPanResponder:         (_, gs) =>
        Math.abs(gs.dx) > Math.abs(gs.dy) * 1.5 && Math.abs(gs.dx) > 25,
      onMoveShouldSetPanResponderCapture:  () => false,
      onPanResponderRelease: (_, gs) => {
        const idx = TABS.indexOf(tabRef.current);
        if (idx === -1) return;
        if (gs.dx < -50 && gs.vx < -0.3) {
          const next = TABS[Math.min(idx + 1, TABS.length - 1)];
          if (next !== tabRef.current) navigation.navigate(next);
        } else if (gs.dx > 50 && gs.vx > 0.3) {
          const prev = TABS[Math.max(idx - 1, 0)];
          if (prev !== tabRef.current) navigation.navigate(prev);
        }
      },
    })
  ).current;

  return panResponder.panHandlers;
}
