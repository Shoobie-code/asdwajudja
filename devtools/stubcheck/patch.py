#!/usr/bin/env python3
"""Applies hierarchy/generic/enum hints to generated stubs. Usage: patch.py <stubdir> <hints>"""
import re, sys, pathlib

root = pathlib.Path(sys.argv[1])
hints = pathlib.Path(sys.argv[2]).read_text().splitlines()

def file_of(cls):
    outer = cls.split('$')[0]
    return root / (outer + '.java')

def simple(cls):
    return cls.replace('$', '/').split('/')[-1]

def header_re(cls):
    return re.compile(r'(public (?:static )?)(class|interface|enum|@interface) ' + re.escape(simple(cls)) + r'( [^{]*)?\{')

for line in hints:
    line = line.strip()
    if not line or line.startswith('#'):
        continue
    op, rest = line.split(' ', 1)
    if op == 'add':  # add <cls> <member source>
        cls, member = rest.split(' ', 1)
        f = file_of(cls)
        if not f.exists():
            pkg = '.'.join(cls.split('/')[:-1])
            f.parent.mkdir(parents=True, exist_ok=True)
            f.write_text(f'package {pkg};\n\npublic class {simple(cls)} {{\n}}\n')
        src = f.read_text()
        m = header_re(cls).search(src)
        assert m, (cls, line)
        src = src[:m.end()] + '\n    ' + member + src[m.end():]
        f.write_text(src)
        continue
    if op == 'sub':  # sub <cls> <regex> => <replacement>
        cls, expr = rest.split(' ', 1)
        pat, rep = expr.split(' => ', 1)
        f = file_of(cls)
        src = f.read_text()
        new, n = re.subn(pat, rep, src)
        assert n, ('no match', line)
        f.write_text(new)
        continue
    cls, arg = (rest.split(' ', 1) + [''])[:2]
    f = file_of(cls)
    if not f.exists():
        pkg = '.'.join(cls.split('/')[:-1])
        f.parent.mkdir(parents=True, exist_ok=True)
        kind = 'interface' if op == 'interface' else 'class'
        f.write_text(f'package {pkg};\n\npublic {kind} {simple(cls)} {{\n}}\n')
    src = f.read_text()
    m = header_re(cls).search(src)
    assert m, (cls, line)
    prefix, kind, tail = m.group(1), m.group(2), (m.group(3) or ' ')
    if op == 'enum':
        # convert static self-typed fields to enum constants
        body_start = m.end()
        name = simple(cls)
        jname = cls.replace('/', '.').replace('$', '.')
        consts = re.findall(r'\n\s+public static ' + re.escape(jname) + r' (\w+);', src[body_start:])
        src2 = re.sub(r'\n\s+public static ' + re.escape(jname) + r' \w+;', '', src[body_start:])
        src2 = re.sub(r'\n\s+public (?:int ordinal|static [\w.]+\[\] values)\(\) \{[^}]*\}', '', src2)
        src = src[:m.start()] + f'{prefix}enum {name}{tail}{{\n        ' + ', '.join(consts) + ';' + src2
    elif op == 'interface':
        src = src[:m.start()] + f'{prefix}interface {simple(cls)}{tail}{{' + src[m.end():]
        src = re.sub(r'public (?!static|default)([\w.<>\[\], ?]+ \w+\([^)]*\)) \{', r'public default \1 {', src)
    elif op == 'extends':
        src = src[:m.start()] + f'{prefix}{kind} {simple(cls)}{tail}extends {arg} {{' + src[m.end():]
    elif op == 'implements':
        src = src[:m.start()] + f'{prefix}{kind} {simple(cls)}{tail}implements {arg} {{' + src[m.end():]
    elif op == 'generic':
        src = src[:m.start()] + f'{prefix}{kind} {simple(cls)}{arg}{tail}{{' + src[m.end():]
    elif op == 'abstract':
        src = src[:m.start()] + f'{prefix}abstract {kind} {simple(cls)}{tail}{{' + src[m.end():]
    else:
        raise SystemExit('unknown op ' + op)
    f.write_text(src)
print('patched')
