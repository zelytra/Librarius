import { useApiAuth } from '../../shared/api';
import { ImportSection } from './ImportSection';
import { ExportSection } from './ExportSection';
import { DeleteAccountSection } from './DeleteAccountSection';
import { ResetLibrarySection } from './ResetLibrarySection';
import styles from './SettingsPage.module.css';

/** Settings' « Données et compte » (#546): bringing a library in, taking it out, or leaving. */
export function DataCategory() {
  const { authed } = useApiAuth();
  return (
    <>
      <div className={styles.sectionBlock} id="import">
        <ImportSection />
      </div>
      {/* Getting the data back out (GDPR art. 20). */}
      <div className={styles.sectionBlock} id="export">
        <ExportSection />
      </div>
      {/* Behind a confirmation: emptying the library, the account kept (#382). */}
      {authed && (
        <div className={styles.sectionBlock}>
          <ResetLibrarySection />
        </div>
      )}
      {/* Last, and behind a confirmation: erasing the account (GDPR art. 17). */}
      <div className={styles.sectionBlock}>
        <DeleteAccountSection />
      </div>
    </>
  );
}
