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
