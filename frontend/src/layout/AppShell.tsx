import { clearAccessToken } from '../auth/apiClient';
import { ReactNode, useMemo, useState } from 'react';
import { AppBar, Toolbar, Typography, IconButton, Button, Container } from '@mui/material';
import Brightness4Icon from '@mui/icons-material/Brightness4';
import Brightness7Icon from '@mui/icons-material/Brightness7';
import { ThemeProvider } from '@mui/material/styles';
import { lightTheme, darkTheme } from '../theme';
import { Link as RouterLink } from 'react-router-dom';
import { Snackbar, Alert } from '@mui/material';
import { useNavigate } from 'react-router-dom';

export default function AppShell({ children }: { children: ReactNode }){
  const [dark, setDark] = useState(false);
  const [sb, setSb] = useState<{open: boolean, msg: string}>({open: false, msg: ''});
  const theme = useMemo(()=> dark? darkTheme : lightTheme, [dark]);
  const navigate = useNavigate();
  return (
    <ThemeProvider theme={theme}>
      <AppBar position="static">
        <Toolbar>
          <Typography variant="h6" sx={{ flexGrow: 1 }}>Converter</Typography>
          <Button color="inherit" component={RouterLink} to="/history">History</Button>
          <Button color="inherit" component={RouterLink} to="/rates">Rates</Button>
          <Button color="inherit" component={RouterLink} to="/jobs">Jobs</Button>
          <Button color="inherit" component={RouterLink} to="/forgot">Forgot</Button>
          <Button color="inherit" component={RouterLink} to="/change-password">Change</Button>
          <Button color="inherit" onClick={async()=>{ await fetch("/api/auth/logout-all",{method:"POST"}); clearAccessToken(); setSb({open:true,msg:"Logged out all devices"}); setTimeout(()=>navigate("/login"),500); }}>Logout all</Button>
          <Button color="inherit" onClick={async()=>{ await fetch("/api/auth/logout",{method:"POST"}); clearAccessToken(); setSb({open:true,msg:'Logged out'}); setTimeout(()=>navigate('/login'),500); }}>Logout</Button>
          <IconButton color="inherit" onClick={()=>setDark(v=>!v)}>
            {dark? <Brightness7Icon/> : <Brightness4Icon/>}
          </IconButton>
        </Toolbar>
      </AppBar>
      <Snackbar open={sb.open} autoHideDuration={2000} onClose={()=>setSb({...sb, open:false})}>
        <Alert severity="success" sx={{ width: '100%' }}>{sb.msg}</Alert>
      </Snackbar>
      <Container sx={{ mt: 3 }}>{children}</Container>
    </ThemeProvider>
  );
}
