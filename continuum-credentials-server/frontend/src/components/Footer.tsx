import { assetPath } from '../basePath';

export function Footer() {
  return (
    <footer className="border-t border-divider bg-base" role="contentinfo">
      <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
        <div className="flex flex-col items-center justify-between gap-4 sm:flex-row">
          <div className="flex items-center gap-2">
            <img src={assetPath('Logo.png')} alt="Continuum logo" className="h-6 w-6" />
            <span className="text-gradient font-semibold">Continuum Credential Manager</span>
          </div>

          <div className="flex items-center gap-6 text-sm text-fg-muted">
            <a
              href="https://github.com/projectcontinuum/continuum-platform-core"
              target="_blank"
              rel="noopener noreferrer"
              className="transition-colors hover:text-fg"
            >
              GitHub
            </a>
            <a
              href="https://github.com/projectcontinuum/continuum-platform-core/issues"
              target="_blank"
              rel="noopener noreferrer"
              className="transition-colors hover:text-fg"
            >
              Issues
            </a>
          </div>
        </div>

        <div className="mt-6 text-center text-sm text-fg-muted/70">
          Apache 2.0 License &middot; &copy; {new Date().getFullYear()} Project Continuum contributors.
        </div>
      </div>
    </footer>
  );
}
