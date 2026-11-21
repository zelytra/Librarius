import { useQuery } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Screen } from '../../shared/ui/primitives';
import { Icon } from '../../shared/ui/Icon';
import { ErrorState, Loading } from '../../shared/ui/states';
import { LEGAL_CONTENT, LEGAL_DOCS, type LegalDoc } from './content';
import { LegalLinks } from './LegalLinks';
import styles from './Legal.module.css';

const isDoc = (value: string | undefined): value is LegalDoc => LEGAL_DOCS.some((doc) => doc === value);

/**
 * A legal document (#74) — terms of service, privacy policy or legal notice — at
 * `/legal/<doc>`. Outside the shell and its sign-in gates: anyone can read it, with or without
 * an account, and the acceptance request links here without leaving a member stuck behind it.
 */
export function LegalPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { doc: param } = useParams();
  const doc: LegalDoc = isDoc(param) ? param : 'terms';
  const language = i18n.resolvedLanguage && LEGAL_CONTENT[i18n.resolvedLanguage] ? i18n.resolvedLanguage : 'fr';
  const content = useQuery({
    queryKey: ['legal', language],
    queryFn: () => LEGAL_CONTENT[language]().then((module) => module.default),
    staleTime: Infinity,
  });

  // Back to wherever the reader came from; a link opened on its own goes Home.
  const back = () => (window.history.length > 1 ? navigate(-1) : navigate('/'));

  return (
    <Screen measure="reading">
      <div className={`sticky-header ${styles.header}`}>
        <button type="button" onClick={back} aria-label={t('common.back')} className={styles.backButton}>
          <Icon name="arrow_back" size={24} color="var(--ink)" />
        </button>
      </div>
      {content.isPending ? (
        <Loading />
      ) : content.isError ? (
        <ErrorState message={t('legal.error')} onRetry={() => void content.refetch()} />
      ) : (
        <article className={styles.document}>
          <h1 className={styles.title}>{content.data[doc].title}</h1>
          <p className={styles.updated}>{content.data[doc].updated}</p>
          {content.data[doc].sections.map((section) => (
            <section key={section.heading} className={styles.section}>
              <h2 className={styles.heading}>{section.heading}</h2>
              {section.paragraphs.map((paragraph) => (
                <p key={paragraph} className={styles.paragraph}>
                  {paragraph}
                </p>
              ))}
            </section>
          ))}
        </article>
      )}
      <div className={styles.footer}>
        <LegalLinks current={doc} />
      </div>
    </Screen>
  );
}
