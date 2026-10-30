-- 계정/IP 원문 없이 단일 hashed bucket에서 fixed window와 잠금을 보존한다.
local now = tonumber(ARGV[1])
local maximum = tonumber(ARGV[2])
local window = tonumber(ARGV[3])
local lock = tonumber(ARGV[4])
local action = ARGV[5]
local raw = redis.call('GET', KEYS[1])
local state = raw and cjson.decode(raw) or {start=0, attempts=0, blocked=0}
if action == 'check' then return math.max(0, math.ceil((state.blocked - now) / 1000)) end
local start = now - now % window
if state.start ~= start then state.start = start; state.attempts = 0 end
state.attempts = state.attempts + 1
if lock > 0 and state.attempts == maximum then state.blocked = now + lock end
redis.call('SET', KEYS[1], cjson.encode(state), 'PX', math.max(start + window + lock, state.blocked) - now)
if state.attempts > maximum then return math.max(1, math.ceil((start + window - now) / 1000)) end
return 0
