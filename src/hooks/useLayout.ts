import { useWindowDimensions } from 'react-native';

export type WindowClass = 'compact' | 'medium' | 'expanded';

export function useLayout() {
  const { width } = useWindowDimensions();

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

  return { windowClass, isCompact, isMedium, isExpanded, columns, contentPadding, contentMaxWidth };
}
