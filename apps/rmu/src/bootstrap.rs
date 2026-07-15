use std::{collections::HashMap, ffi::OsString, fmt, sync::Arc};

use axum::Router;
use sqlx::postgres::{PgConnectOptions, PgPoolOptions};

use crate::{
    eventarc::{EventActions, router},
    firestore_stream::{FirestoreEventStreamReader, GoogleFirestoreEventDocuments},
    postgres::{PostgresArticleProjectionStore, PostgresDeadLetterStore},
    processor::RmuEventActions,
};

#[derive(Clone, PartialEq, Eq)]
pub struct RmuConfig {
    pub project_id: String,
    pub database_url: String,
}

impl fmt::Debug for RmuConfig {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("RmuConfig")
            .field("project_id", &self.project_id)
            .field("database_url", &"[REDACTED]")
            .finish()
    }
}

impl RmuConfig {
    pub fn from_vars<I>(vars: I) -> Result<Self, String>
    where
        I: IntoIterator<Item = (OsString, OsString)>,
    {
        let vars: HashMap<_, _> = vars.into_iter().collect();
        Ok(Self {
            project_id: required_var(&vars, "GOOGLE_CLOUD_PROJECT")?,
            database_url: required_var(&vars, "DATABASE_URL")?,
        })
    }
}

fn required_var(vars: &HashMap<OsString, OsString>, name: &str) -> Result<String, String> {
    vars.get(&OsString::from(name))
        .filter(|value| !value.is_empty())
        .ok_or_else(|| format!("missing {name}"))?
        .clone()
        .into_string()
        .map_err(|_| format!("invalid {name}"))
}

pub async fn build_live_router(config: RmuConfig) -> Result<Router, String> {
    let connect_options: PgConnectOptions = config
        .database_url
        .parse()
        .map_err(|_| "failed to connect to Postgres: invalid DATABASE_URL".to_owned())?;
    let documents = GoogleFirestoreEventDocuments::new(&config.project_id)
        .await
        .map_err(|error| format!("failed to create Firestore client: {error}"))?;
    let pool = PgPoolOptions::new()
        .connect_with(connect_options)
        .await
        .map_err(|_| "failed to connect to Postgres".to_owned())?;
    let actions: Arc<dyn EventActions> = Arc::new(RmuEventActions::new(
        Arc::new(FirestoreEventStreamReader::new(documents)),
        Arc::new(PostgresArticleProjectionStore::new(pool.clone())),
        Arc::new(PostgresDeadLetterStore::new(pool)),
    ));
    Ok(router(actions))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn loads_the_live_service_configuration() {
        let result = RmuConfig::from_vars([
            ("UNRELATED".into(), "ignored".into()),
            ("GOOGLE_CLOUD_PROJECT".into(), "demo-morecat".into()),
            (
                "DATABASE_URL".into(),
                "postgres://user:password@localhost/morecat".into(),
            ),
        ]);

        assert_eq!(
            result,
            Ok(RmuConfig {
                project_id: "demo-morecat".to_owned(),
                database_url: "postgres://user:password@localhost/morecat".to_owned(),
            })
        );
    }

    #[test]
    fn rejects_missing_empty_and_non_utf8_configuration() {
        assert_eq!(
            RmuConfig::from_vars([]),
            Err("missing GOOGLE_CLOUD_PROJECT".to_owned())
        );
        assert_eq!(
            RmuConfig::from_vars([
                ("GOOGLE_CLOUD_PROJECT".into(), "demo-morecat".into()),
                ("DATABASE_URL".into(), "".into()),
            ]),
            Err("missing DATABASE_URL".to_owned())
        );

        #[cfg(unix)]
        {
            use std::os::unix::ffi::OsStringExt;

            assert_eq!(
                RmuConfig::from_vars([
                    (
                        "GOOGLE_CLOUD_PROJECT".into(),
                        OsString::from_vec(vec![0xff])
                    ),
                    (
                        "DATABASE_URL".into(),
                        "postgres://user:secret@localhost/morecat".into(),
                    ),
                ]),
                Err("invalid GOOGLE_CLOUD_PROJECT".to_owned())
            );
        }
    }

    #[test]
    fn redacts_database_credentials_from_debug_output() {
        let config = RmuConfig {
            project_id: "demo-morecat".to_owned(),
            database_url: "postgres://user:secret@localhost/morecat".to_owned(),
        };

        assert_eq!(
            format!("{config:?}"),
            "RmuConfig { project_id: \"demo-morecat\", database_url: \"[REDACTED]\" }"
        );
    }
}

