#[tokio::main]
async fn main() -> Result<(), String> {
    morecat_rmu::bootstrap::run(std::env::vars_os().collect(), Box::pin(shutdown_signal())).await
}

#[cfg(unix)]
async fn shutdown_signal() {
    let mut terminate = tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
        .expect("failed to install SIGTERM handler");
    tokio::select! {
        _ = tokio::signal::ctrl_c() => {}
        _ = terminate.recv() => {}
    }
}

#[cfg(not(unix))]
async fn shutdown_signal() {
    let _ = tokio::signal::ctrl_c().await;
}
