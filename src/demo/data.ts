export const DEMO_ROUTES_DATA = {
  apps: [
    {
      id: 'nginx',
      name: 'nginx',
      protocol: 'HTTP',
      rule: 'Host(`demo.example.com`)',
      service_name: 'nginx',
      target: 'http://nginx:80',
      tls: true,
      enabled: true,
      middlewares: ['headers'],
      entryPoints: ['websecure'],
      certResolver: 'cloudflare',
      configFile: 'dynamic.yml',
      provider: 'file',
    },
    {
      id: 'api-gateway',
      name: 'api-gateway',
      protocol: 'HTTP',
      rule: 'Host(`api.example.com`) && PathPrefix(`/v1`)',
      service_name: 'api-gateway',
      target: 'http://api:3000',
      tls: true,
      enabled: true,
      middlewares: ['ratelimit'],
      entryPoints: ['websecure'],
      certResolver: 'cloudflare',
      configFile: 'dynamic.yml',
      provider: 'file',
    },
    {
      id: 'whoami',
      name: 'whoami',
      protocol: 'HTTP',
      rule: 'Host(`whoami.example.com`)',
      service_name: 'whoami',
      target: 'http://whoami:80',
      tls: true,
      enabled: true,
      middlewares: [],
      entryPoints: ['websecure'],
      certResolver: 'cloudflare',
      configFile: 'dynamic.yml',
      provider: 'file',
    },
    {
      id: 'db-proxy',
      name: 'db-proxy',
      protocol: 'TCP',
      rule: 'HostSNI(`db.example.com`)',
      service_name: 'db-proxy',
      target: 'db:5432',
      tls: true,
      enabled: true,
      middlewares: [],
      entryPoints: ['websecure'],
      configFile: 'dynamic.yml',
      provider: 'file',
    },
    {
      id: 'old-service',
      name: 'old-service',
      protocol: 'HTTP',
      rule: 'Host(`legacy.example.com`)',
      service_name: 'old-service',
      target: 'http://legacy:8080',
      tls: false,
      enabled: false,
      middlewares: [],
      entryPoints: ['web'],
      configFile: 'dynamic.yml',
      provider: 'file',
    },
  ],
  middlewares: [],
};

export const DEMO_MIDDLEWARES = [
  { name: 'headers@file',         type: 'headers',       status: 'enabled', provider: 'file', _proto: 'http' },
  { name: 'ratelimit@file',       type: 'rateLimit',     status: 'enabled', provider: 'file', _proto: 'http' },
  { name: 'basicauth@file',       type: 'basicAuth',     status: 'enabled', provider: 'file', _proto: 'http' },
  { name: 'redirect-https@file',  type: 'redirectScheme',status: 'enabled', provider: 'file', _proto: 'http' },
];

export const DEMO_SERVICES = [
  {
    name: 'nginx@file',
    type: 'loadbalancer',
    status: 'enabled',
    _proto: 'http',
    provider: 'file',
    loadBalancer: { servers: [{ url: 'http://nginx:80' }], passHostHeader: true },
    usedBy: ['nginx@file'],
  },
  {
    name: 'api-gateway@file',
    type: 'loadbalancer',
    status: 'enabled',
    _proto: 'http',
    provider: 'file',
    loadBalancer: { servers: [{ url: 'http://api:3000' }], passHostHeader: true },
    usedBy: ['api-gateway@file'],
  },
  {
    name: 'whoami@file',
    type: 'loadbalancer',
    status: 'enabled',
    _proto: 'http',
    provider: 'file',
    loadBalancer: { servers: [{ url: 'http://whoami:80' }], passHostHeader: true },
    usedBy: ['whoami@file'],
  },
  {
    name: 'db-proxy@file',
    type: 'loadbalancer',
    status: 'enabled',
    _proto: 'tcp',
    provider: 'file',
    loadBalancer: { servers: [{ address: 'db:5432' }] },
    usedBy: ['db-proxy@file'],
  },
  {
    name: 'old-service@file',
    type: 'loadbalancer',
    status: 'warning',
    _proto: 'http',
    provider: 'file',
    loadBalancer: { servers: [{ url: 'http://legacy:8080' }], passHostHeader: true },
    usedBy: ['old-service@file'],
  },
];

export const DEMO_OVERVIEW = {
  http: {
    routers:     { total: 4, warnings: 0, errors: 0 },
    services:    { total: 5, warnings: 1, errors: 0 },
    middlewares: { total: 4, warnings: 0, errors: 0 },
  },
  tcp: {
    routers:  { total: 1, warnings: 0, errors: 0 },
    services: { total: 1, warnings: 0, errors: 0 },
  },
  udp: {
    routers:  { total: 0, warnings: 0, errors: 0 },
    services: { total: 0, warnings: 0, errors: 0 },
  },
};

export const DEMO_ENTRYPOINTS = [
  { name: 'web',       address: ':80'  },
  { name: 'websecure', address: ':443' },
];

export const DEMO_CONFIGS = {
  files: [{ label: 'dynamic.yml', path: 'dynamic.yml' }],
  configDirSet: false,
};

