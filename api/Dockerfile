FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py .

ENV MODEL_PATH=/models/model.litertlm
ENV MODEL_NAME=gemma-3-1b-it
ENV HOST=0.0.0.0
ENV PORT=8000

EXPOSE 8000

VOLUME ["/models"]

CMD ["python", "main.py", "--model", "/models/model.litertlm"]
