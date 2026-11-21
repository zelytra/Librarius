import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Chip } from '../../shared/ui/primitives';
import { KIND_LABEL_KEY } from '../discover/medium';
import { KINDS } from '../../shared/kinds';
import { readLibraryKind, storeLibraryKind, type LibraryKindPreference } from '../../shared/libraryKind';
import styles from './SettingsPage.module.css';

/** The shelf the library opens on (#545): the kind owned most, unless the reader picks one. */
export function LibraryKindSection() {
  const { t } = useTranslation();
  const [preference, setPreference] = useState<LibraryKindPreference>(readLibraryKind);
  const pick = (next: LibraryKindPreference) => {
    setPreference(next);
    storeLibraryKind(next);
  };
  const options: { id: LibraryKindPreference; label: string }[] = [
    { id: 'auto', label: t('settings.libraryKind.auto') },
    // The shelves the library always offers (#383, #597), never one it may not show.
    ...KINDS.map(({ id }) => ({ id, label: t(KIND_LABEL_KEY[id]) })),
  ];
  return (
    <>
      <h3 className={styles.sectionTitle} id="settings-library-kind">{t('settings.libraryKind.title')}</h3>
      <p className={styles.sectionIntro}>{t('settings.libraryKind.description')}</p>
      <div className={styles.chips} role="group" aria-labelledby="settings-library-kind">
        {options.map((option) => (
          <Chip key={option.id} selected={preference === option.id} onClick={() => pick(option.id)}>
            {option.label}
          </Chip>
        ))}
      </div>
    </>
  );
}
