import createClient, { type Client, type ClientOptions } from 'openapi-fetch';

import type { paths } from './schema.js';

export function createApiClient(options: ClientOptions): Client<paths> {
  return createClient<paths>(options);
}
