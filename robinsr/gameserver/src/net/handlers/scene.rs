use std::collections::HashMap;

use common::{
    resources::GAME_RES,
    structs::{AvatarJson, Position},
};
use scene_entity_info::Entity;

use crate::util::{self};

use super::*;

pub async fn on_get_cur_scene_info_cs_req(
    session: &mut PlayerSession,
    _body: &GetCurSceneInfoCsReq,
    res: &mut GetCurSceneInfoScRsp,
) {
    let Some(player) = session.json_data.get() else {
        tracing::error!("data is not set!");
        return;
    };

    let default_scene = SceneInfo {
        game_mode_type: 3,
        entry_id: player.scene.entry_id,
        plane_id: player.scene.plane_id,
        floor_id: player.scene.floor_id,
        ..Default::default()
    };

    let scene = load_scene(session, default_scene.entry_id, false, Option::<u32>::None).await;

    res.scene = if let Ok(scene) = scene {
        Some(scene)
    } else {
        Some(default_scene)
    };
}

pub async fn on_enter_scene_cs_req(
    session: &mut PlayerSession,
    req: &EnterSceneCsReq,
    res: &mut EnterSceneScRsp,
) {
    if load_scene(session, req.entry_id, true, Some(req.entry_id2))
        .await
        .is_err()
    {
        res.retcode = 2605;
    };
}

pub async fn on_get_scene_map_info_cs_req(
    _sesison: &mut PlayerSession,
    req: &GetSceneMapInfoCsReq,
    res: &mut GetSceneMapInfoScRsp,
) {
    for floor_id in req.scene_identifiers.iter().map(|v| v.floor_id) {
        let mut map_info = SceneMapInfo {
            chest_list: vec![
                ChestInfo {
                    chest_type: 101,
                    ..Default::default()
                },
                ChestInfo {
                    chest_type: 102,
                    ..Default::default()
                },
                ChestInfo {
                    chest_type: 104,
                    ..Default::default()
                },
            ],
            floor_id: floor_id,
            scene_identifier: Some(SceneIdentifier {
                floor_id,
                ..Default::default()
            }),
            ..Default::default()
        };

        let floor_configs = GAME_RES
            .map_default_entrance_map
            .get(&floor_id)
            .and_then(|v| {
                GAME_RES
                    .level_output_configs
                    .get(v)
                    .and_then(|v| v.iter().next())
            });

        if let Some((_, floor_config)) = floor_configs {
            for (group_id, group) in floor_config.scenes.iter() {
                map_info.group_list.push(MapInfoGroup {
                    group_id: *group_id,
                    ..Default::default()
                });

                for teleport in group.teleports.keys() {
                    map_info.unlock_teleport_list.push(*teleport)
                }

                for prop in &group.props {
                    map_info.map_info_prop_list.push(MazePropState {
                        group_id: prop.group_id,
                        state: prop.prop_state,
                        config_id: prop.inst_id,
                        extra_info: Option::<PropExtraInfo>::None,
                    });
                    // map_info.maze_group_list.push(MazeGroup {
                    //     group_id: prop.group_id,
                    //     state: prop.prop_state,
                    //     config_id: prop.inst_id,
                    //     extra_info: Option::None,
                    // });
                }
            }

            map_info.lighten_section_list = floor_config.sections.clone();
            map_info.floor_saved_value_map = floor_config.saved_values.clone();
            // #TODO!
            // map_info
            //     .chest_unlock_progress_list
            //     .push(ChestUnlockProgress {
            //         r#type: 0,
            //         total_chest_count: 25,
            //         unlocked_chest_count: 25,
            //     });
        }

        res.scene_map_info_list.push(map_info)
    }
}

pub async fn on_scene_entity_move_cs_req(
    session: &mut PlayerSession,
    req: &SceneEntityMoveCsReq,
    _res: &mut SceneEntityMoveScRsp,
) {
    let Some(player) = session.json_data.get_mut() else {
        tracing::error!("data is not set!");
        return;
    };

    if util::cur_timestamp_ms() <= session.next_scene_save {
        return;
    }

    // save every 5 sec
    session.next_scene_save = util::cur_timestamp_ms() + (5 * 1000);

    for entity in &req.entity_motion_list {
        if entity.entity_id != 0 {
            continue;
        }

        if let Some(motion) = &entity.motion {
            if let Some(pos) = &motion.pos {
                player.position.x = pos.x;
                player.position.y = pos.y;
                player.position.z = pos.z;
            }
            if let Some(rot) = &motion.rot {
                player.position.rot_y = rot.y;
            }
        }
    }

    player.save_persistent().await;
}

