import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  deleteApiUsersIdFollow,
  getGetApiMeFollowingQueryKey,
  putApiUsersIdFollow,
  useGetApiMeFollowers,
  useGetApiMeFollowing,
  useGetApiUsers,
} from '../../api/generated/librarius';
import { LoginGate } from '../../shared/LoginGate';
import { Button, Screen, Segmented } from '../../shared/ui/primitives';
import { Icon } from '../../shared/ui/Icon';
import { TrustBadge } from '../../shared/ui/TrustBadge';
import { colorFor } from '../../shared/ui/coverPalette';
import { initials } from '../author/author';
import styles from './PeoplePage.module.css';

type Tab = 'search' | 'following' | 'followers';

/** Delay before a keystroke turns into a request — the same as the other searches. */
const DEBOUNCE_MS = 300;

interface Person {
  id?: string;
  displayName?: string;
  trusted?: boolean;
}

/**
 * Finding other readers (#202): by name, with who the reader follows and who follows them.
 * Names are not unique — two "Marc" are both listed, told apart by their initials' colour.
 * Following answers at once: the button turns before the server has, and turns back if it
 * refuses.
 */
export function PeoplePage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  return (
    <Screen>
      <div className={styles.header}>
        <button onClick={() => navigate(-1)} aria-label={t('common.back')} className={styles.backButton}>
          <Icon name="arrow_back" size={24} color="var(--ink)" />
        </button>
        <h2 className={styles.title}>{t('people.title')}</h2>
      </div>
      <LoginGate prompt={t('people.prompt')}>
        <People />
      </LoginGate>
    </Screen>
  );
}

function People() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>('search');
  const [input, setInput] = useState('');
  const [term, setTerm] = useState('');
  // The follow state the screen shows, ahead of the server: id -> following.
  const [override, setOverride] = useState<Record<string, boolean>>({});

  useEffect(() => {
    const handle = setTimeout(() => setTerm(input.trim()), DEBOUNCE_MS);
    return () => clearTimeout(handle);
  }, [input]);

  const found = useGetApiUsers({ q: term }, { query: { enabled: tab === 'search' && term.length >= 2 } });
  const following = useGetApiMeFollowing();
  const followers = useGetApiMeFollowers({ query: { enabled: tab === 'followers' } });

  const followedIds = useMemo(() => new Set((following.data ?? []).map((p) => p.id)), [following.data]);
  const isFollowed = (person: Person & { following?: boolean }) =>
    override[person.id!] ?? person.following ?? followedIds.has(person.id);

  async function toggle(person: Person & { following?: boolean }) {
    const id = person.id!;
    const next = !isFollowed(person);
    setOverride((o) => ({ ...o, [id]: next }));
    try {
      if (next) await putApiUsersIdFollow(id);
      else await deleteApiUsersIdFollow(id);
      void queryClient.invalidateQueries({ queryKey: getGetApiMeFollowingQueryKey() });
    } catch {
      setOverride((o) => ({ ...o, [id]: !next }));
    }
  }

  const list: (Person & { following?: boolean })[] =
    tab === 'search' ? found.data ?? [] : tab === 'following' ? following.data ?? [] : followers.data ?? [];
  const empty =
    tab === 'search'
      ? term.length < 2
        ? t('people.searchHint')
        : found.isFetching
          ? null
          : t('people.noMatch')
      : t(tab === 'following' ? 'people.noFollowing' : 'people.noFollowers');

  return (
    <div className={styles.body}>
      <Segmented<Tab>
        options={[
          { id: 'search', label: t('people.tabs.search') },
          { id: 'following', label: t('people.tabs.following', { count: following.data?.length ?? 0 }) },
          { id: 'followers', label: t('people.tabs.followers') },
        ]}
        value={tab}
        onChange={setTab}
      />
      {tab === 'search' && (
        <div className={styles.searchBar}>
          <Icon name="search" size={20} color="var(--faint)" />
          <input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder={t('people.placeholder')}
            aria-label={t('people.placeholder')}
            className={styles.searchInput}
          />
        </div>
      )}
      {list.length === 0 ? (
        empty && <p className={styles.empty}>{empty}</p>
      ) : (
        <ul className={styles.list}>
          {list.map((person) => {
            const name = person.displayName ?? '—';
            const followed = isFollowed(person);
            return (
              <li key={person.id} className={styles.row}>
                <span className={styles.avatar} style={{ background: colorFor(name) }} aria-hidden="true">
                  {initials(name)}
                </span>
                <span className={styles.name}>
                  {name}
                  {person.trusted && <TrustBadge />}
                </span>
                <Button
                  variant={followed ? 'secondary' : 'primary'}
                  size="compact"
                  aria-pressed={followed}
                  aria-label={t(followed ? 'people.unfollowAria' : 'people.followAria', { name })}
                  onClick={() => void toggle(person)}
                >
                  {t(followed ? 'people.following' : 'people.follow')}
                </Button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
