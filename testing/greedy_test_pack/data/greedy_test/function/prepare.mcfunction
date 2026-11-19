execute at @s run forceload add ~-16 ~-16 ~240 ~112
tellraw @s {"text":"Greedy test: wait ~10 seconds for chunks to load, then run /function greedy_test:build","color":"yellow"}
