import React, { useState, useEffect } from 'react';
import {
  AppBar,
  Toolbar,
  Tabs,
  Tab,
  Box,
  IconButton,
  Typography,
  Badge,
  Avatar,
  Menu,
  MenuItem,
  Divider,
  Switch,
  FormControlLabel,
  useTheme,
  alpha
} from '@mui/material';
import {
  Science,
  CurrencyExchange,
  TrendingUp,
  Article,
  Person,
  Notifications,
  Settings,
  DarkMode,
  LightMode,
  Language,
  Help
} from '@mui/icons-material';
import { useNavigate, useLocation } from 'react-router-dom';

function Navigation({ darkMode, setDarkMode }) {
  const navigate = useNavigate();
  const location = useLocation();
  const theme = useTheme();
  const [anchorEl, setAnchorEl] = useState(null);
  const [notificationCount, setNotificationCount] = useState(3);

  const handleChange = (event, newValue) => {
    const routes = ['/unit-conversion', '/currency-conversion', '/news-analysis', '/user-management'];
    navigate(routes[newValue]);
  };

  const getCurrentTab = () => {
    const routes = ['/unit-conversion', '/currency-conversion', '/news-analysis', '/user-management'];
    const currentPath = location.pathname;
    const index = routes.findIndex(route => route === currentPath);
    return index >= 0 ? index : 0;
  };

  const handleMenuOpen = (event) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const handleThemeToggle = () => {
    setDarkMode(!darkMode);
  };

  useEffect(() => {
    // Simulate notification updates
    const interval = setInterval(() => {
      setNotificationCount(Math.floor(Math.random() * 5));
    }, 30000);
    return () => clearInterval(interval);
  }, []);

  return (
    <AppBar 
      position="sticky" 
      elevation={0}
      sx={{
        background: darkMode 
          ? 'linear-gradient(135deg, #1a1a2e 0%, #16213e 100%)'
          : 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
        borderBottom: `1px solid ${alpha(theme.palette.divider, 0.1)}`
      }}
    >
      <Toolbar sx={{ justifyContent: 'space-between', minHeight: 80 }}>
        {/* Logo and Brand */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Science sx={{ fontSize: 32, color: 'white' }} />
          <Typography 
            variant="h5" 
            sx={{ 
              fontWeight: 700, 
              color: 'white',
              background: 'linear-gradient(45deg, #fff 30%, #f0f0f0 90%)',
              backgroundClip: 'text',
              textFillColor: 'transparent',
              WebkitBackgroundClip: 'text',
              WebkitTextFillColor: 'transparent'
            }}
          >
            Smart Converter Pro
          </Typography>
        </Box>

        {/* Navigation Tabs */}
        <Box sx={{ flexGrow: 1, display: 'flex', justifyContent: 'center' }}>
          <Tabs
            value={getCurrentTab()}
            onChange={handleChange}
            sx={{
              '& .MuiTab-root': {
                color: 'rgba(255, 255, 255, 0.7)',
                fontWeight: 500,
                textTransform: 'none',
                fontSize: '1rem',
                minHeight: 48,
                '&:hover': {
                  color: 'white',
                  backgroundColor: alpha(theme.palette.common.white, 0.1)
                }
              },
              '& .Mui-selected': {
                color: 'white !important',
                backgroundColor: alpha(theme.palette.common.white, 0.15),
                borderRadius: 2
              },
              '& .MuiTabs-indicator': {
                backgroundColor: 'white',
                height: 3,
                borderRadius: 2
              }
            }}
          >
            <Tab
              icon={<Science />}
              label="Unit Converter"
              iconPosition="start"
              sx={{ mx: 0.5 }}
            />
            <Tab
              icon={<CurrencyExchange />}
              label="Currency Exchange"
              iconPosition="start"
              sx={{ mx: 0.5 }}
            />
            <Tab
              icon={<Article />}
              label="Market News"
              iconPosition="start"
              sx={{ mx: 0.5 }}
            />
            <Tab
              icon={<Person />}
              label="Account"
              iconPosition="start"
              sx={{ mx: 0.5 }}
            />
          </Tabs>
        </Box>

        {/* Right Side Actions */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          {/* Theme Toggle */}
          <IconButton
            onClick={handleThemeToggle}
            sx={{
              color: 'white',
              bgcolor: alpha(theme.palette.common.white, 0.1),
              '&:hover': {
                bgcolor: alpha(theme.palette.common.white, 0.2),
                transform: 'scale(1.05)'
              },
              transition: 'all 0.3s ease'
            }}
          >
            {darkMode ? <LightMode /> : <DarkMode />}
          </IconButton>

          {/* Notifications */}
          <IconButton
            sx={{
              color: 'white',
              bgcolor: alpha(theme.palette.common.white, 0.1),
              '&:hover': {
                bgcolor: alpha(theme.palette.common.white, 0.2)
              }
            }}
          >
            <Badge badgeContent={notificationCount} color="error">
              <Notifications />
            </Badge>
          </IconButton>

          {/* Language */}
          <IconButton
            sx={{
              color: 'white',
              bgcolor: alpha(theme.palette.common.white, 0.1),
              '&:hover': {
                bgcolor: alpha(theme.palette.common.white, 0.2)
              }
            }}
          >
            <Language />
          </IconButton>

          {/* User Menu */}
          <IconButton
            onClick={handleMenuOpen}
            sx={{
              p: 0,
              ml: 1,
              border: '2px solid rgba(255,255,255,0.3)',
              '&:hover': {
                border: '2px solid white'
              }
            }}
          >
            <Avatar
              sx={{
                width: 40,
                height: 40,
                bgcolor: alpha(theme.palette.common.white, 0.2),
                color: 'white'
              }}
            >
              A
            </Avatar>
          </IconButton>

          <Menu
            anchorEl={anchorEl}
            open={Boolean(anchorEl)}
            onClose={handleMenuClose}
            PaperProps={{
              elevation: 8,
              sx: {
                mt: 1.5,
                minWidth: 200,
                borderRadius: 2,
                '& .MuiMenuItem-root': {
                  px: 2,
                  py: 1.5,
                  borderRadius: 1,
                  mx: 1,
                  '&:hover': {
                    bgcolor: alpha(theme.palette.primary.main, 0.1)
                  }
                }
              }
            }}
          >
            <MenuItem onClick={handleMenuClose}>
              <Person sx={{ mr: 2 }} />
              Profile Settings
            </MenuItem>
            <MenuItem onClick={handleMenuClose}>
              <Settings sx={{ mr: 2 }} />
              Preferences
            </MenuItem>
            <MenuItem onClick={handleMenuClose}>
              <Help sx={{ mr: 2 }} />
              Help & Support
            </MenuItem>
            <Divider sx={{ my: 1 }} />
            <MenuItem>
              <FormControlLabel
                control={
                  <Switch
                    checked={darkMode}
                    onChange={handleThemeToggle}
                    size="small"
                  />
                }
                label="Dark Mode"
                sx={{ '& .MuiFormControlLabel-label': { fontSize: '0.875rem' } }}
              />
            </MenuItem>
          </Menu>
        </Box>
      </Toolbar>
    </AppBar>
  );
}

export default Navigation; 