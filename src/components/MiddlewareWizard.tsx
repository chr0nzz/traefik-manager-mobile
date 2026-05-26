import React, { useEffect, useState } from 'react';
import { ActivityIndicator, StyleSheet, TouchableOpacity, View } from 'react-native';
import { Switch, Text, TextInput } from 'react-native-paper';
import { generateHtpasswd } from '../api/middlewares';
import { darkColors, font, radius, spacing } from '../theme';

type Colors = typeof darkColors;

export const WIZARD_TEMPLATES = new Set([
  'https-redirect', 'basic-auth', 'digest-auth', 'security-headers',
  'rate-limit', 'forward-auth', 'forward-auth-authentik', 'forward-auth-authelia',
  'forward-auth-gatekeeper', 'ip-allowlist', 'ip-allowlist-private', 'cors-headers',
  'redirect-regex', 'strip-prefix', 'add-prefix', 'replace-path',
  'compress', 'retry', 'circuit-breaker', 'buffering', 'in-flight-req',
]);

function Field({ label, c, children }: { label: string; c: Colors; children: React.ReactNode }) {
  return (
    <View style={styles.field}>
      <Text style={[styles.fieldLabel, { color: c.muted }]}>{label}</Text>
      {children}
    </View>
  );
}

function WInput({
  label, value, onChange, c, multiline, keyboardType, placeholder,
}: {
  label: string; value: string; onChange: (v: string) => void; c: Colors;
  multiline?: boolean; keyboardType?: 'default' | 'numeric'; placeholder?: string;
}) {
  return (
    <TextInput
      label={label}
      value={value}
      onChangeText={onChange}
      mode="outlined"
      multiline={multiline}
      numberOfLines={multiline ? 4 : 1}
      autoCapitalize="none"
      autoCorrect={false}
      keyboardType={keyboardType ?? 'default'}
      placeholder={placeholder}
      style={[
        { backgroundColor: c.bg },
        multiline ? { fontFamily: 'monospace', minHeight: 100 } : null,
      ]}
    />
  );
}

function WSw({ label, value, onChange, c }: {
  label: string; value: boolean; onChange: (v: boolean) => void; c: Colors;
}) {
  return (
    <View style={styles.switchRow}>
      <Text style={[styles.switchLabel, { color: c.text }]}>{label}</Text>
      <Switch value={value} onValueChange={onChange} />
    </View>
  );
}

function Chips({ options, selected, onToggle, c }: {
  options: string[]; selected: string[]; onToggle: (v: string) => void; c: Colors;
}) {
  return (
    <View style={styles.chips}>
      {options.map(opt => {
        const on = selected.includes(opt);
        return (
          <TouchableOpacity
            key={opt}
            onPress={() => onToggle(opt)}
            style={[styles.chip, {
              borderColor: on ? c.blue + '88' : c.border,
              backgroundColor: on ? c.blue + '18' : 'transparent',
            }]}
          >
            <Text style={[styles.chipText, { color: on ? c.blue : c.muted }]}>{opt}</Text>
          </TouchableOpacity>
        );
      })}
    </View>
  );
}

function HttpsRedirectWizard({ onYamlChange, c }: { onYamlChange: (y: string) => void; c: Colors }) {
  const [scheme, setScheme]       = useState('https');
  const [permanent, setPermanent] = useState(true);

  useEffect(() => {
    onYamlChange(`redirectScheme:\n  scheme: ${scheme}\n  permanent: ${permanent}`);
  }, [scheme, permanent]);

  return (
    <View style={styles.wizard}>
      <Field label="Scheme" c={c}>
        <Chips options={['https', 'http']} selected={[scheme]} onToggle={setScheme} c={c} />
      </Field>
      <WSw label="Permanent (301)" value={permanent} onChange={setPermanent} c={c} />
    </View>
  );
}

