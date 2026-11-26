import { render, screen } from '@testing-library/react';
import { describe, expect, test, vi } from 'vitest';
import { CategoryChoice } from './CategoryChoice';

/**
 * #648: a category imported from Booknode carries no colour, and the selected state used to be
 * painted with that colour alone — selected, it looked exactly like the others.
 */
describe('CategoryChoice', () => {
  const imported = [
    { id: 'c-romance', code: 'romance', label: 'romance', builtin: false },
    { id: 'c-jeunesse', code: 'jeunesse', label: 'jeunesse', builtin: false },
  ];

  test('marks a selected category without a colour, not by colour alone', () => {
    render(<CategoryChoice categories={imported} selectedIds={['c-romance']} onToggle={vi.fn()} />);

    const on = screen.getByRole('button', { name: 'romance' });
    const off = screen.getByRole('button', { name: 'jeunesse' });
    expect(on).toHaveAttribute('aria-pressed', 'true');
    // A check mark stands for the state beside the tint.
    expect(on.querySelector('.material-symbol')).not.toBeNull();
    expect(off.querySelector('.material-symbol')).toBeNull();
  });

  test('marks the selected rank the same way', () => {
    render(
      <CategoryChoice
        categories={[{ id: 'c-or', code: 'or', label: 'Or', color: '#d9b94e', builtin: true }]}
        selectedId="c-or"
        onSelect={vi.fn()}
      />,
    );

    expect(screen.getByRole('button', { name: 'Or' }).querySelector('.material-symbol')).not.toBeNull();
  });
});
