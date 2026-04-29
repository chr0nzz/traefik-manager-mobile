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

function blendHex(base: string, overlay: string, alpha: number): string {
  const b = base.replace('#', '');
  const o = overlay.replace('#', '');
  const br = parseInt(b.slice(0, 2), 16);
  const bg = parseInt(b.slice(2, 4), 16);
  const bb = parseInt(b.slice(4, 6), 16);
  const or = parseInt(o.slice(0, 2), 16);
  const og = parseInt(o.slice(2, 4), 16);
  const ob = parseInt(o.slice(4, 6), 16);
  const r = Math.round(br * (1 - alpha) + or * alpha);
  const g = Math.round(bg * (1 - alpha) + og * alpha);
  const bl = Math.round(bb * (1 - alpha) + ob * alpha);
  return `#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${bl.toString(16).padStart(2, '0')}`;
}

export function dynamicColorsFromM3(scheme: Record<string, any>, isDark: boolean): Colors {
  const base = isDark ? darkColors : lightColors;
  const surface = scheme.background ?? scheme.surface ?? base.bg;
  const primary = scheme.primary ?? base.blue;
  return {
    bg:                   surface,
    card:                 blendHex(surface, primary, 0.08),
    border:               scheme.outlineVariant ?? scheme.outline,
    text:                 scheme.onSurface,
    muted:                scheme.onSurfaceVariant,
    blue:                 primary,
    green:                base.green,
    yellow:               base.yellow,
    red:                  scheme.error,
    orange:               base.orange,
    purple:               scheme.secondary ?? base.purple,
    teal:                 base.teal,
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
