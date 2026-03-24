import { useEffect, useState } from 'react';
import { Keyboard, Platform } from 'react-native';

/**
 * On iOS: returns keyboard height (window never resizes, so we must offset manually).
 * On Android: always returns 0 — Android resize mode shrinks the window automatically,
 * so bottom-anchored absolute sheets stay above the keyboard without any manual offset.
 */
export function useKeyboardHeight() {
  const [kbHeight, setKbHeight] = useState(0);

  useEffect(() => {
    if (Platform.OS !== 'ios') return;
    const show = Keyboard.addListener('keyboardWillShow', e => setKbHeight(e.endCoordinates.height));
    const hide = Keyboard.addListener('keyboardWillHide', ()  => setKbHeight(0));
    return () => { show.remove(); hide.remove(); };
  }, []);

  return kbHeight;
}