pub async fn on_get_entered_scene_cs_req(
    _session: &mut PlayerSession,
    _req: &GetEnteredSceneCsReq,
    res: &mut GetEnteredSceneScRsp,
) {
    res.entered_scene_info_list = GAME_RES
        .level_output_configs
        .iter()
        .flat_map(|(_, v)| {
            v.iter()
                .filter(|(_, v)| v.is_entered_scene_info)
                .map(|(k, _)| {
                    let split: Vec<_> = k.split("_").collect();
                    let plane_id = &split[0][1..];
                    let floor_id = &split[1][1..];
                    EnteredSceneInfo {
                        floor_id: floor_id.parse().unwrap(),
                        plane_id: plane_id.parse().unwrap(),
                    }
                })
                .collect::<Vec<_>>()
        })
        .collect::<Vec<_>>();
}

async fn load_scene(
    session: &mut PlayerSession,
    entry_id: u32,
    is_enter_scene: bool,
    teleport_id: Option<u32>,
) -> Result<SceneInfo> {
    let Some(json) = session.json_data.get_mut() else {
        tracing::error!("data is not set!");
        return Err(anyhow::format_err!("data is not set!"));
    };

    let (name, scene) = GAME_RES
        .level_output_configs
        .get(&entry_id)
        .and_then(|v| v.iter().next())
        .ok_or_else(|| {
            tracing::error!("Map Entrance Not Found {}", entry_id);
            anyhow::format_err!("Map Entrance Not Found {}", entry_id)
        })?;

    let split: Vec<_> = name.split("_").collect();
    let plane_id = split[0][1..].parse::<u32>()?;
    let floor_id = split[1][1..].parse::<u32>()?;

    let mut json_pos = json.position.clone();
    if let Some(teleport_id) = teleport_id {
        if let Some(teleport) = scene
            .scenes
            .iter()
            .find_map(|(_, v)| v.teleports.get(&teleport_id))
        {
            json_pos.x = teleport.pos.x;
            json_pos.y = teleport.pos.y;
            json_pos.z = teleport.pos.z;
            json_pos.rot_y = teleport.rot.y;
        } else if let Some((_, teleport)) = scene
            .scenes
            .iter()
            .find_map(|v| v.1.teleports.iter().next())
        {
            json_pos.x = teleport.pos.x;
            json_pos.y = teleport.pos.y;
            json_pos.z = teleport.pos.z;
            json_pos.rot_y = teleport.rot.y;
        }
    }

    let mut scene_info = SceneInfo {
        floor_id,
        plane_id,
        entry_id,
        game_mode_type: scene.plane_type,
        leader_entity_id: 1,
        world_id: if scene.world_id == 100 {
            501
        } else {
            scene.world_id
        },
        lighten_section_list: scene.sections.clone(),
        opened_chests_list: scene
            .scenes
            .values()
            .flat_map(|v| v.chests.clone())
            .collect::<Vec<_>>(),
        scene_mission_info: Some(MissionStatusBySceneInfo {
            finished_main_mission_id_list: scene
                .scenes
                .values()
                .flat_map(|s| s.finished_main_missions.clone())
                .collect::<Vec<_>>(),
            sub_mission_status_list: scene
                .scenes
                .values()
                .flat_map(|s| {
                    s.finished_sub_missions.iter().map(|sm| Mission {
                        id: *sm,
                        status: MissionStatus::MissionFinish.into(),
                        progress: 0,
                    })
                })
                .collect::<Vec<_>>(),
            ..Default::default()
        }),
        floor_saved_data: scene.saved_values.clone(),
        scene_identifier: Some(SceneIdentifier {
            floor_id,
            ..Default::default()
        }),
        ..Default::default()
    };

    let lineup_info = AvatarJson::to_lineup_info(&json.lineups);
    let player_pos = MotionInfo {
        rot: Some(Vector {
            x: 0,
            y: json_pos.rot_y,
            z: 0,
        }),
        pos: Some(Vector {
            x: json_pos.x,
            y: json_pos.y,
            z: json_pos.z,
        }),
    };

    let mut loaded_npc: Vec<u32> = vec![];
    let mut prop_entity_id = 1_000;
    let mut npc_entity_id = 20_000;
    let mut monster_entity_id = 30_000;

    for (group_id, group) in &scene.scenes {
        let mut group_info = SceneEntityGroupInfo {
            group_id: *group_id,
            ..Default::default()
        };

        // Load Props
        for prop in &group.props {
            prop_entity_id += 1;

            let prop_position = Position {
                x: (prop.pos.x),
                y: (prop.pos.y),
                z: (prop.pos.z),
                rot_y: (prop.rot.y),
            };

            let entity_info = SceneEntityInfo {
                inst_id: prop.inst_id,
                group_id: prop.group_id,
                motion: Some(prop_position.into()),
                entity: Some(Entity::Prop(ScenePropInfo {
                    prop_state: prop.prop_state,
                    prop_id: prop.prop_id,
                    ..Default::default()
                })),
                entity_id: prop_entity_id,
            };

            group_info.entity_list.push(entity_info);
        }

        // Load NPCs
        for npc in &group.npcs {
            if loaded_npc.contains(&(npc.npc_id)) || json.avatars.contains_key(&(npc.npc_id)) {
                continue;
            }
            npc_entity_id += 1;
            loaded_npc.push(npc.npc_id);

            let npc_position = Position {
                x: npc.pos.x,
                y: npc.pos.y,
                z: npc.pos.z,
                rot_y: npc.rot.y,
            };

            let info = SceneEntityInfo {
                inst_id: npc.inst_id,
                group_id: npc.group_id,
                entity_id: npc_entity_id,
                motion: Some(npc_position.into()),
                entity: Some(Entity::Npc(SceneNpcInfo {
                    npc_id: npc.npc_id,
                    ..Default::default()
                })),
            };

            group_info.entity_list.push(info);
        }

        // Load Monsters
        for monster in &group.monsters {
            monster_entity_id += 1;
            let monster_position = Position {
                x: monster.pos.x,
                y: monster.pos.y,
                z: monster.pos.z,
                rot_y: monster.rot.y,
            };

            let npc_monster = SceneNpcMonsterInfo {
                monster_id: monster.monster_id,
                event_id: monster.event_id,
                world_level: 6,
                ..Default::default()
            };

            let info = SceneEntityInfo {
                inst_id: monster.inst_id,
                group_id: monster.group_id,
                entity_id: monster_entity_id,
                motion: Some(monster_position.into()),
                entity: Some(Entity::NpcMonster(npc_monster)),
            };

            group_info.entity_list.push(info);
        }

        // TODO: for now don't load group that have nothing in it
        if group.props.is_empty() && group.npcs.is_empty() && group.monsters.is_empty() {
            continue;
        }

        scene_info.entity_group_list.push(group_info);

        // TODO: ?
        // scene_info.group_state_list.push(SceneGroupState {
        //     group_id: *group_id,
        //     is_default: true,
        //     state: 0,
        // });
    }

    // load player entity
    scene_info.entity_group_list.push(SceneEntityGroupInfo {
        state: 0,
        group_id: 0,
        property_map: HashMap::with_capacity(0),
        entity_list: json
            .lineups
            .iter()
            .map(|(slot, avatar_id)| SceneEntityInfo {
                inst_id: 0,
                entity_id: (*slot) + 1,
                motion: Some(player_pos),
                entity: Some(Entity::Actor(SceneActorInfo {
                    avatar_type: AvatarType::AvatarFormalType.into(),
                    base_avatar_id: *avatar_id,
                    map_layer: 0,
                    uid: 25,
                })),
                ..Default::default()
            })
            .collect(),
    });

    if is_enter_scene {
        json.scene.entry_id = entry_id;
        json.scene.floor_id = floor_id;
        json.scene.plane_id = plane_id;
        json.position.x = json_pos.x;
        json.position.y = json_pos.y;
        json.position.z = json_pos.z;
        json.position.rot_y = json_pos.rot_y;

        json.save_persistent().await;

        session
            .send(EnterSceneByServerScNotify {
                scene: Some(scene_info.clone()),
                lineup: Some(lineup_info),
                ..Default::default()
            })
            .await?;
    }

    Ok(scene_info)
}
