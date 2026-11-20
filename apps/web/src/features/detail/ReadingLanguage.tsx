import { useTranslation } from 'react-i18next';
import { activeLanguage } from '../../i18n/languages';
import styles from './DetailPage.module.css';

/**
 * The languages offered for a copy (#566): the ones books on this app are read in. Named by
 * the browser in the interface's language (`Intl.DisplayNames`), so no list of names to keep.
 */
const LANGUAGES = ['fr', 'en', 'ja', 'ko', 'zh', 'es', 'it', 'de', 'pt', 'nl'];

/** The language the reader read their copy in (#566); empty is unsaid. */
export function ReadingLanguage({
  value,
  disabled,
  onChange,
}: {
  value: string | null;
  disabled?: boolean;
  onChange: (language: string | null) => void;
}) {
  const { t } = useTranslation();
  const names = new Intl.DisplayNames([activeLanguage()], { type: 'language' });
  const label = (code: string) => {
    const name = names.of(code) ?? code;
    return name.charAt(0).toLocaleUpperCase(activeLanguage()) + name.slice(1);
  };
  // A code stored from elsewhere (an import) is still shown, even off the list.
  const options = value && !LANGUAGES.includes(value) ? [...LANGUAGES, value] : LANGUAGES;
  return (
    <label className={styles.languageField}>
      <span className={styles.languageLabel}>{t('detail.readingLanguage')}</span>
      <select
        className={styles.languageSelect}
        value={value ?? ''}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value || null)}
      >
        <option value="">{t('detail.readingLanguageUnset')}</option>
        {options.map((code) => (
          <option key={code} value={code}>
            {label(code)}
          </option>
        ))}
      </select>
    </label>
  );
}
