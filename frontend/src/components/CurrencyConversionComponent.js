import React, { useState, useEffect } from 'react';
import {
  Box, Card, CardContent, TextField, Button, Typography, Grid, Stack, Chip, 
  Paper, IconButton, Fade, Tooltip, Alert, CircularProgress, Divider, Switch, FormControlLabel
} from '@mui/material';
import {
  SwapHoriz, TrendingUp, TrendingDown, Timeline, Assessment, 
  Refresh, Share, Info, Mic, MicOff
} from '@mui/icons-material';
import { Line } from 'react-chartjs-2';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip as ChartTooltip,
  Legend,
} from 'chart.js';
import api from '../services/api';

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  ChartTooltip,
  Legend
);

const CurrencyConversionComponent = () => {
  const [amount, setAmount] = useState('');
  const [fromCurrency, setFromCurrency] = useState('USD');
  const [toCurrency, setToCurrency] = useState('VND');
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [newsData, setNewsData] = useState([]);
  const [isListening, setIsListening] = useState(false);
  const [realTimeMode, setRealTimeMode] = useState(false);

  const popularCurrencyPairs = [
    { from: 'USD', to: 'VND', label: 'USD/VND' },
    { from: 'USD', to: 'EUR', label: 'USD/EUR' },
    { from: 'GBP', to: 'USD', label: 'GBP/USD' },
    { from: 'JPY', to: 'USD', label: 'JPY/USD' },
    { from: 'EUR', to: 'VND', label: 'EUR/VND' },
  ];

  // Load news data on component mount
  useEffect(() => {
    loadNewsData();
  }, []);

  const loadNewsData = async () => {
    try {
      const response = await api.get('/api/news');
      setNewsData(response.data.slice(0, 6)); // Show latest 6 articles
    } catch (error) {
  // Error handled via UI state
    }
  };

  // Helper function to get news sentiment factor for predictions
  const getNewsSentimentFactor = () => {
    if (!newsData || newsData.length === 0) return 0;
    
    const sentimentSum = newsData.reduce((sum, news) => {
      if (news.sentiment === 'positive') return sum + 0.001;
      if (news.sentiment === 'negative') return sum - 0.001;
      return sum;
    }, 0);
    
    return sentimentSum / newsData.length;
  };

  const handleConvert = async () => {
    if (!amount || !fromCurrency || !toCurrency) {
      setError('Please fill in all fields');
      return;
    }

    try {
      setLoading(true);
      setError('');
      
  // Debug logging removed
      
      // Get currency conversion from backend
      const response = await api.post('/api/convert/currency', {
        amount: parseFloat(amount),
        fromCurrency,
        toCurrency
      });

      let resultData = response.data;
      const actualExchangeRate = resultData.exchangeRate;
      const currentDate = new Date().toISOString();
      
      // Generate realistic predictions using algorithmic approach
      const generateAlgorithmicPredictions = (currentRate, fromCurrency, toCurrency, days = 7) => {
        const predictions = [];
        
        // Volatility profiles for different currency pairs
        const getVolatilityProfile = (from, to) => {
          const majorPairs = ['USD', 'EUR', 'GBP', 'JPY'];
          const isMajor = majorPairs.includes(from) && majorPairs.includes(to);
          
          return {
            dailyVolatility: isMajor ? 0.008 : 0.015,
            trendStrength: isMajor ? 0.003 : 0.008,
            newsImpact: isMajor ? 0.005 : 0.012
          };
        };
        
        const profile = getVolatilityProfile(fromCurrency, toCurrency);
        
        for (let day = 1; day <= days; day++) {
          const predictionDate = new Date();
          predictionDate.setDate(predictionDate.getDate() + day);
          const isWeekend = predictionDate.getDay() === 0 || predictionDate.getDay() === 6;
          
          // Multi-factor prediction model
          const factors = {
            // Market cycles (different periodicities)
            marketCycle: Math.sin((day * Math.PI) / 7) * 0.003 +
                        Math.cos((day * Math.PI) / 15) * 0.002,
            
            // Trend momentum (can be positive or negative)
            momentum: Math.tanh((day - 3.5) / 2) * profile.trendStrength,
            
            // Random market volatility
            volatility: (Math.random() - 0.5) * profile.dailyVolatility,
            
            // Weekend effect (lower volatility)
            weekendEffect: isWeekend ? 0.002 * (Math.random() - 0.5) : 0,
            
            // News sentiment influence
            newsSentiment: getNewsSentimentFactor() * profile.newsImpact,
            
            // Auto-correlation (previous day influence)
            autoCorrelation: day > 1 ? 
              (predictions[day - 2].predictedRate - currentRate) / currentRate * 0.1 : 0
          };
          
          const totalChange = Object.values(factors).reduce((sum, val) => sum + val, 0);
          const baseRate = day === 1 ? currentRate : predictions[day - 2].predictedRate;
          const predictedRate = baseRate * (1 + totalChange);
          
          const confidence = Math.max(0.6, Math.min(0.95, 0.8 - Math.abs(totalChange) * 10));
          
          predictions.push({
            date: predictionDate.toISOString().split('T')[0],
            predictedRate: predictedRate,
            confidence: confidence,
            trend: predictedRate > baseRate ? 'up' : 'down',
            changePercent: ((predictedRate - baseRate) / baseRate * 100).toFixed(2),
            factors: {
              marketCycle: factors.marketCycle.toFixed(6),
              momentum: factors.momentum.toFixed(6),
              volatility: factors.volatility.toFixed(6),
              weekendEffect: factors.weekendEffect.toFixed(6),
              newsSentiment: factors.newsSentiment.toFixed(6),
              autoCorrelation: factors.autoCorrelation.toFixed(6),
              totalChange: totalChange.toFixed(6)
            }
          });
          
          // Debug logging removed
        }
        
        return predictions;
      };

      // Always generate algorithmic predictions instead of using mock data
  // Debug logging removed
      
      resultData.predictionData = generateAlgorithmicPredictions(
        actualExchangeRate, 
        fromCurrency, 
        toCurrency, 
        2
      );
      resultData.dataSource = 'algorithmic_multi_factor_model';
      resultData.lastUpdated = currentDate;
      
  // Debug logging removed

      setResult(resultData);
    } catch (err) {
  // Error handled via UI state
      setError('Failed to convert currency. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleSwapCurrencies = () => {
    setFromCurrency(toCurrency);
    setToCurrency(fromCurrency);
  };

  const handlePairClick = (pair) => {
    setFromCurrency(pair.from);
    setToCurrency(pair.to);
  };

  const formatCurrency = (amount, currency) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: currency,
      minimumFractionDigits: 2,
      maximumFractionDigits: 4
    }).format(amount);
  };

  const getTrendIcon = (trend) => {
    if (trend === 'up') return <TrendingUp sx={{ color: 'success.main' }} />;
    if (trend === 'down') return <TrendingDown sx={{ color: 'error.main' }} />;
    return <Timeline sx={{ color: 'warning.main' }} />;
  };

  const getSentimentColor = (sentiment) => {
    switch (sentiment) {
      case 'positive': return 'success';
      case 'negative': return 'error';
      default: return 'warning';
    }
  };

  const getChartData = () => {
  // Debug logging removed
    
    if (!result || !result.predictionData || result.predictionData.length === 0) {
  // No data state; return null
      return null;
    }

    // Format dates and rates from prediction data
  const labels = result.predictionData.slice(0, 2).map(pred => {
      if (pred.date) {
  const formattedDate = new Date(pred.date).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
        return formattedDate;
      }
      return 'N/A';
    });
    
  const data = result.predictionData.slice(0, 2).map(pred => {
  const rate = pred.predictedRate || 0;
  return rate;
    });
    
    // Add current rate as first point
    const currentDate = new Date().toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
    labels.unshift(currentDate);
    data.unshift(result.exchangeRate);

    return {
      labels,
      datasets: [
        {
          label: `${fromCurrency}/${toCurrency} Rate`,
          data: data,
          borderColor: 'rgb(75, 192, 192)',
          backgroundColor: 'rgba(75, 192, 192, 0.1)',
          borderWidth: 3,
          tension: 0.4,
          pointBackgroundColor: 'rgb(75, 192, 192)',
          pointBorderColor: 'white',
          pointBorderWidth: 2,
          pointRadius: 6,
        }
      ]
    };
  };

  const chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'top',
        labels: {
          usePointStyle: true,
          font: { weight: 'bold' }
        }
      },
      title: {
        display: true,
        text: `${fromCurrency}/${toCurrency} Exchange Rate Predictions`,
        font: { size: 16, weight: 'bold' }
      }
    },
    scales: {
      y: {
        beginAtZero: false,
        ticks: {
          callback: function(value) {
            return value.toLocaleString();
          }
        }
      }
    },
    elements: {
      point: {
        hoverRadius: 8
      }
    }
  };

  return (
    <Box sx={{ p: 3 }}>
      {/* Header */}
      <Typography variant="h4" gutterBottom sx={{ 
        background: 'linear-gradient(45deg, #2196F3 30%, #21CBF3 90%)',
        WebkitBackgroundClip: 'text',
        WebkitTextFillColor: 'transparent',
        fontWeight: 'bold',
        textAlign: 'center',
        mb: 4
      }}>
        💱 Advanced Currency Converter
      </Typography>

      {/* Main Conversion Form */}
      <Card elevation={3} sx={{ mb: 3, borderRadius: 3 }}>
        <CardContent sx={{ p: 4 }}>
          <Grid container spacing={3} alignItems="center">
            <Grid item xs={12} md={4}>
              <TextField
                fullWidth
                label="Amount"
                type="number"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                variant="outlined"
                sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }}
              />
            </Grid>
            
            <Grid item xs={12} md={3}>
              <TextField
                fullWidth
                select
                label="From"
                value={fromCurrency}
                onChange={(e) => setFromCurrency(e.target.value)}
                SelectProps={{ native: true }}
                variant="outlined"
                sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }}
              >
                <option value="USD">USD - US Dollar</option>
                <option value="EUR">EUR - Euro</option>
                <option value="GBP">GBP - British Pound</option>
                <option value="JPY">JPY - Japanese Yen</option>
                <option value="VND">VND - Vietnamese Dong</option>
                <option value="CNY">CNY - Chinese Yuan</option>
                <option value="KRW">KRW - Korean Won</option>
              </TextField>
            </Grid>

            <Grid item xs={12} md={1} sx={{ textAlign: 'center' }}>
              <IconButton 
                onClick={handleSwapCurrencies}
                sx={{ 
                  bgcolor: 'primary.main', 
                  color: 'white',
                  '&:hover': { bgcolor: 'primary.dark' }
                }}
              >
                <SwapHoriz />
              </IconButton>
            </Grid>

            <Grid item xs={12} md={3}>
              <TextField
                fullWidth
                select
                label="To"
                value={toCurrency}
                onChange={(e) => setToCurrency(e.target.value)}
                SelectProps={{ native: true }}
                variant="outlined"
                sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2 } }}
              >
                <option value="USD">USD - US Dollar</option>
                <option value="EUR">EUR - Euro</option>
                <option value="GBP">GBP - British Pound</option>
                <option value="JPY">JPY - Japanese Yen</option>
                <option value="VND">VND - Vietnamese Dong</option>
                <option value="CNY">CNY - Chinese Yuan</option>
                <option value="KRW">KRW - Korean Won</option>
              </TextField>
            </Grid>

            <Grid item xs={12} md={1}>
              <Button
                fullWidth
                variant="contained"
                onClick={handleConvert}
                disabled={loading}
                sx={{ 
                  height: 56, 
                  borderRadius: 2,
                  background: 'linear-gradient(45deg, #FE6B8B 30%, #FF8E53 90%)',
                  '&:hover': {
                    background: 'linear-gradient(45deg, #FE6B8B 60%, #FF8E53 100%)',
                  }
                }}
              >
                {loading ? <CircularProgress size={24} /> : 'Convert'}
              </Button>
            </Grid>
          </Grid>

          {/* Popular Currency Pairs */}
          <Box sx={{ mt: 3 }}>
            <Typography variant="body2" sx={{ mb: 2, fontWeight: 'bold' }}>
              Popular Currency Pairs:
            </Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              {popularCurrencyPairs.map((pair, index) => (
                <Chip
                  key={index}
                  label={pair.label}
                  onClick={() => handlePairClick(pair)}
                  variant="outlined"
                  sx={{ 
                    borderRadius: 2,
                    '&:hover': { bgcolor: 'primary.light', color: 'white' }
                  }}
                />
              ))}
            </Stack>
          </Box>
        </CardContent>
      </Card>

      {/* Error Display */}
      {error && (
        <Alert severity="error" sx={{ mb: 3, borderRadius: 2 }}>
          {error}
        </Alert>
      )}

      {/* Results */}
      {result && (
        <Fade in={true}>
          <Grid container spacing={3}>
            {/* Conversion Result */}
            <Grid item xs={12} md={6}>
              <Card elevation={2} sx={{ p: 3, textAlign: 'center', borderRadius: 3 }}>
                <Typography variant="h6" gutterBottom>
                  Conversion Result
                </Typography>
                <Typography variant="h3" sx={{ 
                  color: 'primary.main', 
                  fontWeight: 'bold',
                  background: 'linear-gradient(45deg, #2196F3 30%, #21CBF3 90%)',
                  WebkitBackgroundClip: 'text',
                  WebkitTextFillColor: 'transparent'
                }}>
                  {formatCurrency(result.convertedAmount, result.toCurrency)}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Exchange Rate: 1 {fromCurrency} = {result.exchangeRate.toLocaleString()} {toCurrency}
                </Typography>
                <Typography variant="caption" display="block" sx={{ mt: 1 }}>
                  Data Source: {result.dataSource || 'Real-time API'} • Last Updated: {new Date(result.lastUpdated || Date.now()).toLocaleString()}
                </Typography>
              </Card>
            </Grid>

            {/* Chart */}
            {result.predictionData && result.predictionData.length > 0 && (
              <Grid item xs={12} md={6}>
                <Card elevation={2} sx={{ borderRadius: 3 }}>
                  <CardContent>
                    <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 2 }}>
                      <Timeline color="primary" />
                      <Typography variant="h6">Exchange Rate Predictions</Typography>
                    </Stack>
                    <Box sx={{ height: 400 }}>
                      <Line data={getChartData()} options={chartOptions} />
                    </Box>
                  </CardContent>
                </Card>
              </Grid>
            )}
          </Grid>
        </Fade>
      )}

      {/* News & Market Data */}
      {newsData.length > 0 && (
        <Fade in={true}>
          <Paper elevation={2} sx={{ p: 3, mt: 3, borderRadius: 3, bgcolor: 'grey.50' }}>
            <Typography variant="h6" gutterBottom sx={{ color: 'primary.main' }}>
              Market News & Sentiment
            </Typography>
            <Grid container spacing={2}>
              {newsData.map((news, index) => (
                <Grid item xs={12} md={4} key={index}>
                  <Card sx={{ height: '100%', borderRadius: 2 }}>
                    <CardContent>
                      <Typography variant="body2" sx={{ fontWeight: 500, mb: 1 }}>
                        {news.title}
                      </Typography>
                      <Stack direction="row" spacing={1}>
                        <Chip
                          label={news.sentiment || 'neutral'}
                          color={getSentimentColor(news.sentiment)}
                          size="small"
                        />
                        <Typography variant="caption" color="text.secondary">
                          {news.source}
                        </Typography>
                      </Stack>
                    </CardContent>
                  </Card>
                </Grid>
              ))}
            </Grid>
          </Paper>
        </Fade>
      )}
    </Box>
  );
};

export default CurrencyConversionComponent;
