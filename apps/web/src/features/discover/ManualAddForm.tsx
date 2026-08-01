import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '../../shared/ui/primitives';
import { ErrorState, Loading } from '../../shared/ui/states';
import { Kind, usePostApiLibrary, type ManualBookDto } from '../../api/generated/librarius';
import { Field, FieldGrid, SelectField } from './fields';
import { ALL_KINDS, KIND_LABEL_KEY } from './medium';
import styles from './ManualAddForm.module.css';

/**
 * Manual entry of a title the catalogs do not carry — self-published, an old edition, a
 * fanzine. It posts the same `ManualBookDto` a catalog result is turned into, so a book
 * typed by hand lands in the collection with the same shape as one that was found.
 *
 * The form picks its own medium: Discover no longer has a screen-wide toggle to read one
 * from, and a title typed by hand can be any medium the taxonomy carries, not just the two
 * a catalogue provider currently answers for.
 */

interface ManualAddFormProps {
  onCancel: () => void;
  onAdded: (title: string) => void;
}

/** A field left empty is absent, not zero: the API stores what it is given. */
function optionalNumber(value: string): number | undefined {
  const parsed = Number(value.trim());
  return value.trim() === '' || Number.isNaN(parsed) ? undefined : parsed;
}

function optionalText(value: string): string | undefined {
  return value.trim() === '' ? undefined : value.trim();
}

export function ManualAddForm({ onCancel, onAdded }: ManualAddFormProps) {
  const { t } = useTranslation();
  const [kind, setKind] = useState<Kind>(Kind.BOOK);
  const [title, setTitle] = useState('');
  const [authors, setAuthors] = useState('');
  const [seriesTitle, setSeriesTitle] = useState('');
  const [volumeNumber, setVolumeNumber] = useState('');
  const [isbn13, setIsbn13] = useState('');
  const [publisher, setPublisher] = useState('');
  const [pageCount, setPageCount] = useState('');
  const [coverUrl, setCoverUrl] = useState('');
  const [failed, setFailed] = useState(false);

  const { mutateAsync: addToLibrary, isPending } = usePostApiLibrary();

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const trimmed = title.trim();
    if (!trimmed) return;
    const book: ManualBookDto = {
      kind,
      title: trimmed,
      authors: optionalText(authors),
      seriesTitle: optionalText(seriesTitle),
      volumeNumber: optionalNumber(volumeNumber),
      isbn13: optionalText(isbn13),
      publisher: optionalText(publisher),
      pageCount: optionalNumber(pageCount),
      coverUrl: optionalText(coverUrl),
    };
    setFailed(false);
    try {
      await addToLibrary({ data: { book, status: 'OWNED' } });
      onAdded(trimmed);
    } catch {
      setFailed(true);
    }
  }

  return (
    <form onSubmit={(e) => void onSubmit(e)} className={styles.form}>
      <h3 className={styles.heading}>{t('discover.manual.heading')}</h3>

      <Field label={t('discover.manual.title')} value={title} onChange={setTitle} required />
      <SelectField
        label={t('discover.manual.kind')}
        value={kind}
        onChange={(value) => setKind(value as Kind)}
        options={ALL_KINDS.map((k) => ({ value: k, label: t(KIND_LABEL_KEY[k]) }))}
      />
      <FieldGrid>
        <Field label={t('discover.manual.authors')} value={authors} onChange={setAuthors} />
        <Field label={t('discover.manual.series')} value={seriesTitle} onChange={setSeriesTitle} />
        <Field
          label={t('discover.manual.volume')}
          value={volumeNumber}
          onChange={setVolumeNumber}
          type="number"
          inputMode="numeric"
        />
        <Field label={t('discover.manual.isbn')} value={isbn13} onChange={setIsbn13} />
        <Field label={t('discover.manual.publisher')} value={publisher} onChange={setPublisher} />
        <Field
          label={t('discover.manual.pageCount')}
          value={pageCount}
          onChange={setPageCount}
          type="number"
          inputMode="numeric"
        />
      </FieldGrid>
      <Field label={t('discover.manual.coverUrl')} value={coverUrl} onChange={setCoverUrl} type="url" />

      {failed && <ErrorState message={t('discover.errors.addFailed')} />}

      <div className={styles.actions}>
        <Button type="submit" disabled={isPending || title.trim() === ''}>
          {t('discover.manual.submit')}
          <Loading size="compact" pending={isPending} />
        </Button>
        <Button type="button" variant="ghost" onClick={onCancel}>
          {t('discover.manual.close')}
        </Button>
      </div>
    </form>
  );
}
