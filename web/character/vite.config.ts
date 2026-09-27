import { defineConfig } from 'vite';

// Gradle (app/build.gradle.kts, task buildCharacterWeb) passes --outDir so the bundle lands in the APK's generated
// assets under character/. The relative base makes it work at https://appassets.androidplatform.net/assets/character/.
export default defineConfig({
  base: './',
  build: { outDir: 'dist', emptyOutDir: true, target: 'es2022', chunkSizeWarningLimit: 1500 },
  server: { host: true },
});
