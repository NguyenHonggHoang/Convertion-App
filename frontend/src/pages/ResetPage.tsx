import { useState, useEffect } from 'react';
import { resetPassword } from '../auth/authApi';
import { Paper, Stack, TextField, Button, Typography, Alert } from '@mui/material';

export default function ResetPage(){
  const [token, setT] = useState('');
  useEffect(()=>{ const q = new URLSearchParams(window.location.search); const t = q.get('token'); if(t) setT(t); },[]);
  const [pass, setP] = useState('Aa@123456');
  const [msg, setMsg] = useState<string|undefined>();
  const reset = async ()=>{ await resetPassword(token, pass); setMsg('Password reset. Now login.'); };
  return (
    <Paper sx={{ p:3, maxWidth: 480 }}>
      <Stack spacing={2}>
        <Typography variant="h6">Reset Password</Typography>
        {msg && <Alert>{msg}</Alert>}
        <TextField label="Token" value={token} onChange={e=>setT(e.target.value)} size="small"/>
        <TextField type="password" label="New password" value={pass} onChange={e=>setP(e.target.value)} size="small"/>
        <Button variant="contained" onClick={reset}>Reset</Button>
      </Stack>
    </Paper>
  );
}