function BasicAuthWizard({ onYamlChange, c }: { onYamlChange: (y: string) => void; c: Colors }) {
  const [users, setUsers]         = useState<string[]>([]);
  const [inputUser, setInputUser] = useState('');
  const [inputPass, setInputPass] = useState('');
  const [realm, setRealm]         = useState('');
  const [adding, setAdding]       = useState(false);

  const addUser = async () => {
    if (!inputUser.trim() || !inputPass.trim()) return;
    setAdding(true);
    try {
      const { hash } = await generateHtpasswd(inputUser.trim(), inputPass.trim());
      setUsers(prev => [...prev, hash]);
      setInputUser('');
      setInputPass('');
    } finally {
      setAdding(false);
    }
  };

  useEffect(() => {
    const userLines = users.length > 0
      ? '\n  users:\n' + users.map(u => `    - "${u}"`).join('\n')
      : '';
    const realmLine = realm.trim() ? `\n  realm: "${realm.trim()}"` : '';
    onYamlChange(`basicAuth:${userLines}${realmLine}`);
  }, [users, realm]);

  return (
    <View style={styles.wizard}>
      <Field label="Add User" c={c}>
        <WInput label="Username" value={inputUser} onChange={setInputUser} c={c} />
        <View style={{ height: spacing.sm }} />
        <WInput label="Password" value={inputPass} onChange={setInputPass} c={c} />
        <TouchableOpacity
          style={[styles.addBtn, { backgroundColor: c.blue + '18', borderColor: c.blue + '44' }]}
          onPress={addUser}
          disabled={adding}
        >
          {adding
            ? <ActivityIndicator size="small" color={c.blue} />
            : <Text style={[styles.addBtnText, { color: c.blue }]}>Add User</Text>}
        </TouchableOpacity>
      </Field>
      {users.length > 0 && (
        <Field label="Users" c={c}>
          <View style={styles.chips}>
            {users.map((u, i) => (
              <View key={i} style={[styles.chip, { borderColor: c.border }]}>
                <Text style={[styles.chipText, { color: c.text }]} numberOfLines={1}>
                  {u.split(':')[0]}
                </Text>
                <TouchableOpacity
                  onPress={() => setUsers(prev => prev.filter((_, j) => j !== i))}
                  hitSlop={8}
                >
                  <Text style={{ color: c.red, marginLeft: 4 }}>✕</Text>
                </TouchableOpacity>
              </View>
            ))}
          </View>
        </Field>
      )}
      <WInput label="Realm (optional)" value={realm} onChange={setRealm} c={c} />
    </View>
  );
}

function DigestAuthWizard({ onYamlChange, c }: { onYamlChange: (y: string) => void; c: Colors }) {
  const [users, setUsers] = useState('');

  useEffect(() => {
    const lines = users.split('\n').map(s => s.trim()).filter(Boolean);
    const yaml = lines.length > 0
      ? `digestAuth:\n  users:\n${lines.map(l => `    - "${l}"`).join('\n')}`
      : 'digestAuth:\n  users: []';
    onYamlChange(yaml);
  }, [users]);

  return (
    <View style={styles.wizard}>
      <WInput
        label="Users (user:realm:hash, one per line)"
        value={users}
        onChange={setUsers}
        c={c}
        multiline
        placeholder={'user:realm:hash\nuser2:realm:hash2'}
      />
    </View>
  );
}

