use anyhow::Result;
use paste::paste;
use tracing::Instrument;

use proto::*;

use super::PlayerSession;
use super::handlers::*;

const HEAD_MAGIC: u32 = 0x9D74C714;
const TAIL_MAGIC: u32 = 0xD7A152C8;

#[derive(Debug)]
pub struct NetOperation {
    pub head: u32,
    pub param1: u32,
    pub param2: u32,
    pub data: u32,
    pub tail: u32,
}

#[derive(Debug)]
pub struct NetPacket {
    pub cmd_type: u16,
    pub head: Vec<u8>,
    pub body: Vec<u8>,
}

impl From<NetPacket> for Vec<u8> {
    fn from(value: NetPacket) -> Self {
        let mut out = Self::new();

        out.extend(HEAD_MAGIC.to_be_bytes());
        out.extend(value.cmd_type.to_be_bytes());
        out.extend((value.head.len() as u16).to_be_bytes());
        out.extend((value.body.len() as u32).to_be_bytes());
        out.extend(value.head);
        out.extend(value.body);
        out.extend(TAIL_MAGIC.to_be_bytes());
        out
    }
}

impl From<&[u8]> for NetPacket {
    fn from(value: &[u8]) -> Self {
        assert_eq!(
            u32::from_be_bytes(value[0..4].try_into().unwrap()),
            HEAD_MAGIC
        );

        let cmd_type = u16::from_be_bytes(value[4..6].try_into().unwrap());

        let head_length = usize::from(u16::from_be_bytes(value[6..8].try_into().unwrap()));

        let body_length = u32::from_be_bytes(value[8..12].try_into().unwrap()) as usize;

        let head_start = 12;
        let head_end = head_start + head_length;
        let head = value[head_start..head_end].to_vec();

        let body_start = head_end;
        let body_end = body_start + body_length;
        let body = value[body_start..body_end].to_vec();

        assert_eq!(
            u32::from_be_bytes(value[body_end..body_end + 4].try_into().unwrap()),
            TAIL_MAGIC
        );

        Self {
            cmd_type,
            head,
            body,
        }
    }
}

impl From<&[u8]> for NetOperation {
    fn from(value: &[u8]) -> Self {
        Self {
            head: u32::from_be_bytes(value[..4].try_into().unwrap()),
            param1: u32::from_be_bytes(value[4..8].try_into().unwrap()),
            param2: u32::from_be_bytes(value[8..12].try_into().unwrap()),
            data: u32::from_be_bytes(value[12..16].try_into().unwrap()),
            tail: u32::from_be_bytes(value[16..20].try_into().unwrap()),
        }
    }
}

impl From<NetOperation> for Vec<u8> {
    fn from(value: NetOperation) -> Self {
        let mut buf = Self::with_capacity(20);
        buf.extend(value.head.to_be_bytes());
        buf.extend(value.param1.to_be_bytes());
        buf.extend(value.param2.to_be_bytes());
        buf.extend(value.data.to_be_bytes());
        buf.extend(value.tail.to_be_bytes());

        buf
    }
}

macro_rules! trait_handler {
    ($($name:tt;)*) => {
        pub trait CommandHandler {
            $(
                paste! {
                    async fn [<on_$name:snake _cs_req>](session: &mut PlayerSession, request: &[<$name CsReq>]) -> Result<()> {
                        let mut response = proto::[<$name ScRsp>]::default();
                        let _ = [<on_$name:snake _cs_req>](session, request, &mut response).await;
                        session.send(response).await?;

                        Ok(())
                    }
                }
            )*

            async fn on_message(session: &mut PlayerSession, cmd_id: u16, payload: Vec<u8>) -> Result<()> {
                use ::prost::Message;
                if PlayerSession::should_send_dummy_rsp(cmd_id) {
                    session.send_dummy_response(cmd_id).await?;
                    return Ok(());
                }


                match cmd_id {
                    $(
                        cmd_id if cmd_id == paste! { <proto::[<$name CsReq>] as proto::CmdID>::CMD_ID } => {
                            let body = paste! { proto::[<$name CsReq>]::decode(&mut &payload[..])? };
                            paste! {
                                Self::[<on_$name:snake _cs_req>](session, &body)
                                    .instrument(tracing::info_span!(stringify!([<on_$name:snake>]), cmd_id = cmd_id))
                                    .await
                            }
                        }
                    )*
                    _ => {
                        tracing::warn!("Unknown command ID: {cmd_id}");
                        Ok(())
                    },
                }
            }
        }
    };
}

trait_handler! {
    PlayerGetToken;
    PlayerLogin;
    GetMissionStatus;
    GetBasicInfo;
    GetAvatarData;
    GetAllLineupData;
    GetCurLineupData;
    GetCurSceneInfo;
    PlayerHeartBeat;
    SetAvatarEnhancedId;
    TakePromotionReward;

    // Entity move (dummy!)
    SceneEntityMove;

    // Inventory (dummy!)
    GetBag;
    GetArchiveData;
    DressAvatar;
    TakeOffEquipment;
    DressRelicAvatar;
    TakeOffRelic;
    RankUpAvatar;

    // Chat (dummy!)
    SendMsg;
    GetPrivateChatHistory;
    GetFriendListInfo;
    GetFriendLoginInfo;

    // In-game lineup
    JoinLineup;
    ChangeLineupLeader;
    ReplaceLineup;
    QuitLineup;

    // Battle
    StartCocoonStage;
    PveBattleResult;
    SceneCastSkill;
    QuickStartCocoonStage;
    SceneEnterStage;

    // Teleport
    GetEnteredScene;
    GetSceneMapInfo;
    EnterScene;

    // Optional
    GetMail;
    GetGachaInfo;
    DoGacha;
    PlayerLoginFinish;
    GetBigDataAllRecommend;
    // SetClientPaused;
}
