use super::*;

pub async fn on_get_mail_cs_req(
    _session: &mut PlayerSession,
    _req: &GetMailCsReq,
    res: &mut GetMailScRsp,
) {
    res.is_end = true;
    res.mail_list = vec![ClientMail {
        title: String::from("Welcome!"),
        sender: String::from("Server"),
        content: String::from("Welcome!"),
        id: 1,
        is_read: false,
        time: 1716041089,
        expire_time: 1718633089,
        para_list: vec![],
        attachment: Some(ItemList {
            item_list: vec![Item {
                item_id: 1310,
                level: 80,
                num: 1,
                ..Default::default()
            }],
        }),
        mail_type: 0,
        template_id: 0,
    }];
}