function SecurityHeadersWizard({ onYamlChange, c }: { onYamlChange: (y: string) => void; c: Colors }) {
  const [ssl, setSsl]             = useState(true);
  const [hsts, setHsts]           = useState(true);
  const [stsAge, setStsAge]       = useState('315360000');
  const [subdomain, setSubdomain] = useState(true);
  const [preload, setPreload]     = useState(true);
  const [nosniff, setNosniff]     = useState(true);
  const [xss, setXss]             = useState(true);
  const [frameDeny, setFrameDeny] = useState(true);
  const [referrer, setReferrer]   = useState(true);

  useEffect(() => {
    const lines = ['headers:'];
    if (ssl)      lines.push('  forceSTSHeader: true');
    if (hsts) {
      lines.push(`  stsSeconds: ${stsAge || '31536000'}`);
      if (subdomain) lines.push('  stsIncludeSubdomains: true');
      if (preload)   lines.push('  stsPreload: true');
    }
    if (nosniff)   lines.push('  contentTypeNosniff: true');
    if (xss)       lines.push('  browserXssFilter: true');
    if (frameDeny) lines.push('  frameDeny: true');
    if (referrer)  lines.push('  referrerPolicy: "strict-origin-when-cross-origin"');
    onYamlChange(lines.join('\n'));
  }, [ssl, hsts, stsAge, subdomain, preload, nosniff, xss, frameDeny, referrer]);

  return (
    <View style={styles.wizard}>
      <WSw label="Force SSL Header" value={ssl} onChange={setSsl} c={c} />
      <WSw label="HSTS" value={hsts} onChange={setHsts} c={c} />
      {hsts && (
        <>
          <WInput label="HSTS Max Age (seconds)" value={stsAge} onChange={setStsAge} c={c} keyboardType="numeric" />
          <WSw label="Include Subdomains" value={subdomain} onChange={setSubdomain} c={c} />
          <WSw label="Preload" value={preload} onChange={setPreload} c={c} />
        </>
      )}
      <WSw label="Content Type Nosniff" value={nosniff} onChange={setNosniff} c={c} />
      <WSw label="XSS Filter" value={xss} onChange={setXss} c={c} />
      <WSw label="Frame Deny" value={frameDeny} onChange={setFrameDeny} c={c} />
      <WSw label="Referrer Policy" value={referrer} onChange={setReferrer} c={c} />
    </View>
  );
}

function RateLimitWizard({ onYamlChange, c }: { onYamlChange: (y: string) => void; c: Colors }) {
  const [average, setAverage] = useState('100');
  const [burst, setBurst]     = useState('50');
  const [period, setPeriod]   = useState('1s');

  useEffect(() => {
    onYamlChange(
      `rateLimit:\n  average: ${average || '100'}\n  burst: ${burst || '50'}\n  period: ${period || '1s'}`,
    );
  }, [average, burst, period]);

  return (
    <View style={styles.wizard}>
      <WInput label="Average (req/period)" value={average} onChange={setAverage} c={c} keyboardType="numeric" />
      <WInput label="Burst" value={burst} onChange={setBurst} c={c} keyboardType="numeric" />
      <WInput label="Period (e.g. 1s, 1m)" value={period} onChange={setPeriod} c={c} />
    </View>
  );
}

function ForwardAuthBase({
  defaultAddress, defaultHeaders, defaultTrust, onYamlChange, c,
}: {
  defaultAddress: string; defaultHeaders: string; defaultTrust: boolean;
  onYamlChange: (y: string) => void; c: Colors;
}) {
  const [address, setAddress] = useState(defaultAddress);
  const [trust, setTrust]     = useState(defaultTrust);
  const [headers, setHeaders] = useState(defaultHeaders);

  useEffect(() => {
    const headerLines = headers.split('\n').map(s => s.trim()).filter(Boolean);
    const lines = ['forwardAuth:', `  address: "${address}"`, `  trustForwardHeader: ${trust}`];
    if (headerLines.length > 0) {
      lines.push('  authResponseHeaders:');
      headerLines.forEach(h => lines.push(`    - ${h}`));
    }
    onYamlChange(lines.join('\n'));
  }, [address, trust, headers]);

  return (
    <View style={styles.wizard}>
      <WInput label="Auth Service URL" value={address} onChange={setAddress} c={c} placeholder="http://auth:9000/verify" />
      <WSw label="Trust Forward Header" value={trust} onChange={setTrust} c={c} />
      <WInput label="Response Headers (one per line)" value={headers} onChange={setHeaders} c={c} multiline placeholder={'X-Auth-User\nX-Auth-Role'} />
    </View>
  );
}

