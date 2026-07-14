use async_trait::async_trait;
use sha2::{Digest, Sha256};
use sqlx::PgPool;

use crate::{
    eventarc::DeadLetter,
    processor::{ArticleProjectionStore, DeadLetterStore},
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

pub struct PostgresDeadLetterStore {
    pool: PgPool,
}

impl PostgresDeadLetterStore {
    pub fn new(pool: PgPool) -> Self {
        Self { pool }
    }
}

#[async_trait]
impl DeadLetterStore for PostgresDeadLetterStore {
    async fn record(&self, dead_letter: DeadLetter) -> Result<(), String> {
        let deduplication_key = dead_letter_deduplication_key(&dead_letter);
        sqlx::query(
            r#"
            INSERT INTO rmu_dead_letters (
              event_id, event_type, source, body, reason, deduplication_key
            ) VALUES ($1, $2, $3, $4, $5, $6)
            ON CONFLICT (deduplication_key) DO NOTHING
            "#,
        )
        .bind(dead_letter.event_id)
        .bind(dead_letter.event_type)
        .bind(dead_letter.source)
        .bind(dead_letter.body)
        .bind(dead_letter.reason)
        .bind(deduplication_key.as_slice())
        .execute(&self.pool)
        .await
        .map(|_| ())
        .map_err(|error| error.to_string())
    }
}

fn dead_letter_deduplication_key(dead_letter: &DeadLetter) -> [u8; 32] {
    let mut hasher = Sha256::new();
    update_optional_field(&mut hasher, dead_letter.event_id.as_deref());
    update_optional_field(&mut hasher, dead_letter.event_type.as_deref());
    update_optional_field(&mut hasher, dead_letter.source.as_deref());
    update_field(&mut hasher, &dead_letter.body);
    update_field(&mut hasher, dead_letter.reason.as_bytes());
    hasher.finalize().into()
}

fn update_optional_field(hasher: &mut Sha256, value: Option<&str>) {
    match value {
        Some(value) => {
            hasher.update([1]);
            update_field(hasher, value.as_bytes());
        }
        None => hasher.update([0]),
    }
}

fn update_field(hasher: &mut Sha256, value: &[u8]) {
    hasher.update((value.len() as u64).to_be_bytes());
    hasher.update(value);
}

#[cfg(all(test, feature = "postgres-integration"))]
mod tests {
    use sqlx::postgres::PgPoolOptions;
    use testcontainers_modules::{
        postgres::Postgres,
        testcontainers::{ImageExt, runners::AsyncRunner},
    };
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

    #[tokio::test]
    async fn records_a_dead_letter_with_optional_cloudevent_metadata() {
        let (_container, pool) = migrated_postgres().await;
        let store = PostgresDeadLetterStore::new(pool.clone());

        store
            .record(DeadLetter {
                event_id: None,
                event_type: Some("google.cloud.firestore.document.v1.created".to_owned()),
                source: None,
                body: vec![0, 1, 2, 255],
                reason: "invalid Firestore document name".to_owned(),
            })
            .await
            .unwrap();

        let row: (
            Option<String>,
            Option<String>,
            Option<String>,
            Vec<u8>,
            String,
        ) = sqlx::query_as(
            "SELECT event_id, event_type, source, body, reason FROM rmu_dead_letters",
        )
        .fetch_one(&pool)
        .await
        .unwrap();
        assert_eq!(
            row,
            (
                None,
                Some("google.cloud.firestore.document.v1.created".to_owned()),
                None,
                vec![0, 1, 2, 255],
                "invalid Firestore document name".to_owned(),
            )
        );
    }

    #[tokio::test]
    async fn records_a_retried_dead_letter_only_once_without_an_event_id() {
        let (_container, pool) = migrated_postgres().await;
        let store = PostgresDeadLetterStore::new(pool.clone());
        let dead_letter = DeadLetter {
            event_id: None,
            event_type: Some("google.cloud.firestore.document.v1.created".to_owned()),
            source: None,
            body: vec![42; 10_000],
            reason: "invalid Firestore document name".to_owned(),
        };

        store.record(dead_letter.clone()).await.unwrap();
        store.record(dead_letter).await.unwrap();

        let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM rmu_dead_letters")
            .fetch_one(&pool)
            .await
            .unwrap();
        assert_eq!(count, 1);
    }

    #[test]
    fn separates_optional_values_and_field_boundaries_in_dead_letter_keys() {
        let dead_letter = |event_id: Option<&str>, event_type: Option<&str>| DeadLetter {
            event_id: event_id.map(str::to_owned),
            event_type: event_type.map(str::to_owned),
            source: None,
            body: Vec::new(),
            reason: "invalid input".to_owned(),
        };

        assert_ne!(
            dead_letter_deduplication_key(&dead_letter(None, Some("a"))),
            dead_letter_deduplication_key(&dead_letter(Some(""), Some("a")))
        );
        assert_ne!(
            dead_letter_deduplication_key(&dead_letter(Some("a"), Some("bc"))),
            dead_letter_deduplication_key(&dead_letter(Some("ab"), Some("c")))
        );
    }

    #[tokio::test]
    async fn reports_dead_letter_insert_failures() {
        let (_container, pool) = migrated_postgres().await;
        sqlx::query("DROP TABLE rmu_dead_letters")
            .execute(&pool)
            .await
            .unwrap();
        let store = PostgresDeadLetterStore::new(pool);

        let result = store
            .record(DeadLetter {
                event_id: Some("event-1".to_owned()),
                event_type: None,
                source: Some("event-source".to_owned()),
                body: Vec::new(),
                reason: "invalid input".to_owned(),
            })
            .await;

        assert!(result.unwrap_err().contains("rmu_dead_letters"));
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
        let container = Postgres::default()
            .with_tag("17-alpine")
            .start()
            .await
            .unwrap();
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
        sqlx::raw_sql(include_str!(
            "../../api/infrastructure/src/main/resources/db/migration/V2__create_rmu_dead_letters.sql"
        ))
        .execute(&pool)
        .await
        .unwrap();
        (container, pool)
    }
}
