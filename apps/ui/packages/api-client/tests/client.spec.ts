import { describe, expect, test } from 'vitest';

import { createApiClient } from '../src/index.js';

describe('createApiClient', () => {
  test('gets a published article through the generated path contract', async () => {
    const requestedUrls: string[] = [];
    const article = {
      articleId: '018f4edc-1f5a-7c4b-aef9-000000000001',
      slug: 'hello-world',
      title: 'Hello',
      body: '# Hello',
      publishedAt: 1234,
    };
    const fetch: typeof globalThis.fetch = async (input) => {
      requestedUrls.push(input instanceof Request ? input.url : input.toString());
      return new Response(JSON.stringify(article), {
        headers: { 'content-type': 'application/json' },
        status: 200,
      });
    };
    const client = createApiClient({ baseUrl: 'https://api.example.test', fetch });

    const result = await client.GET('/articles/{slug}', {
      params: { path: { slug: 'hello-world' } },
    });

    expect(requestedUrls).toEqual(['https://api.example.test/articles/hello-world']);
    expect(result.data).toEqual(article);
  });

  test('retains the HTTP response when the article is not found', async () => {
    const fetch: typeof globalThis.fetch = async () =>
      new Response(null, { status: 404 });
    const client = createApiClient({ baseUrl: 'https://api.example.test', fetch });

    const result = await client.GET('/articles/{slug}', {
      params: { path: { slug: 'missing' } },
    });

    expect(result.response.status).toBe(404);
    expect(result.data).toBeUndefined();
  });
});
