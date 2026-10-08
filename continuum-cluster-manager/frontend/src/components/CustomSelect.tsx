import { useState, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

interface SelectOption {
  value: string;
  label: string;
}

interface CustomSelectProps {
  id?: string;
  value: string;
  onChange: (value: string) => void;
  options: SelectOption[];
  placeholder?: string;
  required?: boolean;
}

export function CustomSelect({ id, value, onChange, options, placeholder = 'Select...', required }: CustomSelectProps) {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  const selectedOption = options.find((o) => o.value === value);

  // Close on outside click
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Close on escape
  useEffect(() => {
    function handleEscape(e: KeyboardEvent) {
      if (e.key === 'Escape') setIsOpen(false);
    }
    if (isOpen) {
      document.addEventListener('keydown', handleEscape);
      return () => document.removeEventListener('keydown', handleEscape);
    }
  }, [isOpen]);

  return (
    <div ref={containerRef} className="relative">
      {/* Hidden native input for form validation */}
      {required && (
        <input
          tabIndex={-1}
          value={value}
          required
          onChange={() => {}}
          className="absolute inset-0 h-0 w-0 opacity-0"
        />
      )}

      {/* Trigger button */}
      <button
        id={id}
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className={`flex w-full items-center justify-between rounded-lg border px-3 py-2 text-left text-sm transition-colors
          ${isOpen
            ? 'border-accent ring-1 ring-accent'
            : 'border-divider hover:border-accent/40'
          }
          bg-base focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent
        `}
      >
        <span className={selectedOption ? 'text-fg' : 'text-fg-muted/50'}>
          {selectedOption?.label ?? placeholder}
        </span>
        <svg
          className={`h-4 w-4 text-fg-muted transition-transform ${isOpen ? 'rotate-180' : ''}`}
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={2}
        >
          <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {/* Dropdown */}
      <AnimatePresence>
        {isOpen && (
          <motion.div
            initial={{ opacity: 0, y: -4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -4 }}
            transition={{ duration: 0.15 }}
            className="absolute z-20 mt-1 w-full overflow-hidden rounded-lg border border-divider bg-card shadow-lg"
          >
            <div className="max-h-48 overflow-y-auto py-1">
              {options.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  onClick={() => {
                    onChange(option.value);
                    setIsOpen(false);
                  }}
                  className={`flex w-full items-center justify-between px-3 py-2 text-left text-sm transition-colors
                    ${option.value === value
                      ? 'bg-accent/10 font-medium text-accent'
                      : 'text-fg hover:bg-surface'
                    }
                  `}
                >
                  <span>{option.label}</span>
                  {option.value === value && (
                    <svg className="h-4 w-4 text-accent" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                    </svg>
                  )}
                </button>
              ))}
              {options.length === 0 && (
                <p className="px-3 py-2 text-sm text-fg-muted">No options available</p>
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
