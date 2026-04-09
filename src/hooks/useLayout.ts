import { useWindowDimensions } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

export type WindowClass = 'compact' | 'medium' | 'expanded';

export function useLayout() {
  const { width } = useWindowDimensions();
  const insets = useSafeAreaInsets();

  const windowClass: WindowClass =
    width < 600  ? 'compact'  :
    width < 840  ? 'medium'   :
                   'expanded';

  const isCompact  = windowClass === 'compact';
  const isMedium   = windowClass === 'medium';
  const isExpanded = windowClass === 'expanded';

  const columns = isCompact ? 1 : 2;

  const contentPadding =
    isExpanded ? 24 :
    isMedium   ? 20 :
                 16;

  const contentMaxWidth =
    isExpanded ? 1200 :
    isMedium   ? 840  :
                 undefined;

  const listBottomPadding = 60 + insets.bottom + 16;

  return { windowClass, isCompact, isMedium, isExpanded, columns, contentPadding, contentMaxWidth, listBottomPadding };
}
