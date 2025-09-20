import { hasRole } from '../auth/roles';
import { useState } from 'react';
import { fetchLatest, upsertRate } from '../api/rates';
import { Stack, Paper, TextField, Button, Typography } from '@mui/material';

export default function ExchangeRatesPage(){
  const [base, setBase] = useState('USD');
  const [symbol, setSymbol] = useState('VND');
  const [rate, setRate] = useState('');
  const [latest, setLatest] = useState<any>(null);

  const query = async ()=> setLatest(await fetchLatest(base, symbol));
  const save = async ()=> { await upsertRate(base, symbol, rate, 'manual'); await query(); };

  return (
    <Stack spacing={2}>
      <Paper sx={{ p:2 }}>
        <Stack direction="row" spacing={2}>
          <TextField label="Base" value={base} onChange={e=>setBase(e.target.value)} size="small"/>
          <TextField label="Symbol" value={symbol} onChange={e=>setSymbol(e.target.value)} size="small"/>
          <Button variant="contained" onClick={query}>Get latest</Button>
        </Stack>
      </Paper>
      <Paper sx={{ p:2 }}>
        <Stack direction="row" spacing={2}>
          <TextField label="New rate" value={rate} onChange={e=>setRate(e.target.value)} size="small"/>
          {hasRole('ADMIN') && <Button variant="outlined" onClick={save}>Upsert</Button>}
        </Stack>
      </Paper>
      <Typography variant="body2" component="pre">{JSON.stringify(latest, null, 2)}</Typography>
    </Stack>
  );
}
