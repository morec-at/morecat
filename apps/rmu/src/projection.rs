use serde::Deserialize;
use uuid::Uuid;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StoredArticleEvent {
    pub seq: u64,
    pub json: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ArticleStatus {
    Draft,
    Published,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ArticleProjection {
    pub article_id: Uuid,
    pub status: ArticleStatus,
    pub slug: String,
    pub title: String,
    pub body: String,
    pub published_at: Option<i64>,
    pub last_applied_seq: u64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ProjectionError {
    EmptyStream,
    InvalidSequence { expected: u64, actual: u64 },
    InvalidEvent(String),
    InvalidTransition,
}

pub fn project_article(
    article_id: Uuid,
    mut events: Vec<StoredArticleEvent>,
) -> Result<ArticleProjection, ProjectionError> {
    events.sort_by_key(|event| event.seq);
    let mut projection = None;

    for (index, stored) in events.into_iter().enumerate() {
        let expected = index as u64 + 1;
        if stored.seq != expected {
            return Err(ProjectionError::InvalidSequence {
                expected,
                actual: stored.seq,
            });
        }

        match (projection.as_mut(), decode_event(&stored.json)?) {
            (None, ArticleEvent::Drafted { slug, title, body }) => {
                projection = Some(ArticleProjection {
                    article_id,
                    status: ArticleStatus::Draft,
                    slug,
                    title,
                    body,
                    published_at: None,
                    last_applied_seq: stored.seq,
                });
            }
            (Some(current), ArticleEvent::Published { published_at })
                if current.status == ArticleStatus::Draft =>
            {
                current.status = ArticleStatus::Published;
                current.published_at = Some(published_at);
                current.last_applied_seq = stored.seq;
            }
            _ => return Err(ProjectionError::InvalidTransition),
        }
    }

    projection.ok_or(ProjectionError::EmptyStream)
}

enum ArticleEvent {
    Drafted {
        slug: String,
        title: String,
        body: String,
    },
    Published {
        published_at: i64,
    },
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct WireEventHeader {
    event_type: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct WireArticleDrafted {
    #[serde(rename = "eventType")]
    _event_type: String,
    schema_version: u32,
    slug: String,
    title: String,
    body: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct WireArticlePublished {
    #[serde(rename = "eventType")]
    _event_type: String,
    schema_version: u32,
    published_at: i64,
}

fn decode_event(json: &str) -> Result<ArticleEvent, ProjectionError> {
    let header: WireEventHeader = serde_json::from_str(json)
        .map_err(|error| ProjectionError::InvalidEvent(error.to_string()))?;

    match header.event_type.as_str() {
        "ArticleDrafted" => {
            let event: WireArticleDrafted = serde_json::from_str(json)
                .map_err(|error| ProjectionError::InvalidEvent(error.to_string()))?;
            if event.schema_version != 1 {
                return Err(ProjectionError::InvalidEvent(
                    "unsupported ArticleDrafted schema".to_owned(),
                ));
            }
            if !valid_slug(&event.slug) || event.title.is_empty() {
                return Err(ProjectionError::InvalidEvent(
                    "invalid ArticleDrafted values".to_owned(),
                ));
            }
            Ok(ArticleEvent::Drafted {
                slug: event.slug,
                title: event.title,
                body: event.body,
            })
        }
        "ArticlePublished" => {
            let event: WireArticlePublished = serde_json::from_str(json)
                .map_err(|error| ProjectionError::InvalidEvent(error.to_string()))?;
            if event.schema_version != 1 {
                return Err(ProjectionError::InvalidEvent(
                    "unsupported ArticlePublished schema".to_owned(),
                ));
            }
            Ok(ArticleEvent::Published {
                published_at: event.published_at,
            })
        }
        event_type => Err(ProjectionError::InvalidEvent(format!(
            "unsupported eventType: {event_type}"
        ))),
    }
}

fn valid_slug(slug: &str) -> bool {
    !slug.is_empty()
        && slug.len() <= 255
        && slug.split('-').all(|segment| {
            !segment.is_empty()
                && segment
                    .bytes()
                    .all(|byte| byte.is_ascii_lowercase() || byte.is_ascii_digit())
        })
}

#[cfg(test)]
mod tests {
    use serde::Deserialize;

    use super::*;

    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct ProjectionFixture {
        article_id: String,
        events: Vec<FixtureEvent>,
        expected_projection: ExpectedProjection,
    }

    #[derive(Deserialize)]
    struct FixtureEvent {
        seq: u64,
        event: serde_json::Value,
    }

    #[derive(Deserialize)]
    #[serde(rename_all = "camelCase")]
    struct ExpectedProjection {
        status: String,
        slug: String,
        title: String,
        body: String,
        published_at: Option<i64>,
        last_applied_seq: u64,
    }

    #[test]
    fn folds_the_article_event_contract_fixture() {
        let fixture: ProjectionFixture =
            serde_json::from_str(include_str!("../../fixtures/article-event-stream.json"))
                .expect("valid projection fixture");
        let article_id = Uuid::parse_str(&fixture.article_id).expect("valid fixture article ID");
        let events = fixture
            .events
            .into_iter()
            .map(|event| StoredArticleEvent {
                seq: event.seq,
                json: event.event.to_string(),
            })
            .collect();

        let result = project_article(article_id, events);

        let expected = fixture.expected_projection;
        assert_eq!(expected.status, "published");
        assert_eq!(
            result,
            Ok(ArticleProjection {
                article_id,
                status: ArticleStatus::Published,
                slug: expected.slug,
                title: expected.title,
                body: expected.body,
                published_at: expected.published_at,
                last_applied_seq: expected.last_applied_seq,
            })
        );
    }

    #[test]
    fn rejects_invalid_draft_values() {
        let article_id = Uuid::parse_str("018f4edc-1f5a-7c4b-aef9-000000000001").unwrap();

        for json in [
            draft_json("../bad", "Title"),
            draft_json("", "Title"),
            draft_json(&"a".repeat(256), "Title"),
            draft_json("-bad", "Title"),
            draft_json("bad-", "Title"),
            draft_json("bad--slug", "Title"),
            draft_json("Bad", "Title"),
            draft_json("bad_slug", "Title"),
            draft_json("valid-slug", ""),
        ] {
            assert_invalid_event(project_article(
                article_id,
                vec![StoredArticleEvent { seq: 1, json }],
            ));
        }
    }

    #[test]
    fn projects_a_draft_and_accepts_digits_in_the_slug() {
        let article_id = Uuid::parse_str("018f4edc-1f5a-7c4b-aef9-000000000001").unwrap();

        let result = project_article(
            article_id,
            vec![StoredArticleEvent {
                seq: 1,
                json: draft_json("article-123", "Title"),
            }],
        );

        assert_eq!(
            result,
            Ok(ArticleProjection {
                article_id,
                status: ArticleStatus::Draft,
                slug: "article-123".to_owned(),
                title: "Title".to_owned(),
                body: "body".to_owned(),
                published_at: None,
                last_applied_seq: 1,
            })
        );
    }

    #[test]
    fn folds_events_in_sequence_order() {
        let article_id = Uuid::parse_str("018f4edc-1f5a-7c4b-aef9-000000000001").unwrap();

        let result = project_article(
            article_id,
            vec![
                StoredArticleEvent {
                    seq: 2,
                    json: published_json(999),
                },
                StoredArticleEvent {
                    seq: 1,
                    json: draft_json("slug", "Title"),
                },
            ],
        );

        assert_eq!(
            result,
            Ok(ArticleProjection {
                article_id,
                status: ArticleStatus::Published,
                slug: "slug".to_owned(),
                title: "Title".to_owned(),
                body: "body".to_owned(),
                published_at: Some(999),
                last_applied_seq: 2,
            })
        );
    }

    #[test]
    fn rejects_incomplete_or_duplicate_sequences() {
        let article_id = Uuid::parse_str("018f4edc-1f5a-7c4b-aef9-000000000001").unwrap();
        let cases = [
            (
                vec![StoredArticleEvent {
                    seq: 2,
                    json: draft_json("slug", "Title"),
                }],
                ProjectionError::InvalidSequence {
                    expected: 1,
                    actual: 2,
                },
            ),
            (
                vec![
                    StoredArticleEvent {
                        seq: 1,
                        json: draft_json("slug", "Title"),
                    },
                    StoredArticleEvent {
                        seq: 1,
                        json: draft_json("slug", "Title"),
                    },
                ],
                ProjectionError::InvalidSequence {
                    expected: 2,
                    actual: 1,
                },
            ),
        ];

        for (events, expected) in cases {
            assert_eq!(project_article(article_id, events), Err(expected));
        }
        assert_eq!(
            project_article(article_id, Vec::new()),
            Err(ProjectionError::EmptyStream)
        );
    }

    #[test]
    fn rejects_invalid_article_lifecycle_transitions() {
        let article_id = Uuid::parse_str("018f4edc-1f5a-7c4b-aef9-000000000001").unwrap();
        let cases = [
            vec![StoredArticleEvent {
                seq: 1,
                json: published_json(999),
            }],
            vec![
                StoredArticleEvent {
                    seq: 1,
                    json: draft_json("slug", "Title"),
                },
                StoredArticleEvent {
                    seq: 2,
                    json: draft_json("slug", "Title"),
                },
            ],
            vec![
                StoredArticleEvent {
                    seq: 1,
                    json: draft_json("slug", "Title"),
                },
                StoredArticleEvent {
                    seq: 2,
                    json: published_json(999),
                },
                StoredArticleEvent {
                    seq: 3,
                    json: published_json(1_000),
                },
            ],
        ];

        for events in cases {
            assert_eq!(
                project_article(article_id, events),
                Err(ProjectionError::InvalidTransition)
            );
        }
    }

    #[test]
    fn rejects_invalid_event_json_and_schema() {
        let article_id = Uuid::parse_str("018f4edc-1f5a-7c4b-aef9-000000000001").unwrap();
        let cases = [
            "not json".to_owned(),
            serde_json::json!({"eventType": "ArticleArchived", "schemaVersion": 1}).to_string(),
            serde_json::json!({
                "eventType": "ArticleDrafted",
                "schemaVersion": 2,
                "slug": "slug",
                "title": "Title",
                "body": "body"
            })
            .to_string(),
            serde_json::json!({
                "eventType": "ArticleDrafted",
                "schemaVersion": 1,
                "slug": "slug",
                "title": "Title",
                "body": "body",
                "extra": true
            })
            .to_string(),
            serde_json::json!({
                "eventType": "ArticlePublished",
                "schemaVersion": 2,
                "publishedAt": 999
            })
            .to_string(),
            serde_json::json!({
                "eventType": "ArticlePublished",
                "schemaVersion": 1
            })
            .to_string(),
            serde_json::json!({
                "eventType": "ArticlePublished",
                "schemaVersion": 1,
                "publishedAt": 999,
                "extra": true
            })
            .to_string(),
        ];

        for json in cases {
            assert_invalid_event(project_article(
                article_id,
                vec![StoredArticleEvent { seq: 1, json }],
            ));
        }
    }

    fn assert_invalid_event(result: Result<ArticleProjection, ProjectionError>) {
        let error = result.expect_err("event should be rejected");
        assert_eq!(
            std::mem::discriminant(&error),
            std::mem::discriminant(&ProjectionError::InvalidEvent(String::new()))
        );
    }

    fn draft_json(slug: &str, title: &str) -> String {
        serde_json::json!({
            "eventType": "ArticleDrafted",
            "schemaVersion": 1,
            "slug": slug,
            "title": title,
            "body": "body"
        })
        .to_string()
    }

    fn published_json(published_at: i64) -> String {
        serde_json::json!({
            "eventType": "ArticlePublished",
            "schemaVersion": 1,
            "publishedAt": published_at
        })
        .to_string()
    }
}
