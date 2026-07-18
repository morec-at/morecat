use std::{
    collections::HashMap, ffi::OsString, fmt, future::Future, io::Write, pin::Pin, sync::Arc,
};

use axum::Router;
use sqlx::postgres::{PgConnectOptions, PgPoolOptions};
use tokio::net::TcpListener;

use crate::{
    eventarc::{EventActions, router},
    firestore_stream::{FirestoreEventStreamReader, GoogleFirestoreEventDocuments},
    postgres::{PostgresArticleProjectionStore, PostgresDeadLetterStore},
    processor::RmuEventActions,
    replay::{ArticleReplay, ReplayError},
};

#[derive(Clone, PartialEq, Eq)]
pub struct RmuConfig {
    pub project_id: String,
    pub database_url: String,
    pub port: u16,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RmuCommand {
    Serve,
    Replay,
}

impl RmuCommand {
    pub fn from_args(args: Vec<OsString>) -> Result<Self, String> {
        match args.as_slice() {
            [] => Ok(Self::Serve),
            [command] if command == "replay" => Ok(Self::Replay),
            _ => Err("usage: morecat-rmu [replay]".to_owned()),
        }
    }
}

impl fmt::Debug for RmuConfig {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("RmuConfig")
            .field("project_id", &self.project_id)
            .field("database_url", &"[REDACTED]")
            .field("port", &self.port)
            .finish()
    }
}

impl RmuConfig {
    pub fn from_vars(vars: Vec<(OsString, OsString)>) -> Result<Self, String> {
        let vars: HashMap<_, _> = vars.into_iter().collect();
        Ok(Self {
            project_id: required_var(&vars, "GOOGLE_CLOUD_PROJECT")?,
            database_url: required_var(&vars, "DATABASE_URL")?,
            port: port(&vars)?,
        })
    }
}

fn port(vars: &HashMap<OsString, OsString>) -> Result<u16, String> {
    match vars.get(&OsString::from("PORT")) {
        None => Ok(8080),
        Some(value) => value
            .to_str()
            .and_then(|value| value.parse().ok())
            .ok_or_else(|| "invalid PORT".to_owned()),
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
    let (documents, pool) = connect_live_dependencies(&config).await?;
    let actions: Arc<dyn EventActions> = Arc::new(RmuEventActions::new(
        Arc::new(FirestoreEventStreamReader::new(documents)),
        Arc::new(PostgresArticleProjectionStore::new(pool.clone())),
        Arc::new(PostgresDeadLetterStore::new(pool)),
    ));
    Ok(router(actions))
}

pub async fn run(
    vars: Vec<(OsString, OsString)>,
    shutdown: Pin<Box<dyn Future<Output = ()> + Send + 'static>>,
) -> Result<(), String> {
    let config = RmuConfig::from_vars(vars)?;
    let port = config.port;
    let app = build_live_router(config).await?;
    let listener = TcpListener::bind(("0.0.0.0", port))
        .await
        .map_err(listener_error)?;
    let address = listener
        .local_addr()
        .expect("bound RMU listener must have a local address");
    let mut stdout = std::io::stdout().lock();
    writeln!(stdout, "RMU listening on {address}").expect("failed to write RMU readiness");
    stdout.flush().expect("failed to flush RMU readiness");
    drop(stdout);
    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown)
        .await
        .map_err(server_error)
}

pub async fn run_replay(vars: Vec<(OsString, OsString)>) -> Result<usize, String> {
    let config = RmuConfig::from_vars(vars)?;
    let (documents, pool) = connect_live_dependencies(&config).await?;
    let projector = RmuEventActions::new(
        Arc::new(FirestoreEventStreamReader::new(documents.clone())),
        Arc::new(PostgresArticleProjectionStore::new(pool.clone())),
        Arc::new(PostgresDeadLetterStore::new(pool)),
    );

    ArticleReplay::new(documents, projector)
        .replay_all()
        .await
        .map_err(replay_error)
}

