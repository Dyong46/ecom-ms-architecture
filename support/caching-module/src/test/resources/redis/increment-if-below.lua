local current = tonumber(redis.call('GET', KEYS[1]) or '0')
local limit = tonumber(ARGV[1])
if current >= limit then
    return 0
end
redis.call('PSETEX', KEYS[1], ARGV[2], current + 1)
return 1
