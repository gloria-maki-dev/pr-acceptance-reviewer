import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';

// Los diffs de ejemplo viven en examples/, en la raíz del repositorio, para que la pantalla y
// las pruebas del backend usen exactamente los mismos archivos.
const repoRoot = fileURLToPath(new URL('..', import.meta.url));

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    fs: {
      allow: [repoRoot],
    },
    // El navegador solo habla con el frontend; el backend queda detrás de este proxy y la
    // credencial del modelo nunca sale del servidor.
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
