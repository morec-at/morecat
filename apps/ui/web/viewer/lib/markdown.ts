import { defaultSchema, type Options } from 'rehype-sanitize';

export const articleSanitizeSchema: Options = {
  ...defaultSchema,
  tagNames: [
    ...(defaultSchema.tagNames as Array<string>),
    'figure',
    'figcaption',
  ],
};
