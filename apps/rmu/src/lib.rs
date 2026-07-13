use uuid::Uuid;

pub mod eventarc;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct EventDocument {
    pub article_id: Uuid,
    pub seq: u64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum InvalidDocumentName {
    WrongPath,
    InvalidArticleId,
    InvalidSequence,
}

pub fn parse_document_name(name: &str) -> Result<EventDocument, InvalidDocumentName> {
    let segments: Vec<_> = name.split('/').collect();
    let [
        "projects",
        project,
        "databases",
        database,
        "documents",
        "articles",
        article_id,
        "events",
        seq,
    ] = segments.as_slice()
    else {
        return Err(InvalidDocumentName::WrongPath);
    };
    if project.is_empty() || database.is_empty() {
        return Err(InvalidDocumentName::WrongPath);
    }

    let article_id = Uuid::parse_str(article_id)
        .ok()
        .filter(|id| id.get_version_num() == 7)
        .ok_or(InvalidDocumentName::InvalidArticleId)?;
    let seq = seq
        .parse::<u64>()
        .ok()
        .filter(|seq| *seq > 0)
        .ok_or(InvalidDocumentName::InvalidSequence)?;

    Ok(EventDocument { article_id, seq })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_an_article_event_document_name() {
        let result = parse_document_name(
            "projects/demo-morecat/databases/(default)/documents/articles/\
             018f4edc-1f5a-7c4b-aef9-000000000001/events/1",
        );

        assert_eq!(
            result,
            Ok(EventDocument {
                article_id: Uuid::parse_str("018f4edc-1f5a-7c4b-aef9-000000000001").unwrap(),
                seq: 1,
            })
        );
    }

    #[test]
    fn rejects_documents_outside_the_article_event_path() {
        let cases = [
            (
                "projects/p/databases/(default)/documents/slugs/hello-world",
                InvalidDocumentName::WrongPath,
            ),
            (
                "projects/p/databases/(default)/documents/articles/\
                 550e8400-e29b-41d4-a716-446655440000/events/1",
                InvalidDocumentName::InvalidArticleId,
            ),
            (
                "projects/p/databases/(default)/documents/articles/not-a-uuid/events/1",
                InvalidDocumentName::InvalidArticleId,
            ),
            (
                "projects/p/databases/(default)/documents/articles/\
                 018f4edc-1f5a-7c4b-aef9-000000000001/events/0",
                InvalidDocumentName::InvalidSequence,
            ),
            (
                "projects/p/databases/(default)/documents/articles/\
                 018f4edc-1f5a-7c4b-aef9-000000000001/events/latest",
                InvalidDocumentName::InvalidSequence,
            ),
            (
                "projects/p/databases/(default)/documents/articles/\
                 018f4edc-1f5a-7c4b-aef9-000000000001/events/1/extra",
                InvalidDocumentName::WrongPath,
            ),
            (
                "projects//databases/(default)/documents/articles/\
                 018f4edc-1f5a-7c4b-aef9-000000000001/events/1",
                InvalidDocumentName::WrongPath,
            ),
            (
                "projects/p/databases//documents/articles/\
                 018f4edc-1f5a-7c4b-aef9-000000000001/events/1",
                InvalidDocumentName::WrongPath,
            ),
        ];

        for (name, expected) in cases {
            assert_eq!(parse_document_name(name), Err(expected), "{name}");
        }
    }
}
