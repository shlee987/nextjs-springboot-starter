-- 사용자 hash tag 하나 안에서만 로그인·회전·fence를 원자 처리한다.
local command = ARGV[1]
local now = tonumber(ARGV[2])
local payload = cjson.decode(ARGV[3])
local policy = ARGV[4]
local idle = tonumber(ARGV[5])
local raw = redis.call('GET', KEYS[1])
local state = raw and cjson.decode(raw) or {logins={}, history={}}
local function fail(code) return cjson.encode({error=code}) end
local function present(value) return value ~= nil and value ~= cjson.null end
local function active(login)
    return login and not present(login.revokedAt)
        and now < login.idleExpiresAt and now < login.absoluteExpiresAt
end
local function revoke(login, reason)
    if login and not present(login.revokedAt) then
        login.revokedAt = now
        login.revokeReason = reason
    end
end
local function save()
    local expiry = now + 1000
    for _, login in pairs(state.logins) do expiry = math.max(expiry, login.absoluteExpiresAt) end
    redis.call('SET', KEYS[1], cjson.encode(state))
    if present(state.blocked) then redis.call('PERSIST', KEYS[1])
    else redis.call('PEXPIREAT', KEYS[1], expiry) end
end
-- 이전 hash는 로그인 절대 만료까지 보존한다. 청소가 늦어도 active 검사는 동일하다.
for sid, login in pairs(state.logins) do
    if login.absoluteExpiresAt <= now then
        state.logins[sid] = nil
        for hash, owner in pairs(state.history) do
            if owner == sid then state.history[hash] = nil end
        end
    end
end
if command == 'block' then
    if present(state.blocked) and state.blocked ~= payload.operationId then return fail('AUTH_STATE_UNAVAILABLE') end
    state.blocked = payload.operationId
    save()
    return '{}'
elseif command == 'unblock' then
    if state.blocked ~= payload.operationId then return fail('AUTH_STATE_UNAVAILABLE') end
    state.blocked = nil
    save()
    return '{}'
elseif command == 'revokeAll' then
    for _, login in pairs(state.logins) do revoke(login, payload.reason) end
    save()
    return '{}'
elseif command == 'revoke' then
    revoke(state.logins[payload.sid], payload.reason)
    save()
    return '{}'
elseif command == 'locateRefresh' then
    local login = state.logins[payload.sid]
    if login and (login.currentRefreshHash == payload.oldHash or state.history[payload.oldHash] == payload.sid) then
        return cjson.encode(login)
    end
    return fail('TOKEN_REVOKED')
end
if present(state.blocked) then return fail('AUTH_STATE_UNAVAILABLE') end
if command == 'create' then
    local count = 0
    for _, login in pairs(state.logins) do if active(login) then count = count + 1 end end
    if count > 0 and policy == 'DENY_NEW' then return fail('CONCURRENT_LOGIN_DENIED') end
    if policy == 'REPLACE_OLD' then
        for _, login in pairs(state.logins) do revoke(login, 'REPLACED') end
    end
    if state.logins[payload.sid] then return fail('AUTH_STATE_UNAVAILABLE') end
    state.logins[payload.sid] = payload
    save()
    return cjson.encode({login=payload, replaced=count > 0 and policy == 'REPLACE_OLD'})
elseif command == 'rotate' then
    local login = state.logins[payload.sid]
    if state.history[payload.oldHash] == payload.sid then
        revoke(login, 'REFRESH_REUSED')
        save()
        return fail('TOKEN_REFRESH_REUSED')
    end
    if not active(login) or login.currentRefreshHash ~= payload.oldHash then return fail('TOKEN_REVOKED') end
    state.history[payload.oldHash] = payload.sid
    login.currentRefreshHash = payload.newHash
    save()
    return cjson.encode(login)
else
    local login = state.logins[payload.sid]
    if not active(login) then return fail('TOKEN_REVOKED') end
    if command == 'touch' then
        login.lastActivityAt = math.max(login.lastActivityAt, now)
        login.idleExpiresAt = math.min(login.absoluteExpiresAt, math.max(login.idleExpiresAt, now + idle))
    end
    save()
    return cjson.encode(login)
end