async fn connect_live_dependencies(
    config: &RmuConfig,
) -> Result<(GoogleFirestoreEventDocuments, sqlx::PgPool), String> {
    let connect_options: PgConnectOptions =
        config.database_url.parse().map_err(invalid_database_url)?;
    let documents = GoogleFirestoreEventDocuments::new(&config.project_id)
        .await
        .map_err(firestore_client_error)?;
    let pool = PgPoolOptions::new()
        .connect_with(connect_options)
        .await
        .map_err(postgres_connection_error)?;
    Ok((documents, pool))
}

fn invalid_database_url(_error: sqlx::Error) -> String {
    "failed to connect to Postgres: invalid DATABASE_URL".to_owned()
}

fn firestore_client_error(error: String) -> String {
    format!("failed to create Firestore client: {error}")
}

fn postgres_connection_error(_error: sqlx::Error) -> String {
    "failed to connect to Postgres".to_owned()
}

fn replay_error(error: ReplayError) -> String {
    format!("replay failed: {error:?}")
}

fn listener_error(error: std::io::Error) -> String {
    match error.kind() {
        std::io::ErrorKind::AddrInUse => {
            "failed to bind RMU listener: address already in use".to_owned()
        }
        std::io::ErrorKind::PermissionDenied => {
            "failed to bind RMU listener: permission denied".to_owned()
        }
        _ => "failed to bind RMU listener".to_owned(),
    }
}

