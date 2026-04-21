#!/bin/bash
echo "AI Edge Gallery API Server (LiteRT-LM)"
echo "======================================"

MODEL_PATH=${MODEL_PATH:-model.litertlm}
PORT=${PORT:-8000}

if [ ! -f "$MODEL_PATH" ]; then
    echo ""
    echo "Model not found: $MODEL_PATH"
    echo ""
    echo "Download a model first:"
    echo "  python main.py --download gemma-3-1b-it"
    echo ""
    echo "Or specify model path:"
    echo "  python main.py --model /path/to/model.litertlm"
    exit 1
fi

echo "Model: $MODEL_PATH"
echo "Port: $PORT"
echo ""

python3 main.py --model "$MODEL_PATH" --port "$PORT"
