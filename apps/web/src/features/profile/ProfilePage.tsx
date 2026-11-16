import { useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import {
  deleteApiUsersIdFollow,
  getGetApiMeFollowingQueryKey,
  getGetApiUsersIdProfileQueryKey,
  putApiUsersIdBlock,
  putApiUsersIdFollow,
  putApiUsersIdFollowNotify,
  useGetApiUsersIdProfile,
  type MemberProfileDto,
  type ProfileReadingDto,
  type ProfileTitleDto,
} from '../../api/generated/librarius';
import { activeLanguage } from '../../i18n/languages';
import { apiErrorStatus } from '../../shared/apiClient';
import { LoginGate } from '../../shared/LoginGate';
import { Icon } from '../../shared/ui/Icon';
import { Modal } from '../../shared/ui/Modal';
import { Button, Chip, Screen, Segmented } from '../../shared/ui/primitives';
import { EmptyState, ErrorState, Loading } from '../../shared/ui/states';
import { toast } from '../../shared/ui/toast';
import { TrustBadge } from '../../shared/ui/TrustBadge';
import { Avatar } from '../../shared/ui/Avatar';
import { MemberBook } from '../../shared/ui/MemberBook';
import { Shelf, type ShelfName } from './Shelf';
import { Stars } from '../detail/StarRating';
import styles from './ProfilePage.module.css';

/**
 * A member's profile (#497): who they are, how the two of you relate, and — when they let
 * you see it (#201: a mutual follow, or a public account) — what they read: the figures, the
 * books under way, the latest finished and the reviews they shared. The API decides what is
 * visible; this screen only draws what comes back.
 */
export function ProfilePage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  return (
    <Screen>
      <div className={styles.header}>
        <button onClick={() => navigate(-1)} aria-label={t('common.back')} className={styles.backButton}>
          <Icon name="arrow_back" size={24} color="var(--ink)" />
        </button>
      </div>
      <LoginGate prompt={t('profile.prompt')}>
        <Profile />
      </LoginGate>
    </Screen>
  );
}

function Profile() {
  const { t } = useTranslation();
  const { id = '' } = useParams();
  const { data: profile, isPending, isError, error, refetch } = useGetApiUsersIdProfile(id);

  if (isPending) return <Loading />;
  if (isError) {
    return apiErrorStatus(error) === 404 ? (
      <EmptyState icon="search_off" title={t('profile.unavailable')} description={t('profile.unavailableHint')} />
    ) : (
      <ErrorState message={t('profile.error')} onRetry={() => void refetch()} />
    );
  }
  return (
    <div className={styles.body}>
      <Identity profile={profile} />
      {profile.reading ? (
        <ProfileTabs id={profile.id!} reading={profile.reading} />
      ) : (
        <EmptyState icon="lock" title={t('profile.private')} description={t('profile.privateHint')} />
      )}
    </div>
  );
}

type Tab = 'overview' | ShelfName;
const TABS: Tab[] = ['overview', 'library', 'toRead', 'wishlist'];

/** The overview, then the member's shelves to browse (#514); the tab lives in the URL. */
function ProfileTabs({ id, reading }: { id: string; reading: ProfileReadingDto }) {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const tab: Tab = TABS.find((candidate) => candidate === params.get('tab')) ?? 'overview';
  return (
    <>
      <Segmented<Tab>
        value={tab}
        onChange={(next) => setParams(next === 'overview' ? {} : { tab: next }, { replace: true })}
        options={TABS.map((candidate) => ({ id: candidate, label: t(`profile.tabs.${candidate}`) }))}
      />
      {tab === 'overview' ? <Reading reading={reading} /> : <Shelf key={tab} memberId={id} shelf={tab} />}
    </>
  );
}

const since = (value?: string) =>
  value ? new Date(value).toLocaleDateString(activeLanguage(), { month: 'long', year: 'numeric' }) : '';

