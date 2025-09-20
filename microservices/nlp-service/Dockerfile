FROM python:3.11-slim

WORKDIR /app

# Install system dependencies for transformers and web scraping
RUN apt-get update && apt-get install -y \
    gcc \
    g++ \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements first for better caching
COPY requirements.txt .

# Install Python dependencies
RUN pip install --no-cache-dir -r requirements.txt

# Copy application
COPY app.py .

# Create model cache directory
RUN mkdir -p /tmp/nlp_models

ENV PYTHONUNBUFFERED=1

# Download FinBERT model during build (optional, for faster startup)
# RUN python -c "from transformers import AutoTokenizer, AutoModelForSequenceClassification; AutoTokenizer.from_pretrained('ProsusAI/finbert'); AutoModelForSequenceClassification.from_pretrained('ProsusAI/finbert')"

# Health check
HEALTHCHECK --interval=30s --timeout=30s --start-period=120s --retries=3 \
    CMD python -c "import requests; requests.get('http://localhost:5004/health')" || exit 1

EXPOSE 5004

CMD ["python", "app.py"]
