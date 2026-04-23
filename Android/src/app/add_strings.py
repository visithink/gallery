import os
import xml.etree.ElementTree as ET

new_strings = {
    'input_data': 'Input Data',
    'custom_data': 'Custom Data',
    'skill_disabled_msg': 'The "%s" skill is currently disabled',
    'import_model': 'Import',
    'port': 'Port:',
    'start': 'Start',
    'stop': 'Stop',
    'view_licenses': 'View licenses',
    'api_server': 'API Server',
    'enter_content': 'Enter content',
    'preview_prompt': 'Preview prompt',
    'record_audio_clip': 'Record audio clip',
    'pick_wav_file': 'Pick wav file',
    'download_and_try': 'Download & Try it',
    'clear_history_title': 'Clear history?',
    'clear_history_content': 'Are you sure you want to clear the history? This action cannot be undone.',
    'acknowledge_user_agreement': 'Acknowledge user agreement',
    'open_user_agreement': 'Open user agreement',
    'unknown_network_error': 'Unknown network error',
    'check_internet_connection': 'Please check your internet connection.',
    'confirm_delete_script': 'Are you sure you want to delete \'%s\'?',
    'from_local_model_file': 'From local model file',
    'unsupported_file_type': 'Unsupported file type',
    'unsupported_file_type_desc': 'Only ".task" or ".litertlm" file type is supported.',
    'unsupported_model_type': 'Unsupported model type',
    'unsupported_model_type_desc': 'Looks like the model is a web-only model and is not supported by the app.',
    'gemma_4_now_available': 'Gemma 4: now available',
    'read_more': 'Read more',
    'check_internet_connection_retry': 'Please check your internet connection and try again later.',
    'retry': 'Retry'
}

new_strings_zh = {
    'input_data': '输入数据',
    'custom_data': '自定义数据',
    'skill_disabled_msg': '"%s" 技能当前已禁用',
    'import_model': '导入',
    'port': '端口:',
    'start': '启动',
    'stop': '停止',
    'view_licenses': '查看许可证',
    'api_server': 'API 服务器',
    'enter_content': '输入内容',
    'preview_prompt': '预览提示词',
    'record_audio_clip': '录制音频片段',
    'pick_wav_file': '选择 wav 文件',
    'download_and_try': '下载并体验',
    'clear_history_title': '清除历史记录？',
    'clear_history_content': '您确定要清除历史记录吗？此操作无法撤销。',
    'acknowledge_user_agreement': '确认用户协议',
    'open_user_agreement': '打开用户协议',
    'unknown_network_error': '未知网络错误',
    'check_internet_connection': '请检查您的互联网连接。',
    'confirm_delete_script': '确定要删除 \'%s\' 吗？',
    'from_local_model_file': '从本地模型文件',
    'unsupported_file_type': '不支持的文件类型',
    'unsupported_file_type_desc': '仅支持 ".task" 或 ".litertlm" 文件类型。',
    'unsupported_model_type': '不支持的模型类型',
    'unsupported_model_type_desc': '该模型似乎是仅限 Web 的模型，应用不支持。',
    'gemma_4_now_available': 'Gemma 4: 现已可用',
    'read_more': '了解更多',
    'check_internet_connection_retry': '请检查您的互联网连接并稍后重试。',
    'retry': '重试'
}

def add_strings(filepath, strings_dict):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # insert before </resources>
    insert_pos = content.find('</resources>')
    if insert_pos == -1: return
    
    new_xml = ""
    for k, v in strings_dict.items():
        if f'name="{k}"' not in content:
            new_xml += f'  <string name="{k}">{v}</string>\n'
            
    content = content[:insert_pos] + new_xml + content[insert_pos:]
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

add_strings(r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\res\values\strings.xml', new_strings)
add_strings(r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\res\values-zh-rCN\strings.xml', new_strings_zh)

print("Strings added to xml files.")
