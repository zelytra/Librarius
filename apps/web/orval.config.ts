import { defineConfig } from 'orval';

// Generates a typed TypeScript client (fetch) from the OpenAPI schema produced by the
// Quarkus API. Regenerate with `pnpm --filter @librarius/web gen:api`.
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
