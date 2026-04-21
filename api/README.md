# AI Edge Gallery API Server

OpenAI-compatible API server using **LiteRT-LM** for efficient edge deployment.

## Features

- OpenAI-compatible API endpoints
- Streaming support (SSE)
- Lightweight - runs on CPU, suitable for edge devices
- Supports `.litertlm` and `.task` model formats from HuggingFace

## Quick Start

```bash
# Install dependencies
pip install -r requirements.txt

# Download and run (automatically downloads model)
python main.py --download gemma-3-1b-it

# Or use existing model file
python main.py --model /path/to/model.litertlm
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/v1/chat/completions` | POST | Chat completion (OpenAI compatible) |
| `/v1/completions` | POST | Text completion (OpenAI compatible) |
| `/v1/models` | GET | List available models |
| `/health` | GET | Health check |

## Usage Examples

### cURL

```bash
# Chat completion
curl http://localhost:8000/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-3-1b-it",
    "messages": [{"role": "user", "content": "Hello!"}]
  }'

# Streaming
curl http://localhost:8000/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma-3-1b-it",
    "messages": [{"role": "user", "content": "Tell me a story"}],
    "stream": true
  }'
```

### Python (OpenAI SDK)

```python
from openai import OpenAI

client = OpenAI(
    base_url="http://localhost:8000/v1",
    api_key="not-needed"
)

response = client.chat.completions.create(
    model="gemma-3-1b-it",
    messages=[{"role": "user", "content": "Hello!"}]
)
print(response.choices[0].message.content)
```

### Streaming

```python
stream = client.chat.completions.create(
    model="gemma-3-1b-it",
    messages=[{"role": "user", "content": "Tell me a story"}],
    stream=True
)
for chunk in stream:
    if chunk.choices[0].delta.content:
        print(chunk.choices[0].delta.content, end="", flush=True)
```

## Available Models

| Model ID | HuggingFace Repo | Size |
|----------|-----------------|------|
| `gemma-3-1b-it` | litert-community/Gemma3-1B-IT | ~550MB |
| `gemma-3n-e2b-it` | litert-community/gemma-3n-E2B-it-litert-preview | ~3GB |
| `gemma-3n-e4b-it` | litert-community/gemma-3n-E4B-it-litert-preview | ~4GB |
| `qwen2.5-1.5b-instruct` | litert-community/Qwen2.5-1.5B-Instruct | ~1.6GB |

## Configuration

| Env Variable | Default | Description |
|--------------|---------|-------------|
| `MODEL_PATH` | model.litertlm | Path to model file |
| `MODEL_NAME` | gemma-3-1b-it | Model identifier |

## Docker

```bash
# Build
docker build -t ai-edge-api .

# Run with model volume
docker run -p 8000:8000 -v ./models:/models ai-edge-api \
  python main.py --model /models/gemma-3-1b-it.litertlm
```

## Docker Compose

```bash
# Start
docker-compose up -d

# With custom model
MODEL_PATH=./models/gemma-3n-e2b-it.litertlm docker-compose up -d
```

## Running on Android (Termux)

```bash
# Install dependencies
pkg install python
pip install -r requirements.txt

# Run server
python main.py --model /sdcard/models/model.litertlm --host 127.0.0.1
```

## Notes

- Models run on CPU by default (GPU support coming)
- First load may take time to compile the model
- Use `.litertlm` or `.task` format models from [litert-community](https://huggingface.co/litert-community)
