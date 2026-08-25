interface ExecutionStatusBadgeProps {
  status: string;
}

export function ExecutionStatusBadge({ status }: ExecutionStatusBadgeProps) {
  const className = status === 'SUCCESS' ? 'status-success' : 'status-failed';

  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${className}`}>
      <span className="h-2 w-2 rounded-full bg-current" />
      {status}
    </span>
  );
}
