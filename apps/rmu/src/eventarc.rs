use std::sync::Arc;

use async_trait::async_trait;
use axum::{
    Router,
    body::Bytes,
    extract::State,
    http::{HeaderMap, StatusCode},
    routing::post,
};
use prost::Message;

use crate::{EventDocument, parse_document_name};

const FIRESTORE_CREATED_EVENT: &str = "google.cloud.firestore.document.v1.created";

#[derive(Clone, PartialEq, Message)]
struct DocumentEventData {
    #[prost(message, optional, tag = "1")]
    value: Option<FirestoreDocument>,
}

#[derive(Clone, PartialEq, Message)]
struct FirestoreDocument {
    #[prost(string, tag = "1")]
    name: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AcceptedEvent {
    pub event_id: String,
    pub document: EventDocument,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DeadLetter {
    pub event_id: Option<String>,
    pub event_type: Option<String>,
    pub source: Option<String>,
    pub body: Vec<u8>,
    pub reason: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ProcessingError {
    Permanent(String),
    Transient(String),
}

#[async_trait]
pub trait EventActions: Send + Sync {
    async fn process(&self, event: AcceptedEvent) -> Result<(), ProcessingError>;
    async fn record_dead_letter(&self, dead_letter: DeadLetter) -> Result<(), String>;
}

#[derive(Clone)]
struct AppState {
    actions: Arc<dyn EventActions>,
}

pub fn router(actions: Arc<dyn EventActions>) -> Router {
    Router::new()
        .route("/", post(receive_event))
        .with_state(AppState { actions })
}

async fn receive_event(
    State(state): State<AppState>,
    headers: HeaderMap,
    body: Bytes,
) -> StatusCode {
    match decode_event(&headers, &body) {
        Ok(event) => match state.actions.process(event).await {
            Ok(()) => StatusCode::NO_CONTENT,
            Err(ProcessingError::Permanent(reason)) => {
                record_dead_letter(&state, &headers, &body, reason).await
            }
            Err(ProcessingError::Transient(_)) => StatusCode::SERVICE_UNAVAILABLE,
        },
        Err(reason) => record_dead_letter(&state, &headers, &body, reason).await,
    }
}

async fn record_dead_letter(
    state: &AppState,
    headers: &HeaderMap,
    body: &[u8],
    reason: String,
) -> StatusCode {
    let dead_letter = DeadLetter {
        event_id: header(headers, "ce-id"),
        event_type: header(headers, "ce-type"),
        source: header(headers, "ce-source"),
        body: body.to_vec(),
        reason,
    };
    match state.actions.record_dead_letter(dead_letter).await {
        Ok(()) => StatusCode::NO_CONTENT,
        Err(_) => StatusCode::SERVICE_UNAVAILABLE,
    }
}

fn decode_event(headers: &HeaderMap, body: &[u8]) -> Result<AcceptedEvent, String> {
    let specversion = required_header(headers, "ce-specversion")?;
    if specversion != "1.0" {
        return Err("unsupported CloudEvents specversion".to_owned());
    }

    let event_type = required_header(headers, "ce-type")?;
    if event_type != FIRESTORE_CREATED_EVENT {
        return Err("unsupported CloudEvent type".to_owned());
    }

    let source = required_header(headers, "ce-source")?;
    let event_id = required_header(headers, "ce-id")?.to_owned();
    if !is_protobuf_content_type(required_header(headers, "content-type")?) {
        return Err("unsupported content type".to_owned());
    }

    let data = DocumentEventData::decode(body).map_err(|_| "malformed protobuf body".to_owned())?;
    let name = data
        .value
        .map(|document| document.name)
        .filter(|name| !name.is_empty())
        .ok_or_else(|| "missing Firestore document value".to_owned())?;
    let database_resource = name
        .split_once("/documents/")
        .map(|(database, _)| database)
        .ok_or_else(|| "invalid Firestore document name".to_owned())?;
    let document = parse_document_name(&name)
        .map_err(|error| format!("invalid Firestore document name: {error:?}"))?;
    let expected_source = format!("//firestore.googleapis.com/{database_resource}");
    if source != expected_source {
        return Err("CloudEvent source does not match the document database".to_owned());
    }

    Ok(AcceptedEvent { event_id, document })
}

fn is_protobuf_content_type(value: &str) -> bool {
    value.parse::<mime::Mime>().is_ok_and(|media_type| {
        media_type.type_() == mime::APPLICATION && media_type.subtype() == "protobuf"
    })
}

fn required_header<'a>(headers: &'a HeaderMap, name: &str) -> Result<&'a str, String> {
    headers
        .get(name)
        .and_then(|value| value.to_str().ok())
        .filter(|value| !value.is_empty())
        .ok_or_else(|| format!("missing or invalid {name} header"))
}

fn header(headers: &HeaderMap, name: &str) -> Option<String> {
    headers
        .get(name)
        .and_then(|value| value.to_str().ok())
        .filter(|value| !value.is_empty())
        .map(str::to_owned)
}

#[cfg(test)]
mod tests {
    use std::sync::{
        Mutex,
        atomic::{AtomicBool, Ordering},
    };

