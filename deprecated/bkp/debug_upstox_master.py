import requests
import gzip
import io
import pandas as pd

url = "https://assets.upstox.com/market-quote/instruments/exchange/NSE.json.gz"
print("Downloading...")
response = requests.get(url)
with gzip.GzipFile(fileobj=io.BytesIO(response.content)) as f:
    df = pd.read_json(f)

print("Columns:", df.columns)
print("Unique Instrument Types:", df['instrument_type'].unique())
print("Unique Segments:", df['segment'].unique())

print("-" * 20)
print("Sample Reliance Row:")
print(df[df['trading_symbol'] == 'RELIANCE'].head(1))
