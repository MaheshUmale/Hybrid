import re
import pandas as pd
import datetime

def parse_log_file(log_path):
    signals = []
    executions = []
    exits = []

    signal_pattern = re.compile(r"SCALP SIGNAL \[(.*?)\]: (.*?) Entry: ([\d\.]+) SL: ([\d\.]+) TP: ([\d\.]+)")
    exec_pattern = re.compile(r"AUTO-EXECUTED \[(.*?)\]: (.*?) Qty: (\d+) @ ([\d\.]+) SL: ([\d\.]+) TP: ([\d\.]+) \(Gate: (.*?)\)")
    exit_pattern = re.compile(r"AUTO-EXIT \[(.*?)\]: (.*?) @ ([\d\.]+) Reason: (.*?) PnL: ([\-\d\.]+) \(Gate Refreshed: (.*?)\)")

    with open(log_path, 'r', encoding='utf-8', errors='ignore') as f:
        for line in f:
            signal_match = signal_pattern.search(line)
            if signal_match:
                signals.append({
                    'Gate': signal_match.group(1),
                    'Symbol': signal_match.group(2).strip(),
                    'Entry': float(signal_match.group(3)),
                    'SL': float(signal_match.group(4)),
                    'TP': float(signal_match.group(5)),
                    'Time': "N/A"
                })

            exec_match = exec_pattern.search(line)
            if exec_match:
                executions.append({
                    'Side': exec_match.group(1),
                    'Symbol': exec_match.group(2).strip(),
                    'Qty': int(exec_match.group(3)),
                    'EntryWait': float(exec_match.group(4)),
                    'GateKey': exec_match.group(7)
                })

            exit_match = exit_pattern.search(line)
            if exit_match:
                exits.append({
                    'Side': exit_match.group(1),
                    'Symbol': exit_match.group(2).strip(),
                    'ExitPrice': float(exit_match.group(3)),
                    'Reason': exit_match.group(4).strip(),
                    'PnL': float(exit_match.group(5)),
                    'GateKey': exit_match.group(6)
                })

    return signals, executions, exits

def analyze_results(signals, executions, exits):
    print(f"Total Signals: {len(signals)}")
    print(f"Total Executions: {len(executions)}")
    print(f"Total Exits: {len(exits)}")

    df_exits = pd.DataFrame(exits)
    
    if df_exits.empty:
        print("No trades completed.")
        return

    KNOWN_GATES = [
        "STUFF_S", "CRUSH_L", "REBID", "RESET",
        "HITCH_L", "HITCH_S", "CLOUD_L", "CLOUD_S",
        "RUBBER_L", "RUBBER_S", "SNAP_B", "SNAP_S",
        "VWAP_REC", "VWAP_REJ", "MAGNET",
        "ORB_L", "ORB_S", "LATE_SQ"
    ]
    
    def get_gate_name(key):
        if not key or key == 'null': return "Unknown"
        key = key.strip()
        for gate in KNOWN_GATES:
            if key.endswith("_" + gate):
                return gate
            if key == gate:
                return gate
        return key.split('_')[-1]

    df_exits['Gate'] = df_exits['GateKey'].apply(get_gate_name)

    total_pnl = df_exits['PnL'].sum()
    win_trades = df_exits[df_exits['PnL'] > 0]
    loss_trades = df_exits[df_exits['PnL'] <= 0]
    win_rate = len(win_trades) / len(df_exits) * 100 if len(df_exits) > 0 else 0

    print("\n" + "="*40)
    print("OVERALL PERFORMANCE")
    print("="*40)
    print(f"Total PnL: {total_pnl:.2f}")
    print(f"Win Rate: {win_rate:.2f}% ({len(win_trades)}W / {len(loss_trades)}L)")
    print(f"Avg PnL per Trade: {df_exits['PnL'].mean():.2f}")

    print("\n" + "="*40)
    print("PERFORMANCE BY STRATEGY (GATE)")
    print("="*40)
    gate_stats = df_exits.groupby('Gate')['PnL'].agg(['count', 'sum', 'mean', 'min', 'max'])
    gate_stats['WinRate'] = df_exits.groupby('Gate').apply(lambda x: (x['PnL'] > 0).sum() / len(x) * 100)
    print(gate_stats.sort_values(by='sum', ascending=False))

    print("\n" + "="*40)
    print("EXIT REASON ANALYSIS")
    print("="*40)
    reason_stats = df_exits.groupby('Reason')['PnL'].agg(['count', 'sum', 'mean'])
    print(reason_stats)

    print("\n" + "="*40)
    print("WEAK SPOTS IDENTIFICATION")
    print("="*40)
    
    weak_strategies = gate_stats[gate_stats['WinRate'] < 40]
    if not weak_strategies.empty:
        print("!! LOW WIN RATE STRATEGIES (< 40%) !!")
        print(weak_strategies[['count', 'WinRate', 'sum']])
    else:
        print("No strategies below 40% win rate.")

    loss_strategies = gate_stats[gate_stats['sum'] < -5000]
    if not loss_strategies.empty:
        print("\n!! HIGH LOSS STRATEGIES (< -5000 Total PnL) !!")
        print(loss_strategies[['count', 'sum']])

if __name__ == "__main__":
    log_file = "backtest_java.log"
    print(f"Analyzing {log_file}...")
    s, e, out = parse_log_file(log_file)
    analyze_results(s, e, out)
