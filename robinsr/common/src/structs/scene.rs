use proto::{MotionInfo, Vector};
use serde::{Deserialize, Serialize};

use crate::impl_from;

// SCENE
#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Scene {
    pub plane_id: u32,
    pub floor_id: u32,
    pub entry_id: u32,
}

impl Default for Scene {
    fn default() -> Self {
        Self {
            plane_id: 20411,
            floor_id: 20411001,
            entry_id: 2041101,
        }
    }
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Position {
    pub x: i32,
    pub y: i32,
    pub z: i32,
    pub rot_y: i32,
}

impl Default for Position {
    fn default() -> Self {
        Position {
            x: -26968,
            y: 78953,
            z: 14457,
            rot_y: 11858,
        }
    }
}

impl_from!(Position, MotionInfo, |value| {
    MotionInfo {
        rot: Some(Vector {
            x: 0,
            y: value.rot_y,
            z: 0,
        }),
        pos: Some(Vector {
            x: value.x,
            y: value.y,
            z: value.z,
        }),
    }
});
