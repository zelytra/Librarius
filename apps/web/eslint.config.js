import js from '@eslint/js';
import globals from 'globals';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import tseslint from 'typescript-eslint';

/**
 * Attributes whose value is shown to the user or read out by a screen reader. A literal
 * there is copy that never reaches the locale file, and `react/jsx-no-literals` cannot
 * catch it: turning `ignoreProps` off would flag every `className`, `type` and icon name
 * along with it.
 */
const USER_FACING_ATTRIBUTES = '/^(aria-label|aria-placeholder|alt|placeholder|title)$/';

const noLiteralAttribute = (valueSelector) => ({
  selector: `JSXAttribute[name.name=${USER_FACING_ATTRIBUTES}] > ${valueSelector}`,
  message: "User-facing text goes through i18n: pass t('key') rather than a literal.",
});

export default tseslint.config(
  { ignores: ['dist', 'dev-dist', 'coverage', 'src/api/generated'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
    plugins: {
      react,
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      // The interface is French and its copy lives in i18n/locales, nowhere else — see
      // https://github.com/zelytra/Librarius/issues/35.
      'react/jsx-no-literals': ['error', { noStrings: true, ignoreProps: true }],
      'no-restricted-syntax': [
        'error',
        noLiteralAttribute('Literal'),
        noLiteralAttribute('JSXExpressionContainer > Literal'),
        noLiteralAttribute('TemplateLiteral'),
        noLiteralAttribute('JSXExpressionContainer > TemplateLiteral'),
      ],
    },
  },
  {
    // A test renders throwaway copy into a fake screen; none of it ever ships. The i18n
    // guard is about the application, and routing test fixtures through the locale file
    // would only make the assertions harder to read.
    files: ['**/*.test.{ts,tsx}', 'src/test/**'],
    rules: {
      'react/jsx-no-literals': 'off',
      'no-restricted-syntax': 'off',
    },
  },
);
