import { defineConfig } from 'orval';

// Génère un client TypeScript typé (fetch) à partir du schéma OpenAPI produit
// par l'API Quarkus. Régénérer avec `pnpm --filter @librarius/web gen:api`.
export default defineConfig({
  librarius: {
    input: './openapi/openapi.json',
    output: {
      mode: 'single',
      target: './src/api/generated/librarius.ts',
      client: 'fetch',
      baseUrl: '',
      clean: true,
      override: {
        header: () => [
          'Généré automatiquement par orval depuis openapi/openapi.json.',
          'NE PAS modifier à la main — lancer `pnpm gen:api`.',
        ],
      },
    },
  },
});
