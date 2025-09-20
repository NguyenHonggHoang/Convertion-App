# Advanced NLP Service with FinBERT, Enhanced Sentiment Analysis and Multi-source News Processing
from flask import Flask, request, jsonify
from flask_cors import CORS
import logging
import os
import re
import html
import unicodedata
import numpy as np
from typing import Dict, List, Tuple, Optional
import json
from datetime import datetime, timedelta

# Advanced NLP imports
try:
    from transformers import AutoTokenizer, AutoModelForSequenceClassification, pipeline
    import torch
    TRANSFORMERS_AVAILABLE = True
except ImportError:
    TRANSFORMERS_AVAILABLE = False
    logging.warning("Transformers not available. Install with: pip install transformers torch")

try:
    import requests
    from bs4 import BeautifulSoup
    WEB_SCRAPING_AVAILABLE = True
except ImportError:
    WEB_SCRAPING_AVAILABLE = False
    logging.warning("Web scraping not available. Install with: pip install requests beautifulsoup4")

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("advanced-nlp-service")

app = Flask(__name__)
CORS(app)

# Configuration
MODEL_CACHE_DIR = os.getenv("MODEL_CACHE_DIR", "/tmp/nlp_models")
os.makedirs(MODEL_CACHE_DIR, exist_ok=True)

class FinBERTAnalyzer:
    """FinBERT-based financial sentiment analysis"""
    
    def __init__(self):
        self.tokenizer = None
        self.model = None
        self.sentiment_pipeline = None
        self._initialize_model()
    
    def _initialize_model(self):
        """Initialize FinBERT model"""
        if not TRANSFORMERS_AVAILABLE:
            logger.warning("FinBERT not available - transformers not installed")
            return
        
        try:
            # Use ProsusAI/finbert for financial sentiment
            model_name = "ProsusAI/finbert"
            
            self.tokenizer = AutoTokenizer.from_pretrained(
                model_name,
                cache_dir=MODEL_CACHE_DIR
            )
            
            self.model = AutoModelForSequenceClassification.from_pretrained(
                model_name,
                cache_dir=MODEL_CACHE_DIR
            )
            
            self.sentiment_pipeline = pipeline(
                "sentiment-analysis",
                model=self.model,
                tokenizer=self.tokenizer,
                device=0 if torch.cuda.is_available() else -1
            )
            
            logger.info("FinBERT model loaded successfully")
            
        except Exception as e:
            logger.error(f"Failed to load FinBERT: {e}")
            self.sentiment_pipeline = None
    
    def analyze_sentiment(self, text: str) -> Tuple[str, float]:
        """
        Analyze sentiment using FinBERT
        Returns: (label, confidence_score)
        """
        if not self.sentiment_pipeline:
            return self._fallback_sentiment(text)
        
        try:
            # Clean and truncate text for FinBERT
            clean_text = self._clean_text(text)
            
            # FinBERT has token limit, truncate if needed
            if len(clean_text) > 512:
                clean_text = clean_text[:512]
            
            result = self.sentiment_pipeline(clean_text)[0]
            
            # Convert FinBERT labels to our format
            label = result['label'].lower()
            score = float(result['score'])
            
            # Map to our scoring system [-1, 1]
            if label == 'positive':
                sentiment_score = score
            elif label == 'negative':
                sentiment_score = -score
            else:  # neutral
                sentiment_score = 0.0
            
            return label, sentiment_score
            
        except Exception as e:
            logger.error(f"FinBERT analysis failed: {e}")
            return self._fallback_sentiment(text)
    
    def _clean_text(self, text: str) -> str:
        """Clean text for FinBERT processing"""
        text = html.unescape(text)
        text = re.sub(r'<[^>]+>', '', text)  # Remove HTML tags
        text = re.sub(r'http[s]?://\S+', '', text)  # Remove URLs
        text = re.sub(r'\s+', ' ', text).strip()
        return text
    
    def _fallback_sentiment(self, text: str) -> Tuple[str, float]:
        """Fallback lexicon-based sentiment when FinBERT fails"""
        positive_words = ["profit", "growth", "increase", "rise", "gain", "bullish", "strong"]
        negative_words = ["loss", "decline", "fall", "drop", "bearish", "weak", "crash"]
        
        text_lower = text.lower()
        pos_score = sum(1 for word in positive_words if word in text_lower)
        neg_score = sum(1 for word in negative_words if word in text_lower)
        
        if pos_score > neg_score:
            return "positive", 0.5
        elif neg_score > pos_score:
            return "negative", -0.5
        else:
            return "neutral", 0.0

