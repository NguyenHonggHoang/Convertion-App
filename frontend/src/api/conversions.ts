import { api } from '../auth/apiClient';
export async function getHistory(userId: number, page=0, size=20){
  const r = await api.get(`/api/conversions/history`, { params: { userId, page, size } });
  return r.data;
}
export async function recordConversion(payload: any, userId: number){
  const r = await api.post(`/api/conversions/record`, payload, { params: { userId } });
  return r.data;
}
