#!/usr/bin/env python3
"""OpenAI-compatible API server using LiteRT-LM for edge deployment."""

import argparse
import json
import os
import time
from typing import AsyncIterator, List, Optional, Union

import uvicorn
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

app = FastAPI(title="AI Edge Gallery API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

engine = None
MODEL_PATH = os.getenv("MODEL_PATH", "model.litertlm")
MODEL_ID = os.getenv("MODEL_NAME", "gemma-3-1b-it")


class ChatMessage(BaseModel):
    role: str
    content: str


class ChatCompletionRequest(BaseModel):
    model: str = "gemma-3-1b-it"
    messages: List[ChatMessage]
    temperature: Optional[float] = 0.7
    top_p: Optional[float] = 0.95
    top_k: Optional[int] = 40
    max_tokens: Optional[int] = 1024
    stream: Optional[bool] = False


class CompletionRequest(BaseModel):
    model: str = "gemma-3-1b-it"
    prompt: Union[str, List[str]]
    temperature: Optional[float] = 0.7
    top_p: Optional[float] = 0.95
    top_k: Optional[int] = 40
    max_tokens: Optional[int] = 1024
    stream: Optional[bool] = False


class ChatCompletionChoice(BaseModel):
    index: int = 0
    message: ChatMessage
    finish_reason: str = "stop"


class CompletionChoice(BaseModel):
    index: int = 0
    text: str
    finish_reason: str = "stop"


class Usage(BaseModel):
    prompt_tokens: int
    completion_tokens: int
    total_tokens: int


class ChatCompletionResponse(BaseModel):
    id: str
    object: str = "chat.completion"
    created: int
    model: str
    choices: List[ChatCompletionChoice]
    usage: Usage


class CompletionResponse(BaseModel):
    id: str
    object: str = "text_completion"
    created: int
    model: str
    choices: List[CompletionChoice]
    usage: Usage


class ModelInfo(BaseModel):
    id: str
    object: str = "model"
    created: int = 0
    owned_by: str = "google"


class ModelList(BaseModel):
    object: str = "list"
    data: List[ModelInfo]


def get_engine():
    global engine
    if engine is None:
        try:
            import litert_lm

            litert_lm.set_min_log_severity(litert_lm.LogSeverity.ERROR)

            if not os.path.exists(MODEL_PATH):
                raise FileNotFoundError(
                    f"Model not found: {MODEL_PATH}. "
                    f"Download from HuggingFace or set MODEL_PATH env."
                )

            engine = litert_lm.Engine(MODEL_PATH, backend=litert_lm.Backend.CPU)
            print(f"Loaded model: {MODEL_PATH}")
        except Exception as e:
            raise RuntimeError(f"Failed to load model: {e}")
    return engine


def download_model(model_id: str, output_path: str = "model.litertlm"):
    """Download model from HuggingFace."""
    try:
        from huggingface_hub import hf_hub_download

        repo_mappings = {
            "gemma-3-1b-it": (
                "litert-community/Gemma3-1B-IT",
                "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task",
            ),
            "gemma-3n-e2b-it": (
                "litert-community/gemma-3n-E2B-it-litert-preview",
                "gemma-3n-E2B-it-int4.task",
            ),
            "gemma-3n-e4b-it": (
                "litert-community/gemma-3n-E4B-it-litert-preview",
                "gemma-3n-E4B-it-int4.task",
            ),
            "qwen2.5-1.5b-instruct": (
                "litert-community/Qwen2.5-1.5B-Instruct",
                "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            ),
        }

        if model_id.lower() in repo_mappings:
            repo_id, filename = repo_mappings[model_id.lower()]
        else:
            repo_id = model_id
            filename = None

        print(f"Downloading model from {repo_id}...")
        model_path = hf_hub_download(
            repo_id=repo_id,
            filename=filename,
            local_dir=".",
            local_dir_use_symlinks=False,
        )

        if model_path != output_path and os.path.exists(model_path):
            os.rename(model_path, output_path)

        print(f"Model saved to: {output_path}")
        return output_path
    except Exception as e:
        raise RuntimeError(f"Failed to download model: {e}")


@app.get("/health")
async def health():
    return {"status": "healthy", "model": MODEL_ID, "model_path": MODEL_PATH}


@app.get("/v1/models")
async def list_models():
    return ModelList(data=[ModelInfo(id=MODEL_ID)])


@app.post("/v1/chat/completions")
async def chat_completions(request: ChatCompletionRequest):
    import litert_lm

    eng = get_engine()

    system_messages = []
    for msg in request.messages:
        if msg.role == "system":
            system_messages.append(
                {
                    "role": "system",
                    "content": [{"type": "text", "text": msg.content}],
                }
            )

    if request.stream:
        return StreamingResponse(
            stream_chat(eng, request.messages, system_messages, request.model),
            media_type="text/event-stream",
        )

    try:
        with eng.create_conversation(
            messages=system_messages if system_messages else None
        ) as conv:
            last_user_msg = None
            for msg in request.messages:
                if msg.role == "user":
                    last_user_msg = msg.content

            if last_user_msg:
                response = conv.send_message(last_user_msg)
                text = response["content"][0]["text"]
            else:
                text = ""

            return ChatCompletionResponse(
                id=f"chatcmpl-{int(time.time())}",
                created=int(time.time()),
                model=request.model,
                choices=[
                    ChatCompletionChoice(
                        message=ChatMessage(role="assistant", content=text)
                    )
                ],
                usage=Usage(prompt_tokens=0, completion_tokens=0, total_tokens=0),
            )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


async def stream_chat(
    eng, messages: List[ChatMessage], system_messages: List, model: str
) -> AsyncIterator[str]:
    request_id = f"chatcmpl-{int(time.time())}"

    try:
        with eng.create_conversation(
            messages=system_messages if system_messages else None
        ) as conv:
            last_user_msg = None
            for msg in messages:
                if msg.role == "user":
                    last_user_msg = msg.content

            if last_user_msg:
                for chunk in conv.send_message_async(last_user_msg):
                    text = chunk.get("content", [{}])[0].get("text", "")
                    if text:
                        chunk_data = {
                            "id": request_id,
                            "object": "chat.completion.chunk",
                            "created": int(time.time()),
                            "model": model,
                            "choices": [{"index": 0, "delta": {"content": text}}],
                        }
                        yield f"data: {json.dumps(chunk_data)}\n\n"

        yield "data: [DONE]\n\n"
    except Exception as e:
        yield f"data: {json.dumps({'error': str(e)})}\n\n"


@app.post("/v1/completions")
async def completions(request: CompletionRequest):
    eng = get_engine()

    prompts = [request.prompt] if isinstance(request.prompt, str) else request.prompt

    if request.stream:
        return StreamingResponse(
            stream_completion(eng, prompts[0], request.model),
            media_type="text/event-stream",
        )

    try:
        results = []
        with eng.create_conversation() as conv:
            for prompt in prompts:
                response = conv.send_message(prompt)
                results.append(response["content"][0]["text"])

        choices = [
            CompletionChoice(index=i, text=text) for i, text in enumerate(results)
        ]
        return CompletionResponse(
            id=f"cmpl-{int(time.time())}",
            created=int(time.time()),
            model=request.model,
            choices=choices,
            usage=Usage(prompt_tokens=0, completion_tokens=0, total_tokens=0),
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


async def stream_completion(eng, prompt: str, model: str) -> AsyncIterator[str]:
    request_id = f"cmpl-{int(time.time())}"

    try:
        with eng.create_conversation() as conv:
            for chunk in conv.send_message_async(prompt):
                text = chunk.get("content", [{}])[0].get("text", "")
                if text:
                    chunk_data = {
                        "id": request_id,
                        "object": "text_completion.chunk",
                        "created": int(time.time()),
                        "model": model,
                        "choices": [{"index": 0, "text": text}],
                    }
                    yield f"data: {json.dumps(chunk_data)}\n\n"

        yield "data: [DONE]\n\n"
    except Exception as e:
        yield f"data: {json.dumps({'error': str(e)})}\n\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="OpenAI-compatible API server using LiteRT-LM"
    )
    parser.add_argument("--host", type=str, default="0.0.0.0", help="Server host")
    parser.add_argument("--port", type=int, default=8000, help="Server port")
    parser.add_argument(
        "--model", type=str, default=None, help="Model ID or path to .litertlm file"
    )
    parser.add_argument(
        "--download",
        type=str,
        default=None,
        help="Download model from HuggingFace (e.g., gemma-3-1b-it)",
    )
    args = parser.parse_args()

    if args.download:
        MODEL_PATH = download_model(args.download)
        MODEL_ID = args.download
        os.environ["MODEL_PATH"] = MODEL_PATH
        os.environ["MODEL_NAME"] = MODEL_ID
    elif args.model:
        if os.path.exists(args.model):
            MODEL_PATH = args.model
            MODEL_ID = (
                os.path.basename(args.model)
                .replace(".litertlm", "")
                .replace(".task", "")
            )
        else:
            MODEL_ID = args.model
            possible_path = f"{args.model}.litertlm"
            if os.path.exists(possible_path):
                MODEL_PATH = possible_path
        os.environ["MODEL_PATH"] = MODEL_PATH
        os.environ["MODEL_NAME"] = MODEL_ID

    print(f"Starting API server")
    print(f"  Model: {MODEL_ID}")
    print(f"  Path: {MODEL_PATH}")
    print(f"  API: http://{args.host}:{args.port}")
    uvicorn.run(app, host=args.host, port=args.port)
