import { Component, type ErrorInfo, type ReactNode } from 'react';
import { reportError } from '../feedback/diagnostics';

interface ErrorBoundaryProps {
  children: ReactNode;
  /** Rendered instead of the children once something below has thrown. */
  fallback: ReactNode;
}

interface ErrorBoundaryState {
  failed: boolean;
}

/**
 * Catches a render exception thrown below it. Without a boundary React unmounts the whole
 * tree on any uncaught error and the user is left staring at a blank page with no way
 * back — the fallback is that way back.
 *
 * The fallback is passed in rather than built here: a class component cannot call
 * `useTranslation()`, and the copy has to come from the locale file like every other
 * string.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { failed: false };

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { failed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unhandled render error', error, info.componentStack);
    // Reported by itself (#490): the reader is looking at the fallback, not at a console.
    reportError(error);
  }

  render() {
    return this.state.failed ? this.props.fallback : this.props.children;
  }
}
