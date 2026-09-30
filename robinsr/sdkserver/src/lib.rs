use std::collections::HashMap;
use std::sync::Arc;

use anyhow::Result;
use axum::Router;
use axum::http::Method;
use axum::http::header::CONTENT_TYPE;
use axum::routing::{get, post};
use config::version_config::VersionConfig;
use services::{auth, dispatch, errors, sr_tools};
use tokio::fs;
use tokio::sync::RwLock;
use tower_http::cors::{Any, CorsLayer};
use tracing::Level;

mod config;
mod services;

const PORT: u16 = 21000;

#[derive(Clone)]
struct AppState {
    hotfix_map: HashMap<String, VersionConfig>,
}

impl AppState {
    async fn get_or_insert_hotfix(&mut self, version: &str, dispatch_seed: &str) -> &VersionConfig {
        if self.hotfix_map.contains_key(version) {
            return &self.hotfix_map[version];
        }

        tracing::info!(
            "trying to fetch hotfix for version {version} with dispatch seed {dispatch_seed}"
        );

        let hotfix = match VersionConfig::fetch_hotfix(version, dispatch_seed).await {
            Ok(hotfix) => hotfix,
            Err(err) => {
                tracing::error!("failed to fetch hotfix. reason: {err}");
                VersionConfig::default()
            }
        };

        self.hotfix_map.insert(version.to_string(), hotfix);

        if let Ok(serialized) = serde_json::to_string_pretty(&self.hotfix_map) {
            let _ = fs::write("versions.json", serialized).await;
        }

        &self.hotfix_map[version]
    }
}

pub async fn start_sdkserver() -> Result<()> {
    let span = tracing::span!(Level::DEBUG, "main");
    let _ = span.enter();
    let hotfix_map = VersionConfig::load_hotfix().await;

    tracing::info!(
        "loaded {} hotfix versions. supported versions: {:?}",
        hotfix_map.len(),
        hotfix_map.keys()
    );

    let state = Arc::new(RwLock::new(AppState { hotfix_map }));

    let router = Router::new()
        .route(
            dispatch::QUERY_DISPATCH_ENDPOINT,
            get(dispatch::query_dispatch),
        )
        .route(
            dispatch::QUERY_GATEWAY_ENDPOINT,
            get(dispatch::query_gateway),
        )
        .route(auth::RISKY_API_CHECK_ENDPOINT, post(auth::risky_api_check))
        .route(
            auth::LOGIN_WITH_PASSWORD_ENDPOINT,
            post(auth::login_with_password),
        )
        .route(
            auth::LOGIN_WITH_SESSION_TOKEN_ENDPOINT,
            post(auth::login_with_session_token),
        )
        .route(
            auth::GRANTER_LOGIN_VERIFICATION_ENDPOINT,
            post(auth::granter_login_verification),
        )
        .route(
            sr_tools::SRTOOLS_UPLOAD_ENDPOINT,
            post(sr_tools::sr_tool_save),
        )
        .route(
            auth::APN_LOGIN_BY_PASSWORD_ENDPOINT,
            post(auth::apn_login_with_password),
        )
        .route(auth::APN_VERIFY_TOKEN_ENDPOINT, post(auth::apn_veriy_token))
        .layer(
            CorsLayer::new()
                .allow_origin(Any)
                .allow_methods([Method::GET, Method::POST, Method::PATCH, Method::DELETE])
                .allow_headers([CONTENT_TYPE]),
        )
        .with_state(state)
        .fallback(errors::not_found);

    let addr = format!("0.0.0.0:{PORT}");
    let server = axum_server::bind(addr.parse::<std::net::SocketAddr>()?);

    tracing::info!("sdkserver is listening at {addr}");
    server.serve(router.into_make_service()).await?;

    Ok(())
}
