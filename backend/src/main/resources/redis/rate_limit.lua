local current = redis.call('incr', KEYS[1])
if current == 1 then
    redis.call('pexpire', KEYS[1], ARGV[1])
end
local ttl = redis.call('pttl', KEYS[1])
if ttl < 0 then
    redis.call('pexpire', KEYS[1], ARGV[1])
    ttl = tonumber(ARGV[1])
end
if current > tonumber(ARGV[2]) then
    return ttl
end
return 0
