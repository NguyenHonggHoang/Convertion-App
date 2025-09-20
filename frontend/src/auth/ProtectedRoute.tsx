import { Navigate } from 'react-router-dom';
import { getAccessToken, logoutNow } from './apiClient';
import { hasRole } from './roles';
import { ReactNode } from 'react';

export default function ProtectedRoute({ children, role }:{ children: ReactNode, role?: string }){
  const tok = getAccessToken();
  if(!tok) return <Navigate to="/login" replace/>;
  try{
    const payload = JSON.parse(atob(tok.split('.')[1].replace(/-/g,'+').replace(/_/g,'/')));
    if(payload.exp && Date.now()/1000 > payload.exp){ logoutNow('expired'); return <Navigate to="/login" replace/>; }
    if(role && !hasRole(role)) return <Navigate to="/login" replace/>;
  }catch{ return <Navigate to="/login" replace/>; }
  return children;
}
