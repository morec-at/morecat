use async_trait::async_trait;
use sqlx::PgPool;

use crate::{
    processor::ArticleProjectionStore,
    projection::{ArticleProjection, ArticleStatus},
};

pub struct PostgresArticleProjectionStore {
    pool: PgPool,
}

impl PostgresArticleProjectionStore {
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

#[async_trait]
impl ArticleProjectionStore for PostgresArticleProjectionStore {
    async fn upsert_if_newer(&self, projection: ArticleProjection) -> Result<(), String> {
        let status = match projection.status {
            ArticleStatus::Draft => "draft",
            ArticleStatus::Published => "published",
        };
        let last_applied_seq = i64::try_from(projection.last_applied_seq)
            .map_err(|_| "last_applied_seq exceeds Postgres BIGINT".to_owned())?;
        sqlx::query(
            r#"
            INSERT INTO articles (
              article_id, status, slug, title, body, published_at, last_applied_seq
            ) VALUES ($1, $2, $3, $4, $5, $6, $7)
            ON CONFLICT (article_id) DO UPDATE SET
              status = EXCLUDED.status,
              slug = EXCLUDED.slug,
              title = EXCLUDED.title,
              body = EXCLUDED.body,
              published_at = EXCLUDED.published_at,
              last_applied_seq = EXCLUDED.last_applied_seq
            WHERE articles.last_applied_seq < EXCLUDED.last_applied_seq
            "#,
        )
        .bind(projection.article_id.to_string())
        .bind(status)
        .bind(projection.slug)
        .bind(projection.title)
        .bind(projection.body)
        .bind(projection.published_at)
        .bind(last_applied_seq)
        .execute(&self.pool)
        .await
        .map(|_| ())
        .map_err(|error| error.to_string())
    }
}

#[cfg(all(test, feature = "postgres-integration"))]
mod tests {
    use sqlx::postgres::PgPoolOptions;
    use testcontainers_modules::{postgres::Postgres, testcontainers::runners::AsyncRunner};
    use uuid::Uuid;

    use crate::projection::{ArticleProjection, ArticleStatus};

    use super::*;

    const ARTICLE_ID: &str = "018f4edc-1f5a-7c4b-aef9-000000000001";

    #[tokio::test]
    async fn inserts_a_new_article_projection() {
        let (_container, pool) = migrated_postgres().await;
        let store = PostgresArticleProjectionStore::new(pool.clone());

        store
            .upsert_if_newer(projection(1, ArticleStatus::Draft))
            .await
            .unwrap();

        let row: (String, String, i64) = sqlx::query_as(
            "SELECT slug, status, last_applied_seq FROM articles WHERE article_id = $1",
        )
        .bind(ARTICLE_ID)
        .fetch_one(&pool)
        .await
        .unwrap();
        assert_eq!(row, ("hello".to_owned(), "draft".to_owned(), 1));
    }

    #[tokio::test]
    async fn updates_an_article_projection_when_the_sequence_advances() {
        let (_container, pool) = migrated_postgres().await;
        let store = PostgresArticleProjectionStore::new(pool.clone());
        store
            .upsert_if_newer(projection(1, ArticleStatus::Draft))
            .await
            .unwrap();

        store
            .upsert_if_newer(projection(2, ArticleStatus::Published))
            .await
            .unwrap();

        let row: (String, Option<i64>, i64) = sqlx::query_as(
            "SELECT status, published_at, last_applied_seq FROM articles WHERE article_id = $1",
        )
        .bind(ARTICLE_ID)
        .fetch_one(&pool)
        .await
        .unwrap();
        assert_eq!(row, ("published".to_owned(), Some(999), 2));
    }

    #[tokio::test]
    async fn keeps_the_newer_article_projection_on_stale_or_duplicate_delivery() {
        let (_container, pool) = migrated_postgres().await;
        let store = PostgresArticleProjectionStore::new(pool.clone());
        store
            .upsert_if_newer(projection(2, ArticleStatus::Published))
            .await
            .unwrap();

        store
            .upsert_if_newer(projection(1, ArticleStatus::Draft))
            .await
            .unwrap();
        store
            .upsert_if_newer(ArticleProjection {
                title: "Duplicate delivery".to_owned(),
                ..projection(2, ArticleStatus::Published)
            })
            .await
            .unwrap();

        let row: (String, String, i64) = sqlx::query_as(
            "SELECT status, title, last_applied_seq FROM articles WHERE article_id = $1",
        )
        .bind(ARTICLE_ID)
        .fetch_one(&pool)
        .await
        .unwrap();
        assert_eq!(row, ("published".to_owned(), "Hello".to_owned(), 2));
    }

    #[tokio::test]
    async fn rejects_sequences_outside_the_postgres_bigint_range() {
        let pool = PgPoolOptions::new()
            .connect_lazy("postgres://unused")
            .unwrap();
        let store = PostgresArticleProjectionStore::new(pool);

        let result = store
            .upsert_if_newer(projection(i64::MAX as u64 + 1, ArticleStatus::Draft))
            .await;

        assert_eq!(
            result,
            Err("last_applied_seq exceeds Postgres BIGINT".to_owned())
        );
    }

    #[tokio::test]
    async fn reports_postgres_upsert_failures() {
        let (_container, pool) = migrated_postgres().await;
        sqlx::query("DROP TABLE articles")
            .execute(&pool)
            .await
            .unwrap();
        let store = PostgresArticleProjectionStore::new(pool);

        let result = store
            .upsert_if_newer(projection(1, ArticleStatus::Draft))
            .await;

        assert!(result.unwrap_err().contains("articles"));
    }

    fn projection(last_applied_seq: u64, status: ArticleStatus) -> ArticleProjection {
        let published_at = match status {
            ArticleStatus::Draft => None,
            ArticleStatus::Published => Some(999),
        };
        ArticleProjection {
            article_id: Uuid::parse_str(ARTICLE_ID).unwrap(),
            status,
            slug: "hello".to_owned(),
            title: "Hello".to_owned(),
            body: "Body".to_owned(),
            published_at,
            last_applied_seq,
        }
    }

    async fn migrated_postgres() -> (
        testcontainers_modules::testcontainers::ContainerAsync<Postgres>,
        PgPool,
    ) {
        let container = Postgres::default().start().await.unwrap();
        let host = container.get_host().await.unwrap();
        let port = container.get_host_port_ipv4(5432).await.unwrap();
        let pool = PgPoolOptions::new()
            .max_connections(1)
            .connect(&format!(
                "postgres://postgres:postgres@{host}:{port}/postgres"
            ))
            .await
            .unwrap();
        sqlx::raw_sql(include_str!(
            "../../api/infrastructure/src/main/resources/db/migration/V1__create_articles.sql"
        ))
        .execute(&pool)
        .await
        .unwrap();
        (container, pool)
    }
}
