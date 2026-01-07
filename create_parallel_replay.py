import sys
import os

# Copy existing backtest_replay logic but change PORT
with open("backtest_replay.py", "r") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if line.strip() == "PORT = 8765":
        new_lines.append("PORT = 8770\n")
    else:
        new_lines.append(line)

with open("backtest_replay_parallel.py", "w") as f:
    f.writelines(new_lines)

print("Created backtest_replay_parallel.py on port 8770")
