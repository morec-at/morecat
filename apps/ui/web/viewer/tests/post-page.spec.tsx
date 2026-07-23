import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, test, vi } from 'vitest';

const { notFound } = vi.hoisted(() => ({
  notFound: vi.fn(() => {
    throw new Error('NEXT_NOT_FOUND');
  }),
}));

vi.mock('next/navigation', () => ({ notFound }));

import PostPage from '../app/posts/[slug]/page';
import { PublishedArticleUpstreamError } from '../published-article';

describe('/posts/{slug}', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  test('turns an API 404 into the Next.js not-found boundary', async () => {
    vi.stubEnv('MORECAT_API_BASE_URL', 'https://api.example.test');
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof globalThis.fetch>(async () =>
        Promise.resolve(new Response(null, { status: 404 })),
      ),
    );

    await expect(
      PostPage({ params: Promise.resolve({ slug: 'draft' }) }),
    ).rejects.toThrow('NEXT_NOT_FOUND');
    expect(notFound).toHaveBeenCalledOnce();
  });

  test('renders the live API response', async () => {
    vi.stubEnv('MORECAT_API_BASE_URL', 'https://api.example.test');
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof globalThis.fetch>(async () =>
        Promise.resolve(
          new Response(
            JSON.stringify({
              articleId: '018f4edc-1f5a-7c4b-aef9-000000000001',
              slug: 'hello-world',
              title: 'Hello from the API',
              body: '# Live body',
              publishedAt: 1_720_000_000_000,
            }),
            {
              headers: { 'content-type': 'application/json' },
              status: 200,
            },
          ),
        ),
      ),
    );

    render(
      await PostPage({
        params: Promise.resolve({ slug: 'hello-world' }),
      }),
    );

    expect(
      screen.getByRole('heading', { level: 1, name: 'Hello from the API' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { level: 1, name: 'Live body' }),
    ).toBeInTheDocument();
  });

  test('does not disguise an upstream failure as a missing article', async () => {
    vi.stubEnv('MORECAT_API_BASE_URL', 'https://api.example.test');
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof globalThis.fetch>(async () =>
        Promise.resolve(new Response(null, { status: 503 })),
      ),
    );

    await expect(
      PostPage({ params: Promise.resolve({ slug: 'hello-world' }) }),
    ).rejects.toBeInstanceOf(PublishedArticleUpstreamError);
    expect(notFound).not.toHaveBeenCalled();
  });
});
