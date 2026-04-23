import xml.etree.ElementTree as ET

strings_to_add = {
    'explore_other_use_cases': 'Explore other use cases',
    'chat_with_gemma_4': 'Chat with the latest Gemma 4 model today',
    'gemma_4_agentic_tasks': 'Have Gemma 4 complete agentic tasks for you',
    'try_gemma_4_today': 'Try Gemma 4 today'
}

strings_to_add_zh = {
    'explore_other_use_cases': '探索其他用例',
    'chat_with_gemma_4': '与最新的 Gemma 4 模型对话',
    'gemma_4_agentic_tasks': '让 Gemma 4 为您完成代理任务',
    'try_gemma_4_today': '立即体验 Gemma 4'
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

print("Added more strings 12")
