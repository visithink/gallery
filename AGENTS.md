# AI Edge Gallery

Android app for on-device LLM inference using LiteRT models.

## Build Commands

```bash
cd Android/src
./gradlew assembleRelease    # Build release APK
./gradlew installDebug        # Build and install debug on device
```

## Required Setup

Before building, configure HuggingFace OAuth credentials:

1. `Android/src/app/src/main/java/com/google/ai/edge/gallery/common/ProjectConfig.kt`:
   - Set `clientId` and `redirectUri`

2. `Android/src/app/build.gradle.kts`:
   - Set `manifestPlaceholders["appAuthRedirectScheme"]` to match redirect URL scheme

## Project Structure

- `Android/src/` - Gradle project root (open in Android Studio)
- `skills/` - Agent skills (deployed to GitHub Pages on push to main)
- `model_allowlist.json` - Available LiteRT models

## Skills Development

See `skills/README.md` for creating agent skills. Skills support:
- Text-only (SKILL.md only)
- JavaScript (scripts/index.html with `window.ai_edge_gallery_get_result`)
- Native intents (run_intent tool)

## API Server

OpenAI-compatible API server in `api/` directory using LiteRT-LM:

```bash
cd api
pip install -r requirements.txt

# Download and run
python main.py --download gemma-3-1b-it

# Or use existing model
python main.py --model /path/to/model.litertlm
```

Endpoints: `/v1/chat/completions`, `/v1/completions`, `/v1/models`

See `api/README.md` for details.

## Notes

- Java 21 required (CI uses temurin)
- Repository not accepting code contributions (see CONTRIBUTING.md)
- Models use `.task` format (LiteRT), loaded from HuggingFace
