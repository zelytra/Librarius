import { STACK_IS_EXTERNAL } from './config';
import { dumpStackLogs, startStack, waitForStack } from './stack';

/** Brings the stack up (unless it is managed elsewhere) and waits until it answers. */
export default async function globalSetup(): Promise<void> {
  if (!STACK_IS_EXTERNAL) startStack();
  try {
    await waitForStack();
  } catch (error) {
    dumpStackLogs();
    throw error;
  }
}
