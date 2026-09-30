use proto::ItemType;

pub mod avatar;
pub mod battle;
pub mod lightcone;
pub mod monster;
pub mod persistent;
pub mod relic;
pub mod scene;

pub use avatar::*;
pub use battle::*;
pub use lightcone::*;
pub use monster::*;
pub use persistent::*;
pub use scene::*;

pub fn get_item_unique_id(internal_uid: u32, item_type: ItemType) -> u32 {
    // srtools start internal_uid from 1
    if item_type == ItemType::ItemEquipment {
        3000 + internal_uid // 3001..3500
    } else {
        internal_uid // 1..2000 ->
    }
}

#[macro_export]
macro_rules! impl_from {
    ($from:ty, $to:ty, |$param:ident| $body:block) => {
        impl From<$from> for $to {
            fn from($param: $from) -> Self {
                $body
            }
        }

        impl From<&$from> for $to {
            fn from($param: &$from) -> Self {
                $body
            }
        }

        impl From<&mut $from> for $to {
            fn from($param: &mut $from) -> Self {
                $body
            }
        }
    };
}
