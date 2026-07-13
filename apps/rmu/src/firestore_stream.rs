use async_trait::async_trait;
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
    ) -> Result<Vec<FirestoreEventDocument>, String>;
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
        let documents = self
            .query
            .load_event_documents(article_id)
            .await
            .map_err(StreamReadError::Unavailable)?;
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
    use super::*;

    struct StubQuery {
        result: Result<Vec<FirestoreEventDocument>, String>,
    }

    #[async_trait]
    impl FirestoreEventDocuments for StubQuery {
        async fn load_event_documents(
            &self,
            _article_id: Uuid,
        ) -> Result<Vec<FirestoreEventDocument>, String> {
            self.result.clone()
        }
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
            result: Err("query failed".to_owned()),
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
}
