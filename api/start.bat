@echo off
echo AI Edge Gallery API Server (LiteRT-LM)
echo ======================================

if "%MODEL_PATH%"=="" set MODEL_PATH=model.litertlm
if "%PORT%"=="" set PORT=8000

if not exist "%MODEL_PATH%" (
    echo.
    echo Model not found: %MODEL_PATH%
    echo.
    echo Download a model first:
    echo   python main.py --download gemma-3-1b-it
    echo.
    echo Or specify model path:
    echo   python main.py --model /path/to/model.litertlm
    exit /b 1
)

echo Model: %MODEL_PATH%
echo Port: %PORT%
echo.

python main.py --model %MODEL_PATH% --port %PORT%