function ForwardAuthWizard({ onYamlChange, c }: { onYamlChange: (y: string) => void; c: Colors }) {
  return (
    <ForwardAuthBase
      defaultAddress=""
      defaultHeaders={'X-Auth-User\nX-Auth-Role'}
      defaultTrust={true}
      onYamlChange={onYamlChange}
      c={c}
    />
  );
}

function AuthentikWizard({ onYamlChange, c }: { onYamlChange: (y: string) => void; c: Colors }) {
  return (
    <ForwardAuthBase
      defaultAddress="http://authentik-server:9000/outpost.goauthentik.io/auth/traefik"
      defaultHeaders={'X-authentik-username\nX-authentik-groups\nX-authentik-email\nX-authentik-name'}
      defaultTrust={true}
      onYamlChange={onYamlChange}
      c={c}
    />
  );
}

function AutheliaWizard({ onYamlChange, c }: { onYamlChange: (y: string) => void; c: Colors }) {
  return (
    <ForwardAuthBase
      defaultAddress="http://authelia:9091/api/authz/forward-auth"
      defaultHeaders={'Remote-User\nRemote-Name\nRemote-Groups\nRemote-Email'}
      defaultTrust={true}
      onYamlChange={onYamlChange}
      c={c}
    />
  );
}

function GatekeeperWizard({ onYamlChange, c }: { onYamlChange: (y: string) => void; c: Colors }) {
  const [baseUrl, setBaseUrl] = useState('');
  const [policy, setPolicy]   = useState('');
  const [trust, setTrust]     = useState(false);
  const [headers, setHeaders] = useState('X-Auth-User\nX-Auth-Email');

  useEffect(() => {
    const policyStr = policy.trim() ? `?policy=${policy.trim()}` : '';
    const address = baseUrl.trim() ? `${baseUrl.trim()}/auth/verify${policyStr}` : '';
    const headerLines = headers.split('\n').map(s => s.trim()).filter(Boolean);
    const lines = ['forwardAuth:'];
    if (address) lines.push(`  address: "${address}"`);
    lines.push(`  trustForwardHeader: ${trust}`);
    if (headerLines.length > 0) {
      lines.push('  authResponseHeaders:');
      headerLines.forEach(h => lines.push(`    - ${h}`));
    }
    onYamlChange(lines.join('\n'));
  }, [baseUrl, policy, trust, headers]);

  return (
    <View style={styles.wizard}>
      <WInput label="Gatekeeper Base URL" value={baseUrl} onChange={setBaseUrl} c={c} placeholder="https://auth.example.com" />
      <WInput label="Policy (optional)" value={policy} onChange={setPolicy} c={c} placeholder="admin" />
      <WSw label="Trust Forward Header" value={trust} onChange={setTrust} c={c} />
      <WInput label="Response Headers (one per line)" value={headers} onChange={setHeaders} c={c} multiline />
    </View>
  );
}

function IpAllowlistBase({
  defaultCidrs, onYamlChange, c,
}: {
  defaultCidrs: string; onYamlChange: (y: string) => void; c: Colors;
}) {
  const [cidrs, setCidrs] = useState(defaultCidrs);

  useEffect(() => {
    const ranges = cidrs.split('\n').map(s => s.trim()).filter(Boolean);
    const yaml = ranges.length > 0
      ? `ipAllowList:\n  sourceRange:\n${ranges.map(r => `    - "${r}"`).join('\n')}`
      : 'ipAllowList:\n  sourceRange: []';
    onYamlChange(yaml);
  }, [cidrs]);

  return (
    <View style={styles.wizard}>
      <WInput
        label="CIDR Ranges (one per line)"
        value={cidrs}
        onChange={setCidrs}
        c={c}
        multiline
        placeholder={'10.0.0.0/8\n192.168.0.0/16'}
      />
    </View>
  );
}

function IpAllowlistWizard({ onYamlChange, c }: { onYamlChange: (y: string) => void; c: Colors }) {
  return <IpAllowlistBase defaultCidrs="" onYamlChange={onYamlChange} c={c} />;
}

