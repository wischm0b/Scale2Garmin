"""Regenerate German/English Android resources. Append entries; preserve existing IDs."""
from pathlib import Path
import json
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
pairs = json.loads((root / 'translations/de-en.json').read_text(encoding='utf-8'))
for locale, column in [('values', 1), ('values-de', 0)]:
    resources = ET.Element('resources')
    for index, pair in enumerate(pairs):
        item = ET.SubElement(resources, 'string', name=f'ui_{index}', formatted='false')
        item.text = '"' + pair[column].replace("'", "\\'") + '"'
    ET.indent(resources, space='    ')
    target = root / 'app/src/main/res' / locale / 'strings.xml'
    target.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(resources).write(target, encoding='utf-8', xml_declaration=True)
target = root / 'app/src/main/java/de/scalebridge/Tr.java'
source = target.read_text(encoding='utf-8')
start = source.index('    static {')
end = source.index('    public static Locale locale', start)
table = '\n'.join(f'        labels.put({json.dumps(pair[0], ensure_ascii=False)},R.string.ui_{index});'
                  for index, pair in enumerate(pairs))
source = source[:start] + '    static {\n' + table + '\n    }\n' + source[end:]
target.write_text(source, encoding='utf-8')
