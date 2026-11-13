/**
 * Outcome messages for user actions (#413): "Ajouté à ta bibliothèque", "Statut : Lu"…
 *
 * <p>A tiny store outside React rather than a context: a mutation callback, a screen under
 * test or a helper can announce an outcome without being wrapped in a provider, and the one
 * {@code <Toaster>} the app mounts is the only thing that renders them.
 */

export type ToastTone = 'success' | 'info' | 'error';

export interface ToastMessage {
  id: number;
  message: string;
  tone: ToastTone;
  /** Material Symbols name drawn before the message. */
  icon: string;
}

type Listener = () => void;

const DEFAULT_ICON: Record<ToastTone, string> = {
  success: 'check_circle',
  info: 'info',
  error: 'error',
};

/** How long a message stays, in milliseconds; an error a little longer, to be read. */
export const TOAST_DURATION: Record<ToastTone, number> = { success: 2600, info: 2600, error: 4500 };

/** At most this many on screen: a burst of actions should not bury the screen. */
const MAX_VISIBLE = 3;

let messages: ToastMessage[] = [];
let nextId = 1;
const listeners = new Set<Listener>();

function emit() {
  listeners.forEach((listener) => listener());
}

export function subscribeToasts(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function currentToasts(): ToastMessage[] {
  return messages;
}

export function dismissToast(id: number) {
  messages = messages.filter((m) => m.id !== id);
  emit();
}

/** Announces an outcome; returns its id. */
export function showToast(message: string, tone: ToastTone = 'success', icon?: string): number {
  const id = nextId++;
  messages = [...messages, { id, message, tone, icon: icon ?? DEFAULT_ICON[tone] }].slice(-MAX_VISIBLE);
  emit();
  return id;
}

export const toast = {
  success: (message: string, icon?: string) => showToast(message, 'success', icon),
  info: (message: string, icon?: string) => showToast(message, 'info', icon),
  error: (message: string, icon?: string) => showToast(message, 'error', icon),
};

/** Clears every message — for tests. */
export function resetToasts() {
  messages = [];
  emit();
}
