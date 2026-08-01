import { render, screen } from '@testing-library/react';
import { describe, expect, test } from 'vitest';
import { TestProviders } from '../../test/utils';
import { TrustBadge } from './TrustBadge';

describe('TrustBadge', () => {
  test('renders an icon and a visible text label, not colour alone (#186)', () => {
    render(
      <TestProviders>
        <TrustBadge />
      </TestProviders>,
    );

    expect(screen.getByText('De confiance')).toBeInTheDocument();
    // The icon is rendered alongside the text, aria-hidden — the label carries the
    // accessible meaning, the same colour-blind-safe pairing as the Series badge.
    const icon = document.querySelector('.material-symbol');
    expect(icon).toHaveAttribute('aria-hidden', 'true');
  });
});
