import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useRef } from 'react';
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router';
import { describe, expect, test, vi } from 'vitest';
import { useScrollMemory } from './scrollMemory';

function Shell() {
  const main = useRef<HTMLElement>(null);
  useScrollMemory(main);
  const navigate = useNavigate();
  return (
    <main ref={main} data-testid="main">
      <button onClick={() => navigate('/detail')}>ouvrir</button>
      <button onClick={() => navigate(-1)}>retour</button>
      <Routes>
        <Route path="/library" element={<p>bibliothèque</p>} />
        <Route path="/detail" element={<p>fiche</p>} />
      </Routes>
    </main>
  );
}

/** #600: back from a book, the library is where it was left; a new screen opens at its top. */
describe('useScrollMemory', () => {
  test('puts the scroll back on a return, and starts a new screen at the top', async () => {
    render(
      <MemoryRouter initialEntries={['/library']}>
        <Shell />
      </MemoryRouter>,
    );
    const main = screen.getByTestId('main');
    main.scrollTop = 1800;
    act(() => main.dispatchEvent(new Event('scroll')));

    await userEvent.click(screen.getByText('ouvrir'));
    expect(await screen.findByText('fiche')).toBeInTheDocument();
    expect(main.scrollTop).toBe(0);

    await userEvent.click(screen.getByText('retour'));
    expect(await screen.findByText('bibliothèque')).toBeInTheDocument();
    await vi.waitFor(() => expect(main.scrollTop).toBe(1800));
  });
});