function IpAllowlistPrivateWizard({ onYamlChange, c }: { onYamlChange: (y: string) => void; c: Colors }) {
  return (
    <IpAllowlistBase
      defaultCidrs={'10.0.0.0/8\n172.16.0.0/12\n192.168.0.0/16\n127.0.0.1/32'}
      onYamlChange={onYamlChange}
      c={c}
    />
  );
}

const ALL_METHODS = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS', 'HEAD'];

function CorsHeadersWizard({ onYamlChange, c }: { onYamlChange: (y: string) => void; c: Colors }) {
  const [methods, setMethods] = useState(ALL_METHODS.filter(m => m !== 'HEAD'));
  const [origins, setOrigins] = useState('*');
  const [headers, setHeaders] = useState('*');
  const [maxAge, setMaxAge]   = useState('100');
  const [vary, setVary]       = useState(true);

  const toggleMethod = (m: string) =>
    setMethods(prev => prev.includes(m) ? prev.filter(x => x !== m) : [...prev, m]);

  useEffect(() => {
    const originList = origins.split('\n').map(s => s.trim()).filter(Boolean);
    const headerList = headers.split('\n').map(s => s.trim()).filter(Boolean);
    const lines = ['headers:'];
    if (methods.length > 0) {
      lines.push('  accessControlAllowMethods:');
      methods.forEach(m => lines.push(`    - ${m}`));
    }
    if (originList.length > 0) {
      lines.push('  accessControlAllowOriginList:');
      originList.forEach(o => lines.push(`    - "${o}"`));
    }
    if (headerList.length > 0) {
      lines.push('  accessControlAllowHeaders:');
      headerList.forEach(h => lines.push(`    - "${h}"`));
    }
    lines.push(`  accessControlMaxAge: ${maxAge || '100'}`);
    lines.push(`  addVaryHeader: ${vary}`);
    onYamlChange(lines.join('\n'));
  }, [methods, origins, headers, maxAge, vary]);

  return (
    <View style={styles.wizard}>
      <Field label="Allowed Methods" c={c}>
        <Chips options={ALL_METHODS} selected={methods} onToggle={toggleMethod} c={c} />
      </Field>
      <WInput label="Allowed Origins (one per line)" value={origins} onChange={setOrigins} c={c} multiline placeholder="*" />
      <WInput label="Allowed Headers (one per line)" value={headers} onChange={setHeaders} c={c} multiline placeholder="*" />
      <WInput label="Max Age (seconds)" value={maxAge} onChange={setMaxAge} c={c} keyboardType="numeric" />
      <WSw label="Add Vary Header" value={vary} onChange={setVary} c={c} />
    </View>
  );
}

function RedirectRegexWizard({ onYamlChange, c }: { onYamlChange: (y: string) => void; c: Colors }) {
  const [regex, setRegex]             = useState('');
  const [replacement, setReplacement] = useState('');
  const [permanent, setPermanent]     = useState(true);

  useEffect(() => {
    onYamlChange(
      `redirectRegex:\n  regex: "${regex}"\n  replacement: "${replacement}"\n  permanent: ${permanent}`,
    );
  }, [regex, replacement, permanent]);

  return (
    <View style={styles.wizard}>
      <WInput label="Regex" value={regex} onChange={setRegex} c={c} placeholder="^http://(.*)" />
      <WInput label="Replacement" value={replacement} onChange={setReplacement} c={c} placeholder={'https://${1}'} />
      <WSw label="Permanent (301)" value={permanent} onChange={setPermanent} c={c} />
    </View>
  );
}

function StripPrefixWizard({ onYamlChange, c }: { onYamlChange: (y: string) => void; c: Colors }) {
  const [prefixes, setPrefixes] = useState('/api\n/v1');

  useEffect(() => {
    const items = prefixes.split('\n').map(s => s.trim()).filter(Boolean);
    const yaml = items.length > 0
      ? `stripPrefix:\n  prefixes:\n${items.map(p => `    - "${p}"`).join('\n')}`
      : 'stripPrefix:\n  prefixes: []';
    onYamlChange(yaml);
  }, [prefixes]);

  return (
    <View style={styles.wizard}>
      <WInput label="Prefixes (one per line)" value={prefixes} onChange={setPrefixes} c={c} multiline placeholder={'/api\n/v1'} />
    </View>
  );
}

