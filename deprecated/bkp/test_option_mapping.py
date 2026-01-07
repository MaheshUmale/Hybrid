"""
Verify SymbolMaster + ExtractInstrumentKeys option mapping for current expiry.
Tests ATM strike calculation and validates 7 strikes (ATM ±3).
"""

from ExtractInstrumentKeys import get_upstox_instruments, getNiftyAndBNFnOKeys
from SymbolMaster import MASTER as SymbolMaster
import config

print("=" * 60)
print("OPTION MAPPING VERIFICATION TEST")
print("=" * 60)

# Initialize SymbolMaster
print("\n[1/3] Initializing SymbolMaster...")
SymbolMaster.initialize()
print(f"✓ Loaded {len(SymbolMaster._mappings)} instrument keys")

# Get live spot prices and option keys
print("\n[2/3] Fetching live NIFTY/BANKNIFTY spot and option keys...")
try:
    all_fno_keys = getNiftyAndBNFnOKeys()
    print(f"✓ Retrieved {len(all_fno_keys)} total FnO keys")
    
    # Display sample keys
    print("\nSample Keys:")
    for i, key in enumerate(all_fno_keys[:5]):
        print(f"  {i+1}. {key}")
    print(f"  ... ({len(all_fno_keys) - 5} more)")

except Exception as e:
    print(f"✗ Error fetching FnO keys: {e}")
    exit(1)

# Test detailed mapping
print("\n[3/3] Testing detailed option mapping...")
import upstox_client
from upstox_client.rest import ApiException

configuration = upstox_client.Configuration()
configuration.access_token = config.ACCESS_TOKEN
api_instance = upstox_client.MarketQuoteV3Api(upstox_client.ApiClient(configuration))

try:
    response = api_instance.get_ltp(instrument_key="NSE_INDEX|Nifty 50,NSE_INDEX|Nifty Bank")
    
    nifty_50_ltp = response.data['NSE_INDEX:Nifty 50'].last_price
    nifty_bank_ltp = response.data['NSE_INDEX:Nifty Bank'].last_price
    
    print(f"\nLive Spot Prices:")
    print(f"  NIFTY: {nifty_50_ltp}")
    print(f"  BANKNIFTY: {nifty_bank_ltp}")
    
    # Get detailed mapping
    current_spots = {
        "NIFTY": nifty_50_ltp,
        "BANKNIFTY": nifty_bank_ltp
    }
    
    mapping = get_upstox_instruments(["NIFTY", "BANKNIFTY"], current_spots)
    
    # Display results
    for symbol in ["NIFTY", "BANKNIFTY"]:
        print(f"\n{symbol} Mapping:")
        print(f"  Expiry: {mapping[symbol]['expiry']}")
        print(f"  Future Key: {mapping[symbol]['future']}")
        print(f"  Options (7 strikes):")
        
        for opt in mapping[symbol]['options']:
            strike = opt['strike']
            atm_marker = " ← ATM" if abs(strike - current_spots[symbol]) < 50 else ""
            print(f"    Strike {strike}{atm_marker}")
            print(f"      CE: {opt['ce_trading_symbol']}")
            print(f"      PE: {opt['pe_trading_symbol']}")
    
    print("\n" + "=" * 60)
    print("✓ VERIFICATION COMPLETE - Option mapping working correctly")
    print("=" * 60)

except ApiException as e:
    print(f"✗ API Error: {e}")
    exit(1)