    use axum::{
        body::Body,
        http::{Request, header::CONTENT_TYPE},
    };
    use base64::{Engine, engine::general_purpose::STANDARD};
    use prost::Message;
    use serde::Deserialize;
    use tower::ServiceExt;

    use super::*;

    const ARTICLE_ID: &str = "018f4edc-1f5a-7c4b-aef9-000000000001";

    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct CloudEventFixture {
        headers: std::collections::BTreeMap<String, String>,
        body_base64: String,
    }

    #[derive(Default)]
    struct RecordingActions {
        processed: Mutex<Vec<AcceptedEvent>>,
        dead_letters: Mutex<Vec<DeadLetter>>,
        fail_processing: AtomicBool,
        fail_permanently: AtomicBool,
        fail_dead_letter: AtomicBool,
    }

    #[async_trait]
    impl EventActions for RecordingActions {
        async fn process(&self, event: AcceptedEvent) -> Result<(), ProcessingError> {
            if self.fail_permanently.load(Ordering::Relaxed) {
                return Err(ProcessingError::Permanent(
                    "invalid event stream".to_owned(),
                ));
            }
            if self.fail_processing.load(Ordering::Relaxed) {
                return Err(ProcessingError::Transient("processing failed".to_owned()));
            }
            self.processed.lock().unwrap().push(event);
            Ok(())
        }

        async fn record_dead_letter(&self, dead_letter: DeadLetter) -> Result<(), String> {
            if self.fail_dead_letter.load(Ordering::Relaxed) {
                return Err("dead-letter failed".to_owned());
            }
            self.dead_letters.lock().unwrap().push(dead_letter);
            Ok(())
        }
    }

    #[tokio::test]
    async fn accepts_a_firestore_document_created_event() {
        let actions = Arc::new(RecordingActions::default());
        let response = router(actions.clone())
            .oneshot(valid_request())
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::NO_CONTENT);
        assert_eq!(
            *actions.processed.lock().unwrap(),
            vec![AcceptedEvent {
                event_id: "event-1".to_owned(),
                document: EventDocument {
                    article_id: uuid::Uuid::parse_str(ARTICLE_ID).unwrap(),
                    seq: 1,
                },
            }]
        );
        assert!(actions.dead_letters.lock().unwrap().is_empty());
    }

    #[tokio::test]
    async fn accepts_the_firestore_binary_cloudevent_fixture() {
        let fixture: CloudEventFixture = serde_json::from_str(include_str!(
            "../tests/fixtures/firestore-document-created.json"
        ))
        .unwrap();
        let mut request = Request::post("/");
        for (name, value) in fixture.headers {
            request = request.header(name, value);
        }
        let request = request
            .body(Body::from(STANDARD.decode(fixture.body_base64).unwrap()))
            .unwrap();
        let actions = Arc::new(RecordingActions::default());

        let response = router(actions.clone()).oneshot(request).await.unwrap();

        assert_eq!(response.status(), StatusCode::NO_CONTENT);
        assert_eq!(actions.processed.lock().unwrap().len(), 1);
        assert!(actions.dead_letters.lock().unwrap().is_empty());
    }

