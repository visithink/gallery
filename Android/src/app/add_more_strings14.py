import xml.etree.ElementTree as ET

strings_to_add = {
    'model': 'Model',
    'accelerator': 'Accelerator',
    'prefill_tokens': 'Prefill tokens',
    'decode_tokens': 'Decode tokens',
    'number_of_runs': 'Number of runs',
    'app_version': 'App version',
    'prefill_speed': 'Prefill speed',
    'decode_speed': 'Decode speed',
    'time_to_first_token': 'Time to first token',
    'first_init_time': 'First init time',
    'steady_init_time': 'Steady init time',
    'start_time_ms': 'start time (ms)',
    'end_time_ms': 'end time (ms)',
    'model_name': 'model name',
    'prefill_tokens_count': 'prefill tokens count',
    'decode_tokens_count': 'decode tokens count',
    'runs_count': 'runs count',
    'prefill_speed_tokens_sec': 'prefill speed (tokens/sec)',
    'decode_speed_tokens_sec': 'decode speed (tokens/sec)',
    'time_to_first_token_sec': 'time to first token (sec)',
    'first_init_time_ms': 'first init time (ms)',
    'steady_init_time_ms': 'steady init time (ms)'
}

strings_to_add_zh = {
    'model': '模型',
    'accelerator': '加速器',
    'prefill_tokens': '预填充 Token 数',
    'decode_tokens': '解码 Token 数',
    'number_of_runs': '运行次数',
    'app_version': '应用版本',
    'prefill_speed': '预填充速度',
    'decode_speed': '解码速度',
    'time_to_first_token': '首 Token 延迟',
    'first_init_time': '首次初始化时间',
    'steady_init_time': '稳定初始化时间',
    'start_time_ms': '开始时间 (毫秒)',
    'end_time_ms': '结束时间 (毫秒)',
    'model_name': '模型名称',
    'prefill_tokens_count': '预填充 Token 数',
    'decode_tokens_count': '解码 Token 数',
    'runs_count': '运行次数',
    'prefill_speed_tokens_sec': '预填充速度 (tokens/秒)',
    'decode_speed_tokens_sec': '解码速度 (tokens/秒)',
    'time_to_first_token_sec': '首 Token 延迟 (秒)',
    'first_init_time_ms': '首次初始化时间 (毫秒)',
    'steady_init_time_ms': '稳定初始化时间 (毫秒)'
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

print("Added more strings 14")
