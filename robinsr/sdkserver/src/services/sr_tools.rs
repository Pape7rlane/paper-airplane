use std::path::Path;

use axum::Json;
use common::sr_tools::FreesrData;
use serde::{Deserialize, Serialize};
use tokio::fs;

pub const SRTOOLS_UPLOAD_ENDPOINT: &str = "/srtools";

#[derive(Debug, Deserialize)]
pub struct SrToolDataReq {
    #[allow(dead_code)]
    pub data: Option<FreesrData>,
}

#[derive(Debug, Serialize)]
pub struct SrToolDataRsp {
    pub message: String,
    pub status: u32,
}

pub async fn sr_tool_save(Json(json): Json<SrToolDataReq>) -> Json<SrToolDataRsp> {
    let Some(json) = json.data else {
        return Json(SrToolDataRsp {
            message: String::from("OK"),
            status: 200,
        });
    };

    let json = match serde_json::to_string_pretty(&json) {
        Ok(json) => json,
        Err(err) => {
            return Json(SrToolDataRsp {
                message: format!("malformed json: {err}"),
                status: 500,
            });
        }
    };

    let path = Path::new("freesr-data.json");
    let env = std::env::current_dir();

    if let Err(err) = fs::write(&path, json).await {
        return Json(SrToolDataRsp {
            message: format!(
                "failed to write freesr-data.json: {err} at path: {path:#?} env: {env:#?}"
            ),
            status: 500,
        });
    };

    Json(SrToolDataRsp {
        message: String::from("OK"),
        status: 200,
    })
}
