require "ISUI/ISPanel"
require "ISUI/ISButton"
require "ISUI/ISScrollingListBox"

-- The Workshop mod is a menu for the desktop app, not an OAuth client.
-- Protocol v1 stays compatible with the published desktop app 2.3.1.
PZCloud = PZCloud or { counter = 0 }
local RELEASE_URL = "https://github.com/SantiagoPana/PZ-Cloud-Management/releases/"
local MAX_COPIES = 500
local fallback = {
    Title = "Knox Net", Menu = "Knox Net",
    Subtitle = "PZ Cloud Management. Requires the free Knox Net desktop app, running on this PC and connected to Google Drive.",
    RestoreHint = "Downloading keeps a copy on this PC. To restore it, close Zomboid completely and use the desktop app.",
    Waiting = "Contacting the desktop app...", Downloading = "Downloading the selected copy. Large worlds can take several minutes...",
    WriteError = "Could not send the request. Check the permissions of your Zomboid folder.",
    Select = "Select a copy first.", Refreshed = "Cloud list updated. Select a copy to download it.",
    Downloaded = "Copy downloaded. Close Zomboid and restore it in the desktop app.",
    Opened = "Desktop window requested. Switch to the Knox Net application.",
    CompanionError = "Could not complete the request. Open the desktop app to check your Google connection.",
    Offline = "No response yet. Check that Knox Net is running and connected, then refresh. A large download may still be running.",
    Ready = "Open the desktop app and sign in with Google, then refresh this list.",
    Refresh = "Refresh", Download = "Download copy", Open = "Show desktop app", GetApp = "Get desktop app", Close = "Close",
    Empty = "No copies to show", EmptyHint = "Open the desktop app, connect Google and save a world to the cloud.",
    Cached = "Last saved list", Current = "Cloud copies", Limited = "Showing the 500 most recent copies.",
    BrowserOpened = "Download page opened. Extract the app, run it and sign in with Google.",
    BrowserError = "Open github.com/SantiagoPana/PZ-Cloud-Management/releases to download the desktop app.",
    UTC = "UTC"
}
local function tr(key)
    local value = getTextOrNull("UI_PZCloud_" .. key)
    return value or fallback[key] or key
