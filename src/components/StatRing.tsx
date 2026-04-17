import React from 'react';
import { View } from 'react-native';
import Svg, { Circle, G } from 'react-native-svg';
import { useThemeStore } from '../store/theme';

interface Segment { value: number; color: string; }

interface Props {
  size?: number;
  strokeWidth?: number;
  segments: Segment[];
}

export function StatRing({ size = 72, strokeWidth = 12, segments }: Props) {
  const c = useThemeStore(s => s.colors);
  const r = (size - strokeWidth) / 2;
  const circ = 2 * Math.PI * r;
  const cx = size / 2;
  const cy = size / 2;
  const total = segments.reduce((s, seg) => s + (seg.value || 0), 0);

  let cumulative = 0;
  const arcs = total > 0
    ? segments.filter(s => s.value > 0).map(seg => {
        const len    = (seg.value / total) * circ;
        const offset = -(cumulative / total) * circ;
        cumulative += seg.value;
        return { len, offset, color: seg.color };
      })
    : [];

  return (
    <View style={{ width: size, height: size }}>
      <Svg width={size} height={size}>
        <Circle cx={cx} cy={cy} r={r} fill="none" stroke={c.muted + '40'} strokeWidth={strokeWidth} />
        <G rotation={-90} origin={`${cx},${cy}`}>
          {arcs.map((arc, i) => (
            <Circle
              key={i}
              cx={cx} cy={cy} r={r}
              fill="none"
              stroke={arc.color}
              strokeWidth={strokeWidth}
              strokeDasharray={[arc.len, circ - arc.len]}
              strokeDashoffset={arc.offset}
            />
          ))}
        </G>
      </Svg>
    </View>
  );
}
