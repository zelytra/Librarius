import { useEffect, useState } from 'react';
import { getApiHello } from './api/generated/librarius';

/**
 * Écran d'amorçage minimal : vérifie la connexion à l'API via le client typé
 * généré depuis l'OpenAPI. Les vrais écrans arrivent dans les PR suivantes.
 */
function App() {
  const [appName, setAppName] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getApiHello()
      .then((res) => {
        if (res.status === 200) {
          const payload = res.data as Record<string, unknown>;
          setAppName(String(payload.app ?? 'API'));
        } else {
          setError(`HTTP ${res.status}`);
        }
      })
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Erreur inconnue'));
  }, []);

  return (
    <main className="app-shell">
      <h1>Ma Bibliothèque</h1>
      <p className="tagline">Votre bibliothèque personnelle de livres et mangas.</p>
      {appName && <p className="api-status api-status--ok">API connectée : {appName}</p>}
      {error && <p className="api-status api-status--err">API injoignable ({error})</p>}
      {!appName && !error && <p className="api-status">Connexion à l'API…</p>}
    </main>
  );
}

export default App;