    #[tokio::test]
    async fn accepts_equivalent_protobuf_content_types() {
        for content_type in [
            "Application/Protobuf",
            "application/protobuf; charset=utf-8",
        ] {
            let actions = Arc::new(RecordingActions::default());
            let request = request_with_header(CONTENT_TYPE.as_str(), content_type);

            let response = router(actions.clone()).oneshot(request).await.unwrap();

            assert_eq!(response.status(), StatusCode::NO_CONTENT, "{content_type}");
            assert_eq!(actions.processed.lock().unwrap().len(), 1, "{content_type}");
            assert!(
                actions.dead_letters.lock().unwrap().is_empty(),
                "{content_type}"
            );
        }
    }

    #[tokio::test]
    async fn acknowledges_permanent_input_errors_after_recording_them() {
        let cases = [
            {
                let mut request = valid_request_for_name(
                    "projects/demo-morecat/databases/(default)/documents/slugs/hello-world",
                );
                request
                    .headers_mut()
                    .insert("ce-id", "wrong-path".parse().expect("valid header value"));
                request
            },
            {
                let mut request = valid_request();
                request.headers_mut().remove("ce-type");
                request
            },
            request_without_header("ce-source"),
            request_without_header("ce-id"),
            request_without_header(CONTENT_TYPE.as_str()),
            request_with_header("ce-type", ""),
            request_with_header("ce-specversion", "0.3"),
            request_with_header("ce-type", "google.cloud.firestore.document.v1.updated"),
            request_with_header(CONTENT_TYPE.as_str(), "application/json"),
            request_with_header(CONTENT_TYPE.as_str(), "text/protobuf"),
            request_with_header(CONTENT_TYPE.as_str(), "not a media type"),
            request_with_data(DocumentEventData { value: None }),
            request_with_data(DocumentEventData {
                value: Some(FirestoreDocument {
                    name: String::new(),
                }),
            }),
            valid_request_for_name("not-a-firestore-document-name"),
            Request::post("/")
                .header("ce-specversion", "1.0")
                .header("ce-id", "malformed-body")
                .header(
                    "ce-source",
                    "//firestore.googleapis.com/projects/demo-morecat/databases/(default)",
                )
                .header("ce-type", FIRESTORE_CREATED_EVENT)
                .header(CONTENT_TYPE, "application/protobuf")
                .body(Body::from(vec![0xff]))
                .unwrap(),
        ];

        for request in cases {
            let actions = Arc::new(RecordingActions::default());
            let response = router(actions.clone()).oneshot(request).await.unwrap();

            assert_eq!(response.status(), StatusCode::NO_CONTENT);
            assert!(actions.processed.lock().unwrap().is_empty());
            let dead_letters = actions.dead_letters.lock().unwrap();
            assert_eq!(dead_letters.len(), 1);
        }
    }

    #[tokio::test]
    async fn preserves_missing_cloudevent_metadata_in_the_dead_letter() {
        let actions = Arc::new(RecordingActions::default());
        let request = Request::post("/")
            .body(Body::from("invalid event"))
            .unwrap();

        let response = router(actions.clone()).oneshot(request).await.unwrap();

        assert_eq!(response.status(), StatusCode::NO_CONTENT);
        let dead_letters = actions.dead_letters.lock().unwrap();
        assert_eq!(dead_letters.len(), 1);
        assert_eq!(dead_letters[0].event_id, None);
        assert_eq!(dead_letters[0].event_type, None);
        assert_eq!(dead_letters[0].source, None);
    }

    #[tokio::test]
    async fn preserves_invalid_cloudevent_metadata_as_missing_in_the_dead_letter() {
        let actions = Arc::new(RecordingActions::default());
        let mut request = valid_request();
        request.headers_mut().insert(
            "ce-id",
            axum::http::HeaderValue::from_bytes(&[0xff]).expect("valid opaque header value"),
        );
        request.headers_mut().insert(
            "ce-source",
            axum::http::HeaderValue::from_bytes(&[0xff]).expect("valid opaque header value"),
        );

        let response = router(actions.clone()).oneshot(request).await.unwrap();

        assert_eq!(response.status(), StatusCode::NO_CONTENT);
        let dead_letters = actions.dead_letters.lock().unwrap();
        assert_eq!(dead_letters.len(), 1);
        assert_eq!(dead_letters[0].event_id, None);
        assert_eq!(dead_letters[0].source, None);
    }

