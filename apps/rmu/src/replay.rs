use async_trait::async_trait;
use uuid::Uuid;

use crate::{eventarc::ProcessingError, firestore_stream::StreamReadError};

#[async_trait]
pub trait ArticleCatalog: Send + Sync {
    async fn list_article_ids(&self) -> Result<Vec<Uuid>, StreamReadError>;
}

#[async_trait]
pub trait ArticleProjector: Send + Sync {
    async fn project(&self, article_id: Uuid) -> Result<(), ProcessingError>;
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ReplayError {
    Catalog(StreamReadError),
    Article {
        article_id: Uuid,
        error: ProcessingError,
    },
}

pub struct ArticleReplay<C, P> {
    catalog: C,
    projector: P,
}

impl<C, P> ArticleReplay<C, P> {
    pub fn new(catalog: C, projector: P) -> Self {
        Self { catalog, projector }
    }
}

impl<C, P> ArticleReplay<C, P>
where
    C: ArticleCatalog,
    P: ArticleProjector,
{
    pub async fn replay_all(&self) -> Result<usize, ReplayError> {
        let article_ids = self
            .catalog
            .list_article_ids()
            .await
            .map_err(ReplayError::Catalog)?;
        for article_id in &article_ids {
            self.projector
                .project(*article_id)
                .await
                .map_err(|error| ReplayError::Article {
                    article_id: *article_id,
                    error,
                })?;
        }
        Ok(article_ids.len())
    }
}

#[cfg(test)]
mod tests {
    use std::sync::Mutex;

    use super::*;

    const FIRST_ARTICLE_ID: &str = "018f4edc-1f5a-7c4b-aef9-000000000001";
    const SECOND_ARTICLE_ID: &str = "018f4edc-1f5a-7c4b-aef9-000000000002";

    struct StubCatalog {
        article_ids: Vec<Uuid>,
        error: Option<StreamReadError>,
    }

    #[async_trait]
    impl ArticleCatalog for StubCatalog {
        async fn list_article_ids(&self) -> Result<Vec<Uuid>, StreamReadError> {
            match &self.error {
                Some(error) => Err(error.clone()),
                None => Ok(self.article_ids.clone()),
            }
        }
    }

    #[derive(Default)]
    struct RecordingProjector {
        article_ids: Mutex<Vec<Uuid>>,
        error_for: Option<Uuid>,
    }

    #[async_trait]
    impl ArticleProjector for RecordingProjector {
        async fn project(&self, article_id: Uuid) -> Result<(), ProcessingError> {
            if self.error_for == Some(article_id) {
                return Err(ProcessingError::Transient("projection failed".to_owned()));
            }
            self.article_ids.lock().unwrap().push(article_id);
            Ok(())
        }
    }

    #[tokio::test]
    async fn replays_every_article_and_reports_the_count() {
        let article_ids = vec![article_id(FIRST_ARTICLE_ID), article_id(SECOND_ARTICLE_ID)];
        let projector = RecordingProjector::default();
        let replay = ArticleReplay::new(
            StubCatalog {
                article_ids: article_ids.clone(),
                error: None,
            },
            projector,
        );

        let result = replay.replay_all().await;

        assert_eq!(result, Ok(2));
        assert_eq!(*replay.projector.article_ids.lock().unwrap(), article_ids);
    }

    #[tokio::test]
    async fn preserves_catalog_and_article_failures() {
        let first = article_id(FIRST_ARTICLE_ID);
        let second = article_id(SECOND_ARTICLE_ID);
        let catalog_error = StreamReadError::Unavailable("catalog failed".to_owned());
        let unavailable = ArticleReplay::new(
            StubCatalog {
                article_ids: Vec::new(),
                error: Some(catalog_error.clone()),
            },
            RecordingProjector::default(),
        );
        assert_eq!(
            unavailable.replay_all().await,
            Err(ReplayError::Catalog(catalog_error))
        );

        let failing = ArticleReplay::new(
            StubCatalog {
                article_ids: vec![first, second],
                error: None,
            },
            RecordingProjector {
                error_for: Some(second),
                ..RecordingProjector::default()
            },
        );
        assert_eq!(
            failing.replay_all().await,
            Err(ReplayError::Article {
                article_id: second,
                error: ProcessingError::Transient("projection failed".to_owned()),
            })
        );
        assert_eq!(*failing.projector.article_ids.lock().unwrap(), vec![first]);
    }

    #[tokio::test]
    async fn accepts_an_empty_catalog() {
        let replay = ArticleReplay::new(
            StubCatalog {
                article_ids: Vec::new(),
                error: None,
            },
            RecordingProjector::default(),
        );

        assert_eq!(replay.replay_all().await, Ok(0));
    }

    fn article_id(value: &str) -> Uuid {
        Uuid::parse_str(value).unwrap()
    }
}
