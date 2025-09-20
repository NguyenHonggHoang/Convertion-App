import axios from 'axios';

const apiBaseUrl = process.env.REACT_APP_API_URL || '';

let logoutTimer = null;

function parseJwtExpSeconds(token) {
  try {
    const base64 = token.split('.')[1];
    const json = atob(base64.replace(/-/g, '+').replace(/_/g, '/'));
    const payload = JSON.parse(json);
    return typeof payload.exp === 'number' ? payload.exp : null;
  } catch {
    return null;
  }
}

function scheduleAutoLogoutFromToken(token) {
  if (logoutTimer) { clearTimeout(logoutTimer); logoutTimer = null; }
  const exp = parseJwtExpSeconds(token);
  if (!exp) return;
  const ms = exp * 1000 - Date.now();
  if (ms <= 0) {
    doLogout('expired');
  } else {
    logoutTimer = setTimeout(() => doLogout('expired'), ms + 500);
  }
}

export function bootstrapAuthAutoLogout() {
  try {
    const t = localStorage.getItem('authToken') || localStorage.getItem('jwtToken');
    if (t) scheduleAutoLogoutFromToken(t);
  } catch {}
}

function doLogout(reason) {
  try {
    localStorage.removeItem('jwtToken');
    localStorage.removeItem('authToken');
    localStorage.removeItem('userInfo');
    localStorage.setItem('auth:logout', String(Date.now()));
  } catch {}
  if (typeof window !== 'undefined') {
    const url = '/login' + (reason ? `?reason=${encodeURIComponent(reason)}` : '');
    window.location.assign(url);
  }
}

const api = axios.create({
  baseURL: apiBaseUrl,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});


api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('jwtToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Add a response interceptor to handle token expiration
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Token expired/invalid: clear and redirect
      doLogout('unauthorized');
    }
    return Promise.reject(error);
  }
);

// Cross-tab logout sync
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (e) => {
    if (e.key === 'auth:logout') {
      if (logoutTimer) { clearTimeout(logoutTimer); logoutTimer = null; }
      window.location.assign('/login');
    }
  });
}

// Bootstrap scheduling from any existing token at startup
bootstrapAuthAutoLogout();

export default api;


