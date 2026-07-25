import { describe, expect, test } from 'vitest';

import { createApiClient } from '../src/index';

describe('createApiClient', () => {
  test('creates an article with bearer authentication and a typed body', async () => {
    const requests: Request[] = [];
    const fetch: typeof globalThis.fetch = async (input, init) => {
      requests.push(input instanceof Request ? input : new Request(input, init));
      return new Response(
        JSON.stringify({ articleId: '018f4edc-1f5a-7c4b-aef9-000000000001' }),
        {
          headers: { 'content-type': 'application/json' },
          status: 201,
        },
      );
    };
    const client = createApiClient({
      baseUrl: 'https://api.example.test',
      fetch,
      headers: { Authorization: 'Bearer test-token' },
    });

    const result = await client.POST('/articles', {
      body: { slug: 'hello-world', title: 'Hello', body: '# Hello' },
    });

    expect(requests).toHaveLength(1);
    expect(requests[0].url).toBe('https://api.example.test/articles');
    expect(requests[0].method).toBe('POST');
    expect(requests[0].headers.get('authorization')).toBe('Bearer test-token');
    await expect(requests[0].clone().json()).resolves.toEqual({
      slug: 'hello-world',
      title: 'Hello',
      body: '# Hello',
    });
    expect(result.data).toEqual({
      articleId: '018f4edc-1f5a-7c4b-aef9-000000000001',
    });
  });

  test('publishes an article with its expected version', async () => {
    const requests: Request[] = [];
    const fetch: typeof globalThis.fetch = async (input, init) => {
      requests.push(input instanceof Request ? input : new Request(input, init));
      return new Response(null, { status: 204 });
    };
    const client = createApiClient({
      baseUrl: 'https://api.example.test',
      fetch,
      headers: { Authorization: 'Bearer test-token' },
    });
    const articleId = '018f4edc-1f5a-7c4b-aef9-000000000001';

    const result = await client.POST('/articles/{articleId}/publish', {
      params: { path: { articleId } },
      body: { expectedVersion: 1 },
    });

    expect(requests).toHaveLength(1);
    expect(requests[0].url).toBe(
      `https://api.example.test/articles/${articleId}/publish`,
    );
    expect(requests[0].method).toBe('POST');
    expect(requests[0].headers.get('authorization')).toBe('Bearer test-token');
    await expect(requests[0].clone().json()).resolves.toEqual({ expectedVersion: 1 });
    expect(result.response.status).toBe(204);
  });

  test('retains a slug conflict response when creating an article', async () => {
    const fetch: typeof globalThis.fetch = async () =>
      new Response(null, { status: 409 });
    const client = createApiClient({ baseUrl: 'https://api.example.test', fetch });

    const result = await client.POST('/articles', {
      body: { slug: 'reserved', title: 'Hello', body: '# Hello' },
    });

    expect(result.response.status).toBe(409);
    expect(result.data).toBeUndefined();
  });

  test('retains an unauthorized response when publishing an article', async () => {
    const fetch: typeof globalThis.fetch = async () =>
      new Response(null, { status: 401 });
    const client = createApiClient({ baseUrl: 'https://api.example.test', fetch });

    const result = await client.POST('/articles/{articleId}/publish', {
      params: {
        path: { articleId: '018f4edc-1f5a-7c4b-aef9-000000000001' },
      },
      body: { expectedVersion: 1 },
    });

    expect(result.response.status).toBe(401);
    expect(result.data).toBeUndefined();
  });

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