#[cfg(all(
    test,
    feature = "firestore-integration",
    feature = "postgres-integration"
))]
mod integration_tests {
    use axum::{
        body::Body,
        http::{Request, StatusCode},
    };
    use base64::{Engine, engine::general_purpose::STANDARD};
    use serde::{Deserialize, Serialize};
    use sqlx::{PgPool, postgres::PgPoolOptions};
    use testcontainers_modules::{
        postgres::Postgres,
        testcontainers::{ImageExt, runners::AsyncRunner},
    };
    use tower::ServiceExt;
    use uuid::Uuid;

    use crate::firestore_stream::GoogleFirestoreEventDocuments;

    use super::*;

    const ARTICLE_ID: &str = "018f4edc-1f5a-7c4b-aef9-000000000001";

    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct CloudEventFixture {
        headers: std::collections::BTreeMap<String, String>,
        body_base64: String,
    }

    #[derive(Deserialize, Serialize)]
    struct EventFields {
        json: String,
    }

    #[tokio::test]
    async fn processes_a_firestore_event_into_the_postgres_projection() {
        let (_container, pool, database_url) = migrated_postgres().await;
        seed_draft_event().await;
        let router = build_live_router(RmuConfig {
            project_id: "demo-morecat".to_owned(),
            database_url,
        })
        .await;

        assert!(router.is_ok());
        let response = router.unwrap().oneshot(fixture_request()).await.unwrap();
        let row: (String, String, String, i64) = sqlx::query_as(
            "SELECT status, slug, title, last_applied_seq FROM articles WHERE article_id = $1",
        )
        .bind(ARTICLE_ID)
        .fetch_one(&pool)
        .await
        .unwrap();
        assert_eq!(response.status(), StatusCode::NO_CONTENT);
        assert_eq!(
            row,
            (
                "draft".to_owned(),
                "hello-world".to_owned(),
                "Hello".to_owned(),
                1,
            )
        );
    }

    #[tokio::test]
    async fn identifies_live_dependency_initialization_failures_without_exposing_credentials() {
        let (_container, _pool, database_url) = migrated_postgres().await;
        let firestore_error = build_live_router(RmuConfig {
            project_id: String::new(),
            database_url: "postgres://user:secret@localhost/morecat".to_owned(),
        })
        .await;
        assert!(matches!(
            firestore_error,
            Err(message) if message.starts_with("failed to create Firestore client:")
                && !message.contains("secret")
        ));

        let postgres_error = build_live_router(RmuConfig {
            project_id: "demo-morecat".to_owned(),
            database_url: "postgres://user:secret@[invalid/morecat".to_owned(),
        })
        .await;
        assert_eq!(
            postgres_error.err(),
            Some("failed to connect to Postgres: invalid DATABASE_URL".to_owned())
        );

        let invalid_credentials =
            database_url.replacen("postgres:postgres@", "postgres:wrong-secret@", 1);
        let connection_error = build_live_router(RmuConfig {
            project_id: "demo-morecat".to_owned(),
            database_url: invalid_credentials,
        })
        .await;
        assert_eq!(
            connection_error.err(),
            Some("failed to connect to Postgres".to_owned())
        );
    }

    async fn seed_draft_event() {
        let article_id = Uuid::parse_str(ARTICLE_ID).unwrap();
        let documents = GoogleFirestoreEventDocuments::new("demo-morecat")
            .await
            .unwrap();
        let parent = documents
            .database()
            .parent_path("articles", article_id.to_string())
            .unwrap();
        documents
            .database()
            .fluent()
            .insert()
            .into("events")
            .document_id("1")
            .parent(&parent)
            .object(&EventFields {
                json: r#"{"eventType":"ArticleDrafted","schemaVersion":1,"slug":"hello-world","title":"Hello","body":"Body"}"#
                    .to_owned(),
            })
            .execute::<EventFields>()
            .await
            .unwrap();
    }

    fn fixture_request() -> Request<Body> {
        let fixture: CloudEventFixture = serde_json::from_str(include_str!(
            "../tests/fixtures/firestore-document-created.json"
        ))
        .unwrap();
        let mut request = Request::post("/");
        for (name, value) in fixture.headers {
            request = request.header(name, value);
        }
        request
            .body(Body::from(STANDARD.decode(fixture.body_base64).unwrap()))
            .unwrap()
    }

    async fn migrated_postgres() -> (
        testcontainers_modules::testcontainers::ContainerAsync<Postgres>,
        PgPool,
        String,
    ) {
        let container = Postgres::default()
            .with_tag("17-alpine")
            .start()
            .await
            .unwrap();
        let host = container.get_host().await.unwrap();
        let port = container.get_host_port_ipv4(5432).await.unwrap();
        let database_url = format!("postgres://postgres:postgres@{host}:{port}/postgres");
        let pool = PgPoolOptions::new()
            .max_connections(1)
            .connect(&database_url)
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
        (container, pool, database_url)
    }
}
