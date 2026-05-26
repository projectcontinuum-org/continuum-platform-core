import { motion, AnimatePresence } from 'framer-motion';

interface KeyValueEditorProps {
  data: Record<string, string>;
  onChange: (data: Record<string, string>) => void;
}

export function KeyValueEditor({ data, onChange }: KeyValueEditorProps) {
  const entries = Object.entries(data);

  const addField = () => {
    const newKey = `key-${entries.length + 1}`;
    onChange({ ...data, [newKey]: '' });
  };

  const removeField = (key: string) => {
    const next = { ...data };
    delete next[key];
    onChange(next);
  };

  const updateKey = (oldKey: string, newKey: string) => {
    if (oldKey === newKey) return;
    const next: Record<string, string> = {};
    for (const [k, v] of Object.entries(data)) {
      next[k === oldKey ? newKey : k] = v;
    }
    onChange(next);
  };

  const updateValue = (key: string, value: string) => {
    onChange({ ...data, [key]: value });
  };

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <label className="block text-sm font-medium text-fg">
          Key-Value Pairs
        </label>
        <button
          type="button"
          onClick={addField}
          className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-accent transition-colors hover:bg-accent/10"
        >
          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
          </svg>
          Add Field
        </button>
      </div>

      <AnimatePresence mode="popLayout">
        {entries.map(([key, value], index) => (
          <motion.div
            key={index}
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            transition={{ duration: 0.2 }}
            className="flex items-start gap-2"
          >
            <input
              type="text"
              value={key}
              onChange={(e) => updateKey(key, e.target.value)}
              placeholder="Key"
              className="w-1/3 rounded-lg border border-divider bg-base px-3 py-2 text-sm text-fg placeholder:text-fg-muted/50 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
            />
            <input
              type="text"
              value={value}
              onChange={(e) => updateValue(key, e.target.value)}
              placeholder="Value"
              className="flex-1 rounded-lg border border-divider bg-base px-3 py-2 text-sm text-fg placeholder:text-fg-muted/50 focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent"
            />
            <button
              type="button"
              onClick={() => removeField(key)}
              className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-fg-muted transition-colors hover:bg-red-100 hover:text-red-600 dark:hover:bg-red-900/30 dark:hover:text-red-400"
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </motion.div>
        ))}
      </AnimatePresence>

      {entries.length === 0 && (
        <p className="py-4 text-center text-sm text-fg-muted">
          No fields yet. Click "Add Field" to add key-value pairs.
        </p>
      )}
    </div>
  );
}
