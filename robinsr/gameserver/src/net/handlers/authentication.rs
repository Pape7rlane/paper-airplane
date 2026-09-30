use proto::*;

use crate::{net::PlayerSession, util};

pub async fn on_player_get_token_cs_req(
    _session: &mut PlayerSession,
    _body: &PlayerGetTokenCsReq,
    res: &mut PlayerGetTokenScRsp,
) {
    res.uid = 25;
}

pub async fn on_player_login_cs_req(
    _session: &mut PlayerSession,
    body: &PlayerLoginCsReq,
    res: &mut PlayerLoginScRsp,
) {
    res.login_random = body.login_random;
    res.server_timestamp_ms = util::cur_timestamp_ms();
    res.stamina = 240;
    res.basic_info = Some(PlayerBasicInfo {
        nickname: String::from("RobinSR"),
        level: 70,
        world_level: 6,
        stamina: 240,
        ..Default::default()
    });
}
