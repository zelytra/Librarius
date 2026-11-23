import { Link } from 'react-router';
import i18n from 'i18next';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { FAQ_SECTIONS } from './faq';
import fr from './faq.fr.json';
import en from './faq.en.json';
import styles from './HelpCategory.module.css';

// The FAQ words ride with this lazy chunk, as a namespace of their own (see faq.ts).
i18n.addResourceBundle('fr', 'faq', fr, true, true);
i18n.addResourceBundle('en', 'faq', en, true, true);

/**
 * Settings › "Un problème ?" (#630): the questions members ask most, by theme, each answer
 * folded behind its question (`<details>`, keyboard and screen-reader friendly), with a
 * shortcut to the screen it is about.
 */
export function HelpCategory() {
  const { t } = useTranslation('faq');
  return (
    <div className={styles.help}>
      <p className={styles.intro}>{t('intro')}</p>
      {FAQ_SECTIONS.map((section) => (
        <section key={section.id} className={styles.section} aria-labelledby={`faq-${section.id}`}>
          <h3 id={`faq-${section.id}`} className={styles.sectionTitle}>
            {t(`sections.${section.id}`)}
          </h3>
          {section.questions.map((question) => (
            <details key={question.id} className={styles.item}>
              <summary className={styles.question}>
                <span>{t(`questions.${question.id}.q`)}</span>
                <Icon name="keyboard_arrow_down" size={20} color="var(--ink-soft)" />
              </summary>
              <div className={styles.answer}>
                <p>{t(`questions.${question.id}.a`)}</p>
                {question.actions && (
                  <div className={styles.actions}>
                    {question.actions.map((action) => (
                      <Link key={action.to + action.label} to={action.to} className={styles.action}>
                        {t(`actions.${action.label}`)}
                        <Icon name="arrow_forward" size={16} color="currentColor" />
                      </Link>
                    ))}
                  </div>
                )}
              </div>
            </details>
          ))}
        </section>
      ))}
    </div>
  );
}
