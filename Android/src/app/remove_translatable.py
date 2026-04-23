import os
filepath = r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\res\values\strings.xml'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace(' translatable="false"', '')
with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
print('Removed translatable attributes')
