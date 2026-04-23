import xml.etree.ElementTree as ET

strings_to_add = {
    'hide_password': 'Hide password',
    'show_password': 'Show password',
    'app_tos_content': 'By using this app, you agree to the [Google Terms of Service](https://policies.google.com/terms?hl=en-US).\n\nTo learn what information we collect and why, how we use it, and how to review and update it, please review the [Google Privacy Policy](https://policies.google.com/privacy?hl=en-US).\n\nYour use of each model is subject to the applicable model license terms.',
    'gemma_tos_prefix': 'Gemma models on the Google AI Edge Gallery app are governed by the',
    'gemma_tos_link': 'Gemma Terms of Service',
    'gemma_tos_suffix': '. Please review these terms and ensure you agree before continuing.',
    'mobile_actions_challenge_instruction_1': '1. ',
    'mobile_actions_challenge_on_your_computer': 'On your computer',
    'mobile_actions_challenge_open': ', open ',
    'mobile_actions_challenge_this_guide': 'this guide',
    'mobile_actions_challenge_instruction_2': '\n2. Follow the instructions to fine tune the model and convert it to .litertlm format.',
    'mobile_actions_challenge_instruction_3': '\n3. Transfer the file to this phone.',
    'mobile_actions_challenge_instruction_4': '\n4. Tap ',
    'mobile_actions_challenge_instruction_5': ' below to unlock the demo.'
}

strings_to_add_zh = {
    'hide_password': '隐藏密码',
    'show_password': '显示密码',
    'app_tos_content': '使用此应用即表示您同意 [Google 服务条款](https://policies.google.com/terms?hl=zh-CN)。\n\n如需了解我们收集哪些信息及其原因、我们如何使用这些信息，以及如何查看和更新这些信息，请查阅 [Google 隐私权政策](https://policies.google.com/privacy?hl=zh-CN)。\n\n您对每个模型的使用均受适用的模型许可条款约束。',
    'gemma_tos_prefix': 'Google AI Edge Gallery 应用上的 Gemma 模型受',
    'gemma_tos_link': 'Gemma 服务条款',
    'gemma_tos_suffix': '约束。在继续之前，请查看这些条款并确保您同意。',
    'mobile_actions_challenge_instruction_1': '1. ',
    'mobile_actions_challenge_on_your_computer': '在您的计算机上',
    'mobile_actions_challenge_open': '，打开',
    'mobile_actions_challenge_this_guide': '此指南',
    'mobile_actions_challenge_instruction_2': '\n2. 按照说明微调模型并将其转换为 .litertlm 格式。',
    'mobile_actions_challenge_instruction_3': '\n3. 将文件传输到此手机。',
    'mobile_actions_challenge_instruction_4': '\n4. 点击下方的',
    'mobile_actions_challenge_instruction_5': '以解锁演示。'
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

print("Added more strings 7")