class MultiSourceNewsCrawler:
    """Enhanced news crawler supporting multiple sources"""
    
    def __init__(self):
        self.sources = {
            "reuters_finance": {
                "url": "https://www.reuters.com/business/finance/",
                "selector": "article",
                "title_selector": "h3",
                "content_selector": "p"
            },
            "bloomberg": {
                "url": "https://www.bloomberg.com/markets",
                "selector": "article",
                "title_selector": "h3",
                "content_selector": "p"
            },
            "financial_times": {
                "url": "https://www.ft.com/markets", 
                "selector": "article",
                "title_selector": "h3",
                "content_selector": "p"
            }
        }
    
    def crawl_source(self, source_name: str, limit: int = 50) -> List[Dict]:
        """Crawl news from specific source"""
        if not WEB_SCRAPING_AVAILABLE:
            logger.warning("Web scraping not available")
            return []
        
        source_config = self.sources.get(source_name)
        if not source_config:
            logger.warning(f"Unknown source: {source_name}")
            return []
        
        try:
            headers = {
                'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
            }
            
            response = requests.get(source_config["url"], headers=headers, timeout=30)
            response.raise_for_status()
            
            soup = BeautifulSoup(response.content, 'html.parser')
            articles = soup.find_all(source_config["selector"])[:limit]
            
            news_data = []
            for article in articles:
                try:
                    title_elem = article.find(source_config["title_selector"])
                    content_elem = article.find(source_config["content_selector"])
                    
                    title = title_elem.get_text().strip() if title_elem else ""
                    content = content_elem.get_text().strip() if content_elem else ""
                    
                    if title or content:
                        news_data.append({
                            "title": title,
                            "content": content,
                            "source": source_name,
                            "crawled_at": datetime.now().isoformat(),
                            "url": source_config["url"]
                        })
                
                except Exception as e:
                    logger.debug(f"Failed to parse article from {source_name}: {e}")
                    continue
            
            logger.info(f"Crawled {len(news_data)} articles from {source_name}")
            return news_data
            
        except Exception as e:
            logger.error(f"Failed to crawl {source_name}: {e}")
            return []
    
    def crawl_all_sources(self, limit_per_source: int = 20) -> List[Dict]:
        """Crawl news from all configured sources"""
        all_news = []
        
        for source_name in self.sources.keys():
            source_news = self.crawl_source(source_name, limit_per_source)
            all_news.extend(source_news)
        
        # Deduplicate based on title similarity
        unique_news = self._deduplicate_news(all_news)
        
        return unique_news
    
    def _deduplicate_news(self, news_list: List[Dict]) -> List[Dict]:
        """Simple deduplication based on title similarity"""
        unique_news = []
        seen_titles = set()
        
        for news in news_list:
            title_normalized = re.sub(r'\W+', '', news['title'].lower())
            if title_normalized not in seen_titles and len(title_normalized) > 10:
                seen_titles.add(title_normalized)
                unique_news.append(news)
        
        return unique_news

