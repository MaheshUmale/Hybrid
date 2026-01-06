import React, { useMemo } from 'react';
import { TrendingUp, TrendingDown, Activity, Wifi, WifiOff, Zap } from 'lucide-react';

const ScalpingMatrixWidget = ({ data }) => {
    const signals = data?.scalpSignals || [];
    const activeSignals = useMemo(() => signals.filter(s => s.status === 'ACTIVE'), [signals]);

    if (!data) return (
        <div className="h-full flex flex-col items-center justify-center p-8 text-gray-400 animate-pulse bg-[#050505]">
            <WifiOff className="w-16 h-16 mb-4 opacity-50" />
            <span className="text-xl font-mono tracking-tighter">WAITING FOR DATA...</span>
        </div>
    );

    if (activeSignals.length === 0) {
        return (
            <div className="h-full flex flex-col items-center justify-center p-8 bg-[#050505]">
                <div className="relative">
                    <span className="absolute inset-0 rounded-full bg-blue-500/10 animate-ping"></span>
                    <Zap className="w-20 h-20 text-blue-500/50 relative z-10" />
                </div>
                <h2 className="text-2xl font-bold text-gray-400 mt-6 tracking-widest uppercase">Scanning</h2>
                <div className="flex gap-3 mt-8">
                    {[1, 2, 3].map(i => (
                        <div key={i} className="w-2 h-2 rounded-full bg-blue-500/20 animate-pulse" style={{ animationDelay: `${i * 0.2}s` }} />
                    ))}
                </div>
            </div>
        );
    }

    return (
        <div className="flex flex-col h-full bg-[#050505] text-white">
            {/* Header */}
            <div className="bg-[#111111] border-b border-gray-800 p-6 flex justify-between items-center sticky top-0 z-20">
                <div className="flex items-center gap-4">
                    <div className="relative flex h-3 w-3">
                        <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
                        <span className="relative inline-flex rounded-full h-3 w-3 bg-green-500"></span>
                    </div>
                    <h1 className="text-3xl font-black tracking-tighter uppercase italic">Live Signals</h1>
                </div>
                <div className="bg-black px-4 py-1.5 rounded-lg border border-gray-800">
                    <span className="text-blue-400 font-bold text-2xl font-mono">{activeSignals.length}</span>
                </div>
            </div>

            {/* List */}
            <div className="flex-1 overflow-y-auto p-4 space-y-4">
                {activeSignals.map((signal, idx) => {
                    const isLong = signal.gate.includes('_L') || signal.gate.includes('_REC') || signal.gate.includes('REBID');
                    const isShort = signal.gate.includes('_S') || signal.gate.includes('_REJ') || signal.gate.includes('RESET') || signal.gate.includes('STUFF');

                    const themeColor = isLong ? 'text-green-400' : (isShort ? 'text-red-400' : 'text-yellow-400');
                    const borderColor = isLong ? 'border-green-500/30' : (isShort ? 'border-red-500/30' : 'border-gray-800');
                    const Icon = isLong ? TrendingUp : (isShort ? TrendingDown : Activity);

                    const symbolClean = signal.symbol.includes('|') ? signal.symbol.split('|')[1] : signal.symbol;

                    return (
                        <div key={idx} className={`flex items-center justify-between p-6 rounded-2xl border-2 ${borderColor} bg-black shadow-2xl transition-all duration-200`}>

                            <div className="flex items-center gap-6">
                                <div className={`p-4 rounded-2xl bg-[#0a0a0a] border border-gray-800 ${themeColor}`}>
                                    <Icon className="w-10 h-10" />
                                </div>
                                <div>
                                    <div className="text-4xl font-black tracking-tighter leading-none mb-1 uppercase">
                                        {symbolClean}
                                    </div>
                                    <div className={`text-sm font-bold tracking-widest uppercase opacity-80 ${themeColor}`}>
                                        {signal.gate.replace(/_/g, ' ')}
                                    </div>
                                </div>
                            </div>

                            <div className="flex gap-12 items-center">
                                <div className="text-right">
                                    <div className="text-gray-600 text-[10px] font-bold uppercase tracking-widest mb-1">Entry Price</div>
                                    <div className="text-4xl font-mono font-bold text-white tracking-tighter">{signal.entry.toFixed(1)}</div>
                                </div>
                                <div className="text-right">
                                    <div className="text-gray-600 text-[10px] font-bold uppercase tracking-widest mb-1">Target</div>
                                    <div className="text-4xl font-mono font-bold text-green-400 tracking-tighter">{signal.tp.toFixed(1)}</div>
                                </div>
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
};

export default ScalpingMatrixWidget;
