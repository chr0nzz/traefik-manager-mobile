import { ActivityIndicator, ScrollView, StyleSheet, TextInput, TouchableOpacity, View } from 'react-native';
import { Text } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useQueryClient } from '@tanstack/react-query';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useState } from 'react';
import { useAgentsStore } from '../../src/store/agents';
import { useTabsStore } from '../../src/store/tabs';
import { useAgentHealth, useAgents, useRenameAgent } from '../../src/hooks/useAgents';
import { useThemeStore } from '../../src/store/theme';
import { font, radius, spacing } from '../../src/theme';
import { AgentInfo } from '../../src/api/agents';

type Colors = ReturnType<typeof useThemeStore.getState>['colors'];

function HealthDot({ id, c }: { id: string; c: Colors }) {
  const { data, isFetching } = useAgentHealth(id);
  if (isFetching && !data) {
    return <ActivityIndicator size="small" color={c.muted} style={{ width: 10, height: 10 }} />;
  }
  const color = data?.ok ? c.green : c.red;
  return <View style={[styles.dot, { backgroundColor: color }]} />;
}

function ServerRow({
  label, isActive, onSwitch, healthId, agentId, onRename, c,
}: {
  label: string; isActive: boolean;
  onSwitch: () => void; healthId?: string;
  agentId?: string; onRename?: (id: string, name: string) => void;
  c: Colors;
}) {
  const [editing, setEditing] = useState(false);
  const [editText, setEditText] = useState(label);

  const handleRenameSubmit = () => {
    const trimmed = editText.trim();
    if (trimmed && trimmed !== label && agentId && onRename) {
      onRename(agentId, trimmed);
    }
    setEditing(false);
  };

  return (
    <View style={[styles.serverRow, { borderBottomColor: c.border }]}>
      <View style={styles.serverLeft}>
        {healthId
          ? <HealthDot id={healthId} c={c} />
          : <View style={[styles.dot, { backgroundColor: c.green }]} />
        }
        <View style={styles.serverLabels}>
          {editing ? (
            <TextInput
              value={editText}
              onChangeText={setEditText}
              onBlur={handleRenameSubmit}
              onSubmitEditing={handleRenameSubmit}
              autoFocus
              style={[styles.renameInput, { color: c.text, borderColor: c.blue }]}
            />
          ) : (
            <Text style={[styles.serverName, { color: c.text }]}>{label}</Text>
          )}
        </View>
      </View>
      <View style={styles.serverRight}>
        {agentId && !editing && (
          <TouchableOpacity
            onPress={() => { setEditText(label); setEditing(true); }}
            hitSlop={8}
            style={styles.iconBtn}
          >
            <MaterialCommunityIcons name="pencil-outline" size={16} color={c.muted} />
          </TouchableOpacity>
        )}
        {editing && (
          <TouchableOpacity onPress={handleRenameSubmit} hitSlop={8} style={styles.iconBtn}>
            <MaterialCommunityIcons name="check" size={16} color={c.green} />
          </TouchableOpacity>
        )}
        {isActive && !editing && (
          <View style={[styles.activeBadge, { backgroundColor: c.blue + '20', borderColor: c.blue + '55' }]}>
            <Text style={[styles.activeBadgeText, { color: c.blue }]}>Active</Text>
          </View>
        )}
        {!isActive && !editing && (
          <TouchableOpacity
            style={[styles.switchBtn, { backgroundColor: c.blue + '18', borderColor: c.blue + '55' }]}
            onPress={onSwitch}
            hitSlop={8}
          >
            <Text style={[styles.switchBtnText, { color: c.blue }]}>Switch</Text>
          </TouchableOpacity>
        )}
      </View>
    </View>
  );
}

