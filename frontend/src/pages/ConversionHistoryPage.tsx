import { useEffect, useState } from 'react';
import { getHistory, recordConversion } from '../api/conversions';
import { DataGrid, GridToolbar } from '@mui/x-data-grid';
import { Box, TextField, Button, Stack, Paper } from '@mui/material';

export default function ConversionHistoryPage(){
  const [data, setData] = useState<any>({ content: [], totalPages: 0, number: 0, totalElements: 0, size: 10 });
  const [userId, setUserId] = useState<number>(1);
  const [fromCurrency, setFrom] = useState('USD');
  const [toCurrency, setTo] = useState('VND');
  const [amount, setAmount] = useState('100');

  const fetchPage = async (page=0, size=10)=> setData(await getHistory(userId, page, size));
  useEffect(()=>{ fetchPage(0, data.size); /* eslint-disable-next-line */ }, [userId]);

  const submit = async ()=>{
    const resp = await recordConversion({ fromCurrency, toCurrency, amount }, userId);
    await fetchPage(data.number, data.size);
  };

  const cols = [
    { field:'id', headerName:'ID', width:90 },
    { field:'fromCurrency', headerName:'From', width:100 },
    { field:'toCurrency', headerName:'To', width:100 },
    { field:'amount', headerName:'Amount', width:120 },
    { field:'rate', headerName:'Rate', width:120 },
    { field:'result', headerName:'Result', width:140 },
    { field:'requestedAt', headerName:'At', width:200 },
  ];

  return (
    <Stack spacing={2}>
      <Paper sx={{ p:2 }}>
        <Stack direction="row" spacing={2}>
          <TextField label="User ID" value={userId} onChange={(e: React.ChangeEvent<HTMLInputElement>) => setUserId(Number(e.target.value || 1))} size="small"/>
          <TextField label="From" value={fromCurrency} onChange={(e: React.ChangeEvent<HTMLInputElement>) => setFrom(e.target.value)} size="small"/>
          <TextField label="To" value={toCurrency} onChange={(e: React.ChangeEvent<HTMLInputElement>) => setTo(e.target.value)} size="small"/>
          <TextField label="Amount" value={amount} onChange={(e: React.ChangeEvent<HTMLInputElement>) => setAmount(e.target.value)} size="small"/>
          <Button variant="contained" onClick={submit}>Record</Button>
          <Button variant="outlined" onClick={async ()=>{
            const r = await fetch(`/api/conversions/history.csv?userId=${userId}&page=${data.number}&size=100`);
            const b = await r.blob(); const u = URL.createObjectURL(b);
            const a = document.createElement('a'); a.href=u; a.download='history.csv'; a.click(); URL.revokeObjectURL(u);
          }}>Export CSV</Button>
        </Stack>
      </Paper>
      <Box sx={{ height: 520, width: '100%' }}>
        <DataGrid
          rows={data.content || []}
          getRowId={(r: any) => r.id}
          columns={cols}
          rowCount={data.totalElements||0}
          pagination
          paginationMode="server"
          pageSizeOptions={[10,20,50]}
          page={data.number||0}
          onPaginationModelChange={async (m: { pageSize?: number; page?: number })=>{
            const size = m.pageSize||data.size; const page = m.page||0;
            const next = await getHistory(userId, page, size);
            setData(next);
          }}
          slots={{ toolbar: GridToolbar }}
          slotProps={{ toolbar: { showQuickFilter: true, quickFilterProps: { debounceMs: 300 } } }}
        />
      </Box>
    </Stack>
  );
}
