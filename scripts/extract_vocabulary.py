"""Extract factual WRB tables from the official 2022/2024 PDF (pdfplumber required).

Usage: python scripts/extract_vocabulary.py path/to/official.pdf
The PDF is a build-time reference only; runtime uses the checked-in TSV files.
"""
import hashlib
import re
import sys
from pathlib import Path
import pdfplumber

source = Path(sys.argv[1])
out = Path('src/main/resources/org/integratedmodelling/klab/authority/wrb')
out.mkdir(parents=True, exist_ok=True)
pdf = pdfplumber.open(source)
codes_text = '\n'.join(pdf.pages[i].extract_text() for i in (151, 152))
groups = dict(re.findall(r'\b([A-Z][a-z]+)\s+([A-Z]{2})\b', codes_text))
codes = dict(re.findall(r'\b([A-Z][a-z]+)\s+([a-z]{2})\b', codes_text))
assert len(groups) == 32, groups

rows = []
for i in range(94, 126):
    page = pdf.pages[i]
    words = page.extract_words()
    header = next(w for w in words if w['text'] == 'Principal')
    top = min(r['top'] for r in page.rects if r['width'] > 50 and header['top'] + 10 < r['top'] < header['top'] + 35)
    spans = sorted((r['x0'], r['x1']) for r in page.rects
                   if r['width'] > 50 and abs(r['top'] - top) < .01)
    # Three table columns, outlined with thin filled rectangles.
    assert len(spans) in (3, 4), (i, spans)
    bottom = min(r['top'] for r in page.rects if r['width'] > 50 and r['top'] > top + 20)
    lists = []
    for left, right in spans[1:]:
        text = page.crop((left + .3, top + .3, right - .3, bottom)).extract_text()
        text = re.sub(r'\s*/\s*', '/', text)
        names = re.findall(r'[A-Z][a-z]+(?:/[A-Z][a-z]+)*', text)
        assert all(n in codes or re.sub(r'^(Endo|Epi|Panto|Ano|Bathy)', '', n).capitalize() in codes
                   for entry in names for n in entry.split('/')), (i, names)
        lists.append(';'.join(names))
    text = page.crop((spans[0][0], header['top'] + 21, spans[0][1], 775)).extract_text()
    name = next(n for n in groups if re.search(r'\b' + n.upper() + r'S?(?=\s|[0-9]|$)', text))
    rows.append('\t'.join([groups[name], name, lists[0], ';'.join(lists[1:])]))
assert len(set(r.split('\t')[0] for r in rows)) == 32
(out / 'groups.tsv').write_text('# code\tname\tprincipal (ranked)\tsupplementary\n' + '\n'.join(rows) + '\n', encoding='utf-8')

# Definition indentation distinguishes standalone qualifiers from defined replacements.
definitions = []
current = None
parent = ''
for i in range(126, 151):
    page = pdf.pages[i]
    margin = 70.9 if (i + 1) % 2 else 42.6
    lines = page.extract_text_lines()
    for line in lines:
        if line['top'] > 790:
            continue
        match = re.match(r'([A-Z][a-z]+) \(([a-z]{1,2})\)', line['text'])
        if match:
            if re.search(r': see ', line['text']):
                current = None
                continue
            name, code = match.groups()
            if codes[name] != code:
                print('Definition/table code discrepancy; using Chapter 6:', name, code, codes[name])
                code = codes[name]
            if line['x0'] < margin + 10:
                parent = name
            current = [code, name, '' if parent == name else parent, line['text']]
            definitions.append(current)
        elif current:
            current[3] += ' ' + line['text']
seen = {r[1] for r in definitions}
assert seen == set(codes), (set(codes) - seen, seen - set(codes))
rows = []
for code, name, parent, text in definitions:
    rules = re.findall(r'\(([1-5])(?:[;:]\s+([^)]*))?\)', text)
    rule = rules[-1][0] if rules else ''
    restriction = rules[-1][1] if rules else ''
    only = re.search(r'([A-Za-z-, ]+) only', restriction)
    prefixes = ','.join(re.findall(r'([A-Z][a-z]+)-', only[1])) if only else ''
    scopes = re.findall(r'\(in ([A-Za-z ,]+) only\)', text)
    scope = ','.join(groups[n] for n in groups if any(re.search(r'\b' + n + r's?\b', s) for s in scopes))
    layer = 'true' if re.search(r'\b(horizon|layer|material|properties)\b', text) else 'false'
    rows.append('\t'.join([code, name, parent, rule, prefixes, scope, layer]))
assert len(rows) == len(codes), len(rows)
(out / 'qualifiers.tsv').write_text('# code\tname\treplaces\tdepth rule\tallowed depth prefixes (override)\tRSG restriction\tlayer diagnostic\n' + '\n'.join(rows) + '\n', encoding='utf-8')
print('Source SHA256:', hashlib.sha256(source.read_bytes()).hexdigest())
print('Groups:', len(groups), 'Qualifiers:', len(codes))






