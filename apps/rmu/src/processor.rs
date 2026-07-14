use std::sync::Arc;

use async_trait::async_trait;
use uuid::Uuid;

use crate::{
    eventarc::{AcceptedEvent, DeadLetter, EventActions, ProcessingError},
    firestore_stream::{FirestoreEventDocuments, FirestoreEventStreamReader, StreamReadError},
    projection::{ArticleProjection, StoredArticleEvent, project_article},
};

#[async_trait]
pub trait ArticleEventStream: Send + Sync {
    async fn load(&self, article_id: Uuid) -> Result<Vec<StoredArticleEvent>, StreamReadError>;
}

#[async_trait]
impl<Q> ArticleEventStream for FirestoreEventStreamReader<Q>
where
    Q: FirestoreEventDocuments,
{
    async fn load(&self, article_id: Uuid) -> Result<Vec<StoredArticleEvent>, StreamReadError> {
        FirestoreEventStreamReader::load(self, article_id).await
    }
}

#[async_trait]
pub trait ArticleProjectionStore: Send + Sync {
    async fn upsert(&self, projection: ArticleProjection) -> Result<(), String>;
}

#[async_trait]
pub trait DeadLetterStore: Send + Sync {
    async fn record(&self, dead_letter: DeadLetter) -> Result<(), String>;
}

pub struct RmuEventActions<S, P, D> {
    stream: Arc<S>,
    projections: Arc<P>,
    dead_letters: Arc<D>,
}

impl<S, P, D> RmuEventActions<S, P, D> {
    pub fn new(stream: Arc<S>, projections: Arc<P>, dead_letters: Arc<D>) -> Self {
        Self {
            stream,
            projections,
            dead_letters,
        }
    }
}

#[async_trait]
impl<S, P, D> EventActions for RmuEventActions<S, P, D>
where
    S: ArticleEventStream,
    P: ArticleProjectionStore,
    D: DeadLetterStore,
{
    async fn process(&self, event: AcceptedEvent) -> Result<(), ProcessingError> {
        let article_id = event.document.article_id;
        let events = self
            .stream
            .load(article_id)
            .await
            .map_err(|error| match error {
                StreamReadError::Unavailable(message) => ProcessingError::Transient(message),
                StreamReadError::InvalidDocument(message) => ProcessingError::Permanent(message),
            })?;
        let projection = project_article(article_id, events).map_err(|error| {
            ProcessingError::Permanent(format!("invalid article event stream: {error:?}"))
        })?;
        self.projections.upsert(projection).await.map_err(|error| {
            ProcessingError::Transient(format!("projection upsert failed: {error}"))
        })
    }

    async fn record_dead_letter(&self, dead_letter: DeadLetter) -> Result<(), String> {
        self.dead_letters.record(dead_letter).await
    }
}

#[cfg(test)]
mod tests {
    use std::sync::Mutex;

    use crate::{
        EventDocument,
        firestore_stream::FirestoreEventDocument,
        projection::{ArticleStatus, StoredArticleEvent},
    };

    use super::*;

    const ARTICLE_ID: &str = "018f4edc-1f5a-7c4b-aef9-000000000001";

    struct StubStream {
        result: Result<Vec<StoredArticleEvent>, StreamReadError>,
    }

    #[async_trait]
    impl ArticleEventStream for StubStream {
        async fn load(
            &self,
            _article_id: Uuid,
        ) -> Result<Vec<StoredArticleEvent>, StreamReadError> {
            self.result.clone()
        }
    }

    struct StubDocuments {
        documents: Vec<FirestoreEventDocument>,
    }

    #[async_trait]
    impl FirestoreEventDocuments for StubDocuments {
        async fn load_event_documents(
            &self,
            _article_id: Uuid,
        ) -> Result<Vec<FirestoreEventDocument>, StreamReadError> {
            Ok(self.documents.clone())
        }
    }