class AdvancedTextProcessor:
    """Enhanced text processing with financial entity recognition"""
    
    def __init__(self):
        # Extended financial categories
        self.categories = {
            "forex": [
                "forex", "fx", "currency", "exchange rate", "dollar", "euro", "yen", "pound",
                "usd", "eur", "jpy", "gbp", "aud", "cad", "chf", "cny", "vnd",
                "central bank", "fed", "ecb", "boj", "intervention", "peg", "float"
            ],
            "equities": [
                "stock", "equity", "share", "nasdaq", "dow", "s&p", "ftse", "nikkei",
                "earnings", "dividend", "ipo", "merger", "acquisition", "buyback",
                "market cap", "pe ratio", "eps", "revenue", "profit"
            ],
            "commodities": [
                "oil", "gold", "silver", "copper", "wheat", "corn", "natural gas",
                "brent", "wti", "commodity", "futures", "opec", "supply", "demand"
            ],
            "crypto": [
                "bitcoin", "ethereum", "crypto", "blockchain", "defi", "nft",
                "btc", "eth", "altcoin", "stablecoin", "mining", "wallet"
            ],
            "macroeconomic": [
                "gdp", "inflation", "cpi", "ppi", "unemployment", "interest rate",
                "recession", "recovery", "growth", "policy", "fiscal", "monetary"
            ]
        }
        
        # Financial entity patterns
        self.currency_pattern = re.compile(r'\b[A-Z]{3}(?:/[A-Z]{3})?\b')
        self.percentage_pattern = re.compile(r'[+-]?\d+(?:\.\d+)?%')
        self.number_pattern = re.compile(r'\$?\d+(?:\.\d+)?(?:[BMK])?')
    
    def extract_financial_entities(self, text: str) -> Dict[str, List]:
        """Extract financial entities from text"""
        entities = {
            "currencies": [],
            "percentages": [],
            "numbers": [],
            "companies": [],
            "indicators": []
        }
        
        # Extract currencies
        currencies = self.currency_pattern.findall(text.upper())
        entities["currencies"] = list(set(currencies))
        
        # Extract percentages
        percentages = self.percentage_pattern.findall(text)
        entities["percentages"] = percentages
        
        # Extract numbers/amounts
        numbers = self.number_pattern.findall(text)
        entities["numbers"] = numbers
        
        # Extract financial indicators (simple keyword matching)
        text_lower = text.lower()
        for category, keywords in self.categories.items():
            found_keywords = [kw for kw in keywords if kw in text_lower]
            if found_keywords:
                entities["indicators"].extend(found_keywords)
        
        return entities
    
    def categorize_advanced(self, text: str) -> Dict[str, float]:
        """Advanced categorization with confidence scores"""
        text_lower = text.lower()
        scores = {}
        
        for category, keywords in self.categories.items():
            score = 0
            for keyword in keywords:
                if keyword in text_lower:
                    # Weight by keyword importance and frequency
                    weight = 2.0 if len(keyword) > 6 else 1.0  # Longer terms more specific
                    frequency = text_lower.count(keyword)
                    score += weight * frequency
            
            # Normalize by text length
            normalized_score = score / max(len(text_lower.split()), 1)
            scores[category] = round(normalized_score, 4)
        
        return scores
    
    def summarize_advanced(self, text: str, max_sentences: int = 3) -> str:
        """Advanced summarization with sentence ranking"""
        sentences = re.split(r'[.!?]+', text)
        sentences = [s.strip() for s in sentences if s.strip()]
        
        if len(sentences) <= max_sentences:
            return text
        
        # Score sentences based on financial keywords and position
        sentence_scores = []
        
        for i, sentence in enumerate(sentences):
            score = 0
            
            # Position weight (earlier sentences more important)
            position_weight = 1.0 - (i / len(sentences)) * 0.3
            
            # Keyword weight
            sentence_lower = sentence.lower()
            for category, keywords in self.categories.items():
                for keyword in keywords:
                    if keyword in sentence_lower:
                        score += 1
            
            # Length normalization (prefer medium-length sentences)
            length_penalty = 0 if 50 <= len(sentence) <= 200 else -0.5
            
            final_score = (score * position_weight) + length_penalty
            sentence_scores.append((sentence, final_score))
        
        # Select top sentences
        sentence_scores.sort(key=lambda x: x[1], reverse=True)
        top_sentences = [s[0] for s in sentence_scores[:max_sentences]]
        
        # Maintain original order
        result_sentences = []
        for sentence in sentences:
            if sentence in top_sentences:
                result_sentences.append(sentence)
        
        return '. '.join(result_sentences) + '.'

# Initialize components
finbert_analyzer = FinBERTAnalyzer()
news_crawler = MultiSourceNewsCrawler()
text_processor = AdvancedTextProcessor()

@app.route('/nlp/finbert', methods=['POST'])
def finbert_sentiment():
    """FinBERT-powered financial sentiment analysis"""
    try:
        data = request.get_json() or {}
        text = data.get('text', '').strip()
        
        if not text:
            return jsonify({'error': 'text is required'}), 400
        
        label, score = finbert_analyzer.analyze_sentiment(text)
        
        return jsonify({
            'text': text,
            'sentiment_label': label,
            'sentiment_score': score,
            'model': 'FinBERT',
            'confidence': abs(score)
        })
        
    except Exception as e:
        logger.exception("FinBERT sentiment analysis failed")
        return jsonify({'error': str(e)}), 500

@app.route('/nlp/advanced_process', methods=['POST'])
def advanced_process():
    """Comprehensive NLP processing with FinBERT and entity extraction"""
    try:
        data = request.get_json() or {}
        title = data.get('title', '').strip()
        content = data.get('content', '').strip()
        
        if not (title or content):
            return jsonify({'error': 'title or content required'}), 400
        
        full_text = f"{title}. {content}".strip('. ')
        
        # FinBERT sentiment analysis
        sentiment_label, sentiment_score = finbert_analyzer.analyze_sentiment(full_text)
        
        # Advanced categorization
        category_scores = text_processor.categorize_advanced(full_text)
        primary_category = max(category_scores, key=category_scores.get) if category_scores else "general"
        
        # Enhanced summarization
        summary = text_processor.summarize_advanced(content or title)
        
        # Financial entity extraction
        entities = text_processor.extract_financial_entities(full_text)
        
        return jsonify({
            'sentiment': {
                'label': sentiment_label,
                'score': sentiment_score,
                'model': 'FinBERT'
            },
            'categorization': {
                'primary_category': primary_category,
                'category_scores': category_scores
            },
            'summary': summary,
            'entities': entities,
            'processing_timestamp': datetime.now().isoformat()
        })
        
    except Exception as e:
        logger.exception("Advanced NLP processing failed")
        return jsonify({'error': str(e)}), 500

