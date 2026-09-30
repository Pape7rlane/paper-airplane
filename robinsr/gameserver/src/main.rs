use anyhow::Result;

pub fn init_tracing() {
    #[cfg(target_os = "windows")]
    ansi_term::enable_ansi_support().unwrap();

    env_logger::init_from_env(env_logger::Env::new().default_filter_or("info"));
}

#[tokio::main]
async fn main() -> Result<()> {
    init_tracing();
    gameserver::start_gameserver().await
}
