import { useNavigate, useParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQueryClient } from '@tanstack/react-query';
import { LoginGate } from '../../shared/LoginGate';
import { apiErrorStatus } from '../../shared/apiClient';
import { Icon } from '../../shared/ui/Icon';
import { Cover } from '../../shared/ui/Cover';
import { colorFor } from '../../shared/ui/coverPalette';
import { Button, Grid } from '../../shared/ui/primitives';
import { EmptyState, ErrorState, Loading } from '../../shared/ui/states';
import {
  getGetApiAuthorsIdQueryKey,
  useDeleteApiAuthorsIdFollow,
  useGetApiAuthorsId,
  usePutApiAuthorsIdFollow,
  type AuthorWorkDto,
} from '../../api/generated/librarius';
import { initials, workCaption } from './author';
import styles from './AuthorPage.module.css';

/** Opacity suffixes of the wash drawn behind the top of the screen, as on Detail/Series. */
const WASH_FROM = 'aa';
const WASH_TO = '00';

/**
 * One title of the bibliography — the shared cover grid of Collection. It opens the
 * caller's own item when they own the work, the series when the work belongs to one they
 * do not own, and stays a plain, unclickable tile when neither exists: a standalone title
 * nobody here has entered yet has nothing to open.
 */
function WorkTile({ work }: { work: AuthorWorkDto }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const title = work.title ?? '—';
  const tag = t(work.kind === 'MANGA' ? 'collection.tag.manga' : 'collection.tag.book');
  const target = work.libraryItemId
    ? `/detail/${work.libraryItemId}`
    : work.seriesId
      ? `/series/${work.seriesId}`
      : undefined;
  return (
    <Cover
      variant="tile"
      title={title}
      imageUrl={work.coverUrl}
      tag={tag}
      caption={workCaption(work, tag)}
      onClick={target ? () => navigate(target) : undefined}
    />
  );
}

function AuthorContent({ id }: { id: string }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data, isPending: loading, isError, error, refetch } = useGetApiAuthorsId(id);

  const invalidateAuthor = () => {
    void queryClient.invalidateQueries({ queryKey: getGetApiAuthorsIdQueryKey(id) });
  };

  const { mutate: follow } = usePutApiAuthorsIdFollow({ mutation: { onSuccess: invalidateAuthor } });
  const { mutate: unfollow } = useDeleteApiAuthorsIdFollow({ mutation: { onSuccess: invalidateAuthor } });

  if (loading) return <Loading />;

  // An author is shared-catalog data, visible whether or not the caller owns anything of
  // theirs (see API.md § Authors): a 404 only ever means the identifier is unknown, never
  // "not owned" the way it can for a series.
  const notFound = apiErrorStatus(error) === 404;
  if (isError && !notFound) {
    return <ErrorState message={t('author.error')} onRetry={() => void refetch()} />;
  }
  if (!data) {
    return (
      <EmptyState
        icon="search_off"
        className={styles.notFound}
        title={t('author.notFound')}
        action={
          <Button variant="secondary" onClick={() => navigate(-1)}>
            {t('common.back')}
          </Button>
        }
      />
    );
  }

  const author = data;
  const name = author.name ?? '—';
  const works = author.works ?? [];

  return (
    <div className={styles.page}>
      {/* The wash is tinted with a colour derived from the name, so it lives on the
          element — same anatomy as Detail and Series. */}
      <div
        className={styles.wash}
        style={{ background: `linear-gradient(180deg, ${colorFor(name)}${WASH_FROM}, ${colorFor(name)}${WASH_TO})` }}
      />
      <div className={styles.body}>
        <div className={styles.backRow}>
          <button onClick={() => navigate(-1)} aria-label={t('common.back')} className={styles.backButton}>
            <Icon name="arrow_back" size={24} color="var(--overlay-ink)" />
          </button>
        </div>

        <div className={styles.intro}>
          <div className={styles.portraitRow}>
            {author.photoUrl ? (
              <div
                className={styles.portrait}
                style={{ background: `center/cover no-repeat url(${author.photoUrl})` }}
              />
            ) : (
              <div className={styles.portrait} style={{ background: colorFor(name) }}>
                <span className={styles.portraitInitials}>{initials(name)}</span>
              </div>
            )}
          </div>

          <h2 className={styles.name}>{name}</h2>
          <p className={styles.workCount}>
            {t('author.workCount', { count: works.length })}
          </p>

          <div className={styles.followRow}>
            <Button
              variant={author.followed ? 'secondary' : 'primary'}
              size="block"
              onClick={() => (author.followed ? unfollow({ id }) : follow({ id }))}
            >
              <Icon
                name={author.followed ? 'bookmark_added' : 'bookmark_add'}
                size={20}
                fill={author.followed}
                color={author.followed ? 'var(--accent-deep)' : 'var(--on-accent)'}
              />
              {t(author.followed ? 'author.following' : 'author.follow')}
            </Button>
          </div>
        </div>

        <h3 className={styles.sectionTitle}>{t('author.bibliography')}</h3>

        {works.length === 0 ? (
          <EmptyState
            icon="library_books"
            className={styles.emptyWorks}
            title={t('author.emptyWorks')}
          />
        ) : (
          <Grid>
            {works.map((work) => (
              <WorkTile key={work.workId ?? `${work.title}-${work.volumeNumber}`} work={work} />
            ))}
          </Grid>
        )}
      </div>
    </div>
  );
}

export function AuthorPage() {
  const { t } = useTranslation();
  const { id = '' } = useParams();
  return (
    <LoginGate prompt={t('auth.prompts.author')}>
      <AuthorContent id={id} />
    </LoginGate>
  );
}
