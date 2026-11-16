import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';

// The example diffs live in examples/, at the repository root, so the screen and the backend
// tests use exactly the same files.
const repoRoot = fileURLToPath(new URL('..', import.meta.url));

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    fs: {
      allow: [repoRoot],
    },
    // The browser only talks to the frontend; the backend sits behind this proxy and the model
    // credential never leaves the server.
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test-setup.ts',
  },
});
