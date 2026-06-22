import { defineConfig } from "vite";

// Pin a dedicated, uncommon port (and fail loudly if it's taken) so Chorect always
// lives at the same URL — avoids silently landing on another local dev server that
// already occupies Vite's default 5173.
export default defineConfig({
  server: { port: 5317, strictPort: true, open: true },
  preview: { port: 5317, strictPort: true },
});