fn server_error(_error: std::io::Error) -> String {
    "RMU server failed".to_owned()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn loads_the_live_service_configuration() {
        let result = RmuConfig::from_vars(vec![
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
                port: 8080,
            })
        );
    }

    #[test]
    fn selects_the_service_or_replay_command() {
        assert_eq!(RmuCommand::from_args(Vec::new()), Ok(RmuCommand::Serve));
        assert_eq!(
            RmuCommand::from_args(vec!["replay".into()]),
            Ok(RmuCommand::Replay)
        );
        assert_eq!(
            RmuCommand::from_args(vec!["unknown".into()]),
            Err("usage: morecat-rmu [replay]".to_owned())
        );
    }

    #[test]
    fn rejects_missing_empty_and_non_utf8_configuration() {
        assert_eq!(
            RmuConfig::from_vars(Vec::new()),
            Err("missing GOOGLE_CLOUD_PROJECT".to_owned())
        );
        assert_eq!(
            RmuConfig::from_vars(vec![
                ("GOOGLE_CLOUD_PROJECT".into(), "demo-morecat".into()),
                ("DATABASE_URL".into(), "".into()),
            ]),
            Err("missing DATABASE_URL".to_owned())
        );

        #[cfg(unix)]
        {
            use std::os::unix::ffi::OsStringExt;

            assert_eq!(
                RmuConfig::from_vars(vec![
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
    fn loads_and_validates_an_explicit_port() {
        let vars = |port: OsString| {
            [
                ("GOOGLE_CLOUD_PROJECT".into(), "demo-morecat".into()),
                (
                    "DATABASE_URL".into(),
                    "postgres://user:password@localhost/morecat".into(),
                ),
                ("PORT".into(), port),
            ]
        };

        assert_eq!(
            RmuConfig::from_vars(vars("9090".into()).into())
                .unwrap()
                .port,
            9090
        );
        for invalid in ["".into(), "-1".into(), "65536".into()] {
            assert_eq!(
                RmuConfig::from_vars(vars(invalid).into()),
                Err("invalid PORT".to_owned())
            );
        }

        #[cfg(unix)]
        {
            use std::os::unix::ffi::OsStringExt;

            assert_eq!(
                RmuConfig::from_vars(vars(OsString::from_vec(vec![0xff])).into()),
                Err("invalid PORT".to_owned())
            );
        }
    }

    #[test]
    fn redacts_database_credentials_from_debug_output() {
        let config = RmuConfig {
            project_id: "demo-morecat".to_owned(),
            database_url: "postgres://user:secret@localhost/morecat".to_owned(),
            port: 8080,
        };

        assert_eq!(
            format!("{config:?}"),
            "RmuConfig { project_id: \"demo-morecat\", database_url: \"[REDACTED]\", port: 8080 }"
        );
    }

    #[test]
    fn identifies_safe_listener_error_kinds_and_redacts_other_details() {
        assert_eq!(
            listener_error(std::io::ErrorKind::AddrInUse.into()),
            "failed to bind RMU listener: address already in use"
        );
        assert_eq!(
            listener_error(std::io::ErrorKind::PermissionDenied.into()),
            "failed to bind RMU listener: permission denied"
        );
        assert_eq!(
            listener_error(std::io::Error::other("listener secret")),
            "failed to bind RMU listener"
        );
        assert_eq!(
            server_error(std::io::Error::other("server secret")),
            "RMU server failed"
        );
    }

    #[test]
    fn maps_live_dependency_and_replay_errors() {
        assert_eq!(
            invalid_database_url(sqlx::Error::PoolClosed),
            "failed to connect to Postgres: invalid DATABASE_URL"
        );
        assert_eq!(
            firestore_client_error("client failed".to_owned()),
            "failed to create Firestore client: client failed"
        );
        assert_eq!(
            postgres_connection_error(sqlx::Error::PoolClosed),
            "failed to connect to Postgres"
        );
        assert_eq!(
            replay_error(ReplayError::Catalog(
                crate::firestore_stream::StreamReadError::Unavailable("query failed".to_owned())
            )),
            "replay failed: Catalog(Unavailable(\"query failed\"))"
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
            port: 8080,
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
            port: 8080,
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
            port: 8080,
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
            port: 8080,
        })
        .await;
        assert_eq!(
            connection_error.err(),
            Some("failed to connect to Postgres".to_owned())
        );

        let run_error = run(
            vec![
                ("GOOGLE_CLOUD_PROJECT".into(), "demo-morecat".into()),
                (
                    "DATABASE_URL".into(),
                    "postgres://user:secret@[invalid/morecat".into(),
                ),
            ],
            Box::pin(std::future::ready(())),
        )
        .await;
        assert_eq!(
            run_error,
            Err("failed to connect to Postgres: invalid DATABASE_URL".to_owned())
        );
    }

    #[tokio::test]
    async fn binds_and_stops_the_live_service() {
        let (_container, _pool, database_url) = migrated_postgres().await;

        let result = run(
            vec![
                ("GOOGLE_CLOUD_PROJECT".into(), "demo-morecat".into()),
                ("DATABASE_URL".into(), database_url.clone().into()),
                ("PORT".into(), "0".into()),
            ],
            Box::pin(std::future::ready(())),
        )
        .await;

        assert_eq!(result, Ok(()));

        let occupied = TcpListener::bind(("0.0.0.0", 0)).await.unwrap();
        let port = occupied.local_addr().unwrap().port();
        let bind_error = run(
            vec![
                ("GOOGLE_CLOUD_PROJECT".into(), "demo-morecat".into()),
                ("DATABASE_URL".into(), database_url.into()),
                ("PORT".into(), port.to_string().into()),
            ],
            Box::pin(std::future::ready(())),
        )
        .await;
        assert_eq!(
            bind_error,
            Err("failed to bind RMU listener: address already in use".to_owned())
        );
    }

    #[tokio::test]
    async fn replays_all_firestore_articles_into_postgres() {
        let (_container, pool, database_url) = migrated_postgres().await;
        let project_id = "demo-morecat-replay";
        seed_replay_event(project_id).await;

        let result = run_replay(vec![
            ("GOOGLE_CLOUD_PROJECT".into(), project_id.into()),
            ("DATABASE_URL".into(), database_url.into()),
        ])
        .await;
        assert_eq!(result, Ok(1));

        let row: (String, String, i64) = sqlx::query_as(
            "SELECT status, slug, last_applied_seq FROM articles WHERE article_id = $1",
        )
        .bind(ARTICLE_ID)
        .fetch_one(&pool)
        .await
        .unwrap();

        assert_eq!(row, ("draft".to_owned(), "hello-world".to_owned(), 1));
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

    async fn seed_replay_event(project_id: &str) {
        let article_id = Uuid::parse_str(ARTICLE_ID).unwrap();
        let documents = GoogleFirestoreEventDocuments::new(project_id)
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
