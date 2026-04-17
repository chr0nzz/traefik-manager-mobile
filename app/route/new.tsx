import React, { useEffect, useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  TouchableOpacity,
  View,
} from 'react-native';
import { Button, SegmentedButtons, Switch, Text, TextInput } from 'react-native-paper';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { RouteFormData } from '../../src/api/routes';
import { font, radius, spacing } from '../../src/theme';
import { useThemeStore } from '../../src/store/theme';
import { useEntrypoints, useMiddlewares, useSaveRoute } from '../../src/hooks/useRoutes';
import { useConfigs } from '../../src/hooks/useConfigs';
import { useSettings } from '../../src/hooks/useSettings';
import { ConfigFilePicker } from '../../src/components/ConfigFilePicker';

const PROTOCOLS = ['http', 'tcp', 'udp'] as const;
type Colors = ReturnType<typeof useThemeStore.getState>['colors'];

export default function NewRouteScreen() {
  const router  = useRouter();
  const insets  = useSafeAreaInsets();
  const c       = useThemeStore(s => s.colors);

  const saveRoute              = useSaveRoute();
  const configs                = useConfigs();
  const { data: settings }     = useSettings();
  const { data: entrypointData } = useEntrypoints();
  const { data: middlewareData } = useMiddlewares();
  const resolvers   = (settings?.cert_resolver ?? '').split(',').map(r => r.trim()).filter(Boolean);
  const configFiles    = configs.data?.files ?? [];
  const configDirSet   = configs.data?.configDirSet ?? false;
  const showConfigPicker = configFiles.length > 1 || configDirSet;

  const [fName,             setFName]             = useState('');
  const [fProto,            setFProto]            = useState('http');
  const [fIp,               setFIp]               = useState('');
  const [fPort,             setFPort]             = useState('');
  const [fConfigFile,       setFConfigFile]       = useState('');
  const [saving,            setSaving]            = useState(false);
  const [saveErr,           setSaveErr]           = useState('');

  const [fSubdomain,        setFSubdomain]        = useState('');
  const [fDomains,          setFDomains]          = useState<string[]>([]);
  const [fEntryPoints,      setFEntryPoints]      = useState<string[]>([]);
  const [fMws,              setFMws]              = useState<string[]>([]);
  const [fScheme,           setFScheme]           = useState('http');
  const [fPassHost,         setFPassHost]         = useState(true);
  const [fInsecure,         setFInsecure]         = useState(false);
  const [fCertResolver,     setFCertResolver]     = useState('');

  const [fTcpRule,          setFTcpRule]          = useState('');
  const [fTcpEntryPoints,   setFTcpEntryPoints]   = useState<string[]>([]);

  const [fUdpEntryPoint,    setFUdpEntryPoint]    = useState('');
  const [fUdpEntryPoints,   setFUdpEntryPoints]   = useState<string[]>([]);

  const domains = settings?.domains ?? [];

  const toggleDomain = (d: string) => {
    setFDomains(prev => prev.includes(d) ? prev.filter(x => x !== d) : [...prev, d]);
  };

  const toggleEntryPoint = (ep: string) => {
    setFEntryPoints(prev => prev.includes(ep) ? prev.filter(x => x !== ep) : [...prev, ep]);
  };

  const toggleTcpEntryPoint = (ep: string) => {
    setFTcpEntryPoints(prev => prev.includes(ep) ? prev.filter(x => x !== ep) : [...prev, ep]);
  };

  const toggleMw = (mw: string) => {
    const base = mw.split('@')[0];
    setFMws(prev => {
      const has = prev.some(m => m.split('@')[0] === base);
      return has ? prev.filter(m => m.split('@')[0] !== base) : [...prev, mw];
    });
  };

  useEffect(() => {
    if (resolvers.length > 0 && !fCertResolver) setFCertResolver(resolvers[0]);
  }, [settings]);

  useEffect(() => {
    if (!entrypointData || fEntryPoints.length > 0) return;
    const names = entrypointData.map(e => e.name);
    const def = names.find(n => n === 'websecure') ?? names.find(n => n === 'https');
    if (def) setFEntryPoints([def]);
  }, [entrypointData]);

  const handleSave = () => {
    if (!fName.trim()) { setSaveErr('Name is required'); return; }
    if (!fIp.trim()) { setSaveErr('Target IP is required'); return; }
    setSaving(true);
    setSaveErr('');
    const data: RouteFormData = {
      serviceName: fName.trim(),
      protocol:    fProto,
      targetIp:    fIp.trim(),
      targetPort:  fPort.trim(),
      configFile:  fConfigFile,
    };
    if (fProto === 'http') {
      data.subdomain         = fSubdomain.trim();
      data.domains           = fDomains;
      data.entryPoints       = fEntryPoints.join(', ');
      data.middlewares       = fMws.join(', ');
      data.scheme            = fScheme;
      data.passHostHeader    = fPassHost;
      data.insecureSkipVerify = fInsecure;
      data.certResolver      = fCertResolver;
    } else if (fProto === 'tcp') {
      data.tcpRule        = fTcpRule.trim();
      data.tcpEntryPoints = fTcpEntryPoints.join(', ');
      data.certResolver   = fCertResolver;
    } else if (fProto === 'udp') {
      data.udpEntryPoint = fUdpEntryPoints.length > 0 ? fUdpEntryPoints[0] : fUdpEntryPoint.trim();
    }
    saveRoute.mutate(
      { data, isEdit: false, originalId: '' },
      {
        onSuccess: (res) => {
          setSaving(false);
          if (res.ok) router.back();
          else setSaveErr(res.message ?? 'Save failed');
        },
        onError: (e) => { setSaving(false); setSaveErr(e.message); },
      },
    );
  };

  return (
    <View style={[styles.screen, { backgroundColor: c.bg, paddingTop: insets.top }]}>

      <View style={[styles.headerBar, { borderBottomColor: c.border, backgroundColor: c.card }]}>
        <TouchableOpacity onPress={() => router.back()} hitSlop={12} style={styles.headerBtn}>
          <MaterialCommunityIcons name="chevron-down" size={26} color={c.text} />
        </TouchableOpacity>
        <Text style={[styles.headerTitle, { color: c.text }]}>New Route</Text>
        <View style={styles.headerActions}>
          <Button
            mode="contained"
            onPress={handleSave}
            loading={saving}
            disabled={saving}
            compact
          >
            Create
          </Button>
        </View>
      </View>

      <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
        <ScrollView
          style={{ flex: 1 }}
          contentContainerStyle={[styles.scrollContent, { paddingBottom: insets.bottom + 24 }]}
          keyboardShouldPersistTaps="handled"
        >
          <TextInput
            label="Name"
            value={fName}
            onChangeText={setFName}
            placeholder="my-service"
            autoCapitalize="none"
            autoCorrect={false}
            mode="outlined"
            style={{ backgroundColor: c.bg }}
          />

          <Text style={[styles.fieldLabel, { color: c.muted }]}>Protocol</Text>
          <SegmentedButtons
            value={fProto}
            onValueChange={setFProto}
            buttons={PROTOCOLS.map(p => ({ value: p, label: p.toUpperCase() }))}
          />

          {fProto === 'http' && (
            <>
              <TextInput
                label="Subdomain"
                value={fSubdomain}
                onChangeText={setFSubdomain}
                placeholder="app"
                autoCapitalize="none"
                autoCorrect={false}
                keyboardType="url"
                mode="outlined"
                style={{ backgroundColor: c.bg }}
              />

              {domains.length > 0 && (
                <>
                  <Text style={[styles.fieldLabel, { color: c.muted }]}>Domain{domains.length > 1 ? 's' : ''}</Text>
                  <View style={styles.chipRow}>
                    {domains.map(d => (
                      <TouchableOpacity
                        key={d}
                        onPress={() => domains.length === 1 ? null : toggleDomain(d)}
                        style={[
                          styles.chip,
                          {
                            backgroundColor: (fDomains.includes(d) || domains.length === 1) ? c.secondaryContainer : 'transparent',
                            borderColor:     (fDomains.includes(d) || domains.length === 1) ? c.blue : c.border,
                          },
                        ]}
                        activeOpacity={domains.length === 1 ? 1 : 0.7}
                      >
                        <Text style={{ color: (fDomains.includes(d) || domains.length === 1) ? c.onSecondaryContainer : c.text, fontSize: font.sm, fontWeight: '500' }}>
                          {d}
                        </Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                </>
              )}
            </>
          )}

          {fProto === 'tcp' && (
            <TextInput
              label="SNI Rule"
              value={fTcpRule}
              onChangeText={setFTcpRule}
              placeholder="HostSNI(`tcp.example.com`) or HostSNI(`*`)"
              autoCapitalize="none"
              autoCorrect={false}
              mode="outlined"
              style={{ backgroundColor: c.bg }}
            />
          )}

          <TextInput
            label="Target IP / Host"
            value={fIp}
            onChangeText={setFIp}
            placeholder="192.168.1.10"
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="url"
            mode="outlined"
            style={{ backgroundColor: c.bg }}
          />
          <TextInput
            label="Target Port"
            value={fPort}
            onChangeText={setFPort}
            placeholder="8080"
            autoCapitalize="none"
            autoCorrect={false}
            keyboardType="numeric"
            mode="outlined"
            style={{ backgroundColor: c.bg }}
          />

          {fProto === 'http' && (
            <>
              {entrypointData && entrypointData.length > 0 ? (
                <>
                  <Text style={[styles.fieldLabel, { color: c.muted }]}>Entry Points</Text>
                  <View style={styles.chipRow}>
                    {entrypointData.map(ep => (
                      <TouchableOpacity
                        key={ep.name}
                        onPress={() => toggleEntryPoint(ep.name)}
                        style={[
                          styles.chip,
                          {
                            backgroundColor: fEntryPoints.includes(ep.name) ? c.secondaryContainer : 'transparent',
                            borderColor:     fEntryPoints.includes(ep.name) ? c.blue : c.border,
                          },
                        ]}
                        activeOpacity={0.7}
                      >
                        <Text style={{ color: fEntryPoints.includes(ep.name) ? c.onSecondaryContainer : c.text, fontSize: font.sm, fontWeight: '500' }}>
                          {ep.name}
                        </Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                </>
              ) : (
                <TextInput
                  label="Entry Points (comma-separated)"
                  value={fEntryPoints.join(', ')}
                  onChangeText={v => setFEntryPoints(v.split(',').map(s => s.trim()).filter(Boolean))}
                  placeholder="https"
                  autoCapitalize="none"
                  autoCorrect={false}
                  mode="outlined"
                  style={{ backgroundColor: c.bg }}
                />
              )}
              {middlewareData && middlewareData.http.length > 0 ? (
                <>
                  <Text style={[styles.fieldLabel, { color: c.muted }]}>Middlewares</Text>
                  <View style={styles.chipRow}>
                    {middlewareData.http.map(mw => {
                      const label = mw.name.split('@')[0];
                      const on = fMws.some(m => m.split('@')[0] === label);
                      return (
                        <TouchableOpacity
                          key={mw.name}
                          onPress={() => toggleMw(mw.name)}
                          style={[
                            styles.chip,
                            {
                              backgroundColor: on ? 'rgba(163,113,247,0.15)' : 'transparent',
                              borderColor:     on ? '#a371f7' : c.border,
                            },
                          ]}
                          activeOpacity={0.7}
                        >
                          <Text style={{ color: on ? '#a371f7' : c.text, fontSize: font.sm, fontWeight: '500' }}>
                            {label}
                          </Text>
                        </TouchableOpacity>
                      );
                    })}
                  </View>
                </>
              ) : (
                <TextInput
                  label="Middlewares (comma-separated)"
                  value={fMws.join(', ')}
                  onChangeText={v => setFMws(v.split(',').map(s => s.trim()).filter(Boolean))}
                  placeholder="auth@file, compress"
                  autoCapitalize="none"
                  autoCorrect={false}
                  mode="outlined"
                  style={{ backgroundColor: c.bg }}
                />
              )}

              <Text style={[styles.fieldLabel, { color: c.muted }]}>Backend Scheme</Text>
              <SegmentedButtons
                value={fScheme}
                onValueChange={setFScheme}
                buttons={[
                  { value: 'http',  label: 'HTTP'  },
                  { value: 'https', label: 'HTTPS' },
                ]}
              />

              <View style={styles.switchRow}>
                <View style={{ flex: 1, gap: 2 }}>
                  <Text style={[styles.fieldLabel, { color: c.text }]}>Pass Host Header</Text>
                  <Text style={[styles.fieldHint, { color: c.muted }]}>Forward original Host to backend</Text>
                </View>
                <Switch
                  value={fPassHost}
                  onValueChange={setFPassHost}
                />
              </View>

              <View style={styles.switchRow}>
                <View style={{ flex: 1, gap: 2 }}>
                  <Text style={[styles.fieldLabel, { color: c.text }]}>Skip TLS Verification</Text>
                  <Text style={[styles.fieldHint, { color: c.muted }]}>For self-signed backend certs (insecureSkipVerify)</Text>
                </View>
                <Switch
                  value={fInsecure}
                  onValueChange={setFInsecure}
                />
              </View>
            </>
          )}

          {fProto === 'tcp' && (
            entrypointData && entrypointData.length > 0 ? (
              <>
                <Text style={[styles.fieldLabel, { color: c.muted }]}>Entry Points</Text>
                <View style={styles.chipRow}>
                  {entrypointData.map(ep => (
                    <TouchableOpacity
                      key={ep.name}
                      onPress={() => toggleTcpEntryPoint(ep.name)}
                      style={[
                        styles.chip,
                        {
                          backgroundColor: fTcpEntryPoints.includes(ep.name) ? c.secondaryContainer : 'transparent',
                          borderColor:     fTcpEntryPoints.includes(ep.name) ? c.blue : c.border,
                        },
                      ]}
                      activeOpacity={0.7}
                    >
                      <Text style={{ color: fTcpEntryPoints.includes(ep.name) ? c.onSecondaryContainer : c.text, fontSize: font.sm, fontWeight: '500' }}>
                        {ep.name}
                      </Text>
                    </TouchableOpacity>
                  ))}
                </View>
              </>
            ) : (
              <TextInput
                label="Entry Points (comma-separated)"
                value={fTcpEntryPoints.join(', ')}
                onChangeText={v => setFTcpEntryPoints(v.split(',').map(s => s.trim()).filter(Boolean))}
                placeholder="tcp, postgres"
                autoCapitalize="none"
                autoCorrect={false}
                mode="outlined"
                style={{ backgroundColor: c.bg }}
              />
            )
          )}

          {fProto === 'udp' && (
            <>
              {entrypointData && entrypointData.length > 0 ? (
                <>
                  <Text style={[styles.fieldLabel, { color: c.muted }]}>Entry Point</Text>
                  <View style={styles.chipRow}>
                    {entrypointData.map(ep => (
                      <TouchableOpacity
                        key={ep.name}
                        onPress={() => setFUdpEntryPoints(prev => prev.includes(ep.name) ? prev.filter(x => x !== ep.name) : [...prev, ep.name])}
                        style={[
                          styles.chip,
                          {
                            backgroundColor: fUdpEntryPoints.includes(ep.name) ? c.secondaryContainer : 'transparent',
                            borderColor:     fUdpEntryPoints.includes(ep.name) ? c.blue : c.border,
                          },
                        ]}
                        activeOpacity={0.7}
                      >
                        <Text style={{ color: fUdpEntryPoints.includes(ep.name) ? c.onSecondaryContainer : c.text, fontSize: font.sm, fontWeight: '500' }}>
                          {ep.name}
                        </Text>
                      </TouchableOpacity>
                    ))}
                  </View>
                </>
              ) : (
                <TextInput
                  label="Entry Point"
                  value={fUdpEntryPoint}
                  onChangeText={setFUdpEntryPoint}
                  placeholder="qbittorrent-udp"
                  autoCapitalize="none"
                  autoCorrect={false}
                  mode="outlined"
                  style={{ backgroundColor: c.bg }}
                />
              )}
              <View style={[styles.infoBox, { backgroundColor: c.blue + '14', borderColor: c.blue + '44' }]}>
                <MaterialCommunityIcons name="information-outline" size={14} color={c.blue} />
                <Text style={[styles.infoText, { color: c.blue }]}>UDP routers don't support rules. Traffic is routed by entry point only.</Text>
              </View>
            </>
          )}

          {resolvers.length > 0 && (fProto === 'http' || fProto === 'tcp') && (
            <>
              <Text style={[styles.fieldLabel, { color: c.muted }]}>Cert Resolver</Text>
              <View style={styles.chipRow}>
                {resolvers.map(r => (
                  <TouchableOpacity
                    key={r}
                    onPress={() => setFCertResolver(r)}
                    style={[
                      styles.chip,
                      {
                        backgroundColor: fCertResolver === r ? c.secondaryContainer : 'transparent',
                        borderColor:     fCertResolver === r ? c.blue : c.border,
                      },
                    ]}
                    activeOpacity={0.7}
                  >
                    <Text style={{ color: fCertResolver === r ? c.onSecondaryContainer : c.text, fontSize: font.sm, fontWeight: '500' }}>
                      {r}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </>
          )}

          {showConfigPicker && (
            <View style={styles.formGroup}>
              <Text style={[styles.fieldLabel, { color: c.muted }]}>Config File</Text>
              <ConfigFilePicker
                files={configFiles}
                configDirSet={configDirSet}
                value={fConfigFile}
                onChange={setFConfigFile}
                allowNew
                c={c}
              />
            </View>
          )}

          {!!saveErr && <Text style={[styles.errTxt, { color: c.red }]}>{saveErr}</Text>}
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen:  { flex: 1 },
  headerBar: {
    flexDirection: 'row', alignItems: 'center',
    paddingHorizontal: spacing.md, paddingVertical: spacing.sm,
    borderBottomWidth: 1, gap: spacing.sm,
  },
  headerBtn:     { width: 36, alignItems: 'flex-start' },
  headerTitle:   { flex: 1, fontSize: font.lg, fontWeight: '700', textAlign: 'center' },
  headerActions: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  scrollContent: { padding: spacing.md, gap: spacing.md },
  formGroup:     { gap: 4 },
  fieldLabel:    { fontSize: font.sm, fontWeight: '500', marginBottom: 4 },
  fieldHint:     { fontSize: font.xs, opacity: 0.8 },
  switchRow:     { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: spacing.sm, paddingVertical: spacing.xs },
  chipRow:       { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  chip:          { borderWidth: 1, borderRadius: radius.sm, paddingHorizontal: spacing.md, paddingVertical: 7 },
  infoBox:       { flexDirection: 'row', alignItems: 'flex-start', gap: 8, padding: 10, borderRadius: radius.sm, borderWidth: 1 },
  infoText:      { fontSize: font.xs, flex: 1 },
  errTxt:        { fontSize: font.sm },
});
