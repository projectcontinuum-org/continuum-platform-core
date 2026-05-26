const TYPE_COLORS: Record<string, string> = {
  aws: 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-400',
  database: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
  git: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
  'basic-auth': 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400',
  generic: 'bg-gray-100 text-gray-800 dark:bg-gray-900/30 dark:text-gray-400',
};

const DEFAULT_COLOR = 'bg-accent/10 text-accent';

interface TypeBadgeProps {
  type: string;
}

export function TypeBadge({ type }: TypeBadgeProps) {
  const normalised = type.toLowerCase();
  const colorClass = TYPE_COLORS[normalised] ?? DEFAULT_COLOR;

  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${colorClass}`}>
      {type}
    </span>
  );
}
