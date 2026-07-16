use std::process::Command;

#[cfg(all(
    unix,
    feature = "firestore-integration",
    feature = "postgres-integration"
))]
use std::{
    io::{BufRead, BufReader},
    net::TcpStream,
    process::Stdio,
    sync::mpsc,
    thread,
    time::Duration,
};

#[cfg(all(feature = "firestore-integration", feature = "postgres-integration"))]
use testcontainers_modules::{
    postgres::Postgres,
    testcontainers::{ImageExt, runners::AsyncRunner},
};

#[test]
fn rejects_missing_runtime_configuration() {
    let unknown_command = Command::new(env!("CARGO_BIN_EXE_morecat-rmu"))
        .arg("unknown")
        .output()
        .unwrap();
    assert!(!unknown_command.status.success());
    assert!(
        String::from_utf8(unknown_command.stderr)
            .unwrap()
            .contains("usage: morecat-rmu [replay]")
    );

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

    let missing_replay = Command::new(env!("CARGO_BIN_EXE_morecat-rmu"))
        .arg("replay")
        .env_remove("GOOGLE_CLOUD_PROJECT")
        .env_remove("DATABASE_URL")
        .env_remove("PORT")
        .output()
        .unwrap();
    assert!(!missing_replay.status.success());
    assert!(
        String::from_utf8(missing_replay.stderr)
            .unwrap()
            .contains("missing GOOGLE_CLOUD_PROJECT")
    );

    let invalid_replay_database = Command::new(env!("CARGO_BIN_EXE_morecat-rmu"))
        .arg("replay")
        .env("GOOGLE_CLOUD_PROJECT", "demo-morecat")
        .env("DATABASE_URL", "postgres://user:secret@[invalid/morecat")
        .output()
        .unwrap();
    assert!(!invalid_replay_database.status.success());
    assert!(
        String::from_utf8(invalid_replay_database.stderr)
            .unwrap()
            .contains("failed to connect to Postgres: invalid DATABASE_URL")
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
            .contains("failed to bind RMU listener: address already in use")
    );

    runtime.block_on(async move { drop(container) });
}

#[cfg(all(feature = "firestore-integration", feature = "postgres-integration"))]
#[test]
fn runs_replay_without_starting_the_http_listener() {
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
    let occupied = std::net::TcpListener::bind(("0.0.0.0", 0)).unwrap();
    let occupied_port = occupied.local_addr().unwrap().port();

    let output = Command::new(env!("CARGO_BIN_EXE_morecat-rmu"))
        .arg("replay")
        .env("GOOGLE_CLOUD_PROJECT", "demo-morecat-empty-replay")
        .env("DATABASE_URL", database_url)
        .env("PORT", occupied_port.to_string())
        .output()
        .unwrap();
    runtime.block_on(async move { drop(container) });

    assert!(output.status.success());
    assert_eq!(
        String::from_utf8(output.stdout).unwrap(),
        "Replayed 0 article(s)\n"
    );
}

#[cfg(all(
    unix,
    feature = "firestore-integration",
    feature = "postgres-integration"
))]
#[test]
fn stops_the_live_service_cleanly_on_interrupt_and_termination() {
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
    let mut results = Vec::new();
    for signal_name in ["-INT", "-TERM"] {
        let mut child = Command::new(env!("CARGO_BIN_EXE_morecat-rmu"))
            .env("GOOGLE_CLOUD_PROJECT", "demo-morecat")
            .env("DATABASE_URL", &database_url)
            .env("PORT", "0")
            .stdout(Stdio::piped())
            .spawn()
            .unwrap();

        let readiness = readiness_port(child.stdout.take().unwrap());
        let Ok(port) = readiness.recv_timeout(Duration::from_secs(10)) else {
            child.kill().unwrap();
            child.wait().unwrap();
            results.push((signal_name, false, false));
            break;
        };
        if TcpStream::connect(("127.0.0.1", port)).is_err() {
            child.kill().unwrap();
            child.wait().unwrap();
            results.push((signal_name, false, false));
            break;
        }
        let signal = Command::new("kill")
            .args([signal_name, &child.id().to_string()])
            .status()
            .unwrap();
        let status = child.wait().unwrap();

        results.push((signal_name, signal.success(), status.success()));
    }
    runtime.block_on(async move { drop(container) });

    for (signal_name, signal_sent, stopped_cleanly) in results {
        assert!(
            signal_sent,
            "RMU did not report readiness for {signal_name}"
        );
        assert!(stopped_cleanly, "RMU did not handle {signal_name}");
    }
}

#[cfg(all(
    unix,
    feature = "firestore-integration",
    feature = "postgres-integration"
))]
fn readiness_port(stdout: impl std::io::Read + Send + 'static) -> mpsc::Receiver<u16> {
    let (sender, receiver) = mpsc::channel();
    thread::spawn(move || {
        let mut line = String::new();
        BufReader::new(stdout).read_line(&mut line).unwrap();
        if let Some(port) = line
            .trim()
            .strip_prefix("RMU listening on 0.0.0.0:")
            .and_then(|port| port.parse().ok())
        {
            sender.send(port).unwrap();
        }
    });
    receiver
}
