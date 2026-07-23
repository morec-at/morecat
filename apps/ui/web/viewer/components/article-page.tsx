import type { paths } from '@morecat/api-client';
import Markdown from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';

import { articleSanitizeSchema } from '../lib/markdown';

export type PublishedArticle = NonNullable<
  paths['/articles/{slug}']['get']['responses'][200]['content']['application/json']
>;

export function ArticlePage({ article }: { article: PublishedArticle }) {
  return (
    <article>
      <h1>{article.title}</h1>
      <div className="article-body">
        <Markdown
          rehypePlugins={[rehypeRaw, [rehypeSanitize, articleSanitizeSchema]]}
          remarkPlugins={[remarkGfm]}
        >
          {article.body}
        </Markdown>
      </div>
    </article>
  );
}
