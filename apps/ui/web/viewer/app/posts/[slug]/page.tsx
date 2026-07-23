import { notFound } from 'next/navigation';

import { ArticlePage } from '../../../article-page';
import {
  loadPublishedArticle,
  PublishedArticleNotFoundError,
} from '../../../published-article';

export const dynamic = 'force-dynamic';

async function loadArticleOrNotFound(slug: string) {
  try {
    return await loadPublishedArticle(slug);
  } catch (error) {
    if (error instanceof PublishedArticleNotFoundError) {
      notFound();
    }
    throw error;
  }
}

export default async function PostPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const article = await loadArticleOrNotFound(slug);
  return <ArticlePage article={article} />;
}