@app.route('/nlp/batch_advanced', methods=['POST'])
def batch_advanced_process():
    """Batch processing with advanced NLP"""
    try:
        data = request.get_json() or {}
        articles = data.get('articles', [])
        
        if not articles:
            return jsonify({'error': 'articles list required'}), 400
        
        results = []
        
        for article in articles:
            try:
                title = article.get('title', '').strip()
                content = article.get('content', '').strip()
                article_id = article.get('id')
                
                full_text = f"{title}. {content}".strip('. ')
                
                if full_text:
                    # Process with FinBERT and advanced NLP
                    sentiment_label, sentiment_score = finbert_analyzer.analyze_sentiment(full_text)
                    category_scores = text_processor.categorize_advanced(full_text)
                    primary_category = max(category_scores, key=category_scores.get) if category_scores else "general"
                    summary = text_processor.summarize_advanced(content or title)
                    entities = text_processor.extract_financial_entities(full_text)
                    
                    results.append({
                        'id': article_id,
                        'sentiment': {
                            'label': sentiment_label,
                            'score': sentiment_score
                        },
                        'category': primary_category,
                        'category_scores': category_scores,
                        'summary': summary,
                        'entities': entities
                    })
                else:
                    results.append({
                        'id': article_id,
                        'error': 'Empty content'
                    })
                    
            except Exception as e:
                results.append({
                    'id': article.get('id'),
                    'error': str(e)
                })
        
        return jsonify({
            'results': results,
            'count': len(results),
            'processed_at': datetime.now().isoformat()
        })
        
    except Exception as e:
        logger.exception("Batch advanced processing failed")
        return jsonify({'error': str(e)}), 500

@app.route('/news/crawl_multi', methods=['POST'])
def crawl_multiple_sources():
    """Crawl news from multiple financial sources"""
    try:
        data = request.get_json() or {}
        sources = data.get('sources', list(news_crawler.sources.keys()))
        limit_per_source = int(data.get('limit_per_source', 20))
        
        if isinstance(sources, str):
            sources = [sources]
        
        all_articles = []
        
        for source in sources:
            if source in news_crawler.sources:
                articles = news_crawler.crawl_source(source, limit_per_source)
                all_articles.extend(articles)
        
        # Process articles with FinBERT
        processed_articles = []
        for article in all_articles:
            try:
                full_text = f"{article['title']}. {article['content']}"
                sentiment_label, sentiment_score = finbert_analyzer.analyze_sentiment(full_text)
                
                processed_articles.append({
                    **article,
                    'sentiment_label': sentiment_label,
                    'sentiment_score': sentiment_score,
                    'processed_with': 'FinBERT'
                })
            except Exception as e:
                processed_articles.append({
                    **article,
                    'sentiment_error': str(e)
                })
        
        return jsonify({
            'articles': processed_articles,
            'count': len(processed_articles),
            'sources_crawled': sources,
            'crawled_at': datetime.now().isoformat()
        })
        
    except Exception as e:
        logger.exception("Multi-source crawling failed")
        return jsonify({'error': str(e)}), 500

@app.route('/health', methods=['GET'])
def health():
    status = {
        'status': 'healthy',
        'service': 'advanced-nlp-service',
        'capabilities': []
    }
    
    if TRANSFORMERS_AVAILABLE and finbert_analyzer.sentiment_pipeline:
        status['capabilities'].append('finbert_sentiment')
    
    if WEB_SCRAPING_AVAILABLE:
        status['capabilities'].append('multi_source_crawling')
    
    status['capabilities'].extend(['advanced_categorization', 'entity_extraction', 'enhanced_summarization'])
    
    return jsonify(status)

# Backward compatibility endpoints
@app.route('/nlp/process', methods=['POST'])
def nlp_process_compat():
    """Backward compatible endpoint with enhanced processing"""
    return advanced_process()

@app.route('/nlp/batch', methods=['POST'])
def nlp_batch_compat():
    """Backward compatible batch endpoint"""
    return batch_advanced_process()

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5004, debug=False)
