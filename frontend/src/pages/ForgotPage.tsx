import { useState } from 'react';
import { forgotPassword } from '../auth/authApi';
import { Paper, Stack, TextField, Button, Typography, Alert } from '@mui/material';
import Captcha from '../components/Captcha';

export default function ForgotPage(){
  const [u, setU] = useState('user');
  const [msg, setMsg] = useState<string|undefined>();
  const [captchaToken, setCT] = useState<string>('');
  const submit = async ()=>{
    await forgotPassword(u /* usernameOrEmail */);
    setMsg('If account exists, we sent an email.');
  };
  return (
    <Paper sx={{ p:3, maxWidth: 480 }}>
      <Stack spacing={2}>
        <Typography variant="h6">Forgot Password</Typography>
        {msg && <Alert>{msg}</Alert>}
        <TextField label="Username or Email" value={u} onChange={(e: React.ChangeEvent<HTMLInputElement>)=>setU(e.target.value)} size="small"/>
  <Captcha onVerify={setCT} />
        <Button variant="contained" onClick={submit}>Request reset</Button>
      </Stack>
    </Paper>
  );
}
