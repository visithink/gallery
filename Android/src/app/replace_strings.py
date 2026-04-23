import os
import re

files_to_replace = {
    r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\java\com\google\ai\edge\gallery\customtasks\agentchat\SkillTesterBottomSheet.kt': [
        ('Text("Input Data")', 'Text(stringResource(R.string.input_data))'),
        ('Text("Custom Data")', 'Text(stringResource(R.string.custom_data))')
    ],
    r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\java\com\google\ai\edge\gallery\customtasks\agentchat\AgentChatScreen.kt': [
        ('Text("The \\"$disabledSkillName\\" skill is currently disabled")', 'Text(stringResource(R.string.skill_disabled_msg, disabledSkillName))')
    ],
    r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\java\com\google\ai\edge\gallery\ui\modelmanager\ModelImportDialog.kt': [
        ('Text("Cancel")', 'Text(stringResource(R.string.cancel))'),
        ('Text("Import")', 'Text(stringResource(R.string.import_model))'),
        ('Text("Close")', 'Text(stringResource(R.string.close))')
    ],
    r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\java\com\google\ai\edge\gallery\ui\home\SettingsDialog.kt': [
        ('Text("Clear")', 'Text(stringResource(R.string.clear))'),
        ('Text("Port:", style = MaterialTheme.typography.bodyMedium)', 'Text(stringResource(R.string.port), style = MaterialTheme.typography.bodyMedium)'),
        ('Text("Start")', 'Text(stringResource(R.string.start))'),
        ('Text("Stop")', 'Text(stringResource(R.string.stop))'),
        ('Text("View licenses")', 'Text(stringResource(R.string.view_licenses))'),
        ('Text("Close")', 'Text(stringResource(R.string.close))')
    ],
    r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\java\com\google\ai\edge\gallery\apiserver\ApiServerScreen.kt': [
        ('Text("API Server")', 'Text(stringResource(R.string.api_server))'),
        ('Text("Port:")', 'Text(stringResource(R.string.port))'),
        ('Text("Start")', 'Text(stringResource(R.string.start))'),
        ('Text("Stop")', 'Text(stringResource(R.string.stop))')
    ],
    r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\java\com\google\ai\edge\gallery\ui\llmsingleturn\PromptTemplatesPanel.kt': [
        ('Text("Enter content")', 'Text(stringResource(R.string.enter_content))'),
        ('Text("Preview prompt", style = MaterialTheme.typography.labelMedium)', 'Text(stringResource(R.string.preview_prompt), style = MaterialTheme.typography.labelMedium)')
    ],
    r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\java\com\google\ai\edge\gallery\ui\common\chat\MessageInputText.kt': [
        ('Text("Take a picture")', 'Text(stringResource(R.string.take_a_picture))'),
        ('Text("Pick from album")', 'Text(stringResource(R.string.pick_from_album))'),
        ('Text("Record audio clip")', 'Text(stringResource(R.string.record_audio_clip))'),
        ('Text("Pick wav file")', 'Text(stringResource(R.string.pick_wav_file))'),
        ('Text("Input history")', 'Text(stringResource(R.string.input_history))')
    ],
    r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\java\com\google\ai\edge\gallery\ui\common\chat\ModelNotDownloaded.kt': [
        ('Text("Download & Try it", maxLines = 1)', 'Text(stringResource(R.string.download_and_try), maxLines = 1)')
    ],
    r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\java\com\google\ai\edge\gallery\ui\common\chat\TextInputHistorySheet.kt': [
        ('Text("Clear history?")', 'Text(stringResource(R.string.clear_history_title))'),
        ('Text("Are you sure you want to clear the history? This action cannot be undone.")', 'Text(stringResource(R.string.clear_history_content))')
    ],
    r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\java\com\google\ai\edge\gallery\ui\common\ErrorDialog.kt': [
        ('Text("Close")', 'Text(stringResource(R.string.close))')
    ],
    r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\java\com\google\ai\edge\gallery\ui\common\ConfigDialog.kt': [
        ('Text("Cancel")', 'Text(stringResource(R.string.cancel))')
    ],
    r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\java\com\google\ai\edge\gallery\ui\common\DownloadAndTryButton.kt': [
        ('Text("Acknowledge user agreement", style = MaterialTheme.typography.titleLarge)', 'Text(stringResource(R.string.acknowledge_user_agreement), style = MaterialTheme.typography.titleLarge)'),
        ('Text("Open user agreement")', 'Text(stringResource(R.string.open_user_agreement))'),
        ('Text("Unknown network error")', 'Text(stringResource(R.string.unknown_network_error))'),
        ('Text("Please check your internet connection.")', 'Text(stringResource(R.string.check_internet_connection))'),
        ('Text("Close")', 'Text(stringResource(R.string.close))')
    ],
    r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\java\com\google\ai\edge\gallery\customtasks\agentchat\AddOrEditSkillBottomSheet.kt': [
        ('Text("Are you sure you want to delete \'$selectedScript\'?")', 'Text(stringResource(R.string.confirm_delete_script, selectedScript))')
    ],
    r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\java\com\google\ai\edge\gallery\GalleryAppTopBar.kt': [
        ('Text("Done")', 'Text(stringResource(R.string.done))')
    ],
    r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\java\com\google\ai\edge\gallery\ui\modelmanager\GlobalModelManager.kt': [
        ('Text("From local model file", modifier = Modifier.clearAndSetSemantics {})', 'Text(stringResource(R.string.from_local_model_file), modifier = Modifier.clearAndSetSemantics {})'),
        ('Text("Unsupported file type")', 'Text(stringResource(R.string.unsupported_file_type))'),
        ('Text("Only \\\".task\\\" or \\\".litertlm\\\" file type is supported.")', 'Text(stringResource(R.string.unsupported_file_type_desc))'),
        ('Text("Unsupported model type")', 'Text(stringResource(R.string.unsupported_model_type))'),
        ('Text("Looks like the model is a web-only model and is not supported by the app.")', 'Text(stringResource(R.string.unsupported_model_type_desc))')
    ],
    r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\java\com\google\ai\edge\gallery\ui\modelmanager\PromoBannerGm4.kt': [
        ('Text(text = "Gemma 4: now available", style = MaterialTheme.typography.titleMedium)', 'Text(text = stringResource(R.string.gemma_4_now_available), style = MaterialTheme.typography.titleMedium)'),
        ('Text("Dismiss")', 'Text(stringResource(R.string.dismiss))'),
        ('Text("Read more")', 'Text(stringResource(R.string.read_more))')
    ],
    r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\java\com\google\ai\edge\gallery\ui\home\HomeScreen.kt': [
        ('Text("Please check your internet connection and try again later.")', 'Text(stringResource(R.string.check_internet_connection_retry))'),
        ('Text("Retry")', 'Text(stringResource(R.string.retry))'),
        ('Text("Cancel")', 'Text(stringResource(R.string.cancel))')
    ],
    r'd:\workspace\ai\GOOGLE\gallery\Android\src\app\src\main\java\com\google\ai\edge\gallery\ui\home\PromoScreenGm4.kt': [
        ('Text("Dismiss", color = Color(0xFFA8C7FA))', 'Text(stringResource(R.string.dismiss), color = Color(0xFFA8C7FA))')
    ]
}

for filepath, replacements in files_to_replace.items():
    if not os.path.exists(filepath): continue
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    needs_string_resource = False
    needs_R = False
    
    for old, new in replacements:
        if old in content:
            content = content.replace(old, new)
            needs_string_resource = True
            needs_R = True
            
    if needs_string_resource and 'import androidx.compose.ui.res.stringResource' not in content:
        # insert after the last import
        imports = re.findall(r'^import .+', content, flags=re.MULTILINE)
        if imports:
            last_import = imports[-1]
            content = content.replace(last_import, last_import + '\nimport androidx.compose.ui.res.stringResource')
    if needs_R and 'import com.google.ai.edge.gallery.R' not in content:
        imports = re.findall(r'^import .+', content, flags=re.MULTILINE)
        if imports:
            last_import = imports[-1]
            content = content.replace(last_import, last_import + '\nimport com.google.ai.edge.gallery.R')
            
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)
        
print("Replacements completed.")
