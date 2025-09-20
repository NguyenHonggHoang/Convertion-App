import { api } from '../auth/apiClient';
export async function fetchLatest(base: string, symbol: string){
  const r = await api.get(`/api/rates/latest`, { params: { base, symbol } });
  return r.data;
}
export async function upsertRate(base: string, symbol: string, rate: string, source?: string){
  const r = await api.post(`/api/rates/upsert`, { base, symbol, rate, source });
  return r.data;
}
