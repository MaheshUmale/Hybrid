from SymbolMaster import MASTER

print("Initializing Symbol Master...")
MASTER.initialize()

print("-" * 30)
symbols = ["NIFTY", "BANKNIFTY", "RELIANCE", "SBIN", "HDFCBANK", "INFY"]

for s in symbols:
    key = MASTER.get_upstox_key(s)
    if key:
        print(f"[OK] {s} -> {key}")
    else:
        print(f"[FAIL] {s} -> Not Found")

print("-" * 30)
print("Reverse Lookup Test:")
test_key = "NSE_INDEX|Nifty 50"
print(f"{test_key} -> {MASTER.get_ticker_from_key(test_key)}")
