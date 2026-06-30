import { useEffect, useState } from 'react';

interface HelloResponse {
  app: string;
  message: string;
  timestamp: string;
}

/**
 * Écran d'amorçage minimal : vérifie la connexion à l'API.
 * Les vrais écrans (Accueil, Collection, etc.) arrivent dans les PR suivantes.
 */
function App() {
  const [hello, setHello] = useState<HelloResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetch('/api/hello')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.json() as Promise<HelloResponse>;
      })
      .then(setHello)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Erreur inconnue'));
  }, []);

  return (
    <main className="app-shell">
      <h1>Ma Bibliothèque</h1>
      <p className="tagline">Votre bibliothèque personnelle de livres et mangas.</p>
      {hello && (
        <p className="api-status api-status--ok">
          API connectée : {hello.app} — {hello.message}
        </p>
      )}
      {error && <p className="api-status api-status--err">API injoignable ({error})</p>}
      {!hello && !error && <p className="api-status">Connexion à l'API…</p>}
    </main>
  );
}

export default App;
