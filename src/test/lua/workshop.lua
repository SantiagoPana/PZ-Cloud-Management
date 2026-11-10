-- Execute the real mod with a small fake PZ UI/file API. No game or account is accessed.
local repo, translationsFile = arg[1], arg[2]
local translations = dofile(translationsFile)
local passed = 0
local function check(value, message) assert(value, message or 'check failed') end
local function eq(a, b) assert(a == b, 'expected ' .. tostring(b) .. ', got ' .. tostring(a)) end
local function test(name, action) action(); passed = passed + 1; print('PASS ' .. name) end
local environment
local function setup(language, width, height, scale)
    local e = { files = {}, reads = 0, writes = 0, closed = 0, now = 1000, draws = {}, widths = 0, scale = scale or 1, ui = {}, width = width or 1920, height = height or 1080 }
    environment = e
    PZCloud = nil
    UIFont = {Small = 'small', Medium = 'medium', Large = 'large'}
    Keyboard = {KEY_F8 = 119}
    Events = {}
    for _, name in ipairs({'OnMainMenuEnter', 'OnResolutionChange', 'OnGameStart', 'OnKeyPressed'}) do
        local event = {listeners = {}}
        event.Add = function(callback) event.listeners[#event.listeners + 1] = callback end
        event.fire = function(...) for _, callback in ipairs(event.listeners) do callback(...) end end
        Events[name] = event
    end
    local Base = {}
    function Base:derive() local child = {}; setmetatable(child, {__index = self}); return child end
    function Base:new(x, y, w, h)
        local instance = {x=x,y=y,width=w,height=h,children={},items={},selected=0,enable=true}
        return setmetatable(instance, {__index=self})
    end
    function Base:initialise() end
    function Base:instantiate() if not self.created then self.created = true; self:createChildren() end end
    function Base:createChildren() end
    function Base:addChild(child) self.children[#self.children+1] = child end
    function Base:addToUIManager() e.ui[self] = true end
    function Base:removeFromUIManager() e.ui[self] = nil end
    function Base:setX(value) self.x=value end
    function Base:setY(value) self.y=value end
    function Base:setVisible(value) self.visible=value end
    function Base:setEnable(value) self.enable=value end
    function Base:prerender() end
    function Base:drawRect() end
    function Base:drawText(text, x, y, r, g, b, a, font)
        e.draws[#e.draws + 1] = {text=text,x=x,y=y,font=font,source=self}
    end
    function Base:clear() self.items={}; self.selected=0 end
    function Base:addItem(text, item) self.items[#self.items+1] = {text=text,item=item,index=#self.items+1} end
    ISPanel = Base:derive('ISPanel')
    ISButton = Base:derive('ISButton')
    function ISButton:new(x,y,w,h,title,target,callback)
        local object=Base.new(self,x,y,w,h); object.title=title; object.target=target; object.callback=callback; return object
    end
    ISScrollingListBox = Base:derive('ISScrollingListBox')
    for _, module in ipairs({'ISUI/ISPanel','ISUI/ISButton','ISUI/ISScrollingListBox'}) do package.preload[module]=function() end end
    function getPlayer() return e.player end
    function getTimestampMs() return e.now end
    function getTextOrNull(key) return translations[language] and translations[language][key] end
    function getCore() return {getScreenWidth=function() return e.width end,getScreenHeight=function() return e.height end} end
    local metrics = {small=14,medium=20,large=24}
    function getTextManager()
        return {
            getFontHeight=function(_,font) return metrics[font]*e.scale end,
            MeasureStringX=function(_,font,text)
                e.widths=e.widths+1
                local _,continuations=text:gsub('[\128-\191]','')
                return (#text-continuations)*metrics[font]*0.5*e.scale
            end
        }
    end
    function getFileReader(name)
        e.reads=e.reads+1
        if not e.files[name] then return nil end
        local lines={}; for line in e.files[name]:gmatch('([^\n]*)\n?') do if line~='' then lines[#lines+1]=line end end
        local index=0
        return {readLine=function() index=index+1; return lines[index] end,close=function() e.closed=e.closed+1 end}
    end
    function getFileWriter(name)
        if e.writeError then return nil end
        local content=''
        return {write=function(_,text) content=content..text end,close=function()
            if e.closeError then error('close failed') end
            e.files[name]=content; e.writes=e.writes+1; e.closed=e.closed+1
        end}
    end
    function openURl(url) e.url=url end
    dofile(repo .. '/mod/PZCloudManagement/42/media/lua/client/PZCloud/CloudSaves.lua')
    function e:open() Events.OnMainMenuEnter.fire(); PZCloud.button.callback(); return PZCloud.panel end
    function e:respond(status)
        self.files['pzcloud-status.txt']=PZCloud.pending.nonce..'|'..(status or 'OK')..'|message\n'
        self.now=self.now+1001; PZCloud.panel:prerender()
    end
    return e
end
local catalog = 'old|Pan-con-lechuga|Sandbox|2026-11-10T05:25:34.851122900Z|PC-A|56.2 MB\nnew|Testing|Sandbox|2026-11-10T14:11:55.165136600Z|PC-B|6.6 MB\n'

test('JSON translations load; cached copies require a fresh response before downloading', function()
    local e=setup('ES'); e.files['pzcloud-catalog.txt']=catalog; local panel=e:open()
    eq(panel.list.items[1].item.fileId,'new'); check(not panel.buttons.DOWNLOAD.enable)
    e:respond(); panel.list.selected=1; panel:prerender(); check(panel.buttons.DOWNLOAD.enable)
    check(panel.list.items[1].item.details:find('2026%-11%-10 14:11 UTC'))
    for _,draw in ipairs(e.draws) do check(not draw.text:find('UI_PZCloud_')) end
    eq(panel.buttons.GET_APP.title, 'Descargar aplicación')
end)
test('Missing locale falls back to readable English and no raw keys', function()
    local e=setup('missing'); local panel=e:open(); panel:prerender()
    eq(panel.buttons.DOWNLOAD.title,'Download copy')
    for _,draw in ipairs(e.draws) do check(not draw.text:find('UI_PZCloud_')) end
end)
test('Empty-state text belongs to the list and is drawn after its background', function()
    local e=setup('ES'); local panel=e:open(); panel.list:prerender()
    local found=false
    for _,draw in ipairs(e.draws) do
        if draw.text=='Todavía no hay copias para mostrar' then
            eq(draw.source,panel.list); check(draw.y>=0 and draw.y<panel.list.height); found=true
        end
    end
    check(found,'empty state was not drawn in the list')
end)
test('Timeout stops polling and marks cached list unavailable for downloads', function()
    local e=setup('ES'); e.files['pzcloud-catalog.txt']=catalog; local panel=e:open()
    e.now=32000; panel:prerender(); check(not PZCloud.pending); check(not panel.catalogFresh)
    local reads=e.reads
    for _=1,1000 do e.now=e.now+1000; panel:prerender() end
    eq(e.reads, reads); check(not panel.buttons.DOWNLOAD.enable); check(panel.buttons.LIST.enable)
end)
test('Download keeps one pending request across close and reopen', function()
    local e=setup('ES'); e.files['pzcloud-catalog.txt']=catalog; local panel=e:open(); e:respond()
    panel.list.selected=1; panel:onClick(panel.buttons.DOWNLOAD)
    eq(PZCloud.pending.action,'DOWNLOAD'); local nonce=PZCloud.pending.nonce; local writes=e.writes
    check(not panel.buttons.LIST.enable); check(not panel.buttons.OPEN.enable)
    panel:onClick(panel.buttons.CLOSE); PZCloud.button.callback(); panel=PZCloud.panel
    eq(PZCloud.pending.nonce,nonce); eq(e.writes,writes)
    e.now=e.now+31000; panel:prerender(); check(PZCloud.pending)
    e:respond(); check(not PZCloud.pending); check(table.concat(panel.statusLines,' '):find('Copia descargada'))
end)
test('Old responses cannot satisfy a new request; errors preserve cached copies', function()
    local e=setup('ES'); e.files['pzcloud-catalog.txt']=catalog; local panel=e:open()
    e.files['pzcloud-status.txt']='previous|OK|done'; panel:prerender(); check(PZCloud.pending)
    e:respond('ERROR'); check(not PZCloud.pending); check(not panel.catalogFresh); eq(#panel.list.items,2)
end)
test('A successful response without a readable catalog is not reported as updated', function()
    local e=setup('ES'); local panel=e:open(); e:respond()
    check(not panel.catalogFresh); check(panel.statusError); check(not panel.buttons.DOWNLOAD.enable)
end)
test('Download button opens only the fixed public release page', function()
    local e=setup('ES'); local panel=e:open(); local writes=e.writes
    panel:onClick(panel.buttons.GET_APP)
    eq(e.url,'https://github.com/SantiagoPana/PZ-Cloud-Management/releases/tag/v2.3.1'); eq(e.writes,writes)
end)
test('Permission and writer-close failures do not leave a pending operation', function()
    for _, failure in ipairs({'writeError','closeError'}) do
        local e=setup('EN'); e[failure]=true; local panel=e:open()
        check(not PZCloud.pending); check(panel.statusError); check(panel.buttons.LIST.enable)
    end
end)
test('Polling is bounded to one read per second, with no idle work', function()
    local e=setup('ES'); local panel=e:open(); panel:prerender(); local reads=e.reads
    for _=1,60 do e.now=e.now+10; panel:prerender() end
    eq(e.reads,reads); e:respond(); reads=e.reads; local widths=e.widths
    for _=1,60 do e.now=e.now+1000; panel:prerender() end
    eq(e.reads,reads); eq(e.widths,widths)
end)
test('Malformed IDs and duplicates are ignored; catalog is bounded', function()
    local e=setup('ES'); local lines={'../bad|Bad|Sandbox|2026|PC|1 MB','same|Copy|Sandbox|2026|PC|1 MB','same|Duplicate|Sandbox|2026|PC|1 MB'}
    for index=1,800 do lines[#lines+1]='id_'..index..'|Copy|Sandbox|2026|PC|1 MB' end
    e.files['pzcloud-catalog.txt']=table.concat(lines,'\n'); local panel=e:open()
    check(#panel.list.items<=500)
    local seen={}; for _,row in ipairs(panel.list.items) do check(not seen[row.item.fileId]); seen[row.item.fileId]=true end
end)
test('Resolution changes rebuild the window; gameplay removes menu UI', function()
    local e=setup('AR'); local panel=e:open(); local nonce=PZCloud.pending.nonce
    e.width=1024; e.height=768; Events.OnResolutionChange.fire(); check(PZCloud.panel~=panel); eq(PZCloud.pending.nonce,nonce)
    e.player={}; Events.OnGameStart.fire(); check(not PZCloud.panel and not PZCloud.button)
    Events.OnKeyPressed.fire(Keyboard.KEY_F8); check(not PZCloud.panel)
end)
test('Buttons, footer and list fit ordinary and larger-font layouts', function()
    for _,size in ipairs({{800,600,1},{1024,768,1.5},{1920,1080,1},{1920,1080,2}}) do
        local e=setup('ES',size[1],size[2],size[3]); local panel=e:open()
        check(panel.list.height>=panel.list.itemheight,'list too short at '..table.concat(size,'/'))
        check(panel.list.y+panel.list.height<panel.hintY)
        for _,button in pairs(panel.buttons) do
            check(button.x>=0 and button.x+button.width<=panel.width,'button width overflow')
            check(button.y+button.height<=panel.height,'button height overflow')
            check(panel.statusY+#panel.statusLines*panel.lineHeight<button.y,'status overlaps buttons')
        end
    end
end)
print(passed .. ' Workshop Lua scenarios passed (simulated PZ UI and filesystem).')
