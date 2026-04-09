export const darkColors = {
  bg:                   '#0d1117',
  card:                 '#161b22',
  border:               '#30363d',
  text:                 '#e6edf3',
  muted:                '#7d8590',
  blue:                 '#24a1de',
  green:                '#22c55e',
  yellow:               '#f59e0b',
  red:                  '#ef4444',
  orange:               '#f0883e',
  purple:               '#a371f7',
  teal:                 '#1abc9c',
  secondaryContainer:   '#1c3a50',
  onSecondaryContainer: '#9ecfef',
};

export const lightColors = {
  bg:                   '#f6f8fa',
  card:                 '#ffffff',
  border:               '#d0d7de',
  text:                 '#1f2328',
  muted:                '#636e7b',
  blue:                 '#0969da',
  green:                '#1a7f37',
  yellow:               '#9a6700',
  red:                  '#cf222e',
  orange:               '#bc4c00',
  purple:               '#8250df',
  teal:                 '#0e7069',
  secondaryContainer:   '#cce5f6',
  onSecondaryContainer: '#003a57',
};

export const colors = darkColors;

type Colors = typeof darkColors;

export function dynamicColorsFromM3(scheme: Record<string, any>): Colors {
  const elev = scheme.elevation as Record<string, string> | undefined;
  return {
    bg:                   scheme.background,
    card:                 elev?.level2 ?? scheme.surfaceVariant ?? scheme.surface,
    border:               scheme.outlineVariant ?? scheme.outline,
    text:                 scheme.onBackground,
    muted:                scheme.onSurfaceVariant,
    blue:                 scheme.primary,
    green:                '#22c55e',
    yellow:               '#f59e0b',
    red:                  scheme.error,
    orange:               '#f0883e',
    purple:               scheme.secondary,
    teal:                 scheme.tertiary,
    secondaryContainer:   scheme.secondaryContainer,
    onSecondaryContainer: scheme.onSecondaryContainer,
  };
}

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
};

export const radius = {
  sm: 8,
  md: 12,
  lg: 16,
  full: 999,
};

export const font = {
  xs: 11,
  sm: 12,
  base: 13,
  md: 14,
  lg: 16,
  xl: 18,
  xxl: 24,
};
