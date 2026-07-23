import { createApiClient } from '@morecat/api-client';

import type { PublishedArticle } from '../components/article-page';

export interface PublishedArticleLoaderOptions {
  apiBaseUrl?: string;
  fetch?: typeof globalThis.fetch;
}

export class PublishedArticleNotFoundError extends Error {
  constructor(slug: string) {
    super(`Published article not found: ${slug}`);
    this.name = 'PublishedArticleNotFoundError';
  }
}

export class PublishedArticleUpstreamError extends Error {
  constructor(status: number) {
    super(`Published article API failed with status ${status}`);
    this.name = 'PublishedArticleUpstreamError';
  }
}

export class ViewerConfigurationError extends Error {
  constructor() {
    super('MORECAT_API_BASE_URL is required');
    this.name = 'ViewerConfigurationError';
  }
}

export async function loadPublishedArticle(
  slug: string,
  options: PublishedArticleLoaderOptions = {},
): Promise<PublishedArticle> {
  const apiBaseUrl = options.apiBaseUrl ?? process.env.MORECAT_API_BASE_URL;
  if (!apiBaseUrl) {
    throw new ViewerConfigurationError();
  }

  const fetch = options.fetch ?? globalThis.fetch;
  const noStoreFetch: typeof globalThis.fetch = (input, init) =>
    fetch(new Request(input, { ...init, cache: 'no-store' }));
  const client = createApiClient({ baseUrl: apiBaseUrl, fetch: noStoreFetch });
  const result = await client.GET('/articles/{slug}', {
    params: { path: { slug } },
  });

  if (result.response.status === 404) {
    throw new PublishedArticleNotFoundError(slug);
  }
  if (!result.data) {
    throw new PublishedArticleUpstreamError(result.response.status);
  }
  return result.data;
}