export default function AgentsScreen() {
  const router          = useRouter();
  const insets          = useSafeAreaInsets();
  const c               = useThemeStore(s => s.colors);
  const qc              = useQueryClient();
  const { data: agents } = useAgents();
  const activeAgentId    = useAgentsStore(s => s.activeAgentId);
  const setActiveAgent   = useAgentsStore(s => s.setActiveAgent);
  const rename           = useRenameAgent();

  const agentList: AgentInfo[] = Array.isArray(agents) ? agents : [];

  const handleSwitch = async (id: string | null) => {
    await setActiveAgent(id);
    useTabsStore.getState().selectServer(id);
    qc.clear();
    router.back();
  };

  return (
    <View style={[styles.screen, { backgroundColor: c.bg }]}>
      <View style={[styles.headerBar, { paddingTop: insets.top + 4, borderBottomColor: c.border, backgroundColor: c.card }]}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={8} style={styles.backBtn}>
          <MaterialCommunityIcons name="arrow-left" size={22} color={c.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: c.text }]}>Servers</Text>
        <View style={styles.headerSpacer} />
      </View>

      <ScrollView contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 24 }]}>
        <Text style={[styles.sectionLabel, { color: c.muted }]}>SELECT ACTIVE SERVER</Text>
        <View style={[styles.card, { backgroundColor: c.card, borderColor: c.border }]}>
          <ServerRow
            label="Host"
            isActive={activeAgentId === null}
            onSwitch={() => handleSwitch(null)}
            c={c}
          />
          {agentList.map((agent, i) => (
            <ServerRow
              key={agent.id}
              label={agent.name}
              isActive={activeAgentId === agent.id}
              onSwitch={() => handleSwitch(agent.id)}
              healthId={agent.id}
              agentId={agent.id}
              onRename={(id, name) => rename.mutate({ id, name })}
              c={c}
            />
          ))}
          {agentList.length === 0 && (
            <View style={styles.empty}>
              <MaterialCommunityIcons name="server-network-off" size={28} color={c.muted} />
              <Text style={[styles.emptyText, { color: c.muted }]}>No agents configured</Text>
              <Text style={[styles.emptyHint, { color: c.muted }]}>Add agents in Settings - Agents on the web app</Text>
            </View>
          )}
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  headerBar: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.lg,
    paddingBottom: spacing.md,
    borderBottomWidth: 1,
    gap: spacing.sm,
  },
  backBtn:      { padding: 2 },
  headerTitle:  { flex: 1, fontSize: font.lg, fontWeight: '700' },
  headerSpacer: { width: 26 },
  content: { padding: spacing.lg, gap: spacing.sm },
  sectionLabel: {
    fontSize: font.xs, fontWeight: '700', letterSpacing: 0.8,
    textTransform: 'uppercase', paddingHorizontal: 4,
  },
  card: {
    borderRadius: radius.md,
    borderWidth: 1,
    overflow: 'hidden',
  },
  serverRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.md,
    paddingVertical: 14,
    borderBottomWidth: 1,
    gap: spacing.sm,
  },
  serverLeft: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 10 },
  serverLabels: { flex: 1 },
  serverName: { fontSize: font.md, fontWeight: '600' },
  serverUrl: { fontSize: font.xs, marginTop: 2 },
  serverRight: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  dot: { width: 8, height: 8, borderRadius: 4, flexShrink: 0 },
  iconBtn: { padding: 4 },
  renameInput: {
    fontSize: font.md, fontWeight: '600',
    borderBottomWidth: 1, paddingVertical: 2, minWidth: 80,
  },
  activeBadge: {
    paddingHorizontal: 8, paddingVertical: 3,
    borderRadius: 999, borderWidth: 1,
  },
  activeBadgeText: { fontSize: font.xs, fontWeight: '700' },
  switchBtn: {
    paddingHorizontal: 10, paddingVertical: 5,
    borderRadius: radius.sm, borderWidth: 1,
  },
  switchBtnText: { fontSize: font.xs, fontWeight: '700' },
  empty: { padding: spacing.xl, alignItems: 'center', gap: spacing.sm },
  emptyText: { fontSize: font.md, fontWeight: '600' },
  emptyHint: { fontSize: font.sm, textAlign: 'center' },
});