function AddPrefixWizard({ onYamlChange, c }: { onYamlChange: (y: string) => void; c: Colors }) {
  const [prefix, setPrefix] = useState('/api');

  useEffect(() => {
    onYamlChange(`addPrefix:\n  prefix: "${prefix}"`);
  }, [prefix]);

  return (
    <View style={styles.wizard}>
      <WInput label="Prefix" value={prefix} onChange={setPrefix} c={c} placeholder="/api" />
    </View>
  );
}

function ReplacePathWizard({ onYamlChange, c }: { onYamlChange: (y: string) => void; c: Colors }) {
  const [path, setPath] = useState('/foo');

  useEffect(() => {
    onYamlChange(`replacePath:\n  path: "${path}"`);
  }, [path]);

  return (
    <View style={styles.wizard}>
      <WInput label="Path" value={path} onChange={setPath} c={c} placeholder="/new-path" />
    </View>
  );
}

function CompressWizard({ onYamlChange, c }: { onYamlChange: (y: string) => void; c: Colors }) {
  const [minBytes, setMinBytes] = useState('1200');

  useEffect(() => {
    onYamlChange(`compress:\n  minResponseBodyBytes: ${minBytes || '1200'}`);
  }, [minBytes]);

  return (
    <View style={styles.wizard}>
      <WInput label="Min Response Body Bytes" value={minBytes} onChange={setMinBytes} c={c} keyboardType="numeric" />
    </View>
  );
}

function RetryWizard({ onYamlChange, c }: { onYamlChange: (y: string) => void; c: Colors }) {
  const [attempts, setAttempts]               = useState('4');
  const [initialInterval, setInitialInterval] = useState('100ms');

  useEffect(() => {
    onYamlChange(
      `retry:\n  attempts: ${attempts || '4'}\n  initialInterval: "${initialInterval || '100ms'}"`,
    );
  }, [attempts, initialInterval]);

  return (
    <View style={styles.wizard}>
      <WInput label="Attempts" value={attempts} onChange={setAttempts} c={c} keyboardType="numeric" />
      <WInput label="Initial Interval (e.g. 100ms, 1s)" value={initialInterval} onChange={setInitialInterval} c={c} />
    </View>
  );
}

function CircuitBreakerWizard({ onYamlChange, c }: { onYamlChange: (y: string) => void; c: Colors }) {
  const [expression, setExpression] = useState('NetworkErrorRatio() > 0.5');

  useEffect(() => {
    onYamlChange(`circuitBreaker:\n  expression: "${expression}"`);
  }, [expression]);

  return (
    <View style={styles.wizard}>
      <WInput label="Expression" value={expression} onChange={setExpression} c={c} placeholder="NetworkErrorRatio() > 0.5" />
    </View>
  );
}

function BufferingWizard({ onYamlChange, c }: { onYamlChange: (y: string) => void; c: Colors }) {
  const [maxReq, setMaxReq]       = useState('10485760');
  const [maxRes, setMaxRes]       = useState('10485760');
  const [retryExpr, setRetryExpr] = useState('');

  useEffect(() => {
    const lines = [
      'buffering:',
      `  maxRequestBodyBytes: ${maxReq || '10485760'}`,
      `  maxResponseBodyBytes: ${maxRes || '10485760'}`,
    ];
    if (retryExpr.trim()) lines.push(`  retryExpression: "${retryExpr.trim()}"`);
    onYamlChange(lines.join('\n'));
  }, [maxReq, maxRes, retryExpr]);

  return (
    <View style={styles.wizard}>
      <WInput label="Max Request Body Bytes" value={maxReq} onChange={setMaxReq} c={c} keyboardType="numeric" />
      <WInput label="Max Response Body Bytes" value={maxRes} onChange={setMaxRes} c={c} keyboardType="numeric" />
      <WInput label="Retry Expression (optional)" value={retryExpr} onChange={setRetryExpr} c={c} placeholder="IsNetworkError() && Attempts() < 2" />
    </View>
  );
}

