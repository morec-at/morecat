use async_trait::async_trait;
use firestore::errors::FirestoreError;
use firestore::{FirestoreDb, FirestoreDbOptions, firestore_document_to_serializable};
use futures::TryStreamExt;
use gcloud_sdk::{SecretValue, Source, Token, TokenSourceType};
use serde::Deserialize;
use std::ffi::OsString;
use std::time::{Duration, SystemTime};
use uuid::Uuid;

use crate::{parse_document_name, projection::StoredArticleEvent};

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FirestoreEventDocument {
    pub name: String,
    pub json: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum StreamReadError {
    Unavailable(String),
    InvalidDocument(String),
}

#[async_trait]
pub trait FirestoreEventDocuments: Send + Sync {
    async fn load_event_documents(
        &self,
        article_id: Uuid,
    ) -> Result<Vec<FirestoreEventDocument>, StreamReadError>;
}

pub struct GoogleFirestoreEventDocuments {
    db: FirestoreDb,
}

impl GoogleFirestoreEventDocuments {
    pub async fn new(project_id: &str) -> Result<Self, String> {
        if project_id.is_empty() {
            return Err("project ID must not be empty".to_owned());
        }
        let options = FirestoreDbOptions::new(project_id.to_owned());
        FirestoreDb::with_options_token_source(
            options,
            gcloud_sdk::GCP_DEFAULT_SCOPES.clone(),
            ClientAuth::detect(std::env::var_os("FIRESTORE_EMULATOR_HOST")).token_source(),
        )
        .await
        .map(|db| Self { db })
        .map_err(firestore_error_message)
    }

    #[cfg(all(test, feature = "firestore-integration"))]
    pub(crate) fn database(&self) -> &FirestoreDb {
        &self.db
    }
}

#[derive(Debug, PartialEq, Eq)]
enum ClientAuth {
    ApplicationDefault,
    Emulator,
}

impl ClientAuth {
    fn detect(emulator_host: Option<OsString>) -> Self {
        if emulator_host.is_some() {
            Self::Emulator
        } else {
            Self::ApplicationDefault
        }
    }

    fn token_source(self) -> TokenSourceType {
        match self {
            Self::ApplicationDefault => TokenSourceType::Default,
            Self::Emulator => TokenSourceType::ExternalSource(Box::new(EmulatorTokenSource)),
        }
    }
}

struct EmulatorTokenSource;

#[async_trait]
impl Source for EmulatorTokenSource {
    async fn token(&self) -> gcloud_sdk::error::Result<Token> {
        Ok(Token::new(
            "Bearer".to_owned(),
            SecretValue::from("owner"),
            (SystemTime::now() + Duration::from_secs(3600)).into(),
        ))
    }
}

#[derive(Deserialize)]
struct EventFields {
    json: String,
}

fn firestore_error_message(error: FirestoreError) -> String {
    error.to_string()
}

fn firestore_unavailable(error: FirestoreError) -> StreamReadError {
    StreamReadError::Unavailable(firestore_error_message(error))
}

#[async_trait]
impl FirestoreEventDocuments for GoogleFirestoreEventDocuments {
    async fn load_event_documents(
        &self,
        article_id: Uuid,
    ) -> Result<Vec<FirestoreEventDocument>, StreamReadError> {
        let parent = format!("{}/articles/{article_id}", self.db.get_documents_path());
        let stream = self
            .db
            .fluent()
            .list()
            .from("events")
            .parent(parent)
            .stream_all_with_errors()
            .await
            .map_err(firestore_unavailable)?;
        let documents: Vec<_> = stream.try_collect().await.map_err(firestore_unavailable)?;

        documents
            .into_iter()
            .map(|document| {
                let name = document.name.clone();
                let fields: EventFields =
                    firestore_document_to_serializable(&document).map_err(|error| {
                        StreamReadError::InvalidDocument(format!(
                            "invalid Firestore event document {name}: {error}"
                        ))
                    })?;
                Ok(FirestoreEventDocument {
                    name: document.name,
                    json: fields.json,
                })
            })
            .collect()
    }
}

pub struct FirestoreEventStreamReader<Q> {
    query: Q,
}

impl<Q> FirestoreEventStreamReader<Q> {
    pub fn new(query: Q) -> Self {
        Self { query }
    }
}

impl<Q: FirestoreEventDocuments> FirestoreEventStreamReader<Q> {
    pub async fn load(&self, article_id: Uuid) -> Result<Vec<StoredArticleEvent>, StreamReadError> {
        let documents = self.query.load_event_documents(article_id).await?;
        let mut events = Vec::with_capacity(documents.len());

        for document in documents {
            let parsed = parse_document_name(&document.name)
                .map_err(|_| StreamReadError::InvalidDocument(document.name.clone()))?;
            if parsed.article_id != article_id {
                return Err(StreamReadError::InvalidDocument(document.name));
            }
            events.push(StoredArticleEvent {
                seq: parsed.seq,
                json: document.json,
            });
        }

        events.sort_by_key(|event| event.seq);
        Ok(events)
    }
}

#[cfg(test)]
mod tests {
    use firestore::errors::{
        FirestoreInvalidParametersError, FirestoreInvalidParametersPublicDetails,
    };
    use serde::{Deserialize, Serialize};

    use super::*;

    #[derive(Deserialize, Serialize)]
    struct SeedEventFields {
        json: String,
    }

    #[derive(Deserialize, Serialize)]
    struct InvalidEventFields {
        json: u64,
    }

    struct StubQuery {
        result: Result<Vec<FirestoreEventDocument>, StreamReadError>,
    }

    #[async_trait]
    impl FirestoreEventDocuments for StubQuery {
        async fn load_event_documents(
            &self,
            _article_id: Uuid,
        ) -> Result<Vec<FirestoreEventDocument>, StreamReadError> {
            self.result.clone()
        }
    }

    #[test]
    fn selects_authentication_for_the_runtime_environment() {
        let application_default = ClientAuth::detect(None);
        let emulator = ClientAuth::detect(Some(OsString::from("127.0.0.1:8080")));

        assert_eq!(application_default, ClientAuth::ApplicationDefault);
        assert_eq!(emulator, ClientAuth::Emulator);
        assert_eq!(
            format!("{:?}", application_default.token_source()),
            "Default"
        );
        assert_eq!(format!("{:?}", emulator.token_source()), "ExternalSource");
    }

    #[test]
    fn maps_firestore_failures_to_unavailable_with_details() {
        let error = FirestoreError::InvalidParametersError(FirestoreInvalidParametersError::new(
            FirestoreInvalidParametersPublicDetails::new(
                "article_id".to_owned(),
                "invalid".to_owned(),
            ),
        ));

        assert_eq!(
            firestore_unavailable(error),
            StreamReadError::Unavailable(
                "Data not found error occurred: Invalid parameters error: article_id. invalid"
                    .to_owned()
            )
        );
    }

    #[tokio::test]
    async fn loads_an_article_event_stream_in_sequence_order() {
        let article_id = Uuid::parse_str("018f4edc-1f5a-7c4b-aef9-000000000001").unwrap();
        let reader = FirestoreEventStreamReader::new(StubQuery {
            result: Ok(vec![
                document(article_id, 2, "published"),
                document(article_id, 1, "drafted"),
            ]),
        });

        let result = reader.load(article_id).await;

        assert_eq!(
            result,
            Ok(vec![
                StoredArticleEvent {
                    seq: 1,
                    json: "drafted".to_owned(),
                },
                StoredArticleEvent {
                    seq: 2,
                    json: "published".to_owned(),
                },
            ])
        );
    }

    #[tokio::test]
    async fn rejects_documents_outside_the_requested_article_stream() {
        let article_id = Uuid::parse_str("018f4edc-1f5a-7c4b-aef9-000000000001").unwrap();
        let other_article_id = Uuid::parse_str("018f4edc-1f5a-7c4b-aef9-000000000002").unwrap();
        let cases = [
            document(other_article_id, 1, "event"),
            FirestoreEventDocument {
                name: "projects/demo-morecat/databases/(default)/documents/slugs/hello-world"
                    .to_owned(),
                json: "event".to_owned(),
            },
            FirestoreEventDocument {
                name: format!(
                    "projects/demo-morecat/databases/(default)/documents/articles/{article_id}/events/01"
                ),
                json: "event".to_owned(),
            },
        ];

        for invalid in cases {
            let expected_name = invalid.name.clone();
            let reader = FirestoreEventStreamReader::new(StubQuery {
                result: Ok(vec![invalid]),
            });

            assert_eq!(
                reader.load(article_id).await,
                Err(StreamReadError::InvalidDocument(expected_name))
            );
        }
    }

    #[tokio::test]
    async fn preserves_firestore_query_failures_as_unavailable() {
        let article_id = Uuid::parse_str("018f4edc-1f5a-7c4b-aef9-000000000001").unwrap();
        let reader = FirestoreEventStreamReader::new(StubQuery {
            result: Err(StreamReadError::Unavailable("query failed".to_owned())),
        });

        assert_eq!(
            reader.load(article_id).await,
            Err(StreamReadError::Unavailable("query failed".to_owned()))
        );
    }

    fn document(article_id: Uuid, seq: u64, json: &str) -> FirestoreEventDocument {
        FirestoreEventDocument {
            name: format!(
                "projects/demo-morecat/databases/(default)/documents/articles/{article_id}/events/{seq}"
            ),
            json: json.to_owned(),
        }
    }

    #[cfg(feature = "firestore-integration")]
    #[tokio::test]
    async fn creates_a_firestore_client_for_the_emulator() {
        let result = GoogleFirestoreEventDocuments::new("demo-morecat").await;

        assert!(result.is_ok());
    }

    #[cfg(feature = "firestore-integration")]
    #[tokio::test]
    async fn lists_event_documents_from_an_article_subcollection() {
        let article_id = Uuid::parse_str("018f4edc-1f5a-7c4b-aef9-000000000011").unwrap();
        let documents = GoogleFirestoreEventDocuments::new("demo-morecat")
            .await
            .unwrap();
        let parent = documents
            .db
            .parent_path("articles", article_id.to_string())
            .unwrap();
        for (seq, json) in [("2", "published"), ("1", "drafted")] {
            documents
                .db
                .fluent()
                .insert()
                .into("events")
                .document_id(seq)
                .parent(&parent)
                .object(&SeedEventFields {
                    json: json.to_owned(),
                })
                .execute::<SeedEventFields>()
                .await
                .unwrap();
        }

        let mut result = documents.load_event_documents(article_id).await.unwrap();
        result.sort_by(|left, right| left.name.cmp(&right.name));

        assert_eq!(
            result,
            vec![
                document(article_id, 1, "drafted"),
                document(article_id, 2, "published"),
            ]
        );
    }

    #[cfg(feature = "firestore-integration")]
    #[tokio::test]
    async fn identifies_an_event_document_with_invalid_fields() {
        let article_id = Uuid::parse_str("018f4edc-1f5a-7c4b-aef9-000000000012").unwrap();
        let documents = GoogleFirestoreEventDocuments::new("demo-morecat")
            .await
            .unwrap();
        let parent = documents
            .db
            .parent_path("articles", article_id.to_string())
            .unwrap();
        documents
            .db
            .fluent()
            .insert()
            .into("events")
            .document_id("1")
            .parent(&parent)
            .object(&InvalidEventFields { json: 42 })
            .execute::<InvalidEventFields>()
            .await
            .unwrap();

        let result = FirestoreEventStreamReader::new(documents)
            .load(article_id)
            .await;
        let expected_name = format!(
            "projects/demo-morecat/databases/(default)/documents/articles/{article_id}/events/1"
        );

        assert!(matches!(
            result,
            Err(StreamReadError::InvalidDocument(message))
                if message.starts_with(&format!("invalid Firestore event document {expected_name}:"))
        ));
    }
}
