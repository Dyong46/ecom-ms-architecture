redis.call('PSETEX', KEYS[1], ARGV[3], ARGV[1])
redis.call('PSETEX', KEYS[2], ARGV[3], ARGV[2])
return {redis.call('GET', KEYS[1]), redis.call('GET', KEYS[2])}
