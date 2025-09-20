import { useState } from 'react';
import { changePassword } from '../auth/authApi';
import { Paper, Stack, TextField, Button, Typography, Alert } from '@mui/material';

export default function ChangePasswordPage(){
  const [oldP, setO] = useState('user123');
  const [newP, setN] = useState('Aa@123456');
  const [msg, setMsg] = useState<string|undefined>();
  const submit = async ()=>{ await changePassword(oldP, newP); setMsg('Changed. All sessions revoked.'); };
  return (
    <Paper sx={{ p:3, maxWidth: 480 }}>
      <Stack spacing={2}>
        <Typography variant="h6">Change Password</Typography>
        {msg && <Alert>{msg}</Alert>}
        <TextField type="password" label="Old password" value={oldP} onChange={(e: React.ChangeEvent<HTMLInputElement>) => setO(e.target.value)} size="small"/>
        <TextField type="password" label="New password" value={newP} onChange={(e: React.ChangeEvent<HTMLInputElement>) => setN(e.target.value)} size="small"/>
        <Button variant="contained" onClick={submit}>Change</Button>
      </Stack>
    </Paper>
  );
}
