import { act, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { renderWithProviders, TestProviders } from '../../test/utils';
import { http, HttpResponse, server } from '../../test/server';
import { resetAuth } from '../../test/oidcMock';
import { AchievementWatcher } from '../../shared/AchievementWatcher';
import { Toaster } from '../../shared/ui/Toaster';
import { resetToasts } from '../../shared/ui/toast';
import type { AchievementsDto } from '../../api/generated/librarius';

vi.mock('react-oidc-context', () => import('../../test/oidcMock'));

const { AchievementsPage } = await import('./AchievementsPage');

const PROGRESS: AchievementsDto = {
  xp: 170,
  level: 3,
  levelXp: 141,
  nextLevelXp: 260,
  achievements: [
    { code: 'FIRST_BOOK', family: 'READING', tiers: 1, tier: 1, unlockedAt: '2026-03-02T10:00:00Z', value: 10, xpEarned: 20 },
    { code: 'BOOKWORM', family: 'READING', tiers: 4, tier: 1, unlockedAt: '2026-03-02T10:00:00Z', value: 12, nextThreshold: 50, xpEarned: 25, nextXp: 75 },
    { code: 'POLYGLOT', family: 'CURATION', tiers: 1, tier: 0, value: 1, nextThreshold: 3, xpEarned: 0, nextXp: 50 },
  ],
  unseen: [],
};

describe('AchievementsPage (#287)', () => {
  beforeEach(() => {
    resetAuth();
    resetToasts();
  });

  test('shows the level, the XP to the next one, and every achievement with its progress', async () => {
    server.use(http.get('*/api/achievements', () => HttpResponse.json(PROGRESS)));
    renderWithProviders(<AchievementsPage />);

    expect(await screen.findByText('Niveau 3')).toBeInTheDocument();
    expect(screen.getByText('170 XP · encore 90 XP pour le niveau 4')).toBeInTheDocument();
    expect(screen.getByText('2 succès sur 3')).toBeInTheDocument();
    // Earned, with its tier; the next bar to clear named in the description.
    expect(screen.getByText('Rat de bibliothèque')).toBeInTheDocument();
    expect(screen.getByText('Bronze')).toBeInTheDocument();
    expect(screen.getByText('Termine 50 livres.')).toBeInTheDocument();
    expect(screen.getByText('12 / 50')).toBeInTheDocument();
    // Locked, with how far along.
    expect(screen.getByText('Polyglotte')).toBeInTheDocument();
    expect(screen.getByText('1 / 3')).toBeInTheDocument();
    expect(screen.getByText(/À débloquer/)).toBeInTheDocument();
  });

  test('announces a new unlock once, then marks it seen', async () => {
    let unseen = [{ code: 'COMEBACK', tier: 1, tiers: 1, xp: 50 }];
    const seen = vi.fn();
    server.use(
      http.get('*/api/achievements', () => HttpResponse.json({ ...PROGRESS, unseen })),
      http.post('*/api/achievements/seen', () => {
        seen();
        unseen = [];
        return new HttpResponse(null, { status: 204 });
      }),
    );
    render(
      <TestProviders>
        <AchievementWatcher />
        <Toaster />
      </TestProviders>,
    );

    expect(await screen.findByText('Succès débloqué : Le retour (+50 XP)')).toBeInTheDocument();
    await waitFor(() => expect(seen).toHaveBeenCalledTimes(1));
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 50));
    });
    expect(screen.getAllByText(/Succès débloqué/)).toHaveLength(1);
  });

  test('announces a burst of unlocks as one message', async () => {
    server.use(http.get('*/api/achievements', () =>
      HttpResponse.json({
        ...PROGRESS,
        unseen: [
          { code: 'BOOKWORM', tier: 1, tiers: 4, xp: 25 },
          { code: 'BOOKWORM', tier: 2, tiers: 4, xp: 75 },
          { code: 'CRITIC', tier: 1, tiers: 2, xp: 25 },
        ],
      })));
    render(
      <TestProviders>
        <AchievementWatcher />
        <Toaster />
      </TestProviders>,
    );

    expect(await screen.findByText('3 succès débloqués (+125 XP) — vois-les dans Succès')).toBeInTheDocument();
    expect(screen.queryByText(/Rat de bibliothèque/)).not.toBeInTheDocument();
  });
});