    #[derive(Default)]
    struct RecordingProjectionStore {
        projections: Mutex<Vec<ArticleProjection>>,
        error: Option<String>,
    }

    #[async_trait]
    impl ArticleProjectionStore for RecordingProjectionStore {
        async fn upsert(&self, projection: ArticleProjection) -> Result<(), String> {
            if let Some(error) = &self.error {
                return Err(error.clone());
            }
            self.projections.lock().unwrap().push(projection);
            Ok(())
        }
    }

    #[derive(Default)]
    struct RecordingDeadLetterStore {
        dead_letters: Mutex<Vec<DeadLetter>>,
        error: Option<String>,
    }

    #[async_trait]
    impl DeadLetterStore for RecordingDeadLetterStore {
        async fn record(&self, dead_letter: DeadLetter) -> Result<(), String> {
            if let Some(error) = &self.error {
                return Err(error.clone());
            }
            self.dead_letters.lock().unwrap().push(dead_letter);
            Ok(())
        }
    }

    #[tokio::test]
    async fn reloads_folds_and_upserts_the_article_projection() {
        let article_id = Uuid::parse_str(ARTICLE_ID).unwrap();
        let projections = Arc::new(RecordingProjectionStore::default());
        let actions = RmuEventActions::new(
            Arc::new(StubStream {
                result: Ok(vec![StoredArticleEvent {
                    seq: 1,
                    json: r#"{"eventType":"ArticleDrafted","schemaVersion":1,"slug":"hello","title":"Hello","body":"Body"}"#.to_owned(),
                }]),
            }),
            projections.clone(),
            Arc::new(RecordingDeadLetterStore::default()),
        );
        let result = actions.process(accepted_event(article_id)).await;

        assert_eq!(result, Ok(()));
        assert_eq!(
            *projections.projections.lock().unwrap(),
            vec![ArticleProjection {
                article_id,
                status: ArticleStatus::Draft,
                slug: "hello".to_owned(),
                title: "Hello".to_owned(),
                body: "Body".to_owned(),
                published_at: None,
                last_applied_seq: 1,
            }]
        );
    }

    #[tokio::test]
    async fn adapts_the_firestore_reader_to_the_article_event_stream() {
        let article_id = Uuid::parse_str(ARTICLE_ID).unwrap();
        let stream = FirestoreEventStreamReader::new(StubDocuments {
            documents: vec![FirestoreEventDocument {
                name: format!(
                    "projects/demo-morecat/databases/(default)/documents/articles/{article_id}/events/1"
                ),
                json: "event-json".to_owned(),
            }],
        });

        let result = ArticleEventStream::load(&stream, article_id).await;

        assert_eq!(
            result,
            Ok(vec![StoredArticleEvent {
                seq: 1,
                json: "event-json".to_owned(),
            }])
        );
    }

    #[tokio::test]
    async fn treats_unavailable_event_streams_as_transient() {
        let article_id = Uuid::parse_str(ARTICLE_ID).unwrap();
        let projections = Arc::new(RecordingProjectionStore::default());
        let actions = RmuEventActions::new(
            Arc::new(StubStream {
                result: Err(StreamReadError::Unavailable("query failed".to_owned())),
            }),
            projections.clone(),
            Arc::new(RecordingDeadLetterStore::default()),
        );

        let result = actions.process(accepted_event(article_id)).await;

        assert_eq!(
            result,
            Err(ProcessingError::Transient("query failed".to_owned()))
        );
        assert!(projections.projections.lock().unwrap().is_empty());
    }

    #[tokio::test]
    async fn treats_invalid_event_documents_as_permanent() {
        let article_id = Uuid::parse_str(ARTICLE_ID).unwrap();
        let projections = Arc::new(RecordingProjectionStore::default());
        let actions = RmuEventActions::new(
            Arc::new(StubStream {
                result: Err(StreamReadError::InvalidDocument(
                    "invalid event document".to_owned(),
                )),
            }),
            projections.clone(),
            Arc::new(RecordingDeadLetterStore::default()),
        );

        let result = actions.process(accepted_event(article_id)).await;

        assert_eq!(
            result,
            Err(ProcessingError::Permanent(
                "invalid event document".to_owned()
            ))
        );
        assert!(projections.projections.lock().unwrap().is_empty());
    }

