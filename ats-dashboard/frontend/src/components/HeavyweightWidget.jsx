import React, { useState, useEffect } from 'react';

const HeavyweightWidget = ({ data }) => {
  const [pulse, setPulse] = useState('');

  // The pulse animation indicates new data has been received.
  useEffect(() => {
    if (data) {
      setPulse('bg-gray-800'); // A subtle flash effect
      const timer = setTimeout(() => setPulse(''), 200);
      return () => clearTimeout(timer);
    }
  }, [data?.weighted_delta]);

  const heavyweights = data?.heavyweights || [
    { name: "RELIANCE", delta: 0, change: 0, qtp: 0 },
    { name: "HDFCBANK", delta: 0, change: 0, qtp: 0 },
    { name: "ICICIBANK", delta: 0, change: 0, qtp: 0 },
    { name: "INFOSYS", delta: 0, change: 0, qtp: 0 },
  ];

  const totalDelta = data?.weighted_delta || 0;
  const totalDeltaColor = totalDelta >= 0 ? 'text-green-400' : 'text-red-400';
  const totalDeltaSign = totalDelta >= 0 ? '+' : '';

  return (
    <div className={`bg-[#1C212E] p-4 rounded-lg shadow-lg transition-colors duration-200 ${pulse}`}>
      <h2 className="text-xl font-bold mb-4">Heavyweights (Nifty 50)</h2>
      <table className="w-full text-sm text-left">
        <thead>
          <tr className="text-gray-400 border-b border-gray-700">
            <th className="py-2">Stock</th>
            <th className="py-2 text-right">Price / % Chg</th>
            <th className="py-2 text-right">QTP</th>
            <th className="py-2 text-right">Weighted Delta</th>
          </tr>
        </thead>
        <tbody>
          {heavyweights.map((stock) => (
             <tr key={stock.name} className="border-b border-gray-800 font-mono">
              <td className="py-2">{stock.name}</td>
              <td className={`py-2 text-right ${stock.delta >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                {stock.change?.toFixed(2) || '0.00'}%
              </td>
              <td className="py-2 text-right">{stock.qtp || '0'}</td>
              <td className={`py-2 text-right ${stock.delta >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                {stock.delta > 0 ? '+' : ''}{stock.delta?.toLocaleString() || '0'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
       <div className="flex justify-between items-center mt-4 text-sm">
        <span className="font-bold">Agg. W. Delta</span>
        <span className={`font-mono font-bold text-lg ${totalDeltaColor}`}>
          {totalDeltaSign}{totalDelta?.toLocaleString()}
        </span>
      </div>
    </div>
  );
};

export default HeavyweightWidget;
