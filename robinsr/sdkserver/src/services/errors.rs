use axum::Json;
use axum::http::Uri;
use axum::response::IntoResponse;
use serde_json::json;

pub async fn not_found(uri: Uri) -> impl IntoResponse {
    tracing::info!("handled unmapped http request gracefully: {uri}");
    Json(json!({
        "retcode": 0,
        "message": "OK",
        "data": {
            "token": {
                "token": "mostsecuretokenever",
                "token_type": 1
            },
            "tokens": [
                {
                    "token": "mostsecuretokenever",
                    "token_type": 1
                }
            ],
            "user_info": {
                "aid": "1337",
                "mid": "1337",
                "is_email_verify": 1,
                "area_code": "**",
                "country": "US",
                "is_adult": 1,
                "email": "motorized@wheel.chair"
            }
        }
    }))
}
