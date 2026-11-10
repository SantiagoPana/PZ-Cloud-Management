require "ISUI/ISPanel"
require "ISUI/ISButton"
require "ISUI/ISLabel"
require "ISUI/ISScrollingListBox"

-- No Java injection, shell commands, OAuth credentials or live-save filesystem access.
-- Only fixed .txt files under Zomboid/Lua are exchanged with the desktop companion.
PZCloud = PZCloud or { counter = 0 }
local function tr(key) return getText("UI_PZCloud_" .. key) end
local function split(line)
    local parts = {}
    for value in (line .. "|"):gmatch("(.-)|") do parts[#parts + 1] = value end
    return parts
end
local function readLines(filename)
    local lines = {}
    local ok = pcall(function()
        local reader = getFileReader(filename, false)
        if not reader then return end
        local line = reader:readLine()
        while line and #lines < 20000 do
            lines[#lines + 1] = line
            line = reader:readLine()
        end
        reader:close()
    end)
    return lines, ok
end
local function request(action, fileId)
    PZCloud.counter = PZCloud.counter + 1
    local nonce = string.format("%.0f", getTimestampMs()) .. "_" .. PZCloud.counter
    local ok = pcall(function()
        local writer = getFileWriter("pzcloud-request.txt", true, false)
        if not writer then error("writer unavailable") end
        writer:write("1|" .. nonce .. "|" .. action .. (fileId and ("|" .. fileId) or "") .. "\n")
        writer:close()
    end)
    return ok and nonce or nil
end

local CloudPanel = ISPanel:derive("PZCloudPanel")
function CloudPanel:reload()
    self.list:clear()
    local lines = readLines("pzcloud-catalog.txt")
    for _, line in ipairs(lines) do
        local fields = split(line)
        if #fields >= 6 and fields[1]:match("^[A-Za-z0-9_-]+$") then
            self.list:addItem(fields[2] .. "  /  " .. fields[3], {
                fileId = fields[1], name = fields[2], mode = fields[3],
                created = fields[4], device = fields[5], size = fields[6]
            })
        end
    end
end
function CloudPanel:send(action, fileId)
    self.nonce = request(action, fileId)
    self.sentAt = getTimestampMs()
    self.status:setName(self.nonce and tr("Waiting") or tr("WriteError"))
end
function CloudPanel:onClick(button)
    if button.internal == "CLOSE" then self:setVisible(false); self:removeFromUIManager(); PZCloud.panel = nil
    elseif button.internal == "OPEN" then self:send("OPEN")
    elseif button.internal == "LIST" then self:send("LIST")
    elseif button.internal == "DOWNLOAD" then
        local selected = self.list.items[self.list.selected]
        if selected then self:send("DOWNLOAD", selected.item.fileId)
        else self.status:setName(tr("Select")) end
    end
end
function CloudPanel:prerender()
    ISPanel.prerender(self)
    self:drawText(tr("Title"), 20, 15, 0.7, 0.86, 0.5, 1, UIFont.Large)
    self:drawText(tr("Subtitle"), 20, 53, 0.8, 0.84, 0.8, 1, UIFont.Small)
    self:drawText(tr("RestoreHint"), 20, self.height - 114, 0.8, 0.84, 0.8, 1, UIFont.Small)
    local now = getTimestampMs()
    if self.nonce and (not self.lastPoll or now - self.lastPoll > 1000) then
        self.lastPoll = now
        local lines = readLines("pzcloud-status.txt")
        local fields = split(lines[1] or "")
        if fields[1] == self.nonce then
            self.status:setName(fields[2] == "OK" and tr("Done") or tr("CompanionError"))
            self:reload()
            self.nonce = nil
        elseif now - self.sentAt > 15000 then self.status:setName(tr("Offline")) end
    end
end
function CloudPanel:createChildren()
    ISPanel.createChildren(self)
    self.list = ISScrollingListBox:new(20, 87, self.width - 40, self.height - 220)
    self.list:initialise(); self.list:instantiate(); self.list.itemheight = 58
    self.list.doDrawItem = function(list, y, item, alt)
        if list.selected == item.index then list:drawRect(0, y, list.width, list.itemheight, 0.4, 0.3, 0.45, 0.25) end
        list:drawText(item.text, 10, y + 7, 0.95, 0.95, 0.95, 1, UIFont.Medium)
        local data = item.item
        list:drawText(data.created .. "  |  " .. data.device .. "  |  " .. data.size, 10, y + 32, 0.7, 0.75, 0.7, 1, UIFont.Small)
        return y + list.itemheight
    end
    self:addChild(self.list)
    self.status = ISLabel:new(20, self.height - 87, 20, tr("Ready"), 0.8, 0.85, 0.8, 1, UIFont.Small, true)
    self.status:initialise(); self:addChild(self.status)
    local definitions = {{"LIST", "Refresh", 130}, {"DOWNLOAD", "Download", 150}, {"OPEN", "Open", 210}, {"CLOSE", "Close", 110}}
    local x = 20
    for _, definition in ipairs(definitions) do
        local b = ISButton:new(x, self.height - 49, definition[3], 29, tr(definition[2]), self, CloudPanel.onClick)
        b.internal = definition[1]; b:initialise(); b:instantiate(); self:addChild(b); x = x + definition[3] + 10
    end
    self:reload()
end
local function openPanel()
    if getPlayer() then return end
    if PZCloud.panel then PZCloud.panel:setVisible(true); return end
    local width = math.min(900, getCore():getScreenWidth() - 40)
    local height = math.min(560, getCore():getScreenHeight() - 40)
    local panel = CloudPanel:new((getCore():getScreenWidth() - width) / 2, (getCore():getScreenHeight() - height) / 2, width, height)
    panel.backgroundColor = {r=0.07, g=0.11, b=0.1, a=0.98}
    panel.moveWithMouse = true; panel:initialise(); panel:instantiate(); panel:addToUIManager()
    PZCloud.panel = panel; panel:send("LIST")
end
local function positionButton()
    if PZCloud.button then PZCloud.button:setX(getCore():getScreenWidth() - 220); PZCloud.button:setY(getCore():getScreenHeight() - 100) end
end
local function menuEnter()
    if PZCloud.button then PZCloud.button:removeFromUIManager() end
    local button = ISButton:new(0, 0, 200, 32, "Cloud Saves", nil, openPanel)
    button:initialise(); button:instantiate(); button:addToUIManager(); PZCloud.button = button; positionButton()
end
Events.OnMainMenuEnter.Add(menuEnter)
if Events.OnResolutionChange then Events.OnResolutionChange.Add(positionButton) end
Events.OnGameStart.Add(function()
    if PZCloud.button then PZCloud.button:removeFromUIManager(); PZCloud.button = nil end
    if PZCloud.panel then PZCloud.panel:removeFromUIManager(); PZCloud.panel = nil end
end)
Events.OnKeyPressed.Add(function(key) if key == Keyboard.KEY_F8 and not getPlayer() then openPanel() end end)
