import { STACK_IS_EXTERNAL } from './config';
import { stopStack } from './stack';

/**
 * Tears the stack down, volumes included. `E2E_KEEP_STACK=1` keeps it running to
 * inspect the database or the API logs after a failure.
 */
export default async function globalTeardown(): Promise<void> {
  if (STACK_IS_EXTERNAL || process.env.E2E_KEEP_STACK === '1') return;
  stopStack();
}
