interface StatusBadgeProps {
  paused: boolean;
}

export function StatusBadge({ paused }: StatusBadgeProps) {
  const className = paused ? 'status-paused' : 'status-active';
  const label = paused ? 'Paused' : 'Active';

  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${className}`}>
      <span className="h-2 w-2 rounded-full bg-current" />
      {label}
    </span>
  );
}
