"""Map iOS catalog keys (English text) to Android string resources and build translations.

Placeholders are normalised to {} for matching. Android %1$s/%d/%s, iOS %@/%lld/%1$@.
"""
import json, re, sys, html
import xml.etree.ElementTree as ET

RES = '/home/chronzz/code/traefik-manager/traefik-manager-mobile/android/app/src/main/res'
LANGS = {  # iOS locale -> Android values dir
    'zh-Hans': 'values-b+zh+Hans', 'cs': 'values-cs', 'da': 'values-da', 'de': 'values-de',
    'es': 'values-es', 'fr': 'values-fr', 'fr-CA': 'values-fr-rCA', 'nl': 'values-nl',
    'pt-PT': 'values-pt', 'pt-BR': 'values-pt-rBR', 'ru': 'values-ru',
}

AND_PH = re.compile(r'%(?:(\d+)\$)?([sd])')
IOS_PH = re.compile(r'%(?:(\d+)\$)?(@|lld|ld|d)')


def android_text(el):
    text = ''.join(el.itertext())
    text = text.replace("\\'", "'").replace('\\"', '"').replace('\\n', '\n').replace('\\@', '@').replace('\\?', '?')
    if len(text) >= 2 and text[0] == '"' and text[-1] == '"':
        text = text[1:-1]
    return text.replace('%%', '%')


def load(dirname):
    strings, plurals = {}, {}
    try:
        root = ET.parse(f'{RES}/{dirname}/strings.xml').getroot()
    except FileNotFoundError:
        return strings, plurals
    for el in root:
        name = el.get('name')
        if el.tag == 'string':
            strings[name] = android_text(el)
        elif el.tag == 'plurals':
            plurals[name] = {i.get('quantity'): android_text(i) for i in el}
    return strings, plurals


def norm(text):
    text = AND_PH.sub('{}', text)
    text = IOS_PH.sub('{}', text)
    text = text.replace('…', '...').replace('’', "'").replace('—', '-').replace('–', '-')
    return re.sub(r'\s+', ' ', text).strip().lower()


def ios_specs(key):
    """Type of each iOS argument in order: '@' or 'lld'."""
    specs = []
    for m in IOS_PH.finditer(key):
        specs.append('@' if m.group(2) == '@' else 'lld')
    return specs


def to_ios(android, specs, src_android):
    """Rewrite Android placeholders as iOS ones, keeping argument positions."""
    if not specs:
        return android.replace('%', '%%') if '%' in android and False else android
    # argument index of each placeholder occurrence in the English Android source
    multi = len(specs) > 1
    seq = iter(range(1, 100))

    def rep(m):
        index = int(m.group(1)) if m.group(1) else next(seq)
        if index > len(specs):
            raise ValueError('arg out of range')
        spec = specs[index - 1]
        return f'%{index}${spec}' if multi else f'%{spec}'
    out = AND_PH.sub(rep, android)
    return out


def main():
    keys = json.load(open('/tmp/i18n/ioskeys.json'))
    catalog = json.load(open(sys.argv[1]))
    en_s, en_p = load('values')
    by_norm = {}
    for name, value in en_s.items():
        by_norm.setdefault(norm(value), name)
    plural_by_norm = {}
    for name, forms in en_p.items():
        for q in ('other', 'one'):
            if q in forms:
                plural_by_norm.setdefault(norm(forms[q]), name)

    all_keys = set(keys) | set(catalog['strings'])
    matched, plural_matched, missing = {}, {}, []
    for key in sorted(all_keys):
        if not re.search('[A-Za-z]', key):
            continue
        entry = catalog['strings'].get(key, {})
        en = entry.get('localizations', {}).get('en', {})
        is_plural = 'variations' in en or 'substitutions' in en
        n = norm(key)
        if is_plural and n in plural_by_norm:
            plural_matched[key] = plural_by_norm[n]
        elif n in by_norm:
            matched[key] = by_norm[n]
        elif n in plural_by_norm:
            plural_matched[key] = plural_by_norm[n]
        else:
            missing.append(key)
    json.dump({'strings': matched, 'plurals': plural_matched, 'missing': missing},
              open('/tmp/i18n/match.json', 'w'), indent=1, ensure_ascii=False)
    print('keys', len(all_keys), 'strings', len(matched), 'plurals', len(plural_matched), 'missing', len(missing))


if __name__ == '__main__':
    main()
