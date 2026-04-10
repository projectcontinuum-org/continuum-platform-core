// Detect the service prefix from the current URL so the app works
// both when served directly at /ui/ and behind a gateway prefix like /cluster-manager/ui/.
const match = window.location.pathname.match(/^(.*?)\/ui(\/|$)/);

// The gateway prefix before /ui (e.g., '/cluster-manager' or '')
export const SERVICE_BASE = match?.[1] ?? '';

// The full base path for React Router (e.g., '/cluster-manager/ui' or '/ui')
export const UI_BASE = `${SERVICE_BASE}/ui`;

// Resolve a path relative to the UI base (e.g., assetPath('Logo.png') => '/cluster-manager/ui/Logo.png')
export function assetPath(path: string): string {
  return `${UI_BASE}/${path}`;
}