    #[tokio::test]
    async fn rejects_a_cloudevent_source_that_does_not_match_the_document_database() {
        let actions = Arc::new(RecordingActions::default());
        let mut request = valid_request();
        request.headers_mut().insert(
            "ce-source",
            "//storage.googleapis.com/projects/demo-morecat"
                .parse()
                .expect("valid header value"),
        );

        let response = router(actions.clone()).oneshot(request).await.unwrap();

        assert_eq!(response.status(), StatusCode::NO_CONTENT);
        assert!(actions.processed.lock().unwrap().is_empty());
        let dead_letters = actions.dead_letters.lock().unwrap();
        assert_eq!(dead_letters.len(), 1);
        assert!(dead_letters[0].reason.contains("CloudEvent source"));
    }

    #[tokio::test]
    async fn returns_service_unavailable_for_transient_processing_failures() {
        let actions = Arc::new(RecordingActions::default());
        actions.fail_processing.store(true, Ordering::Relaxed);

        let response = router(actions.clone())
            .oneshot(valid_request())
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::SERVICE_UNAVAILABLE);
        assert!(actions.processed.lock().unwrap().is_empty());
        assert!(actions.dead_letters.lock().unwrap().is_empty());
    }

    #[tokio::test]
    async fn acknowledges_permanent_processing_failures_after_recording_them() {
        let actions = Arc::new(RecordingActions::default());
        actions.fail_permanently.store(true, Ordering::Relaxed);

        let response = router(actions.clone())
            .oneshot(valid_request())
            .await
            .unwrap();

        assert_eq!(response.status(), StatusCode::NO_CONTENT);
        assert!(actions.processed.lock().unwrap().is_empty());
        let dead_letters = actions.dead_letters.lock().unwrap();
        assert_eq!(dead_letters.len(), 1);
        assert!(dead_letters[0].reason.contains("invalid event stream"));
    }

    #[tokio::test]
    async fn returns_service_unavailable_when_dead_letter_recording_fails() {
        let actions = Arc::new(RecordingActions::default());
        actions.fail_dead_letter.store(true, Ordering::Relaxed);
        let mut request = valid_request();
        request.headers_mut().remove("ce-type");

        let response = router(actions.clone()).oneshot(request).await.unwrap();

        assert_eq!(response.status(), StatusCode::SERVICE_UNAVAILABLE);
        assert!(actions.processed.lock().unwrap().is_empty());
        assert!(actions.dead_letters.lock().unwrap().is_empty());
    }

    fn valid_request() -> Request<Body> {
        valid_request_for_name(&format!(
            "projects/demo-morecat/databases/(default)/documents/articles/{ARTICLE_ID}/events/1"
        ))
    }

    fn valid_request_for_name(name: &str) -> Request<Body> {
        request_with_data(DocumentEventData {
            value: Some(FirestoreDocument {
                name: name.to_owned(),
            }),
        })
    }

    fn request_with_data(data: DocumentEventData) -> Request<Body> {
        Request::post("/")
            .header("ce-specversion", "1.0")
            .header("ce-id", "event-1")
            .header(
                "ce-source",
                "//firestore.googleapis.com/projects/demo-morecat/databases/(default)",
            )
            .header("ce-type", FIRESTORE_CREATED_EVENT)
            .header(CONTENT_TYPE, "application/protobuf")
            .body(Body::from(data.encode_to_vec()))
            .unwrap()
    }

    fn request_with_header(name: &str, value: &str) -> Request<Body> {
        let mut request = valid_request();
        request.headers_mut().insert(
            name.parse::<axum::http::HeaderName>()
                .expect("valid header name"),
            value.parse().expect("valid header value"),
        );
        request
    }

    fn request_without_header(name: &str) -> Request<Body> {
        let mut request = valid_request();
        request.headers_mut().remove(name);
        request
    }
}
