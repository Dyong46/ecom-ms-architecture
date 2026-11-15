#!/usr/bin/env python3
"""Check local dependency/import boundaries without Maven or network access."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
# Existing foundational relationships only; not permission for new feature coupling.
LEGACY = {'web-module': {'common-module'},
          'common-data-model': {'common-module', 'web-module'}}
modules = ET.parse(ROOT / 'support/pom.xml').findall('m:modules/m:module', NS)
modules = [node.text for node in modules]
packages = {}
for module in modules:
    for source in (ROOT / 'support' / module / 'src/main/java').rglob('*.java'):
        match = re.search(r'^package\s+([\w.]+);', source.read_text(), re.M)
        if match:
            packages[match.group(1)] = module
errors = []
for module in modules:
    folder = ROOT / 'support' / module
    pom = ET.parse(folder / 'pom.xml')
    # Include profile dependencies and dependencyManagement: optional/test scope is no exemption.
    for dep in pom.findall('.//m:dependency', NS):
        if dep.findtext('m:groupId', namespaces=NS) == 'com.duong':
            target = dep.findtext('m:artifactId', namespaces=NS)
            if target not in LEGACY.get(module, set()):
                errors.append(f'{module}/pom.xml: forbidden project dependency {target}')
    for source in (folder / 'src').rglob('*.java'):
        for ref in re.findall(r'\bcom\.duong(?:\.[A-Za-z_$][\w$]*)+', source.read_text()):
            owners = [owner for package, owner in packages.items()
                      if ref == package or ref.startswith(package + '.')]
            if owners:
                owner = owners[0]
                if owner != module and owner not in LEGACY.get(module, set()):
                    errors.append(f'{source.relative_to(ROOT)}: forbidden reference {ref}')
            elif ref.startswith(('com.duong.ecommerce.', 'com.duong.gateway.')):
                errors.append(f'{source.relative_to(ROOT)}: application reference {ref}')
if errors:
    print('\n'.join(sorted(set(errors))), file=sys.stderr)
    sys.exit(1)
print('Support boundaries passed (POM dependencies and Java references).')