    #[tokio::test]
    async fn treats_invalid_article_event_streams_as_permanent() {
        let article_id = Uuid::parse_str(ARTICLE_ID).unwrap();
        let projections = Arc::new(RecordingProjectionStore::default());
        let actions = RmuEventActions::new(
            Arc::new(StubStream {
                result: Ok(Vec::new()),
            }),
            projections.clone(),
            Arc::new(RecordingDeadLetterStore::default()),
        );

        let result = actions.process(accepted_event(article_id)).await;

        assert_eq!(
            result,
            Err(ProcessingError::Permanent(
                "invalid article event stream: EmptyStream".to_owned()
            ))
        );
        assert!(projections.projections.lock().unwrap().is_empty());
    }

    #[tokio::test]
    async fn treats_projection_upsert_failures_as_transient() {
        let article_id = Uuid::parse_str(ARTICLE_ID).unwrap();
        let projections = Arc::new(RecordingProjectionStore {
            projections: Mutex::default(),
            error: Some("database unavailable".to_owned()),
        });
        let actions = RmuEventActions::new(
            Arc::new(StubStream {
                result: Ok(vec![StoredArticleEvent {
                    seq: 1,
                    json: r#"{"eventType":"ArticleDrafted","schemaVersion":1,"slug":"hello","title":"Hello","body":"Body"}"#.to_owned(),
                }]),
            }),
            projections.clone(),
            Arc::new(RecordingDeadLetterStore::default()),
        );

        let result = actions.process(accepted_event(article_id)).await;

        assert_eq!(
            result,
            Err(ProcessingError::Transient(
                "projection upsert failed: database unavailable".to_owned()
            ))
        );
        assert!(projections.projections.lock().unwrap().is_empty());
    }

    #[tokio::test]
    async fn records_dead_letters_in_the_dead_letter_store() {
        let dead_letters = Arc::new(RecordingDeadLetterStore::default());
        let actions = RmuEventActions::new(
            Arc::new(StubStream {
                result: Ok(Vec::new()),
            }),
            Arc::new(RecordingProjectionStore::default()),
            dead_letters.clone(),
        );
        let dead_letter = DeadLetter {
            event_id: Some("event-1".to_owned()),
            event_type: Some("event-type".to_owned()),
            source: Some("event-source".to_owned()),
            body: vec![1, 2, 3],
            reason: "invalid input".to_owned(),
        };

        let result = actions.record_dead_letter(dead_letter.clone()).await;

        assert_eq!(result, Ok(()));
        assert_eq!(
            *dead_letters.dead_letters.lock().unwrap(),
            vec![dead_letter]
        );
    }

    #[tokio::test]
    async fn preserves_dead_letter_store_failures() {
        let actions = RmuEventActions::new(
            Arc::new(StubStream {
                result: Ok(Vec::new()),
            }),
            Arc::new(RecordingProjectionStore::default()),
            Arc::new(RecordingDeadLetterStore {
                dead_letters: Mutex::default(),
                error: Some("dead-letter database unavailable".to_owned()),
            }),
        );
        let dead_letter = DeadLetter {
            event_id: None,
            event_type: None,
            source: None,
            body: Vec::new(),
            reason: "invalid input".to_owned(),
        };

        let result = actions.record_dead_letter(dead_letter).await;

        assert_eq!(result, Err("dead-letter database unavailable".to_owned()));
    }

    fn accepted_event(article_id: Uuid) -> AcceptedEvent {
        AcceptedEvent {
            event_id: "event-1".to_owned(),
            document: EventDocument { article_id, seq: 1 },
        }
    }
}
