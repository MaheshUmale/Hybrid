import upstox_client
import config
from SymbolMaster import MASTER as SymbolMaster
import time

print("Initializing Symbol Master...")
SymbolMaster.initialize()

configuration = upstox_client.Configuration()
configuration.access_token = config.ACCESS_TOKEN
api_instance = upstox_client.MarketQuoteV3Api(upstox_client.ApiClient(configuration))

test_symbols = ["NIFTY", "RELIANCE", "SBIN"]
keys_map = {}
query_keys = []

print("Resolving Keys...")
for sym in test_symbols:
    u_key = SymbolMaster.get_upstox_key(sym)
    if u_key:
        print(f"{sym} -> {u_key}")
        keys_map[u_key] = sym
        query_keys.append(u_key)
    else:
        print(f"[FAIL] {sym} -> Key Not Found")

    if not query_keys:
        print("No keys to query.")
        exit()

    # User-provided pattern:
    # api_client = upstox_client.ApiClient(configuration)
    # history_api_instance = upstox_client.HistoryV3Api(api_client)
    # historCandlejson = history_api_instance.get_historical_candle_data1(...)

    print("Initializing HistoryV3Api...")
    api_client = upstox_client.ApiClient(configuration)
    history_api_instance = upstox_client.HistoryV3Api(api_client)
    
    from datetime import datetime
    today = datetime.now().strftime("%Y-%m-%d")
    
    # For test, just fetch for one symbol to verify
    test_key = query_keys[0] # e.g. NSE_INDEX|Nifty 50
    test_sym = keys_map[test_key]
    
    print(f"Fetching Intra-Day Candle Data for {test_sym} ({test_key})...")
    
    # PROBE THE METHOD SIGNATURE FIRST
    import inspect
    try:
        sig = inspect.signature(history_api_instance.get_intra_day_candle_data)
        print(f"BINGO! Method Signature: {sig}")
    except Exception as e:
        print(f"Could not get signature: {e}")

    try:
        # User Instruction: response = history_api_instance.get_intra_day_candle_data(instrument_key , "minutes", "1")
        # Signature confirmed: (instrument_key, unit, interval, ...)
        
        print("Attempting with USER EXACT PARAMS: 'minutes', '1'")
        response = history_api_instance.get_intra_day_candle_data(test_key, "minutes", "1") 

        
        if response and hasattr(response, 'data') and hasattr(response.data, 'candles'):
            candles = response.data.candles
            print(f"Fetched {len(candles)} candles.")
            if candles:
                last = candles[0]
                print(f"Last Candle: {last}")
                # Format: [timestamp, open, high, low, close, volume, oi] usually
            print("SUCCESS: Intra-Day API works.")
        else:
            print("Response empty or invalid format.")

    except Exception as e:
        print(f"API Error: {e}")
        import traceback
        traceback.print_exc()

        # Retry with User Exact Params if standard enum failed
        try:
             print("Retrying with 'minutes', '1'...")
             response = history_api_instance.get_intra_day_candle_data(test_key, "minutes", "1")
             print("SUCCESS with user params.")
        except Exception as e2:
             print(f"Retry Error: {e2}")
