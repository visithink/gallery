import xml.etree.ElementTree as ET

strings_to_add = {
    'new_feature': 'New',
    'experimental': 'Experimental',
    'copy_url': 'Copy URL',
    'close_viewer': 'Close viewer',
    'clear_filter': 'Clear filter',
    'model_imported_successfully': 'Model imported successfully',
    'api_url': 'API URL',
    'prompt': 'Prompt',
    'response': 'Response',
    'benchmark_results_for': 'benchmark results for %s'
}

strings_to_add_zh = {
    'new_feature': '新功能',
    'experimental': '实验性',
    'copy_url': '复制 URL',
    'close_viewer': '关闭查看器',
    'clear_filter': '清除筛选',
    'model_imported_successfully': '模型导入成功',
    'api_url': 'API URL',
    'prompt': '提示词',
    'response': '响应',
    'benchmark_results_for': '%s 的基准测试结果'
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

print("Added more strings 5")
