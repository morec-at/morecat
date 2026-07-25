import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, test } from 'vitest';

import RootLayout, { metadata } from '../app/layout';

describe('RootLayout', () => {
  test('renders Japanese document metadata and its children', () => {
    const html = renderToStaticMarkup(
      <RootLayout>
        <main>morecat</main>
      </RootLayout>,
    );

    expect(metadata).toMatchObject({ title: 'morecat' });
    expect(html).toContain('<html lang="ja">');
    expect(html).toContain('<body><main>morecat</main></body>');
  });
});
