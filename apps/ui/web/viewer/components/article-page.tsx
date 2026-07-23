import type { paths } from '@morecat/api-client';
import Markdown, { type Components } from 'react-markdown';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';

import { articleSanitizeSchema } from '../lib/markdown';

export type PublishedArticle = NonNullable<
  paths['/articles/{slug}']['get']['responses'][200]['content']['application/json']
>;

const trustedYouTubeEmbed =
  /^https:\/\/www\.youtube(?:-nocookie)?\.com\/embed\/[A-Za-z0-9_-]+(?:\?[^"<>]*)?$/;

const markdownComponents: Components = {
  iframe(properties) {
    const { node, src, ...iframeProperties } = properties;
    void node;

    if (!trustedYouTubeEmbed.test(src ?? '')) {
      return null;
    }

    return (
      <iframe
        {...iframeProperties}
        allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture"
        allowFullScreen
        loading="lazy"
        referrerPolicy="strict-origin-when-cross-origin"
        sandbox="allow-scripts allow-same-origin allow-presentation"
        src={src}
      />
    );
  },
};

export function ArticlePage({ article }: { article: PublishedArticle }) {
  return (
    <article>
      <h1>{article.title}</h1>
      <div className="article-body">
        <Markdown
          components={markdownComponents}
          rehypePlugins={[rehypeRaw, [rehypeSanitize, articleSanitizeSchema]]}
          remarkPlugins={[remarkGfm]}
        >
          {article.body}
        </Markdown>
      </div>
    </article>
  );
}
