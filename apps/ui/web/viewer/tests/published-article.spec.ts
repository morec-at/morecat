import { describe, expect, test, vi } from 'vitest';

import {
  loadPublishedArticle,
  PublishedArticleNotFoundError,
  PublishedArticleUpstreamError,
  ViewerConfigurationError,
} from '../lib/published-article';

describe('loadPublishedArticle', () => {
  test('loads an article through the generated client without caching it', async () => {
    const article = {
      articleId: '018f4edc-1f5a-7c4b-aef9-000000000001',
      slug: 'hello-world',
      title: 'Hello',
      body: '# Hello',
      publishedAt: 1_720_000_000_000,
    };
    const fetch = vi.fn<typeof globalThis.fetch>(async () =>
      Promise.resolve(
        new Response(JSON.stringify(article), {
          headers: { 'content-type': 'application/json' },
          status: 200,
        }),
      ),
    );

    await expect(
      loadPublishedArticle('hello-world', {
        apiBaseUrl: 'https://api.example.test',
        fetch,
      }),
    ).resolves.toEqual(article);

    expect(fetch).toHaveBeenCalledOnce();
    const request = fetch.mock.calls[0]?.[0];
    expect(request).toBeInstanceOf(Request);
    expect((request as Request).url).toBe(
      'https://api.example.test/articles/hello-world',
    );
    expect((request as Request).cache).toBe('no-store');
  });

  test('distinguishes a non-public route from an upstream failure', async () => {
    const invalidSlugFetch: typeof globalThis.fetch = async () =>
      new Response(null, { status: 400 });
    const notFoundFetch: typeof globalThis.fetch = async () =>
      new Response(null, { status: 404 });
    const failedFetch: typeof globalThis.fetch = async () =>
      new Response(null, { status: 503 });

    await expect(
      loadPublishedArticle('Invalid Slug', {
        apiBaseUrl: 'https://api.example.test',
        fetch: invalidSlugFetch,
      }),
    ).rejects.toBeInstanceOf(PublishedArticleNotFoundError);
    await expect(
      loadPublishedArticle('draft', {
        apiBaseUrl: 'https://api.example.test',
        fetch: notFoundFetch,
      }),
    ).rejects.toBeInstanceOf(PublishedArticleNotFoundError);
    await expect(
      loadPublishedArticle('published', {
        apiBaseUrl: 'https://api.example.test',
        fetch: failedFetch,
      }),
    ).rejects.toBeInstanceOf(PublishedArticleUpstreamError);
  });

  test('fails clearly when the server API base URL is not configured', async () => {
    await expect(
      loadPublishedArticle('hello-world', { apiBaseUrl: '' }),
    ).rejects.toBeInstanceOf(ViewerConfigurationError);
  });
});
