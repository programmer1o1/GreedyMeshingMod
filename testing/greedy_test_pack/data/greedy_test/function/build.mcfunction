kill @e[type=marker,tag=greedy_origin]
summon marker ~ ~ ~ {Tags:["greedy_origin"]}
time set noon
weather clear
execute at @e[type=marker,tag=greedy_origin,limit=1] run function greedy_test:build_cells
