export function getOrCreateDeviceId(): string{
  const k = 'device_id';
  let v = localStorage.getItem(k);
  if(!v){
    v = crypto.randomUUID ? crypto.randomUUID() : (Date.now() + '-' + Math.random());
    localStorage.setItem(k, v);
  }
  return v;
}
