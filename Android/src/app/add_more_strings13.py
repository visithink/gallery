import xml.etree.ElementTree as ET

strings_to_add = {
    'gemma_4_e2b_e4b_description': 'Gemma 4 E2B & E4B are here! Try them in AI Chat, Agent Skills, or the use cases below.'
}

strings_to_add_zh = {
    'gemma_4_e2b_e4b_description': 'Gemma 4 E2B 和 E4B 已经发布！您可以在 AI 对话、技能代理或以下用例中体验它们。'
}

def add_strings(filepath, strings_dict):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    insert_pos = content.find('</resources>')
    if insert_pos == -1: return

    new_xml = ""
    for k, v in strings_dict.items():
        if f'name="{k}"' not in content:
            new_xml += f'  <string name="{k}">{v}</string>\n'

    content = content[:insert_pos] + new_xml + content[insert_pos:]

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

add_strings(r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\res\values\strings.xml', strings_to_add)
add_strings(r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\res\values-zh-rCN\strings.xml', strings_to_add_zh)

print("Added more strings 13")
