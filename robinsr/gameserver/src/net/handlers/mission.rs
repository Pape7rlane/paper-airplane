use super::*;

pub async fn on_get_mission_status_cs_req(
    _session: &mut PlayerSession,
    body: &GetMissionStatusCsReq,
    res: &mut GetMissionStatusScRsp,
) {
    res.finished_main_mission_id_list = body.sub_mission_id_list.clone();
    res.sub_mission_status_list = body
        .main_mission_id_list
        .iter()
        .map(|id| Mission {
            id: *id,
            progress: 1,
            status: MissionStatus::MissionFinish.into(),
        })
        .collect();
}

pub async fn on_get_main_mission_custom_value_cs_req(
    _session: &mut PlayerSession,
    _body: &GetMainMissionCustomValueCsReq,
    res: &mut GetMainMissionCustomValueScRsp,
) {
    res.retcode = 0;
}

pub async fn on_update_track_main_mission_cs_req(
    _session: &mut PlayerSession,
    body: &UpdateTrackMainMissionCsReq,
    res: &mut UpdateTrackMainMissionScRsp,
) {
    res.retcode = 0;
    res.track_mission_id = body.track_mission_id;
}
