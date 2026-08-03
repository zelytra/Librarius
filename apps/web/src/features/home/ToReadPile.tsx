import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Cover } from '../../shared/ui/Cover';
import { AuthorNames } from '../author/AuthorNames';
import { SectionHeader } from '../../shared/ui/primitives';
import { useGetApiLibrary, type LibraryItemDto } from '../../api/generated/librarius';
import styles from './HomePage.module.css';

/**
 * Covers on the shelf. A window onto the pile, not the pile itself — the header says how
 * big the whole thing is, and the Collection is where one browses all of it.
 */
const SHELF_SIZE = 12;

/**
 * The to-read pile ("PAL"): the titles the reader owns and has never opened (#166).
 *
 * <p>The counters already reported how many there were; this is the shelf that says
 * <em>which</em> ones. Only {@code OWNED} is asked for, and the four statuses are exclusive
 * server-side, so a title being read, already read, or given up on is not in this pile —
 * an abandoned book is not waiting to be read (#163).
 *
 * <p>Self-contained, like {@code UpcomingReleases}: it fetches its own page and decides on
 * its own whether it has anything to say. That keeps it out of {@code HomePage}'s
 * loading/error gate — a slow pile must not hold up the shelves above it — and means a
 * reader who hid this section never even issues the request, since a hidden section is
 * never rendered (see {@code DashboardSections}).
 */
export function ToReadPile() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  // No `sort`: the API's default is most recently added first, which is what a pile wants —
  // the book bought last week is the one most likely to be picked up next.
  const { data, isPending, isError } = useGetApiLibrary({ status: 'OWNED', size: SHELF_SIZE });

  const items = data?.items ?? [];
  // Nothing to say, so nothing to draw — not an empty box. The dashboard's own empty state
  // already covers a library with nothing in it at all.
  if (isPending || isError || items.length === 0) return null;

  const open = (it: LibraryItemDto) => navigate(`/detail/${it.id}`, { state: { item: it } });

  return (
    <section>
      <SectionHeader
        title={t('home.toRead')}
        // The server's count of the whole pile, not the length of this page: a shelf of 12
        // out of 40 that announced "12" would quietly under-report the backlog.
        action={t('home.toReadCount', { toRead: data?.total ?? items.length })}
      />
      <div className={`scroll-x ${styles.shelf}`}>
        {items.map((it) => (
          <Cover
            key={it.id}
            title={it.book?.title ?? '—'}
            imageUrl={it.book?.coverUrl}
            caption={<AuthorNames text={it.book?.authors} />}
            onClick={() => open(it)}
          />
        ))}
      </div>
    </section>
  );
}
