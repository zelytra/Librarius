import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import { AuthProvider } from 'react-oidc-context';
import { QueryClientProvider } from '@tanstack/react-query';
import App from './App.tsx';
import { ThemeProvider } from './shared/theme/ThemeProvider';
import { AuthTokenBridge } from './shared/AuthTokenBridge';
import { createQueryClient } from './shared/queryClient';
import { authProviderProps, nativeUserManager } from './auth/oidc';
import { routeAuthCallbacks } from './auth/nativeAuth';
import { i18nReady } from './i18n';
import './shared/styles/global.css';
import { installErrorReporting } from './shared/feedback/diagnostics';

const queryClient = createQueryClient();
// Errors thrown outside React, and promises nobody awaited, are reported by themselves (#490).
installErrorReporting();

// In the container the authorization answer arrives as a link the OS routes to the app, not
// as a navigation this page can read, so something has to be listening for it before the
// reader can come back signed in (#528). Nothing to register in a browser.
if (nativeUserManager) void routeAuthCallbacks(nativeUserManager);

// An English reader's locale is fetched first (see i18n/index.ts); a failure still renders,
// in French, rather than leaving a blank page.
void i18nReady.catch(() => undefined).then(() => createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider {...authProviderProps}>
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
));
