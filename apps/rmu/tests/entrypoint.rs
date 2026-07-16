use std::process::Command;

#[cfg(all(
    unix,
    feature = "firestore-integration",
    feature = "postgres-integration"
))]
use std::{
    net::{TcpListener, TcpStream},
    thread,
    time::{Duration, Instant},
};

#[cfg(all(feature = "firestore-integration", feature = "postgres-integration"))]
use testcontainers_modules::{
    postgres::Postgres,
    testcontainers::{ImageExt, runners::AsyncRunner},
};

#[test]
fn rejects_missing_runtime_configuration() {
    let missing = Command::new(env!("CARGO_BIN_EXE_morecat-rmu"))
        .env_remove("GOOGLE_CLOUD_PROJECT")
        .env_remove("DATABASE_URL")
        .env_remove("PORT")
        .output()
        .unwrap();

    assert!(!missing.status.success());
    assert!(
        String::from_utf8(missing.stderr)
            .unwrap()
            .contains("missing GOOGLE_CLOUD_PROJECT")
    );

    let invalid_database = Command::new(env!("CARGO_BIN_EXE_morecat-rmu"))
        .env("GOOGLE_CLOUD_PROJECT", "demo-morecat")
        .env("DATABASE_URL", "postgres://user:secret@[invalid/morecat")
        .env("PORT", "8080")
        .output()
        .unwrap();

    assert!(!invalid_database.status.success());
    assert!(
        String::from_utf8(invalid_database.stderr)
            .unwrap()
            .contains("failed to connect to Postgres: invalid DATABASE_URL")
    );
}

#[cfg(all(feature = "firestore-integration", feature = "postgres-integration"))]
#[test]
fn initializes_live_dependencies_before_reporting_a_bind_failure() {
    let runtime = tokio::runtime::Runtime::new().unwrap();
    let container = runtime.block_on(async {
        Postgres::default()
            .with_tag("17-alpine")
            .start()
            .await
            .unwrap()
    });
    let host = runtime.block_on(container.get_host()).unwrap();
    let port = runtime
        .block_on(container.get_host_port_ipv4(5432))
        .unwrap();
    let database_url = format!("postgres://postgres:postgres@{host}:{port}/postgres");
    let occupied = std::net::TcpListener::bind(("0.0.0.0", 0)).unwrap();
    let occupied_port = occupied.local_addr().unwrap().port();

    let output = Command::new(env!("CARGO_BIN_EXE_morecat-rmu"))
        .env("GOOGLE_CLOUD_PROJECT", "demo-morecat")
        .env("DATABASE_URL", database_url)
        .env("PORT", occupied_port.to_string())
        .output()
        .unwrap();

    assert!(!output.status.success());
    assert!(
        String::from_utf8(output.stderr)
            .unwrap()
            .contains("failed to bind RMU listener")
    );

    runtime.block_on(async move { drop(container) });
}

#[cfg(all(
    unix,
    feature = "firestore-integration",
    feature = "postgres-integration"
))]
#[test]
fn stops_the_live_service_cleanly_on_interrupt() {
    let runtime = tokio::runtime::Runtime::new().unwrap();
    let container = runtime.block_on(async {
        Postgres::default()
            .with_tag("17-alpine")
            .start()
            .await
            .unwrap()
    });
    let host = runtime.block_on(container.get_host()).unwrap();
    let postgres_port = runtime
        .block_on(container.get_host_port_ipv4(5432))
        .unwrap();
    let database_url = format!("postgres://postgres:postgres@{host}:{postgres_port}/postgres");
    let port = available_port();
    let mut child = Command::new(env!("CARGO_BIN_EXE_morecat-rmu"))
        .env("GOOGLE_CLOUD_PROJECT", "demo-morecat")
        .env("DATABASE_URL", database_url)
        .env("PORT", port.to_string())
        .spawn()
        .unwrap();

    let deadline = Instant::now() + Duration::from_secs(10);
    while TcpStream::connect(("127.0.0.1", port)).is_err() {
        assert!(Instant::now() < deadline, "RMU listener did not start");
        thread::sleep(Duration::from_millis(25));
    }
    let signal = Command::new("kill")
        .args(["-INT", &child.id().to_string()])
        .status()
        .unwrap();
    let status = child.wait().unwrap();
    runtime.block_on(async move { drop(container) });

    assert!(signal.success());
    assert!(status.success());
}

#[cfg(all(
    unix,
    feature = "firestore-integration",
    feature = "postgres-integration"
))]
fn available_port() -> u16 {
    TcpListener::bind(("127.0.0.1", 0))
        .unwrap()
        .local_addr()
        .unwrap()
        .port()
}
