import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  test: {
    coverage: {
      provider: 'v8',
      include: [
        'app/**/*.tsx',
        'article-page.tsx',
        'markdown.ts',
        'published-article.ts',
      ],
      reporter: ['text', 'html', 'lcov'],
      reportsDirectory: 'coverage',
      thresholds: {
        branches: 100,
        functions: 100,
        lines: 100,
        statements: 100,
      },
    },
    environment: 'jsdom',
    setupFiles: ['./tests/setup.ts'],
  },
});
