"""Write translations from the Android resources (and manual.json) into the iOS string catalog."""
import json, re, sys
sys.path.insert(0, '/tmp/i18n')
from match import LANGS, load, ios_specs, AND_PH, norm

catalog_path = sys.argv[1]
catalog = json.load(open(catalog_path))
match = json.load(open('/tmp/i18n/match.json'))
manual = json.load(open('/tmp/i18n/manual.json'))
keys = json.load(open('/tmp/i18n/ioskeys.json'))


def unit(value):
    return {'stringUnit': {'state': 'translated', 'value': value}}


def convert(text, specs):
    if not specs:
        return text.replace('%%', '%')
    multi = len(specs) > 1
    counter = iter(range(1, 100))
    found = set()

    def rep(m):
        index = int(m.group(1)) if m.group(1) else next(counter)
        if index > len(specs):
            raise ValueError
        found.add(index)
        return f'%{index}${specs[index - 1]}' if multi else f'%{specs[index - 1]}'
    out = AND_PH.sub(rep, text)
    # every iOS argument must survive except a plural count dropped by a "one" form
    return out, found


stats = {}
_, EN_PLURALS = load('values')
for lang, folder in LANGS.items():
    strings, plurals = load(folder)
    done = 0
    for key, name in match['strings'].items():
        value = strings.get(name)
        if not value:
            continue
        specs = ios_specs(key)
        try:
            if specs:
                out, found = convert(value, specs)
                if len(found) != len(specs):
                    continue
            else:
                out = convert(value, specs)
        except ValueError:
            continue
        entry = catalog['strings'].setdefault(key, {})
        entry.setdefault('localizations', {})[lang] = unit(out)
        done += 1
    for key, name in match['plurals'].items():
        forms = plurals.get(name)
        if not forms:
            continue
        specs = ios_specs(key)
        entry = catalog['strings'].setdefault(key, {})
        en = entry.get('localizations', {}).get('en', {})
        converted = {}
        try:
            for q, text in forms.items():
                out, found = convert(text, specs) if specs else (text, set())
                if q == 'other' and specs and len(found) != len(specs):
                    raise ValueError
                converted[q] = out
        except ValueError:
            continue
        if 'other' not in converted and 'many' in converted:
            converted['other'] = converted['many']
        if 'other' not in converted:
            continue
        if 'lld' not in specs:
            en_forms = EN_PLURALS.get(name, {})
            pick = next((q for q, t in en_forms.items() if norm(t) == norm(key)), 'other')
            entry.setdefault('localizations', {})[lang] = unit(converted.get(pick, converted['other']))
        elif 'substitutions' in en or len(specs) > 1:
            entry.setdefault('localizations', {})[lang] = unit(converted['other'])
        else:
            entry.setdefault('localizations', {})[lang] = {
                'variations': {'plural': {q: unit(v) for q, v in converted.items()}}
            }
        done += 1
    for key, values in manual.items():
        if lang in values:
            entry = catalog['strings'].setdefault(key, {})
            entry.setdefault('localizations', {})[lang] = unit(values[lang])
            done += 1
    stats[lang] = done

# keys the code uses that were never in the catalog get an entry so Xcode keeps them tidy
for key in keys:
    catalog['strings'].setdefault(key, {})
catalog['strings'] = dict(sorted(catalog['strings'].items(), key=lambda kv: kv[0].lower()))
json.dump(catalog, open(catalog_path, 'w'), indent=2, ensure_ascii=False)
open(catalog_path, 'a').write('\n')
print(stats)
