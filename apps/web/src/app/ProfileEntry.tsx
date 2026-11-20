import { NavLink } from 'react-router';
import { useTranslation } from 'react-i18next';
import { Avatar } from '../shared/ui/Avatar';
import type { MeDto } from '../api/generated/librarius';

/**
 * The reader's own profile in the navigation (#555): their picture stands for the icon and
 * their name for the label, so the entry says whose account this is at a glance.
 */
export function ProfileEntry({
  me,
  className,
  labelClassName,
  onClick,
}: {
  me: MeDto;
  className: string;
  labelClassName?: string;
  onClick?: () => void;
}) {
  const { t } = useTranslation();
  const name = me.displayName || t('nav.profile');
  return (
    <NavLink
      to={`/people/${me.id}`}
      className={className}
      onClick={onClick}
      aria-label={t('nav.profileOf', { name })}
    >
      <Avatar id={me.id} name={name} version={me.avatarVersion} size={26} decorative />
      <span className={labelClassName}>{name}</span>
    </NavLink>
  );
}
