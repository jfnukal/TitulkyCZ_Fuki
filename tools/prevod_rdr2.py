"""PlayCaption Fuki – převodník: oficiální angličtina (XML) + komunitní čeština (.strings) → tabulka EN<TAB>CS."""
import sys, re, xml.etree.ElementTree as ET

def joaat(s):
    h = 0
    for c in s.lower().encode('utf-8'):
        h = (h + c) & 0xFFFFFFFF
        h = (h + (h << 10)) & 0xFFFFFFFF
        h ^= h >> 6
    h = (h + (h << 3)) & 0xFFFFFFFF
    h ^= h >> 11
    h = (h + (h << 15)) & 0xFFFFFFFF
    return h

ZNACKY = re.compile(r'~[a-zA-Z]{1,3}(:[^~]*)?~')
def cisty(t):
    t = t.replace('~n~', ' ')
    t = ZNACKY.sub('', t)
    return re.sub(r'\s+', ' ', t).strip()

def klic(k):
    k = k.strip()
    if re.fullmatch(r'0x[0-9A-Fa-f]{1,8}', k): return int(k, 16)
    return joaat(k)

def nacti_cs(cesta):
    d = open(cesta, 'rb').read()
    if not d.lstrip().startswith(b'0x') and d[:2] == b'T\x1c':   # LokalizaceNET: XOR 0x64
        d = bytes(b ^ 0x64 for b in d)
    cs = {}
    for r in d.decode('utf-8', 'replace').splitlines():
        if '=' not in r: continue
        k, t = r.split('=', 1)
        cs[klic(k)] = cisty(t)
    return cs

def nacti_en(cesta):
    en = {}
    for _, el in ET.iterparse(cesta):
        if el.tag == 'String' and el.get('id'):
            en[klic(el.get('id'))] = cisty(el.text or '')
        el.clear()
    return en

# Použití (PC, Python 3):
#   python prevod_rdr2.py "English Translation.xml" lokalizace.net.strings rdr2_cs.tsv.gz
# Výsledek nahrát do telefonu/TV:
#   adb push rdr2_cs.tsv.gz /sdcard/Android/data/cz.titulky.tv.fuki/files/tabulky/
# Tabulka je jen pro vlastní potřebu – čeština patří jejím překladatelům, nešířit ji dál.
if __name__ == '__main__':
    import gzip
    en = nacti_en(sys.argv[1]); cs = nacti_cs(sys.argv[2])
    n = 0
    otevri = (lambda c: gzip.open(c, 'wt', encoding='utf-8', newline='\n')) if sys.argv[3].endswith('.gz') \
        else (lambda c: open(c, 'w', encoding='utf-8', newline='\n'))
    with otevri(sys.argv[3]) as f:
        f.write('#PlayCaption tabulka EN\tCS\n#hra: Red Dead Redemption 2\n#alias: RDR2, RDR 2, Red Dead Redemption II\n')
        for k, e in en.items():
            c = cs.get(k)
            if not c or not e or c == e: continue
            if sum(ch.isalpha() for ch in e) < 2: continue
            f.write(e.replace('\t', ' ') + '\t' + c.replace('\t', ' ') + '\n'); n += 1
    print(f'angličtina {len(en)}, čeština {len(cs)}, spárováno {n}')
