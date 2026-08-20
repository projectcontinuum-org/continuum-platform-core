import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  base: './',
  build: { outDir: 'dist' },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.KNIME_SCHEDULER_URL || 'http://localhost:8084',
        changeOrigin: true,
      },
    },
  },
});
