import { defineConfig } from 'vite';

// Built into ../app/build/web (added to the APK assets by Gradle). Relative base
// so it works under https://appassets.androidplatform.net/assets/.
export default defineConfig({
  base: './',
  build: { outDir: '../app/build/web', emptyOutDir: true, target: 'es2022' },
  server: { host: true },
});
