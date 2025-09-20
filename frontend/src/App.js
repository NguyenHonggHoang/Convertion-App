import React, { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import api, { bootstrapAuthAutoLogout } from './services/api';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import { Container, Box, Fab, Zoom } from '@mui/material';
import { KeyboardArrowUp } from '@mui/icons-material';

// Import components
import UnitConversionComponent from './components/UnitConversionComponent';
import CurrencyConversionComponent from './components/CurrencyConversionComponent';
import NewsAnalysisComponent from './components/NewsAnalysisComponent';
import UserManagementComponent from './components/UserManagementComponent';
import Navigation from './components/Navigation';
import { AuthProvider } from './context/AuthContext';

// Simple placeholder components for pages that have import issues
const ExchangeRatesPage = () => (
  <Box sx={{ p: 3 }}>
    <h2>Exchange Rates</h2>
    <p>Exchange rates functionality coming soon...</p>
  </Box>
);

const ConversionHistoryPage = () => (
  <Box sx={{ p: 3 }}>
    <h2>Conversion History</h2>
    <p>Conversion history functionality coming soon...</p>
  </Box>
);

function App() {
  const [darkMode, setDarkMode] = useState(false);
  const [showScrollTop, setShowScrollTop] = useState(false);

  // Check for saved theme preference
  useEffect(() => {
    const savedTheme = localStorage.getItem('darkMode');
    if (savedTheme !== null) {
      setDarkMode(JSON.parse(savedTheme));
    }
  }, []);

  // Save theme preference
  useEffect(() => {
    localStorage.setItem('darkMode', JSON.stringify(darkMode));
  }, [darkMode]);

  // Handle scroll to top visibility
  useEffect(() => {
    const handleScroll = () => {
      setShowScrollTop(window.scrollY > 400);
    };

    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  // Ensure auto-logout scheduling is active (in case services loaded after)
  useEffect(()=>{ try{ bootstrapAuthAutoLogout(); }catch{} },[]);

  // Create dynamic theme
  const theme = createTheme({
    palette: {
      mode: darkMode ? 'dark' : 'light',
      primary: {
        main: darkMode ? '#90caf9' : '#1976d2',
        light: darkMode ? '#bbdefb' : '#42a5f5',
        dark: darkMode ? '#1565c0' : '#1565c0',
      },
      secondary: {
        main: darkMode ? '#f48fb1' : '#dc004e',
        light: darkMode ? '#f8bbd9' : '#e91e63',
        dark: darkMode ? '#c2185b' : '#ad1457',
      },
      background: {
        default: darkMode ? '#121212' : '#f5f5f5',
        paper: darkMode ? '#1e1e1e' : '#ffffff',
      },
      text: {
        primary: darkMode ? '#ffffff' : '#000000',
        secondary: darkMode ? '#b0b0b0' : '#666666',
      },
    },
    typography: {
      fontFamily: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',
      h4: {
        fontWeight: 600,
        letterSpacing: '-0.02em',
      },
      h5: {
        fontWeight: 600,
      },
      h6: {
        fontWeight: 600,
      },
    },
    shape: {
      borderRadius: 12,
    },
    components: {
      MuiCard: {
        styleOverrides: {
          root: {
            borderRadius: 16,
            boxShadow: darkMode 
              ? '0 8px 32px rgba(0, 0, 0, 0.3)'
              : '0 8px 32px rgba(0, 0, 0, 0.1)',
          },
        },
      },
      MuiButton: {
        styleOverrides: {
          root: {
            borderRadius: 12,
            textTransform: 'none',
            fontWeight: 600,
            fontSize: '1rem',
            padding: '12px 24px',
          },
        },
      },
      MuiTextField: {
        styleOverrides: {
          root: {
            '& .MuiOutlinedInput-root': {
              borderRadius: 12,
            },
          },
        },
      },
    },
  });

  const scrollToTop = () => {
    window.scrollTo({
      top: 0,
      behavior: 'smooth',
    });
  };

  return (
    <AuthProvider>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <Router>
          <div className="App">
            <Navigation darkMode={darkMode} setDarkMode={setDarkMode} />
            
            <Container maxWidth="xl" sx={{ py: 4 }}>
              <Routes>
                <Route path="/" element={<UnitConversionComponent />} />
                <Route path="/unit-conversion" element={<UnitConversionComponent />} />
                <Route path="/currency-conversion" element={<CurrencyConversionComponent />} />
                <Route path="/exchange-rates" element={<ExchangeRatesPage />} />
                <Route path="/conversion-history" element={<ConversionHistoryPage />} />
                <Route path="/news-analysis" element={<NewsAnalysisComponent />} />
                <Route path="/user-management" element={<UserManagementComponent />} />
                <Route path="/login" element={<UserManagementComponent />} />
              </Routes>
            </Container>

            {/* Scroll to Top Button */}
            <Zoom in={showScrollTop}>
              <Fab
                color="primary"
                size="medium"
                onClick={scrollToTop}
                sx={{
                  position: 'fixed',
                  bottom: 24,
                  right: 24,
                  zIndex: 1000,
                  boxShadow: '0 8px 32px rgba(25, 118, 210, 0.3)',
                }}
              >
                <KeyboardArrowUp />
              </Fab>
            </Zoom>

            {/* Footer */}
            <Box
              component="footer"
              sx={{
                py: 4,
                px: 2,
                mt: 'auto',
                background: darkMode
                  ? 'linear-gradient(135deg, #1a1a2e 0%, #16213e 100%)'
                  : 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                color: 'white',
                textAlign: 'center',
              }}
            >
              <Container maxWidth="lg">
                <Box sx={{ opacity: 0.8 }}>
                  © 2025 Smart Converter Pro. All rights reserved. | 
                  Currency and unit conversion tools.
                </Box>
              </Container>
            </Box>
          </div>
        </Router>
      </ThemeProvider>
    </AuthProvider>
  );
}

export default App; 