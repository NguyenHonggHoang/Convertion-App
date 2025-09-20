import React, { useState, useRef } from 'react';
import {
  Box,
  Paper,
  Typography,
  Grid,
  TextField,
  Button,
  Alert,
  Card,
  CardContent,
  Tabs,
  Tab,
  Divider
} from '@mui/material';
import { Person, Login, PersonAdd } from '@mui/icons-material';
import api from '../services/api';
import CaptchaComponent from './Captcha';

const UserManagementComponent = () => {
  // Removed unused login/logout from context to satisfy eslint no-unused-vars
  const [activeTab, setActiveTab] = useState(0);
  
  
  const [loginData, setLoginData] = useState({ username: '', password: '' });
  const [registerData, setRegisterData] = useState({
    username: '',
    password: '',
    email: '',
    fullName: ''
  });
  const [userProfile, setUserProfile] = useState({
    email: '',
    fullName: ''
  });
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  
  // reCAPTCHA states
  const [showLoginCaptcha, setShowLoginCaptcha] = useState(false);
  const [showRegisterCaptcha, setShowRegisterCaptcha] = useState(false);
  const [loginCaptchaToken, setLoginCaptchaToken] = useState(null);
  const [registerCaptchaToken, setRegisterCaptchaToken] = useState(null);
  
  // reCAPTCHA refs
  const loginCaptchaRef = useRef(null);
  const registerCaptchaRef = useRef(null);

  // Simple check: if token exists in localStorage, user is authenticated
  const isAuthenticated = localStorage.getItem('authToken') || localStorage.getItem('jwtToken');

  const handleTabChange = (event, newValue) => {
    setActiveTab(newValue);
    setError('');
    setSuccess('');
  };

  const handleLogin = async (e) => {
    e.preventDefault();
  // Removed debug logs
    
    try {
      setError('');
      
      // Prepare login payload
      const loginPayload = { ...loginData };
      
      // Add reCAPTCHA token if available
      if (showLoginCaptcha && loginCaptchaToken) {
        loginPayload.captchaToken = loginCaptchaToken;
      }
      
      const response = await api.post('/api/auth/login', loginPayload);
      
      // Handle different response formats from backend
      const token = response.data.access_token || response.data.jwtToken;
      
      if (token) {
        // Store token in localStorage for backend API calls
        localStorage.setItem('authToken', token);
        localStorage.setItem('jwtToken', token);
        
        setSuccess('Login successful! Redirecting...');
        
        // Reset reCAPTCHA states on success
        setShowLoginCaptcha(false);
        setLoginCaptchaToken(null);
        
        // Simple solution: reload page after successful login
        setTimeout(() => {
          window.location.reload();
        }, 1000);
      } else {
        setError('Login failed: No token received from server');
      }
      
    } catch (err) {
  // Swallow console logs; surface via UI
      
      // Check if reCAPTCHA is required (429 status)
      if (err.response?.status === 429) {
  const errorData = err.response?.data;
        
        if (errorData?.error === 'captcha_required') {
          setShowLoginCaptcha(true);
          setError('Please complete the verification below to continue.');
          // Show captcha component
        } else if (errorData?.error === 'invalid_captcha') {
          setError('reCAPTCHA verification failed. Please try again.');
          // Reset and show reCAPTCHA again
          if (loginCaptchaRef.current) {
            loginCaptchaRef.current.reset();
          }
          setLoginCaptchaToken(null);
        } else {
          setError('Too many attempts. Please try again later.');
        }
      } else {
        setError(err.response?.data?.message || err.response?.data?.error || 'Login failed');
      }
    }
  };

  const handleRegister = async (e) => {
    e.preventDefault();
    try {
      setError('');
      
      // Prepare registration payload
      const registerPayload = { ...registerData };
      
      // Add reCAPTCHA token if available
      if (showRegisterCaptcha && registerCaptchaToken) {
        registerPayload.captchaToken = registerCaptchaToken;
      }
      
      const response = await api.post('/api/auth/register', registerPayload);
      
      // Handle different response formats from backend
      const token = response.data.access_token || response.data.jwtToken;
      
      if (token) {
        // Store token in localStorage for backend API calls
        localStorage.setItem('authToken', token);
        localStorage.setItem('jwtToken', token);
        
        setSuccess('Registration successful! Redirecting...');
        
        // Reset reCAPTCHA states on success
        setShowRegisterCaptcha(false);
        setRegisterCaptchaToken(null);
        
        // Simple solution: reload page after successful registration
        setTimeout(() => {
          window.location.reload();
        }, 1000);
      } else {
        setError('Registration failed: No token received from server');
      }
      
    } catch (err) {
  // Swallow console logs; surface via UI
      
      // Check if reCAPTCHA is required (429 status)
      if (err.response?.status === 429) {
  const errorData = err.response?.data;
        
        if (errorData?.error === 'captcha_required') {
          setShowRegisterCaptcha(true);
          setError('Please complete the verification below to continue.');
          // Show captcha component
        } else if (errorData?.error === 'invalid_captcha') {
          setError('reCAPTCHA verification failed. Please try again.');
          // Reset and show reCAPTCHA again
          if (registerCaptchaRef.current) {
            registerCaptchaRef.current.reset();
          }
          setRegisterCaptchaToken(null);
        } else {
          setError('Too many attempts. Please try again later.');
        }
      } else {
        setError(err.response?.data?.message || err.response?.data?.error || 'Registration failed');
      }
    }
  };

  const handleLogout = async () => {
    // Call backend to blacklist current access token (if present)
    try {
  await api.post('/api/auth/logout');
    } catch (e) {
      // Ignore failures and proceed to client-side cleanup
    }

    // Clear all auth data on client
    localStorage.removeItem('jwtToken');
    localStorage.removeItem('authToken');
    localStorage.removeItem('userId');
    localStorage.removeItem('username');
    localStorage.removeItem('userInfo');

    setSuccess('Logged out successfully');

    // Reload page to reset auth state
    setTimeout(() => {
      window.location.reload();
    }, 800);
  };

  const handleUpdateProfile = async (e) => {
    e.preventDefault();
    try {
      setError('');
      const token = localStorage.getItem('authToken');
      await api.put('/api/users/me', userProfile, {
        headers: { Authorization: `Bearer ${token}` }
      });
      
      setSuccess('Profile updated successfully!');
      setUserProfile({ email: '', fullName: '' });
    } catch (err) {
      setError(err.response?.data?.message || 'Profile update failed');
    }
  };

  // reCAPTCHA handlers
  const handleLoginCaptchaChange = (token) => {
    setLoginCaptchaToken(token);
  };

  const handleLoginCaptchaExpired = () => {
    setLoginCaptchaToken(null);
  };

  const handleRegisterCaptchaChange = (token) => {
    setRegisterCaptchaToken(token);
  };

  const handleRegisterCaptchaExpired = () => {
    setRegisterCaptchaToken(null);
  };

  return (
    <Box sx={{ maxWidth: 800, mx: 'auto' }}>
      <Typography variant="h4" gutterBottom>
        User Management
      </Typography>

      {!isAuthenticated ? (
        <Paper sx={{ p: 3 }}>
          <Tabs value={activeTab} onChange={handleTabChange} centered>
            <Tab icon={<Login />} label="Login" />
            <Tab icon={<PersonAdd />} label="Register" />
          </Tabs>

          <Divider sx={{ my: 2 }} />

          {activeTab === 0 && (
            <Box component="form" onSubmit={handleLogin}>
              <Grid container spacing={2}>
                <Grid item xs={12}>
                  <TextField
                    fullWidth
                    label="Username"
                    value={loginData.username}
                    onChange={(e) => setLoginData({...loginData, username: e.target.value})}
                    required
                  />
                </Grid>
                <Grid item xs={12}>
                  <TextField
                    fullWidth
                    label="Password"
                    type="password"
                    value={loginData.password}
                    onChange={(e) => setLoginData({...loginData, password: e.target.value})}
                    required
                  />
                </Grid>
                
                {/* reCAPTCHA for Login */}
                {showLoginCaptcha && (
                  <Grid item xs={12}>
                    <CaptchaComponent
                      ref={loginCaptchaRef}
                      onCaptchaChange={handleLoginCaptchaChange}
                      onExpired={handleLoginCaptchaExpired}
                    />
                  </Grid>
                )}
                
                <Grid item xs={12}>
                  <Button
                    type="submit"
                    variant="contained"
                    fullWidth
                    startIcon={<Login />}
                    disabled={showLoginCaptcha && !loginCaptchaToken}
                  >
                    Login
                  </Button>
                </Grid>
              </Grid>
            </Box>
          )}

          {activeTab === 1 && (
            <Box component="form" onSubmit={handleRegister}>
              <Grid container spacing={2}>
                <Grid item xs={12}>
                  <TextField
                    fullWidth
                    label="Username"
                    value={registerData.username}
                    onChange={(e) => setRegisterData({...registerData, username: e.target.value})}
                    required
                  />
                </Grid>
                <Grid item xs={12}>
                  <TextField
                    fullWidth
                    label="Email"
                    type="email"
                    value={registerData.email}
                    onChange={(e) => setRegisterData({...registerData, email: e.target.value})}
                    required
                  />
                </Grid>
                <Grid item xs={12}>
                  <TextField
                    fullWidth
                    label="Full Name"
                    value={registerData.fullName}
                    onChange={(e) => setRegisterData({...registerData, fullName: e.target.value})}
                  />
                </Grid>
                <Grid item xs={12}>
                  <TextField
                    fullWidth
                    label="Password"
                    type="password"
                    value={registerData.password}
                    onChange={(e) => setRegisterData({...registerData, password: e.target.value})}
                    required
                  />
                </Grid>
                
                {/* reCAPTCHA for Registration */}
                {showRegisterCaptcha && (
                  <Grid item xs={12}>
                    <CaptchaComponent
                      ref={registerCaptchaRef}
                      onCaptchaChange={handleRegisterCaptchaChange}
                      onExpired={handleRegisterCaptchaExpired}
                    />
                  </Grid>
                )}
                
                <Grid item xs={12}>
                  <Button
                    type="submit"
                    variant="contained"
                    fullWidth
                    startIcon={<PersonAdd />}
                    disabled={showRegisterCaptcha && !registerCaptchaToken}
                  >
                    Register
                  </Button>
                </Grid>
              </Grid>
            </Box>
          )}
        </Paper>
      ) : (
        <Grid container spacing={3}>
          <Grid item xs={12} md={6}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  <Person sx={{ mr: 1, verticalAlign: 'middle' }} />
                  User Profile
                </Typography>
                <Typography variant="body2" color="text.secondary" gutterBottom>
                  Welcome, {localStorage.getItem('username')}!
                </Typography>
                <Button
                  variant="outlined"
                  onClick={handleLogout}
                  sx={{ mt: 2 }}
                >
                  Logout
                </Button>
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12} md={6}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Update Profile
                </Typography>
                <Box component="form" onSubmit={handleUpdateProfile}>
                  <Grid container spacing={2}>
                    <Grid item xs={12}>
                      <TextField
                        fullWidth
                        label="Email"
                        type="email"
                        value={userProfile.email}
                        onChange={(e) => setUserProfile({...userProfile, email: e.target.value})}
                      />
                    </Grid>
                    <Grid item xs={12}>
                      <TextField
                        fullWidth
                        label="Full Name"
                        value={userProfile.fullName}
                        onChange={(e) => setUserProfile({...userProfile, fullName: e.target.value})}
                      />
                    </Grid>
                    <Grid item xs={12}>
                      <Button
                        type="submit"
                        variant="contained"
                        fullWidth
                      >
                        Update Profile
                      </Button>
                    </Grid>
                  </Grid>
                </Box>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      )}

      {error && (
        <Alert severity="error" sx={{ mt: 2 }}>
          {error}
        </Alert>
      )}

      {success && (
        <Alert severity="success" sx={{ mt: 2 }}>
          {success}
        </Alert>
      )}
    </Box>
  );
};

export default UserManagementComponent; 