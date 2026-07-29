import { ScrollView, StyleSheet, TouchableOpacity, View } from 'react-native';
import { ActivityIndicator, Surface, Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useQuery } from '@tanstack/react-query';
import { getClientIpDiagnostic, IpClass } from '../../src/api/traefik';
import { useThemeStore } from '../../src/store/theme';
import { font, radius, spacing } from '../../src/theme';

type Colors = ReturnType<typeof useThemeStore.getState>['colors'];

const CLASS_LABEL: Record<IpClass, string> = {
  'public':     'Public',
  'private':    'Private',
  'cgnat':      'CGNAT',
  'loopback':   'Loopback',
  'link-local': 'Link-local',
  'unknown':    'Unknown',
};

function classColor(cls: IpClass, c: Colors) {
  if (cls === 'public') return c.green;
  if (cls === 'unknown') return c.muted;
  return '#d4a017';
}

function Section({ title, children, c }: { title: string; children: React.ReactNode; c: Colors }) {
  return (
    <View style={styles.section}>
      <Text style={[styles.sectionLabel, { color: c.muted }]}>{title}</Text>
      <Surface style={[styles.sectionBody, { backgroundColor: c.card }]} elevation={1}>
        {children}
      </Surface>
    </View>
  );
}

function Row({ label, value, cls, hint, isLast, c }: {
  label: string; value: string; cls?: IpClass; hint?: string; isLast: boolean; c: Colors;
}) {
  return (
    <View style={[styles.row, !isLast && { borderBottomWidth: 1, borderBottomColor: c.border }]}>
      <View style={styles.rowTop}>
        <Text style={[styles.rowLabel, { color: c.muted }]}>{label}</Text>
        {!!cls && (
          <View style={[styles.chip, { borderColor: classColor(cls, c) }]}>
            <Text style={[styles.chipText, { color: classColor(cls, c) }]}>{CLASS_LABEL[cls]}</Text>
          </View>
        )}
      </View>
      <Text style={[styles.rowValue, { color: c.text }]} selectable>{value || '—'}</Text>
      {!!hint && <Text style={[styles.rowHint, { color: c.muted }]}>{hint}</Text>}
    </View>
  );
}

export default function ClientIpScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const c      = useThemeStore(s => s.colors);

  const q = useQuery({ queryKey: ['client-ip'], queryFn: getClientIpDiagnostic, retry: 1 });
  const d = q.data;

  const misleading = d
    && d.effective_class !== 'public'
    && d.effective_class !== 'unknown'
    && d.forwarded_for_chain.length > 0;

  const headerEntries = Object.entries(d?.headers ?? {});

  return (
    <View style={[styles.screen, { backgroundColor: c.bg }]}>
      <View style={[styles.headerBar, { paddingTop: insets.top + 4, borderBottomColor: c.border, backgroundColor: c.card }]}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={8} style={styles.backBtn}>
          <MaterialCommunityIcons name="arrow-left" size={22} color={c.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: c.text }]}>Client IP</Text>
        <View style={styles.headerSpacer} />
      </View>

      <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 24 }]}>
        <Text style={[styles.intro, { color: c.muted }]}>
          What this server sees for your request. This is the address used for the
          login rate limiter, the audit log, ipAllowList and CrowdSec.
        </Text>

        {q.isLoading && <ActivityIndicator style={styles.loader} />}

        {q.isError && (
          <Surface style={[styles.notice, { backgroundColor: c.card, borderColor: c.red }]} elevation={0}>
            <Text style={[styles.noticeText, { color: c.text }]}>
              Could not read the diagnostic. It needs Traefik Manager v1.8.0 or newer.
            </Text>
          </Surface>
        )}

        {!!d && (
          <>
            {misleading && (
              <Surface style={[styles.notice, { backgroundColor: c.card, borderColor: '#d4a017' }]} elevation={0}>
                <Text style={[styles.noticeText, { color: c.text }]}>
                  The trusted client IP is {CLASS_LABEL[d.effective_class].toLowerCase()}, but forwarding
                  headers are present. If you expect public clients, the upstream trustedIPs or the
                  trusted hop count is probably wrong, and real client IPs are being lost.
                </Text>
              </Surface>
            )}

            <Section title="RESULT" c={c}>
              <Row
                label="Trusted client IP"
                value={d.effective_ip}
                cls={d.effective_class}
                hint="After trusted-proxy processing. This is what the app acts on."
                isLast={false}
                c={c}
              />
              <Row
                label="Socket peer"
                value={d.socket_peer}
                cls={d.socket_peer_class}
                hint="The raw TCP connection, before any header is trusted."
                isLast={false}
                c={c}
              />
              <Row
                label="Trusted proxy hops"
                value={String(d.proxy_hops)}
                hint="Set with PROXY_FIX_HOPS. Only count hops you control."
                isLast
                c={c}
              />
            </Section>

            {d.forwarded_for_chain.length > 0 && (
              <Section title="X-FORWARDED-FOR CHAIN" c={c}>
                {d.forwarded_for_chain.map((ip, i) => (
                  <Row
                    key={`${ip}-${i}`}
                    label={i === 0 ? 'Client (claimed)' : `Proxy ${i}`}
                    value={ip}
                    cls={d.classes[ip]}
                    isLast={i === d.forwarded_for_chain.length - 1}
                    c={c}
                  />
                ))}
              </Section>
            )}

            <Section title="FORWARDING HEADERS" c={c}>
              {headerEntries.map(([name, value], i) => (
                <Row
                  key={name}
                  label={name}
                  value={value}
                  isLast={i === headerEntries.length - 1}
                  c={c}
                />
              ))}
            </Section>
          </>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen:       { flex: 1 },
  headerBar:    { flexDirection: 'row', alignItems: 'center', paddingHorizontal: spacing.md, paddingBottom: spacing.sm, borderBottomWidth: 1 },
  backBtn:      { padding: 4 },
  headerTitle:  { flex: 1, textAlign: 'center', fontSize: font.lg, fontWeight: '700' },
  headerSpacer: { width: 30 },
  content:      { padding: spacing.md },
  intro:        { fontSize: font.sm, lineHeight: 18, marginBottom: spacing.md },
  loader:       { marginTop: spacing.lg },
  notice:       { borderRadius: radius.md, borderWidth: 1, padding: spacing.md, marginBottom: spacing.md },
  noticeText:   { fontSize: font.sm, lineHeight: 18 },
  section:      { marginBottom: spacing.lg },
  sectionLabel: { fontSize: font.xs, fontWeight: '700', letterSpacing: 0.5, marginBottom: spacing.sm },
  sectionBody:  { borderRadius: radius.md, overflow: 'hidden' },
  row:          { paddingHorizontal: spacing.md, paddingVertical: spacing.sm },
  rowTop:       { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 2 },
  rowLabel:     { fontSize: font.xs, fontWeight: '700', letterSpacing: 0.5 },
  rowValue:     { fontSize: font.sm, fontFamily: 'monospace' },
  rowHint:      { fontSize: font.xs, marginTop: 2, lineHeight: 15 },
  chip:         { paddingHorizontal: 6, paddingVertical: 1, borderRadius: radius.sm, borderWidth: 1 },
  chipText:     { fontSize: font.xs, fontWeight: '700' },
});
