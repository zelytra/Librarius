import type { ReactNode } from 'react';
import { useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useGetApiMe } from '../../api/generated/librarius';
import { useApiAuth } from '../../shared/api';
import { LoginGate } from '../../shared/LoginGate';
import { Screen, ScreenTitle, Segmented } from '../../shared/ui/primitives';
import { EmptyState, Loading } from '../../shared/ui/states';
import { AdminReports } from './AdminReports';
import { AdminMonitoring } from './AdminMonitoring';

type Tab = 'reports' | 'monitoring';

/**
 * The admin screens (#488): the reports members sent and the errors recorded (#489, #490),
 * and the monitoring of the instance (#491). Only for the Keycloak realm role `admin`; the
 * API checks it on every call, this screen only spares everyone else a page of 403s.
 */
export function AdminPage() {
  const { t } = useTranslation();
  return (
    <Screen>
      <ScreenTitle>{t('admin.title')}</ScreenTitle>
      <LoginGate prompt={t('admin.prompt')}>
        <AdminOnly>
          <AdminTabs />
        </AdminOnly>
      </LoginGate>
    </Screen>
  );
}

function AdminOnly({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const { authed } = useApiAuth();
  const { data: me, isPending } = useGetApiMe({ query: { enabled: authed } });
  if (isPending) return <Loading />;
  if (!me?.admin) {
    return <EmptyState icon="lock" title={t('admin.restricted')} description={t('admin.restrictedHint')} />;
  }
  return <>{children}</>;
}

function AdminTabs() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const tab: Tab = params.get('tab') === 'monitoring' ? 'monitoring' : 'reports';
  return (
    <>
      <Segmented<Tab>
        value={tab}
        onChange={(next) => setParams(next === 'reports' ? {} : { tab: next }, { replace: true })}
        options={[
          { id: 'reports', label: t('admin.tabs.reports') },
          { id: 'monitoring', label: t('admin.tabs.monitoring') },
        ]}
      />
      {tab === 'reports' ? <AdminReports /> : <AdminMonitoring />}
    </>
  );
}
