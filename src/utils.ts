export function providerOf(name: string): string {
  if (!name) return 'file';
  const parts = name.split('@');
  return parts.length > 1 ? parts[parts.length - 1] : 'file';
}

export function formatDate(filename: string): string {
  const match = filename.match(/(\d{8})_(\d{6})/);
  if (!match) return filename;
  const [, date, time] = match;
  const y = date.slice(0, 4);
  const mo = date.slice(4, 6);
  const d = date.slice(6, 8);
  const h = time.slice(0, 2);
  const mi = time.slice(2, 4);
  return `${y}-${mo}-${d} ${h}:${mi}`;
}

export function statusOf(item: { status?: string }): 'ok' | 'warn' | 'err' {
  const s = (item.status || '').toLowerCase();
  if (s === 'enabled' || s === 'success' || s === 'ok') return 'ok';
  if (s === 'warning' || s === 'warn') return 'warn';
  return 'err';
}

export function pct(n: number, total: number): string {
  if (total === 0) return '0%';
  return Math.round((n / total) * 100) + '%';
}

export function normalizeUrl(url: string): string {
  let u = url.trim().replace(/\/+$/, '');
  if (u && !u.startsWith('http://') && !u.startsWith('https://')) {
    u = 'https://' + u;
  }
  return u;
}