export const DEMO_CERTS = {
  certs: [
    { resolver: 'cloudflare', main: 'demo.example.com',   sans: ['demo.example.com'],                         not_after: '2026-08-20T00:00:00Z' },
    { resolver: 'cloudflare', main: 'api.example.com',    sans: ['api.example.com', 'api-v2.example.com'],     not_after: '2026-08-20T00:00:00Z' },
    { resolver: 'cloudflare', main: 'auth.example.com',   sans: ['auth.example.com'],                         not_after: '2026-07-01T00:00:00Z' },
    { resolver: 'letsencrypt', main: 'internal.corp',     sans: ['internal.corp', '*.internal.corp'],          not_after: '2026-09-15T00:00:00Z' },
    { resolver: 'letsencrypt', main: 'monitor.corp',      sans: ['monitor.corp'],                              not_after: '2026-06-10T00:00:00Z' },
  ],
  errors: [],
};

export const DEMO_PLUGINS = {
  plugins: [
    { name: 'traefik-real-ip',       moduleName: 'github.com/soulbalz/traefik-real-ip',        version: 'v1.0.3' },
    { name: 'geoblock',              moduleName: 'github.com/PascalMinder/geoblock',            version: 'v0.2.8' },
    { name: 'bouncer',               moduleName: 'github.com/maxlerebourg/crowdsec-bouncer-traefik-plugin', version: 'v1.3.5' },
    { name: 'rewrite-response-headers', moduleName: 'github.com/XciD/traefik-plugin-rewrite-headers', version: 'v0.0.3' },
  ],
};

export const DEMO_CROWDSEC_DECISIONS = [
  { id: 1, value: '203.0.113.42',   type: 'ban',     duration: '4h0m0s',    scenario: 'crowdsecurity/http-bf-wordpress_bf',       origin: 'cscli' },
  { id: 2, value: '198.51.100.7',   type: 'ban',     duration: '24h0m0s',   scenario: 'crowdsecurity/ssh-bf',                      origin: 'CAPI'  },
  { id: 3, value: '192.0.2.1',      type: 'captcha', duration: '1h0m0s',    scenario: 'crowdsecurity/http-crawl-non_statics',      origin: 'CAPI'  },
  { id: 4, value: '203.0.113.100',  type: 'ban',     duration: '48h0m0s',   scenario: 'crowdsecurity/iptables-scan-multi_ports',   origin: 'cscli' },
  { id: 5, value: '198.51.100.200', type: 'bypass',  duration: '8760h0m0s', scenario: 'manual',                                    origin: 'cscli' },
];

export const DEMO_CROWDSEC_ALERTS = [
  { startAt: '2026-05-23T08:14:22Z', source: { ip: '203.0.113.42' },   scenario: 'crowdsecurity/http-bf-wordpress_bf',    decisions: [] },
  { startAt: '2026-05-23T07:55:01Z', source: { ip: '198.51.100.7' },   scenario: 'crowdsecurity/ssh-bf',                  decisions: [] },
  { startAt: '2026-05-23T06:30:45Z', source: { ip: '192.0.2.1' },      scenario: 'crowdsecurity/http-crawl-non_statics',  decisions: [] },
  { startAt: '2026-05-22T23:11:09Z', source: { ip: '203.0.113.100' },  scenario: 'crowdsecurity/iptables-scan-multi_ports', decisions: [] },
];

export const DEMO_LOGS = {
  lines: [
    '192.168.1.10 - - [06/Apr/2026:12:00:01 +0000] "GET /api/health HTTP/2.0" 200 42 "-" "Go-http-client/2.0" 1 "websecure@internal" "http://backend:8080" 2ms',
    '192.168.1.55 - - [06/Apr/2026:12:00:03 +0000] "GET / HTTP/2.0" 200 3842 "-" "Mozilla/5.0" 1 "websecure@internal" "http://app:3000" 8ms',
    '10.0.0.1 - - [06/Apr/2026:12:00:05 +0000] "POST /api/login HTTP/2.0" 401 61 "-" "curl/7.88.1" 1 "websecure@internal" "http://auth:4000" 3ms',
    '192.168.1.10 - - [06/Apr/2026:12:00:07 +0000] "GET /dashboard HTTP/2.0" 304 0 "-" "Mozilla/5.0" 1 "websecure@internal" "http://app:3000" 1ms',
    '10.0.0.2 - - [06/Apr/2026:12:00:09 +0000] "GET /missing HTTP/2.0" 404 19 "-" "curl/7.88.1" 1 "websecure@internal" "http://app:3000" 2ms',
    '192.168.1.55 - - [06/Apr/2026:12:00:11 +0000] "GET /metrics HTTP/2.0" 200 1204 "-" "Prometheus/2.40.0" 1 "websecure@internal" "http://metrics:9090" 5ms',
    '10.0.0.3 - - [06/Apr/2026:12:00:13 +0000] "GET /api/data HTTP/2.0" 500 88 "-" "axios/1.4.0" 1 "websecure@internal" "http://api:3000" 142ms',
    '192.168.1.10 - - [06/Apr/2026:12:00:15 +0000] "GET /static/app.js HTTP/2.0" 200 98432 "-" "Mozilla/5.0" 1 "websecure@internal" "http://app:3000" 12ms',
  ],
};
