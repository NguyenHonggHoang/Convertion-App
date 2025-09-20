import React, { useState, useEffect } from 'react';
import {
  Box,
  Paper,
  Typography,
  Grid,
  Card,
  CardContent,
  Chip,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Button,
  Alert,
  LinearProgress,
  TextField,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions
} from '@mui/material';
import { TrendingUp, TrendingDown, Remove } from '@mui/icons-material';
import api from '../services/api';

const NewsAnalysisComponent = () => {
  const [news, setNews] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('all');
  const [selectedSentiment, setSelectedSentiment] = useState('all');
  const [alertDialogOpen, setAlertDialogOpen] = useState(false);
  const [alertConfig, setAlertConfig] = useState({
    baseCurrency: '',
    targetCurrency: '',
    alertType: 'GREATER_THAN',
    thresholdRate: ''
  });

  const categories = ['all', 'economy', 'forex', 'crypto', 'stocks', 'commodities'];
  const sentiments = ['all', 'positive', 'negative', 'neutral'];
  const currencies = ['USD', 'EUR', 'JPY', 'GBP', 'VND', 'CAD', 'AUD', 'CHF'];

  const [base, setBase] = useState('USD');
  const [quote, setQuote] = useState('EUR');

  useEffect(() => {
    fetchNews();
  }, []);

  const fetchNews = async () => {
    try {
      setLoading(true);
      setError('');
      
      let newsData = [];
      try {
        const backendResponse = await api.get('/api/news');
        if (backendResponse.data && Array.isArray(backendResponse.data)) {
          newsData = backendResponse.data;
        }
      } catch (backendError) {
      }
      
      // If no data from backend, try crawl service directly
      if (newsData.length === 0) {
        try {
          const crawlResponse = await fetch('http://localhost:5003/crawl?window_hours=24&limit=20');
          if (crawlResponse.ok) {
            const crawlData = await crawlResponse.json();
            if (crawlData.news && Array.isArray(crawlData.news)) {
              newsData = crawlData.news.map((item, index) => ({
                id: item.id || index,
                title: item.title || 'No title',
                summary: item.summary || item.text || 'No summary available',
                url: item.url || '#',
                category: item.category || 'general',
                publishedAt: item.published_at || new Date().toISOString(),
                sentimentLabel: item.sentiment_label || 'neutral',
                sentimentScore: item.sentiment_score || 0
              }));
            }
          }
        } catch (crawlError) {
        }
      }
      
      
      setNews(newsData);
    } catch (err) {
      setError('Failed to fetch news');
    } finally {
      setLoading(false);
    }
  };

  const handleRefreshPair = async () => {
    try {
      setLoading(true);
      setError('');
      await api.post(`/api/news/refresh/pair?base=${encodeURIComponent(base)}&quote=${encodeURIComponent(quote)}`);
      await fetchNews();
    } catch (err) {
      setError('Failed to refresh news for pair');
    } finally {
      setLoading(false);
    }
  };

  const getSentimentIcon = (sentiment) => {
    switch (sentiment) {
      case 'positive':
        return <TrendingUp color="success" />;
      case 'negative':
        return <TrendingDown color="error" />;
      default:
        return <Remove color="action" />;
    }
  };

  const getSentimentColor = (sentiment) => {
    switch (sentiment) {
      case 'positive':
        return 'success';
      case 'negative':
        return 'error';
      default:
        return 'default';
    }
  };

  const filteredNews = news.filter(article => {
    const categoryMatch = selectedCategory === 'all' || article.category === selectedCategory;
    const sentimentMatch = selectedSentiment === 'all' || article.sentimentLabel === selectedSentiment;
    return categoryMatch && sentimentMatch;
  });

  const handleCreateAlert = () => {
    setAlertDialogOpen(true);
  };

  const handleAlertSubmit = async () => {
    try {
      const token = localStorage.getItem('authToken');
      await api.post('/api/alerts', alertConfig, {
        headers: { Authorization: `Bearer ${token}` }
      });
      setAlertDialogOpen(false);
      setAlertConfig({
        baseCurrency: '',
        targetCurrency: '',
        alertType: 'GREATER_THAN',
        thresholdRate: ''
      });
    } catch (err) {
      setError('Failed to create alert');
    }
  };

  return (
    <Box sx={{ maxWidth: 1200, mx: 'auto' }}>
      <Typography variant="h4" gutterBottom>
        News Analysis & Alerts
      </Typography>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} md={3}>
            <FormControl fullWidth>
              <InputLabel>Category</InputLabel>
              <Select
                value={selectedCategory}
                onChange={(e) => setSelectedCategory(e.target.value)}
                label="Category"
              >
                {categories.map((category) => (
                  <MenuItem key={category} value={category}>
                    {category.charAt(0).toUpperCase() + category.slice(1)}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>
          
          <Grid item xs={12} md={3}>
            <FormControl fullWidth>
              <InputLabel>Sentiment</InputLabel>
              <Select
                value={selectedSentiment}
                onChange={(e) => setSelectedSentiment(e.target.value)}
                label="Sentiment"
              >
                {sentiments.map((sentiment) => (
                  <MenuItem key={sentiment} value={sentiment}>
                    {sentiment.charAt(0).toUpperCase() + sentiment.slice(1)}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>

          <Grid item xs={12} md={2}>
            <FormControl fullWidth>
              <InputLabel>Base</InputLabel>
              <Select value={base} onChange={(e) => setBase(e.target.value)} label="Base">
                {currencies.map((c) => (
                  <MenuItem key={c} value={c}>{c}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>

          <Grid item xs={12} md={2}>
            <FormControl fullWidth>
              <InputLabel>Quote</InputLabel>
              <Select value={quote} onChange={(e) => setQuote(e.target.value)} label="Quote">
                {currencies.map((c) => (
                  <MenuItem key={c} value={c}>{c}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </Grid>
          
          <Grid item xs={12} md={2}>
            <Button
              variant="contained"
              onClick={handleRefreshPair}
              fullWidth
            >
              Refresh Pair
            </Button>
          </Grid>
        </Grid>
      </Paper>

      {loading && (
        <Box sx={{ mb: 2 }}>
          <LinearProgress />
        </Box>
      )}

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      <Grid container spacing={3}>
        {filteredNews.map((article) => (
          <Grid item xs={12} md={6} lg={4} key={article.id}>
            <Card>
              <CardContent>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 2 }}>
                  <Chip
                    label={article.category}
                    size="small"
                    color="primary"
                    variant="outlined"
                  />
                  <Box sx={{ display: 'flex', alignItems: 'center' }}>
                    {getSentimentIcon(article.sentimentLabel)}
                    <Chip
                      label={article.sentimentLabel}
                      size="small"
                      color={getSentimentColor(article.sentimentLabel)}
                      sx={{ ml: 1 }}
                    />
                  </Box>
                </Box>
                
                <Typography variant="h6" gutterBottom>
                  {article.title}
                </Typography>
                
                <Typography variant="body2" color="text.secondary" paragraph>
                  {article.summary}
                </Typography>
                
                <Typography variant="caption" color="text.secondary">
                  {new Date(article.publishedAt).toLocaleDateString()}
                </Typography>
                
                <Box sx={{ mt: 2 }}>
                  <Button
                    size="small"
                    href={article.url}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Read More
                  </Button>
                </Box>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      {/* Alert Configuration Dialog */}
      <Dialog open={alertDialogOpen} onClose={() => setAlertDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Create Exchange Rate Alert</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 1 }}>
            <Grid item xs={12} md={6}>
              <TextField
                fullWidth
                label="Base Currency"
                value={alertConfig.baseCurrency}
                onChange={(e) => setAlertConfig({...alertConfig, baseCurrency: e.target.value})}
                placeholder="USD"
              />
            </Grid>
            <Grid item xs={12} md={6}>
              <TextField
                fullWidth
                label="Target Currency"
                value={alertConfig.targetCurrency}
                onChange={(e) => setAlertConfig({...alertConfig, targetCurrency: e.target.value})}
                placeholder="EUR"
              />
            </Grid>
            <Grid item xs={12} md={6}>
              <FormControl fullWidth>
                <InputLabel>Alert Type</InputLabel>
                <Select
                  value={alertConfig.alertType}
                  onChange={(e) => setAlertConfig({...alertConfig, alertType: e.target.value})}
                  label="Alert Type"
                >
                  <MenuItem value="GREATER_THAN">Greater Than</MenuItem>
                  <MenuItem value="LESS_THAN">Less Than</MenuItem>
                </Select>
              </FormControl>
            </Grid>
            <Grid item xs={12} md={6}>
              <TextField
                fullWidth
                label="Threshold Rate"
                type="number"
                value={alertConfig.thresholdRate}
                onChange={(e) => setAlertConfig({...alertConfig, thresholdRate: e.target.value})}
                placeholder="0.85"
              />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAlertDialogOpen(false)}>Cancel</Button>
          <Button onClick={handleAlertSubmit} variant="contained">Create Alert</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default NewsAnalysisComponent; 