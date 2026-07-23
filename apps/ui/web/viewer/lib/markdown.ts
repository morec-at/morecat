import { defaultSchema, type Options } from 'rehype-sanitize';

export const articleSanitizeSchema: Options = {
  ...defaultSchema,
  attributes: {
    ...defaultSchema.attributes,
    iframe: ['height', 'src', 'title', 'width'],
  },
  tagNames: [
    ...(defaultSchema.tagNames as Array<string>),
    'figure',
    'figcaption',
    'iframe',
  ],
};
