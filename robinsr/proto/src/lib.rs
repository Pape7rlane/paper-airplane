#![allow(warnings)]
include!("../out/_.rs");

pub use prost::DecodeError as ProtobufDecodeError;
pub use prost::Message as Protobuf;

pub trait CmdID {
    const CMD_ID: u16;

    fn get_cmd_id(&self) -> u16 {
        Self::CMD_ID
    }
}
