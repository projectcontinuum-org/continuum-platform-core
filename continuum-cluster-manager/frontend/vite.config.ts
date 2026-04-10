import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  base: './',
  build: {
    outDir: 'dist',
  },
  server: {
    port: 5173,
    proxy: {
      // Proxy API requests to the cluster-manager backend during development
      '/api': {
        target: process.env.CLUSTER_MANAGER_URL || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
