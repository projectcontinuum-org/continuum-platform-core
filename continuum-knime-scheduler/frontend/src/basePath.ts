// Detects the /ui mount point from the current path so the same build works
// whether served directly at /ui/ or behind the gateway at /knime-scheduler/ui/.
const match = window.location.pathname.match(/^(.*?)\/ui(\/|$)/);
const mountPrefix = match ? match[1] : '';

export const UI_BASE = `${mountPrefix}/ui`;
export const SERVICE_BASE = mountPrefix;

export function assetPath(path: string): string {
  return `${UI_BASE}/${path}`;
}
