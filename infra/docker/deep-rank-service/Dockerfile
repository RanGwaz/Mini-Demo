FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir --default-timeout=300 --retries=10 \
    -i https://pypi.tuna.tsinghua.edu.cn/simple \
    --extra-index-url https://download.pytorch.org/whl/cu126 \
    -r requirements.txt

COPY app.py ./app.py
COPY reco_features.py ./reco_features.py

EXPOSE 18080

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "18080"]
