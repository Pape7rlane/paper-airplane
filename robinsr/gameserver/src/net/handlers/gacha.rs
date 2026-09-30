pub use super::*;

pub async fn on_get_gacha_info_cs_req(
    _session: &mut PlayerSession,
    _req: &GetGachaInfoCsReq,
    res: &mut GetGachaInfoScRsp,
) {
    res.gacha_info_list = vec![GachaInfo {
        end_time: 1924992000,
        begin_time: 0,
        gacha_ceiling: Some(GachaCeiling::default()),
        prize_item_list: vec![23002, 1003, 1101, 1104, 23000, 23003],
        item_detail_list: vec![
            1001, 1002, 1008, 1009, 1013, 1103, 1105, 1106, 1108, 1109, 1110, 1111, 1201, 1202,
            1206, 1207, 1210, 1003, 1004, 1101, 1107, 1104, 1209, 1211, 21000, 21001, 21002, 21003,
            21004, 21005, 21006, 21007, 21008, 21009, 21010, 21011, 21012, 21013, 21014, 21015,
            21016, 21017, 21018, 21019, 21020, 23000, 23002, 23003, 23004, 23005, 23012, 23013,
        ],
        gacha_id: 1001,
        ..Default::default()
    }];
}

pub async fn on_do_gacha_cs_req(
    _session: &mut PlayerSession,
    req: &DoGachaCsReq,
    res: &mut DoGachaScRsp,
) {
    res.gacha_id = req.gacha_id;
    res.gacha_num = req.gacha_num;
    res.gacha_item_list = (0..req.gacha_num)
        .map(|v| GachaItem {
            is_new: false,
            gacha_item: Some(Item {
                item_id: if v % 2 == 0 { 1310 } else { 1314 },
                ..Default::default()
            }),
            token_item: Some(ItemList {
                item_list: vec![Item {
                    item_id: 251,
                    num: 100,
                    ..Default::default()
                }],
            }),
            transfer_item_list: Some(ItemList { item_list: vec![] }),
        })
        .collect();
}
