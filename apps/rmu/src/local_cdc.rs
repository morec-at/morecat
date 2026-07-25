use std::{future::Future, io::Write, pin::Pin, sync::Arc};

use firestore::{
    FirestoreDb, FirestoreListenEvent, FirestoreListenerTarget, FirestoreMemListenStateStorage,
};

use crate::{parse_document_name, replay::ArticleProjector};

const ARTICLE_EVENTS_TARGET: i32 = 1;
type BoxedResult<T> = Result<T, Box<dyn std::error::Error + Send + Sync>>;

pub async fn run_bridge(
    db: &FirestoreDb,
    projector: Arc<dyn ArticleProjector>,
    shutdown: Pin<Box<dyn Future<Output = ()> + Send + 'static>>,
) -> Result<(), String> {
    let mut listener = db
        .create_listener(FirestoreMemListenStateStorage::new())
        .await
        .expect("in-memory Firestore listener creation must succeed");
    db.fluent()
        .select()
        .from("events")
        .all_descendants()
        .listen()
        .add_target(
            FirestoreListenerTarget::try_from(ARTICLE_EVENTS_TARGET)
                .expect("article event listener target must be valid"),
            &mut listener,
        )
        .expect("valid article event listener target must be accepted");
    listener
        .start(move |event| {
            let projector = projector.clone();
            process_change(event, projector)
        })
        .await
        .expect("in-memory Firestore listener state must start");
    let mut stdout = std::io::stdout().lock();
    writeln!(stdout, "Local CDC bridge listening for article events")
        .expect("failed to write local CDC bridge readiness");
    stdout
        .flush()
        .expect("failed to flush local CDC bridge readiness");
    drop(stdout);

    shutdown.await;
    listener
        .shutdown()
        .await
        .expect("Firestore listener shutdown must succeed");
    Ok(())
}

async fn process_change(
    event: FirestoreListenEvent,
    projector: Arc<dyn ArticleProjector>,
) -> BoxedResult<()> {
    handle_change(event, projector)
        .await
        .map_err(|error| error.into())
}

pub async fn handle_change(
    event: FirestoreListenEvent,
    projector: Arc<dyn ArticleProjector>,
) -> Result<(), String> {
    let FirestoreListenEvent::DocumentChange(change) = event else {
        return Ok(());
    };
    let document_name = change
        .document
        .map(|document| document.name)
        .filter(|name| !name.is_empty())
        .ok_or_else(|| "Firestore change is missing its document name".to_owned())?;
    let document = parse_document_name(&document_name)
        .map_err(|_| "Firestore change has an invalid article event path".to_owned())?;
    projector
        .project(document.article_id)
        .await
        .map_err(|_| "article projection failed".to_owned())
}

#[cfg(test)]
mod tests {
    use std::sync::Mutex;

    use async_trait::async_trait;
    use gcloud_sdk::google::firestore::v1::{Document, DocumentChange, TargetChange};
    use uuid::Uuid;

    use crate::eventarc::ProcessingError;

    use super::*;

    const ARTICLE_ID: &str = "018f4edc-1f5a-7c4b-aef9-000000000001";

    #[derive(Default)]
    struct RecordingProjector {
        article_ids: Mutex<Vec<Uuid>>,
        error: Option<ProcessingError>,
    }

    #[async_trait]
    impl ArticleProjector for RecordingProjector {
        async fn project(&self, article_id: Uuid) -> Result<(), ProcessingError> {
            self.article_ids.lock().unwrap().push(article_id);
            self.error.clone().map_or(Ok(()), Err)
        }
    }

    #[tokio::test]
    async fn projects_the_article_changed_by_the_firestore_listener() {
        let projector = Arc::new(RecordingProjector::default());
        let event = FirestoreListenEvent::DocumentChange(DocumentChange {
            document: Some(Document {
                name: format!(
                    "projects/demo-morecat/databases/(default)/documents/articles/{ARTICLE_ID}/events/1"
                ),
                ..Default::default()
            }),
            ..Default::default()
        });

        let result = handle_change(event, projector.clone()).await;

        assert_eq!(result, Ok(()));
        assert_eq!(
            *projector.article_ids.lock().unwrap(),
            vec![Uuid::parse_str(ARTICLE_ID).unwrap()]
        );
    }

    #[tokio::test]
    async fn ignores_listener_metadata_events() {
        let projector = Arc::new(RecordingProjector::default());

        let result = handle_change(
            FirestoreListenEvent::TargetChange(TargetChange::default()),
            projector.clone(),
        )
        .await;

        assert_eq!(result, Ok(()));
        assert!(projector.article_ids.lock().unwrap().is_empty());
    }

    #[tokio::test]
    async fn rejects_missing_and_invalid_article_event_paths() {
        let projector = Arc::new(RecordingProjector::default());
        let missing = FirestoreListenEvent::DocumentChange(DocumentChange::default());
        let empty = FirestoreListenEvent::DocumentChange(DocumentChange {
            document: Some(Document::default()),
            ..Default::default()
        });
        let invalid = FirestoreListenEvent::DocumentChange(DocumentChange {
            document: Some(Document {
                name: "projects/demo-morecat/databases/(default)/documents/slugs/hello".to_owned(),
                ..Default::default()
            }),
            ..Default::default()
        });

        assert_eq!(
            handle_change(missing, projector.clone()).await,
            Err("Firestore change is missing its document name".to_owned())
        );
        assert_eq!(
            handle_change(empty, projector.clone()).await,
            Err("Firestore change is missing its document name".to_owned())
        );
        assert_eq!(
            handle_change(invalid, projector.clone()).await,
            Err("Firestore change has an invalid article event path".to_owned())
        );
        assert!(projector.article_ids.lock().unwrap().is_empty());
    }

    #[tokio::test]
    async fn reports_projection_failures_without_exposing_details() {
        let projector = Arc::new(RecordingProjector {
            error: Some(ProcessingError::Transient("database secret".to_owned())),
            ..Default::default()
        });
        let event = FirestoreListenEvent::DocumentChange(DocumentChange {
            document: Some(Document {
                name: format!(
                    "projects/demo-morecat/databases/(default)/documents/articles/{ARTICLE_ID}/events/1"
                ),
                ..Default::default()
            }),
            ..Default::default()
        });

        assert_eq!(
            process_change(event, projector)
                .await
                .unwrap_err()
                .to_string(),
            "article projection failed"
        );
    }
}
