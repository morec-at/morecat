import { render, screen } from '@testing-library/react';
import { describe, expect, test } from 'vitest';

import { ArticlePage } from '../components/article-page';

describe('ArticlePage', () => {
  test('renders a published article returned by the generated API contract', () => {
    render(
      <ArticlePage
        article={{
          articleId: '018f4edc-1f5a-7c4b-aef9-000000000001',
          slug: 'hello-world',
          title: 'Hello, morecat',
          body: 'A first post.',
          publishedAt: 1_720_000_000_000,
        }}
      />,
    );

    expect(
      screen.getByRole('heading', { level: 1, name: 'Hello, morecat' }),
    ).toBeInTheDocument();
    expect(screen.getByText('A first post.')).toBeInTheDocument();
  });

  test('renders GitHub Flavored Markdown as semantic HTML', () => {
    const { container } = render(
      <ArticlePage
        article={{
          articleId: '018f4edc-1f5a-7c4b-aef9-000000000001',
          slug: 'gfm',
          title: 'GFM',
          body: '~~old~~\n\n| cat | mood |\n| --- | --- |\n| Mugi | happy |',
          publishedAt: 1_720_000_000_000,
        }}
      />,
    );

    expect(container.querySelector('del')).toHaveTextContent('old');
    expect(screen.getByRole('table')).toHaveTextContent('Mugi');
  });

  test('sanitizes malicious raw HTML and URLs before rendering', () => {
    const { container } = render(
      <ArticlePage
        article={{
          articleId: '018f4edc-1f5a-7c4b-aef9-000000000001',
          slug: 'safe-markdown',
          title: 'Safe Markdown',
          body: [
            '<em>allowed emphasis</em>',
            '<script>alert(1)</script>',
            '<img src="/cat.png" alt="cat" onerror="alert(2)">',
            '[unsafe link](javascript:alert(3))',
          ].join('\n\n'),
          publishedAt: 1_720_000_000_000,
        }}
      />,
    );

    expect(container.querySelector('em')).toHaveTextContent('allowed emphasis');
    expect(container.querySelector('script')).not.toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'cat' })).not.toHaveAttribute(
      'onerror',
    );
    expect(screen.getByText('unsafe link').closest('a')).not.toHaveAttribute(
      'href',
    );
  });
});
