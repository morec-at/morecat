#[tokio::main]
async fn main() -> Result<(), String> {
    let command =
        morecat_rmu::bootstrap::RmuCommand::from_args(std::env::args_os().skip(1).collect())?;
    let vars = std::env::vars_os().collect();
    match command {
        morecat_rmu::bootstrap::RmuCommand::Serve => {
            morecat_rmu::bootstrap::run(vars, Box::pin(shutdown_signal())).await
        }
        morecat_rmu::bootstrap::RmuCommand::Replay => {
            let count = morecat_rmu::bootstrap::run_replay(vars).await?;
            println!("Replayed {count} article(s)");
            Ok(())
        }
    }
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
