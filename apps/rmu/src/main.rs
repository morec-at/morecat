#[tokio::main]
async fn main() -> Result<(), String> {
    morecat_rmu::bootstrap::run(
        std::env::vars_os().collect(),
        Box::pin(async {
            let _ = tokio::signal::ctrl_c().await;
        }),
    )
    .await
}
