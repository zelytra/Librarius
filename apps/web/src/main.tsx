import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from 'react-oidc-context';
import { QueryClientProvider } from '@tanstack/react-query';
import App from './App.tsx';
import { ThemeProvider } from './shared/theme/ThemeProvider';
import { AuthTokenBridge } from './shared/AuthTokenBridge';
import { createQueryClient } from './shared/queryClient';
import { oidcConfig } from './auth/oidc';
import './i18n';
import './shared/styles/global.css';

const queryClient = createQueryClient();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider {...oidcConfig}>
      <QueryClientProvider client={queryClient}>
        {/* Publishes the session to the API client; renders nothing. */}
        <AuthTokenBridge />
        <ThemeProvider>
          <BrowserRouter>
            <App />
          </BrowserRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </AuthProvider>
  </StrictMode>,
);
