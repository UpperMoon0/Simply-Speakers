-- Runs inside a placed CC:Tweaked computer. No replacement peripheral or Lua runtime.
local first, second, short, foreign = "@FIRST@", "@SECOND@", "@SHORT@", "@FOREIGN@"
local checks, events, detached = 0, {}, {}
local function check(value, why)
    checks = checks + 1
    assert(value, why)
end
-- Explicitly yield between test phases before filesystem I/O.
local function write(path, text)
    sleep(0)
    local f = assert(fs.open(path, "w")); f.write(text); f.close()
end
local function waitFor(predicate, why)
    local deadline = os.clock() + 5
    while not predicate() and os.clock() < deadline do sleep(0.05) end
    check(predicate(), why)
end
local function find(id)
    local name
    local value = peripheral.find("simply_speaker", function(n, p)
        if p.getStatus().speakerId == id then name = n; return true end
    end)
    check(value ~= nil, "actual peripheral discovery failed: " .. id)
    return value, name
end
local function request(mode)
    write("request.txt", mode)
    while true do
        local _, ack = os.pullEvent("ss_fixture_ack")
        if ack == mode then return end
    end
end
local function run()
    sleep(0.2)
    local p, name = find("__cc_owned")
    local free = find("__cc_unowned")
    if fs.exists("reboot.txt") then
        check(p.getStatus().repeatMode == "none", "repeat lost across computer reboot")
        check(math.abs(p.getVolume() - 0.4) < 0.001, "volume lost across computer reboot")
        check(p.getRange() == 32 and #p.getQueue() == 0, "settings/queue lost across reboot")
        check(#p.getSavedPlaylists() == 2, "owner catalog lost across reboot")
        check(not p.getStatus().playing, "reboot restarted playback")
        write("result.txt", "PASS assertions=" .. tonumber(readRebootChecks()) + checks .. " reboot=true")
        return
    end
    local exposed = {}
    for _, method in ipairs(peripheral.getMethods(name)) do exposed[method] = true end
    for _, method in ipairs({"play","pause","togglePause","stop","restart","next","previous","seek","setTrack",
        "getStatus","setVolume","getVolume","setRange","getRange","setLooping","isLooping","setShuffle",
        "setRepeatMode","queueNext","queueLast","getQueue","clearQueue","removeQueued","moveQueued",
        "getPlaylist","addToPlaylist","removeFromPlaylist","clearPlaylist","playPlaylist","selectPlaylistTrack",
        "getSettings","setAudioDropoff","setDirectionality","setConeAngle","setRearAttenuation",
        "getNetworkName","setNetworkName","getSavedPlaylists","playSavedPlaylist","getLibrary"}) do
        check(exposed[method], "missing Lua method " .. method)
    end
    -- Getters must complete promptly without an unrelated timer or speaker event.
    local started = os.clock()
    check(type(p.getStatus()) == "table" and type(p.getPlaylist()) == "table", "snapshot conversion")
    check(os.clock() - started < 1.5, "getter did not wake on main-thread completion")
    check(p.getStatus().canControl and not p.getStatus().canManage, "public owner access flags")
    check(#p.getLibrary()==3, "owned library filtering")
    check(not p.setTrack(foreign) and not p.setTrack("does-not-exist"), "foreign/missing recording selected")
    check(not p.queueNext(foreign) and not p.queueLast(foreign) and not p.addToPlaylist(foreign), "queue/add ownership bypass")
    check(not p.setTrack("http://127.0.0.1/test.wav"), "private stream accepted")
    check(not p.setTrack("https://example.com/test.mp3"), "disabled stream accepted")
    check(not p.setTrack("https://example.com/page"), "web page accepted as audio")
    request("streams_on")
    for _, url in ipairs({"http://127.0.0.1/clip.wav","http://192.168.1.1/clip.mp3","http://[::1]/clip.wav","http://localhost/clip.mp3","file:///clip.wav"}) do
        check(not p.setTrack(url) and not p.queueNext(url), "enabled-stream address filter: "..url)
    end
    check(p.setTrack("https://8.8.8.8/fixture.mp3"), "allowed public stream selection")
    check(p.getStatus().trackId=="https://8.8.8.8/fixture.mp3", "stream track snapshot")
    check(p.queueLast("https://8.8.8.8/fixture.mp3") and p.clearQueue() and p.setTrack(""), "public stream queue/clear")
    request("streams_off")
    check(not p.setVolume(0/0) and not p.seek(math.huge), "nonfinite numbers accepted")
    check(not p.setRepeatMode("oops"), "invalid repeat silently changed preference")
    check(not pcall(p.setVolume, "wrong type") and not pcall(p.setRange), "CC argument errors missing")
    check(p.setTrack(first) and not p.getStatus().playing, "selection unexpectedly started playback")
    check(p.getStatus().trackId == first and p.getStatus().track == "cc-first.wav", "manifest identity")
    check(not p.setNetworkName("unauthorized") and not p.setDirectionality(0.5), "owned-network management bypass")
    check(free.setNetworkName("Fixture free") and free.getNetworkName() == "Fixture free", "unowned display name")
    check(free.setDirectionality(0.7) and free.setConeAngle(150) and free.setRearAttenuation(0.25), "directional setters")
    local settings = free.getSettings()
    check(math.abs(settings.directionality-0.7)<0.001 and settings.coneAngle==150 and math.abs(settings.rearAttenuation-0.25)<0.001, "directional snapshot")
    check(free.setConeAngle(999) and free.getSettings().coneAngle==350, "cone upper bound")
    check(p.setVolume(0.4) and math.abs(p.getVolume()-0.4)<0.001, "volume")
    check(p.setRange(999) and p.getRange()==64 and p.setRange(32), "range bounds")
    check(p.setAudioDropoff(0.3) and math.abs(p.getSettings().audioDropoff-0.3)<0.001, "audio dropoff")
    check(p.setLooping(true) and p.isLooping() and p.getStatus().repeatMode=="track", "loop/repeat compatibility")
    check(p.setRepeatMode("playlist") and not p.isLooping(), "repeat mode consolidation")
    check(p.setRepeatMode("none") and p.setShuffle(true) and p.getStatus().shuffle, "shuffle/repeat state")
    check(p.setShuffle(false), "shuffle disable")
    check(p.play(), "play")
    waitFor(function() return (events["speaker_started:"..name] or 0)>0 end, "started event")
    check(p.getStatus().playing, "playing status")
    local pausedBefore = events["speaker_paused:"..name] or 0
    check(p.pause() and p.getStatus().paused, "pause")
    waitFor(function() return (events["speaker_paused:"..name] or 0)>pausedBefore end, "paused event")
    sleep(0.15)
    check(events["speaker_paused:"..name]==pausedBefore+1, "duplicate event attachment")
    check(p.togglePause() and p.getStatus().playing, "toggle/resume")
    waitFor(function() return (events["speaker_resumed:"..name] or 0)>0 end, "resumed event")
    check(p.seek(3) and p.getStatus().position>=3, "seek")
    check(p.restart() and p.getStatus().position<1, "restart")
    check(p.stop() and not p.getStatus().playing, "stop")
    waitFor(function() return (events["speaker_stopped:"..name] or 0)>0 end, "stopped event")
    for _, mode in ipairs({"owner_only", "trusted", "operators"}) do
        request(mode)
        local before = p.getStatus()
        check(not before.canControl, "protected access flag " .. mode)
        check(not p.play() and not p.pause() and not p.setVolume(0.9) and not p.setRange(8), "protected transport/settings "..mode)
        check(not p.queueNext(first) and not p.clearPlaylist() and not p.setRepeatMode("track"), "protected playlist "..mode)
        check(p.getStatus().trackId==before.trackId and math.abs(p.getVolume()-0.4)<0.001, "denial changed state "..mode)
        check(#p.getSavedPlaylists()==0 and #p.getLibrary()==0, "protected personal catalog leaked")
    end
    request("public")
    local catalog = p.getSavedPlaylists()
    check(#catalog==2 and catalog[1].count==2 and catalog[2].count==1, "personal catalog discovery")
    check(not p.playSavedPlaylist("missing"), "missing playlist accepted")
    check(p.queueLast(second) and p.playSavedPlaylist(catalog[1].id), "saved playlist play")
    check(#p.getQueue()==1, "switching templates discarded speaker queue")
    check(#p.getPlaylist()==2 and p.getPlaylist()[1].slot==1, "one-based playlist")
    check(p.getStatus().trackId==first, "playlist starts from first item")
    check(p.clearQueue() and p.queueLast(first) and p.queueLast(second), "queue append")
    check(#p.getQueue()==2 and p.getQueue()[1]==first, "queue order")
    check(p.moveQueued(2,-1) and p.getQueue()[1]==second, "queue move")
    check(not p.moveQueued(1,-1) and not p.removeQueued(0) and not p.removeQueued(99), "queue index bounds")
    check(p.removeQueued(1) and #p.getQueue()==1 and p.clearQueue(), "queue remove/clear")
    check(not p.queueNext("missing-recording") and not p.queueLast("missing-recording"), "missing queue recording accepted")
    check(p.pause(), "pause before queue capacity test")
    for slot=1,256 do check(p.queueLast(first), "queue capacity slot "..slot) end
    check(#p.getQueue()==256 and not p.queueLast(first) and not p.queueNext(first), "queue overflow accepted")
    check(p.clearQueue() and p.play(), "queue capacity cleanup/resume")
    check(p.queueNext(second) and p.next() and p.getStatus().trackId==second, "queued next")
    check(p.next() and p.getStatus().trackId==second, "playlist resumes after queue")
    check(p.previous() and p.getStatus().trackId==first, "previous")
    check(p.selectPlaylistTrack(2) and p.getStatus().trackId==second, "one-based playlist selection")
    check(not p.selectPlaylistTrack(0) and not p.selectPlaylistTrack(99), "playlist slot bounds")
    check(p.addToPlaylist(first) and #p.getPlaylist()==3, "runtime playlist add")
    check(p.removeFromPlaylist(first) and #p.getPlaylist()==1, "runtime playlist remove")
    check(p.clearPlaylist() and #p.getPlaylist()==0 and not p.playPlaylist(), "empty runtime playlist")
    check(p.playSavedPlaylist(catalog[1].id) and #p.getPlaylist()==2, "saved template was edited by runtime operations")
    waitFor(function() return (events["speaker_track_changed:"..name] or 0)>0 end, "track-changed event")
    request("detach")
    check(next(detached)~=nil, "actual peripheral detach event missing")
    request("reattach")
    -- Finish means exhausted playback; explicitly clear the still-active playlist first.
    check(p.stop() and p.clearPlaylist() and p.setRepeatMode("none") and p.setTrack(short) and p.play(), "short recording start")
    waitFor(function() return (events["speaker_finished:"..name] or 0)>0 end, "finished event")
    check(p.stop() and p.clearQueue() and p.setVolume(0.4) and p.setRange(32), "final reset")
    write("reboot.txt", tostring(checks))
    os.reboot()
end
function readRebootChecks()
    local f=assert(fs.open("reboot.txt","r"));local n=f.readAll();f.close();return n
end
local function collect()
    while true do
        local kind,name,id,network=os.pullEvent()
        if kind:match("^speaker_") then
            check(type(name)=="string" and type(id)=="string" and type(network)=="string", "event payload conversion")
            events[kind..":"..name]=(events[kind..":"..name] or 0)+1
        elseif kind=="peripheral_detach" then detached[name]=true end
    end
end
local ok, failure = xpcall(function() parallel.waitForAny(run,collect) end, function(e) return tostring(e) end)
if not ok then write("result.txt","FAIL assertions="..checks.." "..failure);printError(failure) end