end
local function split(line)
    local parts = {}
    for value in (line .. "|"):gmatch("(.-)|") do parts[#parts + 1] = value end
    return parts
end
local function readLines(filename, limit)
    local lines, reader = {}, nil
    local ok = pcall(function()
        reader = getFileReader(filename, false)
        if not reader then error("file unavailable") end
        while #lines < limit do
            local line = reader:readLine()
            if not line then break end
            if #line > 4096 then error("line too long") end
            lines[#lines + 1] = line
        end
    end)
    if reader then pcall(function() reader:close() end) end
    return lines, ok
end
local function measure(font, text) return getTextManager():MeasureStringX(font, text) end
local function wrap(text, width)
    local lines, line = {}, ""
    for word in text:gmatch("%S+") do
        local nextLine = line == "" and word or (line .. " " .. word)
        if line ~= "" and measure(UIFont.Small, nextLine) > width then
            lines[#lines + 1] = line; line = word
        else line = nextLine end
    end
    if line ~= "" then lines[#lines + 1] = line end
    return lines
end
local function fit(text, font, width)
    if measure(font, text) <= width then return text end
    while #text > 0 and measure(font, text .. "...") > width do
        local index = #text
        while index > 1 and text:byte(index) >= 128 and text:byte(index) < 192 do index = index - 1 end
        text = text:sub(1, index - 1)
    end
    return text .. "..."
end
local function displayDate(value)
    local day, hour = value:match("^(%d%d%d%d%-%d%d%-%d%d)T(%d%d:%d%d)")
    if not day then return value end
    local zone = value:match("([+-]%d%d:%d%d)$") or (value:sub(-1) == "Z" and tr("UTC") or "")
    return day .. " " .. hour .. (zone ~= "" and (" " .. zone) or "")
end
local function request(action, fileId)
    PZCloud.counter = PZCloud.counter + 1
    local nonce = string.format("%.0f", getTimestampMs()) .. "_" .. PZCloud.counter
    local writer
    local ok = pcall(function()
        writer = getFileWriter("pzcloud-request.txt", true, false)
        if not writer then error("writer unavailable") end
        writer:write("1|" .. nonce .. "|" .. action .. (fileId and ("|" .. fileId) or "") .. "\n")
    end)
    if writer then
        local closed = pcall(function() writer:close() end)
        ok = ok and closed
    end
    return ok and { nonce = nonce, action = action, sentAt = getTimestampMs() } or nil
end

local CloudPanel = ISPanel:derive("PZCloudPanel")
function CloudPanel:setStatus(key, isError)
    self.statusLines = wrap(tr(key), self.width - 40)
    self.statusError = isError
end
function CloudPanel:reload(fresh)
    local selected = self.list.items[self.list.selected]
    local selectedId = selected and selected.item.fileId
    local lines, readable = readLines("pzcloud-catalog.txt", MAX_COPIES + 1)
    local copies, seen = {}, {}
    for _, line in ipairs(lines) do
        local f = split(line)
        if #f == 6 and #f[1] <= 200 and f[1]:match("^[A-Za-z0-9_-]+$") and not seen[f[1]] then
            seen[f[1]] = true
            copies[#copies + 1] = { fileId = f[1], name = f[2], mode = f[3], created = f[4], device = f[5], size = f[6] }
        end
    end
    table.sort(copies, function(a, b)
        if a.created == b.created then return a.fileId < b.fileId end
        return a.created > b.created
    end)
    self.list:clear(); self.list.selected = 0
    for index = 1, math.min(#copies, MAX_COPIES) do
        local data = copies[index]
        data.details = fit(displayDate(data.created) .. "  |  " .. data.mode .. "  |  " .. data.device .. "  |  " .. data.size, UIFont.Small, self.list.width - 38)
        self.list:addItem(fit(data.name, UIFont.Medium, self.list.width - 38), data)
        if data.fileId == selectedId then self.list.selected = index end
    end
    self.catalogFresh = fresh and readable or false
    self.countText = tr(self.catalogFresh and "Current" or "Cached") .. ": " .. #self.list.items
    if #copies > MAX_COPIES then self.countText = self.countText .. "  ·  " .. tr("Limited") end
    self:updateButtons()
end
function CloudPanel:updateButtons()
    local busy = PZCloud.pending ~= nil
    self.buttons.LIST:setEnable(not busy)
    self.buttons.OPEN:setEnable(not busy)
    self.buttons.DOWNLOAD:setEnable(not busy and self.catalogFresh and self.list.items[self.list.selected] ~= nil)
end
function CloudPanel:send(action, fileId)
    if PZCloud.pending then return end
    PZCloud.pending = request(action, fileId)
    self.lastPoll = nil
    self:setStatus(PZCloud.pending and (action == "DOWNLOAD" and "Downloading" or "Waiting") or "WriteError", not PZCloud.pending)
    self:updateButtons()
end
function CloudPanel:onClick(button)
    if button.internal == "CLOSE" then
        self:removeFromUIManager(); PZCloud.panel = nil
    elseif button.internal == "GET_APP" then
        local ok = pcall(function() openURl(RELEASE_URL) end)
        self:setStatus(ok and "BrowserOpened" or "BrowserError", not ok)
    elseif button.internal == "OPEN" then self:send("OPEN")
    elseif button.internal == "LIST" then self:send("LIST")
    elseif button.internal == "DOWNLOAD" then
        local selected = self.list.items[self.list.selected]
        if selected and self.catalogFresh then self:send("DOWNLOAD", selected.item.fileId)
        else self:setStatus("Select") end
    end
end
function CloudPanel:poll()
    local pending, now = PZCloud.pending, getTimestampMs()
    if not pending or (self.lastPoll and now - self.lastPoll < 1000) then return end
    self.lastPoll = now
    local lines = readLines("pzcloud-status.txt", 1)
    local fields = split(lines[1] or "")
    if fields[1] == pending.nonce then
        local success = fields[2] == "OK"
        PZCloud.pending = nil
        if pending.action == "LIST" then
            self:reload(success)
            success = success and self.catalogFresh
        end
        local done = pending.action == "DOWNLOAD" and "Downloaded" or (pending.action == "OPEN" and "Opened" or "Refreshed")
        self:setStatus(success and done or "CompanionError", not success)
        self:updateButtons()
    elseif now - pending.sentAt > (pending.action == "DOWNLOAD" and 300000 or 30000) then
        PZCloud.pending = nil
        self.catalogFresh = false
        self.countText = tr("Cached") .. ": " .. #self.list.items
        self:setStatus("Offline", true)
        self:updateButtons()
    end
end
function CloudPanel:drawLines(lines, y, red, green, blue)
    for _, line in ipairs(lines) do
        self:drawText(line, 20, y, red, green, blue, 1, UIFont.Small)
        y = y + self.lineHeight
    end
end
function CloudPanel:prerender()
    ISPanel.prerender(self)
    self:poll(); self:updateButtons()
    self:drawText(tr("Title"), 20, 15, 0.7, 0.86, 0.5, 1, UIFont.Large)
    self:drawLines(self.subtitleLines, self.subtitleY, 0.8, 0.84, 0.8)
    self:drawText(self.countText, 20, self.list.y - self.lineHeight - 7, 0.7, 0.76, 0.7, 1, UIFont.Small)
    self:drawLines(self.hintLines, self.hintY, 0.8, 0.84, 0.8)
    self:drawLines(self.statusLines, self.statusY, self.statusError and 1 or 0.7, self.statusError and 0.65 or 0.86, 0.55)
end
function CloudPanel:createChildren()
    ISPanel.createChildren(self)
    self.lineHeight = getTextManager():getFontHeight(UIFont.Small) + 3
    self.subtitleY = 22 + getTextManager():getFontHeight(UIFont.Large)
    self.subtitleLines = wrap(tr("Subtitle"), self.width - 40)
    self.hintLines = wrap(tr("RestoreHint"), self.width - 40)
    self.emptyHintLines = wrap(tr("EmptyHint"), self.width - 80)
    self.buttons = {}
    local definitions = {{"LIST", "Refresh"}, {"DOWNLOAD", "Download"}, {"OPEN", "Open"}, {"GET_APP", "GetApp"}, {"CLOSE", "Close"}}
    local x, row, height = 20, 0, self.lineHeight + 14
    for _, definition in ipairs(definitions) do
        local title = tr(definition[2])
        local width = measure(UIFont.Small, title) + 26
        if x + width > self.width - 20 then row = row + 1; x = 20 end
        local button = ISButton:new(x, row * (height + 8), width, height, title, self, CloudPanel.onClick)
        button.internal = definition[1]; button:initialise(); button:instantiate(); self:addChild(button)
        self.buttons[definition[1]] = button; x = x + width + 8
    end
    local buttonsY = self.height - 16 - (row + 1) * (height + 8)
    for _, button in pairs(self.buttons) do button:setY(button.y + buttonsY) end
    self.statusY = buttonsY - self.lineHeight * 3 - 10
    self.hintY = self.statusY - #self.hintLines * self.lineHeight - 12
    local listY = self.subtitleY + (#self.subtitleLines + 1) * self.lineHeight + 18
    self.list = ISScrollingListBox:new(20, listY, self.width - 40, self.hintY - listY - 16)
    self.list:initialise(); self.list:instantiate()
    self.list.itemheight = getTextManager():getFontHeight(UIFont.Medium) + self.lineHeight + 20
    -- Draw inside the list after its background, so the empty-state text stays visible.
    self.list.prerender = function(list)
        ISScrollingListBox.prerender(list)
        if #list.items == 0 then
            local y = 20
            list:drawText(tr("Empty"), 12, y, 0.9, 0.93, 0.9, 1, UIFont.Medium)
            y = y + getTextManager():getFontHeight(UIFont.Medium) + 8
            for _, line in ipairs(self.emptyHintLines) do
                list:drawText(line, 12, y, 0.7, 0.76, 0.7, 1, UIFont.Small)
                y = y + self.lineHeight
            end
        end
    end
    self.list.doDrawItem = function(list, y, item, alt)
        if list.selected == item.index then list:drawRect(0, y, list.width, list.itemheight, 0.5, 0.3, 0.45, 0.25)
        elseif alt then list:drawRect(0, y, list.width, list.itemheight, 0.15, 0.25, 0.3, 0.25) end
        list:drawText(item.text, 10, y + 7, 0.95, 0.95, 0.95, 1, UIFont.Medium)
        list:drawText(item.item.details, 10, y + list.itemheight - self.lineHeight - 3, 0.7, 0.77, 0.7, 1, UIFont.Small)
        return y + list.itemheight
    end
    self:addChild(self.list)
    self:reload(false); self:setStatus("Ready")
end
local function closeMenu()
    if PZCloud.button then PZCloud.button:removeFromUIManager(); PZCloud.button = nil end
    if PZCloud.panel then PZCloud.panel:removeFromUIManager(); PZCloud.panel = nil end
end
local function openPanel()
    if getPlayer() then return end
    if PZCloud.panel then PZCloud.panel:setVisible(true); return end
    local width = math.min(940, getCore():getScreenWidth() - 40)
    local height = math.min(660, getCore():getScreenHeight() - 40)
    local panel = CloudPanel:new((getCore():getScreenWidth() - width) / 2, (getCore():getScreenHeight() - height) / 2, width, height)
    panel.backgroundColor = {r=0.07, g=0.11, b=0.1, a=0.98}
    panel.moveWithMouse = true; panel:initialise(); panel:instantiate(); panel:addToUIManager()
    PZCloud.panel = panel
    if PZCloud.pending then panel:setStatus(PZCloud.pending.action == "DOWNLOAD" and "Downloading" or "Waiting")
    else panel:send("LIST") end
end
local function positionButton()
    if PZCloud.button then
        -- Anchor the Workshop shortcut to the upper center with a modest safe margin.
        -- Clamp the margin so it remains pleasant on both small and 4K screens.
        local screenWidth, screenHeight = getCore():getScreenWidth(), getCore():getScreenHeight()
        local topMargin = math.max(20, math.min(42, math.floor(screenHeight * 0.04)))
        PZCloud.button:setX(math.floor((screenWidth - PZCloud.button.width) / 2))
        PZCloud.button:setY(topMargin)
    end
    if PZCloud.panel then
        PZCloud.panel:removeFromUIManager(); PZCloud.panel = nil
        openPanel() -- Recreate wrapped text and button rows for the new resolution.
    end
end
local function menuEnter()
    closeMenu()
    local width = measure(UIFont.Small, tr("Menu")) + 30
    local button = ISButton:new(0, 0, width, getTextManager():getFontHeight(UIFont.Small) + 16, tr("Menu"), nil, openPanel)
    button:initialise(); button:instantiate(); button:addToUIManager(); PZCloud.button = button; positionButton()
end
Events.OnMainMenuEnter.Add(menuEnter)
if Events.OnResolutionChange then Events.OnResolutionChange.Add(positionButton) end
Events.OnGameStart.Add(closeMenu)
Events.OnKeyPressed.Add(function(key) if key == Keyboard.KEY_F8 and not getPlayer() then openPanel() end end)
