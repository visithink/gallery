import xml.etree.ElementTree as ET

strings_to_add = {
    'settings': 'Settings',
    'app_version': 'App version: %s',
    'theme': 'Theme',
    'theme_auto': 'Auto',
    'theme_light': 'Light',
    'theme_dark': 'Dark',
    'theme_unknown': 'Unknown',
    'hf_access_token': 'HuggingFace access token',
    'expires_at': 'Expires at: %s',
    'not_available': 'Not available',
    'token_auto_retrieved': 'The token will be automatically retrieved when a gated model is downloaded',
    'enter_token_manually': 'Enter token manually',
    'cd_refresh_model': 'Refresh model',
    'api_server_desc': 'Start an OpenAI-compatible HTTP server on this device. Load a model in a chat session first, then start the server.',
    'third_party_libraries': 'Third-party libraries'
}

strings_to_add_zh = {
    'settings': '设置',
    'app_version': '应用版本: %s',
    'theme': '主题',
    'theme_auto': '跟随系统',
    'theme_light': '浅色',
    'theme_dark': '深色',
    'theme_unknown': '未知',
    'hf_access_token': 'HuggingFace 访问令牌',
    'expires_at': '过期时间: %s',
    'not_available': '不可用',
    'token_auto_retrieved': '当下载受限模型时，令牌将自动获取',
    'enter_token_manually': '手动输入令牌',
    'cd_refresh_model': '刷新模型',
    'api_server_desc': '在此设备上启动兼容 OpenAI 的 HTTP 服务器。请先在聊天会话中加载模型，然后再启动服务器。',
    'third_party_libraries': '第三方库'
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

print("Added more strings")
