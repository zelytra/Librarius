import type { ReactNode } from 'react';
import { useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useGetApiMe } from '../../api/generated/librarius';
import { useApiAuth } from '../../shared/api';
import { LoginGate } from '../../shared/LoginGate';
import { Screen, ScreenTitle, Segmented } from '../../shared/ui/primitives';
import { EmptyState, Loading } from '../../shared/ui/states';
import { AdminReports } from './AdminReports';
import { AdminCatalog } from './AdminCatalog';
import { AdminMonitoring } from './AdminMonitoring';
import { AdminUsers } from './AdminUsers';

type Tab = 'reports' | 'catalog' | 'users' | 'monitoring';

/**
 * The admin screens (#488): the reports members sent and the errors recorded (#489, #490),
 * the moderation of the shared catalog (#618, #621), the members (#499) and the monitoring of
 * the instance (#491). Only for the Keycloak realm role `admin`; the
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
  const asked = params.get('tab');
  const tab: Tab = asked === 'monitoring' || asked === 'users' || asked === 'catalog' ? asked : 'reports';
  return (
    <>
      <Segmented<Tab>
        value={tab}
        onChange={(next) => setParams(next === 'reports' ? {} : { tab: next }, { replace: true })}
        options={[
          { id: 'reports', label: t('admin.tabs.reports') },
          { id: 'catalog', label: t('admin.tabs.catalog') },
          { id: 'users', label: t('admin.tabs.users') },
          { id: 'monitoring', label: t('admin.tabs.monitoring') },
        ]}
      />
      {tab === 'reports' ? (
        <AdminReports />
      ) : tab === 'catalog' ? (
        <AdminCatalog />
      ) : tab === 'users' ? (
        <AdminUsers />
      ) : (
        <AdminMonitoring />
      )}
    </>
  );
}
