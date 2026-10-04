local Input = CS.UnityEngine.Input
local KeyCode = CS.UnityEngine.KeyCode
local Vector3 = CS.UnityEngine.Vector3
local Time = CS.UnityEngine.Time
local Camera = CS.UnityEngine.Camera
local GameObject = CS.UnityEngine.GameObject

_G.__FREECAM_ENABLED = _G.__FREECAM_ENABLED or false
_G.__FREECAM_SPEED = _G.__FREECAM_SPEED or 15.0

local RAINBOW_TEXT = "<color=#bb00ff>นี่คือเวอร์ชั่นทดสอบของเกย์ C is gay and i love to play </color> <color=#FF0000>Ho</color><color=#FF7F00>nk</color><color=#FFFF00>ai</color> <color=#00FF00>St</color><color=#0000FF>ar</color> <color=#4B0082>Gay</color>"

local function show_ui(msg)
    pcall(function()
        local obj = GameObject.Find("VersionText")
        if obj then
            local textComp = obj:GetComponentInChildren(typeof(CS.RPG.Client.LocalizedText))
            if textComp then
                textComp.text = msg
            end
        end
    end)
end

local function get_active_cam()
    local cam = Camera.main
    if cam and cam.gameObject.activeInHierarchy then
        return cam
    end
    local all = Camera.allCameras
    if all and all.Length > 0 then
        for i = 0, all.Length - 1 do
            local c = all[i]
            if c and c.gameObject.activeInHierarchy and c.name ~= "UICamera" and not string.find(c.name, "UI") then
                return c
            end
        end
        return all[0]
    end
    return nil
end

local function set_cinemachine_enabled(cam, enabled)
    pcall(function()
        if not cam then return end
        local brain = cam:GetComponent(typeof(CS.Cinemachine.CinemachineBrain))
        if brain then brain.enabled = enabled end
        local brain2 = cam:GetComponent("CinemachineBrain")
        if brain2 then brain2.enabled = enabled end
    end)
    if not enabled then
        pcall(function()
            local vcs = GameObject.FindObjectsOfType(typeof(CS.Cinemachine.CinemachineVirtualCamera))
            if vcs and vcs.Length > 0 then
                for i = 0, vcs.Length - 1 do
                    vcs[i].enabled = false
                end
            end
        end)
    else
        pcall(function()
            local vcs = GameObject.FindObjectsOfType(typeof(CS.Cinemachine.CinemachineVirtualCamera))
            if vcs and vcs.Length > 0 then
                for i = 0, vcs.Length - 1 do
                    vcs[i].enabled = true
                end
            end
        end)
    end
end

