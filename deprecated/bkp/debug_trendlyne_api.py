import requests
import json

def test_trendlyne_lookup(symbol):
    print(f"Searching for: {symbol}")
    search_url = "https://smartoptions.trendlyne.com/phoenix/api/search-contract-stock/"
    params = {'query': symbol.lower()}
    
    try:
        response = requests.get(search_url, params=params, timeout=10)
        response.raise_for_status()
        data = response.json()
        
        print(f"Status Code: {response.status_code}")
        print("Response Body (first item of data):")
        if data and 'body' in data and 'data' in data['body'] and len(data['body']['data']) > 0:
            first_item = data['body']['data'][0]
            print(json.dumps(first_item, indent=2))
            
            # Check for 'symbol' or other identifying keys
            available_keys = list(first_item.keys())
            print(f"Available Keys: {available_keys}")
        else:
            print("No data found or unexpected structure.")
            print(json.dumps(data, indent=2))
            
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    test_trendlyne_lookup("nifty")
    print("\n" + "="*50 + "\n")
    test_trendlyne_lookup("reliance")
