import json
import gzip
import os

def generate_data(instrument_key="NSE_EQ_TEST", num_ticks=10000, output_file="generated_data.json.gz"):
    """
    Generates a gzipped JSON file with sample market data.
    """
    data = []
    start_price = 100.0
    start_time = 1672531200000

    for i in range(num_ticks):
        price = start_price + (i * 0.1)  # Predictable price increase
        bid_price = price - 0.05
        ask_price = price + 0.05
        timestamp = start_time + (i * 1000)

        tick = {
            "feeds": {
                instrument_key: {
                    "fullFeed": {
                        "marketFF": {
                            "ltpc": {
                                "ltp": price,
                                "ltq": "10",
                                "ltt": str(timestamp),
                                "cp": start_price
                            },
                            "marketLevel": {
                                "bidAskQuote": [
                                    {
                                        "bidP": bid_price,
                                        "askP": ask_price
                                    }
                                ]
                            }
                        }
                    }
                }
            }
        }
        data.append(tick)

    # Write to a gzipped file
    with gzip.open(output_file, "wt", encoding="utf-8") as f:
        json.dump(data, f)

    print(f"Generated {num_ticks} ticks for {instrument_key} in {output_file}")

if __name__ == "__main__":
    output_dir = "ats-core/src/main/resources/data"

    # Create the output directory if it doesn't exist
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    generate_data(output_file=os.path.join(output_dir, "generated_data.json.gz"))