function _G.__FREECAM_TICK()
    pcall(function()
        if Input.GetKeyDown(KeyCode.Tab) then
            _G.__FREECAM_ENABLED = not _G.__FREECAM_ENABLED
            local cam = get_active_cam()
            if _G.__FREECAM_ENABLED then
                if cam then
                    _G.__FC_POS = cam.transform.position
                    _G.__FC_EULER = cam.transform.eulerAngles
                    set_cinemachine_enabled(cam, false)
                end
                show_ui("<color=#00FF88><b>[FreeCam: ON]</b> Speed: " .. string.format("%.1f", _G.__FREECAM_SPEED) .. " | Arrow Keys: Fly | Right-Click: Look | WASD: Walk</color>")
            else
                if cam then
                    set_cinemachine_enabled(cam, true)
                end
                show_ui(RAINBOW_TEXT)
            end
        end

        if _G.__FREECAM_ENABLED then
            local cam = get_active_cam()
            if cam then
                set_cinemachine_enabled(cam, false)

                if not _G.__FC_POS then
                    _G.__FC_POS = cam.transform.position
                    _G.__FC_EULER = cam.transform.eulerAngles
                end

                local now = Time.unscaledTime or 0
                local dt = now - (_G.__FC_LAST_TIME or now)
                if dt <= 0 or dt > 0.1 then dt = 0.016 end
                _G.__FC_LAST_TIME = now

                -- Speed adjustment (+ / -)
                if Input.GetKeyDown(KeyCode.Equals) or Input.GetKeyDown(KeyCode.KeypadPlus) then
                    _G.__FREECAM_SPEED = math.min(300.0, _G.__FREECAM_SPEED * 1.5)
                    show_ui("<color=#00FF88><b>[FreeCam: ON]</b> Speed: " .. string.format("%.1f", _G.__FREECAM_SPEED) .. " | Arrow Keys: Fly</color>")
                end
                if Input.GetKeyDown(KeyCode.Minus) or Input.GetKeyDown(KeyCode.KeypadMinus) then
                    _G.__FREECAM_SPEED = math.max(0.5, _G.__FREECAM_SPEED / 1.5)
                    show_ui("<color=#00FF88><b>[FreeCam: ON]</b> Speed: " .. string.format("%.1f", _G.__FREECAM_SPEED) .. " | Arrow Keys: Fly</color>")
                end

                -- Mouse Look (Hold Right Mouse Button)
                if Input.GetMouseButton(1) then
                    local rotX = Input.GetAxis("Mouse X") or 0
                    local rotY = Input.GetAxis("Mouse Y") or 0
                    _G.__FC_EULER = Vector3(_G.__FC_EULER.x - rotY * 2.5, _G.__FC_EULER.y + rotX * 2.5, 0)
                end

                -- Compute 3D direction vectors from camera Euler angles
                local radY = _G.__FC_EULER.y * (math.pi / 180.0)
                local radX = _G.__FC_EULER.x * (math.pi / 180.0)
                local cosX = math.cos(radX)

                local fwd = Vector3(math.sin(radY) * cosX, -math.sin(radX), math.cos(radY) * cosX)
                local right = Vector3(math.cos(radY), 0, -math.sin(radY))

                local move = Vector3(0, 0, 0)

                -- Continuous flying via Arrow Keys
                if Input.GetKey(KeyCode.UpArrow) then
                    move = move + fwd
                end
                if Input.GetKey(KeyCode.DownArrow) then
                    move = move - fwd
                end
                if Input.GetKey(KeyCode.RightArrow) then
                    move = move + right
                end
                if Input.GetKey(KeyCode.LeftArrow) then
                    move = move - right
                end
                if Input.GetKey(KeyCode.PageUp) or Input.GetKey(KeyCode.E) or Input.GetKey(KeyCode.RightShift) then
                    move = move + Vector3(0, 1, 0)
                end
                if Input.GetKey(KeyCode.PageDown) or Input.GetKey(KeyCode.Q) or Input.GetKey(KeyCode.RightControl) then
                    move = move - Vector3(0, 1, 0)
                end

                if move.sqrMagnitude > 0.0001 then
                    _G.__FC_POS = _G.__FC_POS + move.normalized * (_G.__FREECAM_SPEED * dt)
                end

                -- Enforce locked free camera transform EVERY frame
                cam.transform.position = _G.__FC_POS
                cam.transform.eulerAngles = _G.__FC_EULER
            end
        end
    end)
end

-- Multiple hooks for complete Unity & xLua render pipeline coverage
if not _G.__FREECAM_REGISTERED then
    _G.__FREECAM_REGISTERED = true

    local function hook(target, evt)
        pcall(function() target[evt]('+', function(c) _G.__FREECAM_TICK() end) end)
        pcall(function() target[evt] = target[evt] + function(c) _G.__FREECAM_TICK() end end)
    end

    hook(Camera, "onPreRender")
    hook(Camera, "onPreCull")
    hook(Camera, "onPostRender")
    pcall(function() CS.UnityEngine.Application.onBeforeRender('+', function() _G.__FREECAM_TICK() end) end)
    pcall(function() CS.UnityEngine.Application.onBeforeRender = CS.UnityEngine.Application.onBeforeRender + function() _G.__FREECAM_TICK() end end)
end

if not _G.__FREECAM_ENABLED then
    show_ui(RAINBOW_TEXT)
end
