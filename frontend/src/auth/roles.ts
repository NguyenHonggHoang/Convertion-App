let cachedRoles: string[] = [];
export function setRolesFromAccessToken(token?: string){
  cachedRoles = [];
  if(!token) return;
  try{
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g,'+').replace(/_/g,'/')));
    const r = payload.roles || [];
    cachedRoles = Array.isArray(r) ? r : [];
  }catch{}
}
export function hasRole(role: string){ return cachedRoles.includes(role) || cachedRoles.includes('ROLE_'+role); }
export function roles(){ return cachedRoles.slice(); }
