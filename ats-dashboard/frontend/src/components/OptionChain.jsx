import React from 'react';

const OptionChain = ({ data }) => {
  const isCallFocus = data?.auction_state === 'INITIATIVE_BUY';
  const isPutFocus = data?.auction_state === 'INITIATIVE_SELL';

  // Group options by strike price
  const strikes = (data?.option_window || []).reduce((acc, option) => {
    acc[option.strike] = acc[option.strike] || {};
    acc[option.strike][option.type] = option;
    return acc;
  }, {});

  const sortedStrikes = Object.keys(strikes).sort((a, b) => a - b);

  const getRowStyle = (strike) => {
    const isATM = Math.abs(strike - data?.spot) < 50; // Simple ATM logic
    let style = 'border-b border-gray-800';
    if(isATM) {
      if (isCallFocus) style += ' signal-buy';
      if (isPutFocus) style += ' signal-sell';
    }
    return style;
  };

  const getCellStyle = (type) => {
     if ((type === 'CE' && isPutFocus) || (type === 'PE' && isCallFocus)) {
      return 'opacity-30 filter grayscale';
    }
    return 'opacity-100';
  }

  return (
    <div className="bg-[#1C212E] p-4 rounded-lg shadow-lg h-full">
      <h2 className="text-xl font-bold mb-4">Dynamic Option Chain (ATM &gt; 2)</h2>
      <table className="w-full text-sm text-center font-mono">
        <thead className="text-gray-400">
          <tr>
            <th className="py-2 text-left">Call OI</th>
            <th className="py-2">Call Chg (%)</th>
            <th className="py-2 bg-gray-900">Strike</th>
            <th className="py-2">Put Chg (%)</th>
            <th className="py-2 text-right">Put OI</th>
          </tr>
        </thead>
        <tbody>
          {sortedStrikes.map((strike) => {
            const call = strikes[strike].CE;
            const put = strikes[strike].PE;
            return (
              <tr key={strike} className={getRowStyle(strike)}>
                {/* CALLS */}
                <td className={`py-2 text-left transition-opacity duration-300 ${getCellStyle('CE')}`}>{strikes[strike]?.CE?.oi?.toLocaleString() || '-'}</td>
                <td className={`py-2 transition-opacity duration-300 ${getCellStyle('CE')}`}>{strikes[strike]?.CE && typeof strikes[strike]?.CE?.oi_chg === 'number' ? `${strikes[strike]?.CE.oi_chg > 0 ? '+' : ''}${strikes[strike]?.CE.oi_chg.toFixed(1)}` : '-'}</td>

                {/* STRIKE */}
                <td className="py-2 bg-gray-900 font-bold text-lg">{strike}</td>

                {/* PUTS */}
                <td className={`py-2 transition-opacity duration-300 ${getCellStyle('PE')}`}>{strikes[strike]?.PE && typeof strikes[strike]?.PE?.oi_chg === 'number' ? `${strikes[strike]?.PE.oi_chg > 0 ? '+' : ''}${strikes[strike]?.PE.oi_chg.toFixed(1)}` : '-'}</td>
                <td className={`py-2 text-right transition-opacity duration-300 ${getCellStyle('PE')}`}>{strikes[strike]?.PE?.oi?.toLocaleString() || '-'}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
       <div className="text-center mt-4 text-sm text-gray-400">
          Current PCR: <span className="font-mono text-white">{data?.pcr || '0.00'}</span>
      </div>
    </div>
  );
};

export default OptionChain;
