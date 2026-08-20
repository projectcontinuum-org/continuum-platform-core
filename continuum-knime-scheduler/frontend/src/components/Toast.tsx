import { motion, AnimatePresence } from 'framer-motion';

export interface ToastNotification {
  type: 'success' | 'error';
  message: string;
}

interface ToastProps {
  notification: ToastNotification | null;
  onDismiss: () => void;
}

export function Toast({ notification, onDismiss }: ToastProps) {
  return (
    <AnimatePresence>
      {notification && (
        <motion.div
          initial={{ opacity: 0, y: 50, x: '-50%' }}
          animate={{ opacity: 1, y: 0, x: '-50%' }}
          exit={{ opacity: 0, y: 50, x: '-50%' }}
          className={`fixed bottom-6 left-1/2 z-50 flex items-center gap-3 rounded-lg px-4 py-3 shadow-lg ${
            notification.type === 'success' ? 'bg-green-600 text-white' : 'bg-red-600 text-white'
          }`}
        >
          {notification.type === 'success' ? (
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
            </svg>
          ) : (
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          )}
          <span className="text-sm font-medium">{notification.message}</span>
          <button onClick={onDismiss} className="ml-2 rounded p-1 hover:bg-white/20">
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
