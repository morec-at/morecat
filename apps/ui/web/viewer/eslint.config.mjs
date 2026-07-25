import nextVitals from 'eslint-config-next/core-web-vitals';

const config = [
  ...nextVitals,
  {
    ignores: ['.next/**', 'coverage/**', 'next-env.d.ts'],
  },
];

export default config;
