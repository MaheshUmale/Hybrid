"""
Backfill historical option chain data from Trendlyne SmartOptions API.
This populates a local SQLite database with 1-minute interval historical data.
"""
import requests
import time
import sqlite3
import os
import json
from datetime import datetime, timedelta, date

# ==========================================================================
# 1. DATABASE LAYER (SQLite)
# ==========================================================================
class OptionDatabase:
    def __init__(self, db_path="options_data.db"):
        self.db_path = db_path
        self._init_db()

    def _init_db(self):
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        # Table for Aggregate PCR/OI data
        cursor.execute('''CREATE TABLE IF NOT EXISTS option_aggregates (
                            symbol TEXT, 
                            date TEXT, 
                            timestamp TEXT, 
                            expiry TEXT,
                            call_oi INTEGER,
                            put_oi INTEGER,
                            pcr REAL,
                            PRIMARY KEY (symbol, date, timestamp)
                          )''')
        
        # Table for Per-Strike detailed data
        cursor.execute('''CREATE TABLE IF NOT EXISTS option_chain_details (
                            symbol TEXT, 
                            date TEXT, 
                            timestamp TEXT, 
                            strike REAL,
                            call_oi INTEGER,
                            put_oi INTEGER,
                            call_oi_chg INTEGER,
                            put_oi_chg INTEGER,
                            PRIMARY KEY (symbol, date, timestamp, strike)
                          )''')
        # Table for Market Breadth
        cursor.execute('''CREATE TABLE IF NOT EXISTS market_breadth (
                            date TEXT, 
                            timestamp TEXT, 
                            advances INTEGER,
                            declines INTEGER,
                            unchanged INTEGER,
                            total INTEGER,
                            PRIMARY KEY (date, timestamp)
                          )''')
        # Table for Historical Daily Stats (Context)
        cursor.execute('''CREATE TABLE IF NOT EXISTS pcr_history (
                            symbol TEXT, 
                            date TEXT, 
                            pcr REAL, 
                            call_oi INTEGER,
                            put_oi INTEGER,
                            PRIMARY KEY (symbol, date)
                          )''')
        conn.commit()
        conn.close()

    def save_snapshot(self, symbol, trading_date, timestamp, expiry, aggregates, details):
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        try:
            # Save Aggregates
            cursor.execute("""INSERT OR REPLACE INTO option_aggregates 
                              VALUES (?, ?, ?, ?, ?, ?, ?)""",
                           (symbol, trading_date, timestamp, expiry, 
                            aggregates['call_oi'], aggregates['put_oi'], aggregates['pcr']))
            
            # Save Details
            for strike, d in details.items():
                cursor.execute("""INSERT OR REPLACE INTO option_chain_details 
                                  VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                               (symbol, trading_date, timestamp, float(strike),
                                d['call_oi'], d['put_oi'], d['call_oi_chg'], d['put_oi_chg']))
            
            conn.commit()
        except Exception as e:
            print(f"[DB ERROR] {e}")
            conn.rollback()
        finally:
            conn.close()

    def save_breadth(self, trading_date, timestamp, data):
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        try:
            cursor.execute("""INSERT OR REPLACE INTO market_breadth 
                              VALUES (?, ?, ?, ?, ?, ?)""",
                           (trading_date, timestamp, 
                            data['advances'], data['declines'], data['unchanged'], data['total']))
            conn.commit()
        except Exception as e:
            print(f"[DB ERROR Breadth] {e}")
            conn.rollback()
        finally:
            conn.close()

    def get_latest_breadth(self):
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        cursor.execute("""SELECT * FROM market_breadth 
                          ORDER BY date DESC, timestamp DESC LIMIT 1""")
        row = cursor.fetchone()
        conn.close()
        if row:
            return {
                'date': row[0],
                'timestamp': row[1],
                'advances': row[2],
                'declines': row[3],
                'unchanged': row[4],
                'total': row[5]
            }
        return None

    def get_latest_aggregates(self, symbol):
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        cursor.execute("""SELECT * FROM option_aggregates 
                          WHERE symbol=? 
                          ORDER BY date DESC, timestamp DESC LIMIT 1""", (symbol,))
        row = cursor.fetchone()
        conn.close()
        if row:
            return {
                'symbol': row[0],
                'date': row[1],
                'timestamp': row[2],
                'expiry': row[3],
                'call_oi': row[4],
                'put_oi': row[5],
                'pcr': row[6]
            }
        return None

    def get_latest_chain(self, symbol):
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        # Get latest timestamp
        cursor.execute("""SELECT date, timestamp FROM option_chain_details 
                          WHERE symbol=? 
                          ORDER BY date DESC, timestamp DESC LIMIT 1""", (symbol,))
        last = cursor.fetchone()
        if not last:
            conn.close()
            return []
        
        d, ts = last
        cursor.execute("""SELECT * FROM option_chain_details 
                          WHERE symbol=? AND date=? AND timestamp=?""", (symbol, d, ts))
        rows = cursor.fetchall()
        conn.close()
        
        chain = []
        for r in rows:
            chain.append({
                'strike': r[3],
                'call_oi': r[4],
                'put_oi': r[5],
                'call_oi_chg': r[6],
                'put_oi_chg': r[7]
            })
        return chain

    def save_daily_stats(self, symbol, trading_date, pcr, call_oi, put_oi):
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        try:
            cursor.execute("""INSERT OR REPLACE INTO pcr_history 
                              VALUES (?, ?, ?, ?, ?)""",
                           (symbol, trading_date, pcr, call_oi, put_oi))
            conn.commit()
        except Exception as e:
            print(f"[DB ERROR Stats] {e}")
        finally:
            conn.close()

    def get_pcr_history(self, symbol, days=30):
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        cursor.execute("""SELECT * FROM pcr_history 
                          WHERE symbol=? 
                          ORDER BY date DESC LIMIT ?""", (symbol, days))
        rows = cursor.fetchall()
        conn.close()
        return rows

# Keep a cache to avoid repeated API calls
STOCK_ID_CACHE = {}
DB = OptionDatabase()

def get_stock_id_for_symbol(symbol):
    """Automatically lookup Trendlyne stock ID for a given symbol"""
    if symbol in STOCK_ID_CACHE:
        return STOCK_ID_CACHE[symbol]
    
    search_url = "https://smartoptions.trendlyne.com/phoenix/api/search-contract-stock/"
    params = {'query': symbol.lower()}
    
    try:
        response = requests.get(search_url, params=params, timeout=10)
        response.raise_for_status()
        data = response.json()
        
        if data and 'body' in data and 'data' in data['body'] and len(data['body']['data']) > 0:
            # Match strictly or take first
            for item in data['body']['data']:
                if item.get('stock_code', '').upper() == symbol.upper():
                    stock_id = item['stock_id']
                    STOCK_ID_CACHE[symbol] = stock_id
                    return stock_id
            
            stock_id = data['body']['data'][0]['stock_id']
            STOCK_ID_CACHE[symbol] = stock_id
            return stock_id
        return None
    except Exception as e:
        print(f"[ERROR] Stock Lookup {symbol}: {e}")
        return None

def backfill_from_trendlyne(symbol, stock_id, expiry_date_str, timestamp_snapshot):
    """Fetch and save historical OI data from Trendlyne for a specific timestamp snapshot"""
    
    url = f"https://smartoptions.trendlyne.com/phoenix/api/live-oi-data/"
    params = {
        'stockId': stock_id,
        'expDateList': expiry_date_str,
        'minTime': "09:15",
        'maxTime': timestamp_snapshot 
    }
    
    try:
        response = requests.get(url, params=params, timeout=10)
        response.raise_for_status()
        data = response.json()
        
        if data['head']['status'] != '0':
            return False
        
        body = data['body']
        oi_data = body.get('oiData', {})
        input_data = body.get('inputData', {})

        trading_date = input_data.get('tradingDate', date.today().strftime("%Y-%m-%d"))
        expiry = input_data.get('expDateList', [expiry_date_str])[0]
        
        total_call_oi = 0
        total_put_oi = 0
        details = {}

        for strike_str, strike_data in oi_data.items():
            c_oi = int(strike_data.get('callOi', 0))
            p_oi = int(strike_data.get('putOi', 0))
            total_call_oi += c_oi
            total_put_oi += p_oi
            
            details[strike_str] = {
                'call_oi': c_oi,
                'put_oi': p_oi,
                'call_oi_chg': int(strike_data.get('callOiChange', 0)),
                'put_oi_chg': int(strike_data.get('putOiChange', 0))
            }

        pcr = round(total_put_oi / total_call_oi, 2) if total_call_oi > 0 else 1.0
        
        aggregates = {
            'call_oi': total_call_oi,
            'put_oi': total_put_oi,
            'pcr': pcr
        }

        DB.save_snapshot(symbol, trading_date, timestamp_snapshot, expiry, aggregates, details)
        return True

    except Exception as e:
        print(f"[ERROR] Fetch {symbol} @ {timestamp_snapshot}: {e}")
        return False

def generate_time_intervals(start_time="09:15", end_time="15:30", interval_minutes=1):
    """Generate time strings in HH:MM format with 1-minute default"""
    start = datetime.strptime(start_time, "%H:%M")
    end = datetime.strptime(end_time, "%H:%M")
    current = start
    times = []
    while current <= end:
        times.append(current.strftime("%H:%M"))
        current += timedelta(minutes=interval_minutes)
    return times

def run_backfill(symbols_list=None):
    if not symbols_list:
        symbols_list = ["NIFTY", "BANKNIFTY", "RELIANCE", "SBIN", "HDFCBANK"]

    print("=" * 60)
    print(f"STARTING TRENDLYNE BACKFILL (1-MIN INTERVALS)")
    print("=" * 60)

    now = datetime.now()
    market_open = now.replace(hour=9, minute=15, second=0, microsecond=0)
    market_close = now.replace(hour=15, minute=30, second=0, microsecond=0)
    
    if now < market_open:
        end_time_str = "15:30" # Assume yesterday or full day backfill loop
    elif now > market_close:
        end_time_str = "15:30"
    else:
        end_time_str = now.strftime("%H:%M")

    time_slots = generate_time_intervals(end_time=end_time_str)
    print(f"Time Slots: {len(time_slots)} | Symbols: {len(symbols_list)}")

    for symbol in symbols_list:
        stock_id = get_stock_id_for_symbol(symbol)
        if not stock_id:
            print(f"[SKIP] No Stock ID for {symbol}")
            continue

        try:
            # Fetch Expiry
            expiry_url = f"https://smartoptions.trendlyne.com/phoenix/api/fno/get-expiry-dates/?mtype=options&stock_id={stock_id}"
            resp = requests.get(expiry_url, timeout=10)
            expiry_list = resp.json().get('body', {}).get('expiryDates', [])
            if not expiry_list:
                print(f"[SKIP] No Expiry for {symbol}")
                continue
            
            nearest_expiry = expiry_list[0]
            print(f"Syncing {symbol} | Expiry: {nearest_expiry}...")

            success_count = 0
            for ts in time_slots:
                if backfill_from_trendlyne(symbol, stock_id, nearest_expiry, ts):
                    success_count += 1
                
                # Sleep briefly to avoid rate limits
                if success_count % 10 == 0:
                    time.sleep(0.1)

            print(f"[OK] {symbol}: Captured {success_count}/{len(time_slots)} points")
        except Exception as e:
            print(f"[FAIL] {symbol}: {e}")

if __name__ == "__main__":
    # You can pass specific symbols or let it use defaults
    # For now, let's target Nifty and BankNifty
    target_symbols = ["NIFTY", "BANKNIFTY", "RELIANCE"]
    run_backfill(target_symbols)
    print("\n[DB PATH]:", os.path.abspath("options_data.db"))

