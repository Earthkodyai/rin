import { defineConfig } from 'vite';

// Gradle (app/build.gradle.kts, task buildCharacterWeb) passes --outDir so the bundle lands in the APK's generated
// assets under character/. The relative base makes it work at https://appassets.androidplatform.net/assets/character/.
export default defineConfig({
  base: './',
  build: { outDir: 'dist', emptyOutDir: true, target: 'es2022', chunkSizeWarningLimit: 1500 },
  // `npm run dev` can load the debug-only VRoid sample (git-ignored) from the app module:
  // http://localhost:5173/?model=/@fs/<repo>/app/src/debug/assets/character/model/dev.vrm&mood=cheerful
  server: { host: true, fs: { allow: ['.', '../../app/src/debug/assets/character/model'] } },
});
