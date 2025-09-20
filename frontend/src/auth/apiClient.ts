import { getOrCreateDeviceId } from './device';
import { setRolesFromAccessToken } from './roles';
import axios from 'axios';
let accessToken: string | null = null;
let logoutTimer: any;
export const api = axios.create({ baseURL: (process.env.REACT_APP_API_BASE || '/api'), withCredentials: true });
export function getAccessToken(){ return accessToken; }

function parseJwtExpSeconds(token: string): number | null {
	try{
		const base64 = token.split('.')[1];
		const json = atob(base64.replace(/-/g,'+').replace(/_/g,'/'));
		const payload = JSON.parse(json);
		return typeof payload.exp === 'number' ? payload.exp : null;
	}catch{ return null; }
}

function scheduleAutoLogout(token: string){
	if(logoutTimer) clearTimeout(logoutTimer);
	const exp = parseJwtExpSeconds(token);
	if(!exp) return;
	const ms = exp*1000 - Date.now();
	if(ms <= 0){
		logoutNow('expired');
	} else {
		logoutTimer = setTimeout(()=>logoutNow('expired'), ms + 500);
	}
}

export function setAccessToken(t: string){
	accessToken = t; setRolesFromAccessToken(t);
	scheduleAutoLogout(t);
}

api.interceptors.request.use(cfg=>{ if(accessToken){ cfg.headers = cfg.headers||{}; (cfg.headers as any)['Authorization'] = `Bearer ${accessToken}`; } return cfg; });

export function clearAccessToken(){ accessToken = undefined as any; if(logoutTimer) { clearTimeout(logoutTimer); logoutTimer = null; } }

export async function logoutNow(reason?: string){
	try{ await api.post('/auth/logout'); }catch{/* ignore */}
	clearAccessToken();
	try{
		localStorage.removeItem('jwtToken');
		localStorage.removeItem('authToken');
		localStorage.setItem('auth:logout', String(Date.now()));
	}catch{}
	// Redirect to login
	if(typeof window !== 'undefined'){
		const url = '/login' + (reason ? `?reason=${encodeURIComponent(reason)}` : '');
		window.location.assign(url);
	}
}

api.interceptors.response.use(r=>r, err=>{
	if(err?.response?.status === 401){
		logoutNow('unauthorized');
	}
	return Promise.reject(err);
});

// Cross-tab logout sync
if(typeof window !== 'undefined'){
	window.addEventListener('storage', (e)=>{
		if(e.key === 'auth:logout'){
			clearAccessToken();
			try{ setRolesFromAccessToken(''); }catch{}
			window.location.assign('/login');
		}
	});
}

try{ (api as any).defaults = (api as any).defaults || {}; (api as any).defaults.headers = (api as any).defaults.headers || {}; (api as any).defaults.headers.common = { ...(api as any).defaults.headers.common, 'X-Device-Id': getOrCreateDeviceId() }; }catch{};

// Boot: restore token from localStorage if present
try{
	const t = localStorage.getItem('authToken') || localStorage.getItem('jwtToken');
	if(t){ setAccessToken(t); }
}catch{}