function InFlightReqWizard({ onYamlChange, c }: { onYamlChange: (y: string) => void; c: Colors }) {
  const [amount, setAmount] = useState('10');

  useEffect(() => {
    onYamlChange(`inFlightReq:\n  amount: ${amount || '10'}`);
  }, [amount]);

  return (
    <View style={styles.wizard}>
      <WInput label="Max Concurrent Requests" value={amount} onChange={setAmount} c={c} keyboardType="numeric" />
    </View>
  );
}

export function MiddlewareWizard({
  template, onYamlChange, c,
}: {
  template: string; onYamlChange: (yaml: string) => void; c: Colors;
}) {
  switch (template) {
    case 'https-redirect':          return <HttpsRedirectWizard onYamlChange={onYamlChange} c={c} />;
    case 'basic-auth':              return <BasicAuthWizard onYamlChange={onYamlChange} c={c} />;
    case 'digest-auth':             return <DigestAuthWizard onYamlChange={onYamlChange} c={c} />;
    case 'security-headers':        return <SecurityHeadersWizard onYamlChange={onYamlChange} c={c} />;
    case 'rate-limit':              return <RateLimitWizard onYamlChange={onYamlChange} c={c} />;
    case 'forward-auth':            return <ForwardAuthWizard onYamlChange={onYamlChange} c={c} />;
    case 'forward-auth-authentik':  return <AuthentikWizard onYamlChange={onYamlChange} c={c} />;
    case 'forward-auth-authelia':   return <AutheliaWizard onYamlChange={onYamlChange} c={c} />;
    case 'forward-auth-gatekeeper': return <GatekeeperWizard onYamlChange={onYamlChange} c={c} />;
    case 'ip-allowlist':            return <IpAllowlistWizard onYamlChange={onYamlChange} c={c} />;
    case 'ip-allowlist-private':    return <IpAllowlistPrivateWizard onYamlChange={onYamlChange} c={c} />;
    case 'cors-headers':            return <CorsHeadersWizard onYamlChange={onYamlChange} c={c} />;
    case 'redirect-regex':          return <RedirectRegexWizard onYamlChange={onYamlChange} c={c} />;
    case 'strip-prefix':            return <StripPrefixWizard onYamlChange={onYamlChange} c={c} />;
    case 'add-prefix':              return <AddPrefixWizard onYamlChange={onYamlChange} c={c} />;
    case 'replace-path':            return <ReplacePathWizard onYamlChange={onYamlChange} c={c} />;
    case 'compress':                return <CompressWizard onYamlChange={onYamlChange} c={c} />;
    case 'retry':                   return <RetryWizard onYamlChange={onYamlChange} c={c} />;
    case 'circuit-breaker':         return <CircuitBreakerWizard onYamlChange={onYamlChange} c={c} />;
    case 'buffering':               return <BufferingWizard onYamlChange={onYamlChange} c={c} />;
    case 'in-flight-req':           return <InFlightReqWizard onYamlChange={onYamlChange} c={c} />;
    default:                        return null;
  }
}

const styles = StyleSheet.create({
  wizard: { gap: spacing.md },
  field: { gap: spacing.xs },
  fieldLabel: {
    fontSize: font.xs,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  switchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: radius.sm,
  },
  switchLabel: { flex: 1, fontSize: font.md },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: radius.sm,
    borderWidth: 1,
  },
  chipText: { fontSize: font.xs, fontWeight: '600' },
  addBtn: {
    marginTop: spacing.sm,
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: radius.sm,
    borderWidth: 1,
    alignItems: 'center',
  },
  addBtnText: { fontSize: font.sm, fontWeight: '600' },
});
