import type { ReactNode } from 'react';
import { Link } from 'react-router';
import type { MemberBookDto } from '../../api/generated/librarius';
import { bookTarget } from '../memberBook';
import { BookCover } from './BookCover';
import { colorFor } from './coverPalette';
import styles from './MemberBook.module.css';

/**
 * A book of someone else's — on a profile, a shelf, the feed — as one link to its screen
 * (#513): cover, title, authors, and whatever the list adds at the end.
 */
export function MemberBook({ book, detail, coverWidth = 40 }: { book: MemberBookDto; detail?: ReactNode; coverWidth?: number }) {
  const title = book.title ?? '';
  const target = bookTarget(book);
  return (
    <Link to={target.to} state={target.state} className={styles.book}>
      <span aria-hidden="true">
        <BookCover color={colorFor(title)} imageUrl={book.coverUrl ?? undefined} width={coverWidth}
          height={Math.round(coverWidth * 1.45)} radius={6} />
      </span>
      <span className={styles.text}>
        <span className={styles.title}>{title}</span>
        {book.authors && <span className={styles.authors}>{book.authors}</span>}
      </span>
      {detail && <span className={styles.detail}>{detail}</span>}
    </Link>
  );
}