function Identity({ profile }: { profile: MemberProfileDto }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [busy, setBusy] = useState(false);
  const [confirmBlock, setConfirmBlock] = useState(false);
  const name = profile.displayName ?? '—';
  const id = profile.id!;

  const relation = profile.following && profile.followsYou
    ? t('profile.mutual')
    : profile.followsYou
      ? t('profile.followsYou')
      : null;

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: getGetApiUsersIdProfileQueryKey(id) });
    void queryClient.invalidateQueries({ queryKey: getGetApiMeFollowingQueryKey() });
  };

  async function toggleNotify(on: boolean) {
    setBusy(true);
    try {
      await putApiUsersIdFollowNotify(id, { notifyActivity: on });
      toast.success(t(on ? 'profile.notifyOn' : 'profile.notifyOff', { name }));
      refresh();
    } catch {
      toast.error(t('profile.actionFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function toggleFollow() {
    setBusy(true);
    try {
      if (profile.following) {
        await deleteApiUsersIdFollow(id);
        toast.success(t('profile.unfollowed', { name }));
      } else {
        await putApiUsersIdFollow(id);
        toast.success(t('profile.followed', { name }));
      }
      refresh();
    } catch {
      toast.error(t('profile.actionFailed'));
    } finally {
      setBusy(false);
    }
  }

  async function block() {
    setBusy(true);
    try {
      await putApiUsersIdBlock(id);
      setConfirmBlock(false);
      toast.success(t('profile.blocked', { name }));
      // Their profile is out of reach from now on: back to the list of readers.
      navigate('/people', { replace: true });
      void queryClient.invalidateQueries({ queryKey: getGetApiMeFollowingQueryKey() });
    } catch {
      toast.error(t('profile.actionFailed'));
      setBusy(false);
    }
  }

  return (
    <section className={styles.identity}>
      <Avatar id={id} name={name} version={profile.avatarVersion} size={64} decorative />
      <div className={styles.who}>
        <h2 className={styles.name}>
          {name}
          {profile.trusted && <TrustBadge />}
        </h2>
        <p className={styles.meta}>
          {[
            profile.memberSince && t('profile.since', { date: since(profile.memberSince) }),
            t('profile.followers', { count: profile.followers ?? 0 }),
            t('profile.followingCount', { count: profile.followingCount ?? 0 }),
          ]
            .filter(Boolean)
            .map((part) => (
              <span key={part}>{part}</span>
            ))}
        </p>
        {relation && <p className={styles.relation}>{relation}</p>}
      </div>
      <div className={styles.actions}>
        {profile.self ? (
          <Link to="/settings" className={styles.editLink}>
            <Icon name="edit" size={16} color="currentColor" />
            {t('profile.edit')}
          </Link>
        ) : (
          <>
            <Button
              variant={profile.following ? 'secondary' : 'primary'}
              size="compact"
              aria-pressed={Boolean(profile.following)}
              aria-label={t(profile.following ? 'people.unfollowAria' : 'people.followAria', { name })}
              disabled={busy}
              onClick={() => void toggleFollow()}
            >
              {t(profile.following ? 'people.following' : 'people.follow')}
            </Button>
            <Button variant="secondary" size="compact" disabled={busy} onClick={() => setConfirmBlock(true)}>
              <Icon name="block" size={16} color="currentColor" />
              {t('profile.block')}
            </Button>
          </>
        )}
      </div>
      {/* #516: following notifies of the member's activity, unless switched off here. */}
      {profile.notifyActivity != null && (
        <button
          type="button"
          role="switch"
          aria-checked={profile.notifyActivity}
          disabled={busy}
          className={styles.switch}
          onClick={() => void toggleNotify(!profile.notifyActivity)}
        >
          <span className={`${styles.track} ${profile.notifyActivity ? styles.trackOn : ''}`} aria-hidden="true">
            <span className={styles.thumb} />
          </span>
          {t('profile.notifyActivity')}
        </button>
      )}
      {confirmBlock && (
        <Modal title={t('profile.blockTitle', { name })} onClose={() => setConfirmBlock(false)}>
          <p className={styles.blockText}>{t('profile.blockText', { name })}</p>
          <div className={styles.blockActions}>
            <Button variant="secondary" onClick={() => setConfirmBlock(false)}>
              {t('common.cancel')}
            </Button>
            <Button variant="primary" disabled={busy} onClick={() => void block()}>
              {t('profile.blockConfirm', { name })}
            </Button>
          </div>
        </Modal>
      )}
    </section>
  );
}

function Reading({ reading }: { reading: ProfileReadingDto }) {
  const { t } = useTranslation();
  const figures: [string, number | undefined][] = [
    ['profile.stats.read', reading.read],
    ['profile.stats.readThisYear', reading.readThisYear],
    ['profile.stats.reading', reading.reading],
    ['profile.stats.toRead', reading.toRead],
    ['profile.stats.wished', reading.wished],
    ['profile.stats.pagesRead', reading.pagesRead],
  ];
  const current = reading.currentlyReading ?? [];
  const finished = reading.recentlyFinished ?? [];
  const reviews = reading.reviews ?? [];

  return (
    <>
      <ul className={styles.figures} aria-label={t('profile.stats.label')}>
        {figures.map(([key, value]) => (
          <li key={key} className={styles.figure}>
            <span className={styles.figureValue}>{(value ?? 0).toLocaleString(activeLanguage())}</span>
            <span className={styles.figureLabel}>{t(key)}</span>
          </li>
        ))}
      </ul>

      {(reading.topGenres ?? []).length > 0 && (
        <section className={styles.section}>
          <h3 className={styles.sectionTitle}>{t('profile.genres')}</h3>
          <div className={styles.chips}>
            {reading.topGenres!.map((genre) => (
              <Chip key={genre}>{genre}</Chip>
            ))}
          </div>
        </section>
      )}

      <TitleList title={t('profile.current')} empty={t('profile.currentEmpty')} titles={current}
        detail={(item) => (item.percent != null ? t('profile.percent', { percent: item.percent }) : null)} />
      <TitleList title={t('profile.finished')} empty={t('profile.finishedEmpty')} titles={finished}
        detail={(item) => (item.rating != null ? <Stars value={item.rating} /> : null)} />

      <section className={styles.section}>
        <h3 className={styles.sectionTitle}>{t('profile.reviews')}</h3>
        {reviews.length === 0 ? (
          <p className={styles.empty}>{t('profile.reviewsEmpty')}</p>
        ) : (
          <ul className={styles.list}>
            {reviews.map((item) => (
              <li key={item.book?.editionId} className={styles.review}>
                <MemberBook book={item.book ?? {}} detail={item.rating != null ? <Stars value={item.rating} /> : null} />
                {item.review && <p className={styles.reviewText}>{item.review}</p>}
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}

function TitleList({
  title,
  empty,
  titles,
  detail,
}: {
  title: string;
  empty: string;
  titles: ProfileTitleDto[];
  detail: (item: ProfileTitleDto) => React.ReactNode;
}) {
  return (
    <section className={styles.section}>
      <h3 className={styles.sectionTitle}>{title}</h3>
      {titles.length === 0 ? (
        <p className={styles.empty}>{empty}</p>
      ) : (
        <ul className={styles.list}>
          {titles.map((item) => (
            <li key={item.book?.editionId} className={styles.row}>
              <MemberBook book={item.book ?? {}} detail={detail(item)} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
