import React from 'react';
import { Activity, ShieldCheck, Target } from 'lucide-react';

const ActiveTradesWidget = ({ data }) => {
  const trades = data?.active_trades || [];

  return (
    <div className="flex flex-col h-full bg-[#050505] text-white">
      {/* Header */}
      <div className="bg-[#111111] border-b border-gray-800 p-6 flex justify-between items-center sticky top-0 z-20">
        <div className="flex items-center gap-4">
          <Activity className="text-blue-400 w-8 h-8" />
          <h1 className="text-3xl font-black tracking-tighter uppercase italic">Executions</h1>
        </div>
        <div className="bg-black px-4 py-1.5 rounded-lg border border-gray-800">
          <span className="text-green-400 font-bold text-2xl font-mono">{trades.length}</span>
        </div>
      </div>

      {/* List */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {trades.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center opacity-20">
            <ShieldCheck className="w-24 h-24 mb-4" />
            <span className="text-xl font-mono uppercase tracking-widest">No Active Positions</span>
          </div>
        ) : (
          trades.map((trade, idx) => {
            const isProfit = trade.pnl >= 0;
            const pnlColor = isProfit ? 'text-green-400' : 'text-red-400';
            const borderColor = isProfit ? 'border-green-500/20' : 'border-red-500/20';

            return (
              <div key={idx} className={`flex items-center justify-between p-6 rounded-2xl border-2 ${borderColor} bg-black shadow-2xl`}>
                <div className="flex items-center gap-6">
                  <div className="bg-[#0a0a0a] p-4 rounded-2xl border border-gray-800">
                    <Target className="text-blue-400 w-8 h-8" />
                  </div>
                  <div>
                    <div className="text-4xl font-black tracking-tighter uppercase leading-none mb-1">
                      {trade.symbol.includes('|') ? trade.symbol.split('|')[1] : trade.symbol}
                    </div>
                    <div className="text-xs font-bold text-gray-500 tracking-widest uppercase">
                      Qty: {trade.qty} | EP: {trade.entry.toFixed(2)}
                    </div>
                  </div>
                </div>

                <div className="text-right">
                  <div className="text-gray-600 text-[10px] font-bold uppercase tracking-widest mb-1">Unrealized PNL</div>
                  <div className={`text-4xl font-mono font-bold tracking-tighter ${pnlColor}`}>
                    {isProfit ? '+' : ''}{trade.pnl.toFixed(2)}
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Footer / Strategy Info */}
      <div className="bg-black border-t border-gray-800 p-4 text-center">
        <span className="text-gray-600 text-[10px] font-bold uppercase tracking-widest">Global Risk Guard Active</span>
      </div>
    </div>
  );
};

export default ActiveTradesWidget;
