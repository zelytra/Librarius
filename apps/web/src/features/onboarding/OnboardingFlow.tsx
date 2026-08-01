import { useEffect, useId, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Icon } from '../../shared/ui/Icon';
import { Button } from '../../shared/ui/primitives';
import styles from './OnboardingFlow.module.css';

interface OnboardingStep {
  icon: string;
  titleKey: string;
  descriptionKey: string;
  actionKey: string;
  /** Where the primary action leads — the whole point of naming the action at all. */
  route: string;
}

/**
 * The three things a new account is worth being told about, in the order they matter
 * most to someone coming with an existing library (#76): bring it over first, search
 * the catalog for what a Booknode/CSV export cannot carry, and only then the optional
 * annual goal — a number nobody sets before they own a single title.
 */
const STEPS: OnboardingStep[] = [
  {
    icon: 'upload_file',
    titleKey: 'onboarding.steps.import.title',
    descriptionKey: 'onboarding.steps.import.description',
    actionKey: 'onboarding.steps.import.action',
    route: '/settings',
  },
  {
    icon: 'search',
    titleKey: 'onboarding.steps.discover.title',
    descriptionKey: 'onboarding.steps.discover.description',
    actionKey: 'onboarding.steps.discover.action',
    route: '/discover',
  },
  {
    icon: 'flag',
    titleKey: 'onboarding.steps.goal.title',
    descriptionKey: 'onboarding.steps.goal.description',
    actionKey: 'onboarding.steps.goal.action',
    route: '/settings',
  },
];

/**
 * The short first-run tour (#76): what to do with an empty library, in three steps.
 *
 * <p>Shares the sheet shape the end-of-reading flow already uses (`OutcomeSheet`) — a
 * scrim, a centred panel, Escape and an outside click both closing it — so a new
 * account meets a pattern this application repeats rather than a one-off modal.
 *
 * <p>Every step's primary action **leaves** the tour for the screen it names, which is
 * the point: the flow orients, it does not try to hold the reader inside itself. The
 * "later" way out — the skip link, Escape, the outside click, the close button — all
 * call the same {@link onDismiss}, because nothing here distinguishes an abandoned tour
 * from a finished one; both mean "do not offer this again".
 */
export function OnboardingFlow({ onDismiss }: { onDismiss: () => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const headingId = useId();
  const panel = useRef<HTMLDivElement>(null);
  const [index, setIndex] = useState(0);

  const step = STEPS[index];
  const isLast = index === STEPS.length - 1;

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onDismiss();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onDismiss]);

  useEffect(() => {
    panel.current?.focus();
  }, []);

  const goToStep = () => {
    onDismiss();
    navigate(step.route);
  };

  return (
    <div className={styles.scrim} onClick={onDismiss}>
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={headingId}
        tabIndex={-1}
        className={styles.panel}
        onClick={(event) => event.stopPropagation()}
      >
        <button
          type="button"
          onClick={onDismiss}
          aria-label={t('onboarding.close')}
          className={styles.close}
        >
          <Icon name="close" size={20} color="var(--ink-soft)" />
        </button>

        <p className={styles.eyebrow}>{t('onboarding.eyebrow')}</p>

        <div className={styles.iconWrap}>
          <Icon name={step.icon} size={28} color="var(--accent-deep)" />
        </div>

        <h3 id={headingId} className={styles.title}>
          {t(step.titleKey)}
        </h3>
        <p className={styles.description}>{t(step.descriptionKey)}</p>

        <div className={styles.dots} aria-hidden="true">
          {STEPS.map((s, i) => (
            <span key={s.titleKey} className={`${styles.dot} ${i === index ? styles.dotActive : ''}`} />
          ))}
        </div>

        <div className={styles.actions}>
          <Button variant="primary" size="block" onClick={goToStep}>
            {t(step.actionKey)}
          </Button>
          {isLast ? (
            <Button variant="ghost" size="block" onClick={onDismiss}>
              {t('onboarding.done')}
            </Button>
          ) : (
            <Button variant="ghost" size="block" onClick={() => setIndex((i) => i + 1)}>
              {t('onboarding.next')}
            </Button>
          )}
        </div>

        <button type="button" onClick={onDismiss} className={styles.skip}>
          {t('onboarding.skip')}
        </button>
      </div>
    </div>
  );
}
