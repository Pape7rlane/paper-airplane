use super::*;

pub async fn on_get_basic_info_cs_req(
    _session: &mut PlayerSession,
    _body: &GetBasicInfoCsReq,
    res: &mut GetBasicInfoScRsp,
) {
    res.player_setting_info = Some(PlayerSettingInfo::default());
    res.gender = Gender::Woman as u32;
    res.is_gender_set = true;
}

pub async fn on_player_heart_beat_cs_req(
    _session: &mut PlayerSession,
    body: &PlayerHeartBeatCsReq,
    res: &mut PlayerHeartBeatScRsp,
) {
    res.client_time_ms = body.client_time_ms;
    res.server_time_ms = body.client_time_ms;
    res.download_data = Some(ClientDownloadData {
        version: 51,
        time: res.server_time_ms as i64,
        data: rbase64::decode("BAEcA2xvZwJDUwtVbml0eUVuZ2luZQtBcHBsaWNhdGlvbg90YXJnZXRGcmFtZVJhdGUPUXVhbGl0eVNldHRpbmdzCnZTeW5jQ291bnQIcGF0Y2hGcHMKR2FtZU9iamVjdARGaW5kKFVJUm9vdC9BYm92ZURpYWxvZy9CZXRhSGludERpYWxvZyhDbG9uZSkDUlBHBkNsaWVudA1Mb2NhbGl6ZWRUZXh0BnR5cGVvZhZHZXRDb21wb25lbnRJbkNoaWxkcmVuJjxjb2xvcj0jMDBlMWZmPnQubWUvbmVvbnRlYW0yNTwvY29sb3I+BHRleHQLVmVyc2lvblRleHQiPGNvbG9yPSMwMGUxZmY+Um9iaW5TUiEgfCA8L2NvbG9yPg88Y29sb3I9IzAwZTFmZj4KR2xvYmFsVmFycw1zX1ZlcnNpb25EYXRhF0dldFNlcnZlclBha1R5cGVWZXJzaW9uCDwvY29sb3I+C3BhdGNoQmV0YVdtBG1haW4GeHBjYWxsBQEBAAAAAAEWAAEAAAAEAQEYAAUAAAAAAgAAAAAACwwAAwACBADABAFoARABAAMEAAAADAAGAAUEAMAEAQAAEAEA0AcAAAAWAAEACAMCAwMDBAQCBADAAwUDBgQFBADAAwcABwgBGAAAAAAAAQAAAAABCAAAAAAJAAAAAAA2DAEDAAIEAMAPAAF2BAAAAAUBBQAVAAICDAQIAAcYAMAPAwQUCQAAAE0sAwIMAgsAAACgQBUCAgAUAACxDAAAABUAAAIFAQ0AEAEA1Q4AAAAMAQMAAgQAwA8AAXYEAAAABQEPABUAAgIMBAgABxgAwA8DBBQJAAAATSwDAgwCCwAAAKBAFQICABQAALEMAAAAFQAAAgUCEAAFAxEADAgIAAcYAMAPBwisEgAAAA8GByMTAAAAFAYGkRQAAAAVBgICBgQGAAUFFQA1AQIFEAEA1Q4AAAAWAAEAFgMCAwMDCQQCBADAAwoDCwMMAw0EBxgAwAMOAw8EAACgQAMQAxEDEgMTAxQDFQMWAxcDGAMZAAwaARgAAAAAAAAAAAAAAAAAAAAAAAAAAAMAAAAAAAAAAAAAAAAAAAAAAQEBAAAAAAAAAAAAAQD8AAUOAAAAAAEAAQAAAAMJAAAAFQABARYAAQAAABkbARgAAAEaAAAAAAcAAAEAAAxFAAAARAAAAEQBAQBEAgIARAMDAEoAAgAMBAUAAABAQAYFAwAGBgAAFQQDARYAAQAGBgAGAQYCBgMDHAQAAEBABAABAgMBAAEYAAMDBQ0ABAAAAAAAAQAAAAAE").unwrap(),
        ..Default::default()
    });
}

pub async fn on_player_login_finish_cs_req(
    session: &mut PlayerSession,
    _req: &PlayerLoginFinishCsReq,
    _res: &mut PlayerLoginFinishScRsp,
) -> Result<()> {
    session
        .send(ContentPackageSyncDataScNotify {
            data: Some(ContentPackageData {
                content_package_list: [
                    200001, 200002, 200003, 200004, 200005, 200006, 200007, 200008, 200009, 200010,
                    200011, 200012, 150017, 150015, 150021, 150018, 130011, 130012, 130013, 150025,
                    140006, 150026, 130014, 150034, 150029, 150035, 150041, 150039, 150045, 150057,
                    150042, 150067, 150064, 150063, 150024, 171002, 150068, 150070, 150071, 150073,
                    150074, 150075, 150076, 150077, 150078, 150079,
                ]
                .into_iter()
                .map(|v| ContentPackageInfo {
                    status: ContentPackageStatus::Finished.into(),
                    content_id: v,
                })
                .collect(),
                ..Default::default()
            }),
        })
        .await?;

    Ok(())
}
