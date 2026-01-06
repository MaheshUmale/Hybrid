import React, { useEffect, useRef } from 'react';
import { createChart } from 'lightweight-charts';
import { CheckCircle, XCircle, AlertTriangle } from 'lucide-react';


const SentimentWidget = ({ data }) => {
  const chartContainerRef = useRef();
  const chartRef = useRef();
  const seriesRef = useRef();

  // Chart setup
  useEffect(() => {
    if (chartContainerRef.current) {
      chartRef.current = createChart(chartContainerRef.current, {
        layout: { background: { color: 'transparent' }, textColor: '#8B949E' },
        grid: { vertLines: { visible: false }, horzLines: { visible: false } },
        rightPriceScale: { visible: false },
        timeScale: { visible: false },
        crosshair: { mode: 0 },
        handleScroll: false,
        handleScale: false,
      });
      seriesRef.current = chartRef.current.addAreaSeries({
        lineColor: '#2962FF',
        topColor: 'rgba(41, 98, 255, 0.4)',
        bottomColor: 'rgba(41, 98, 255, 0)',
        lineWidth: 2,
      });
      chartRef.current.timeScale().fitContent();
      return () => chartRef.current.remove();
    }
  }, []);

  // Data update
  useEffect(() => {
    if (seriesRef.current && data?.timestamp && data?.pcr) {
      seriesRef.current.update({ time: data.timestamp / 1000, value: data.pcr });
    }
  }, [data]);

  const auctionState = data?.auction_state || 'ROTATION';
  const stateColor = auctionState === 'INITIATIVE_BUY' ? 'text-green-400' : auctionState === 'INITIATIVE_SELL' ? 'text-red-400' : 'text-yellow-400';
  const alertText = auctionState.replace('_', ' ') + ' Setup';


  return (
    <div className="bg-[#1C212E] p-4 rounded-lg shadow-lg h-full flex flex-col justify-between">
      <h2 className="text-xl font-bold mb-2">Sentiment & Triggers</h2>

      <div className="flex items-stretch space-x-4">
        {/* PCR Trend */}
        <div className="w-1/2 flex flex-col">
           <p className="text-sm text-gray-400">PCR Trend (5min):</p>
           <div className="flex-grow" ref={chartContainerRef} />
           <p className="text-center font-mono text-lg">{data?.pcr?.toFixed(2)}</p>
        </div>

        {/* Key Alerts */}
        <div className="w-1/2 text-sm space-y-2">
            <p className="font-bold">Key Alerts (Live):</p>
            {(data?.alerts || []).map((alert, index) => (
              <div key={index} className="flex items-center space-x-2">
                {alert.type === 'success' && <CheckCircle size={16} className="text-green-500" />}
                {alert.type === 'warning' && <AlertTriangle size={16} className="text-yellow-500" />}
                {alert.type === 'error' && <XCircle size={16} className="text-red-500" />}
                <span>{alert.message}</span>
              </div>
            ))}
        </div>
      </div>

      {/* Auction State */}
      <div className="mt-4 text-center">
        <p className="text-gray-400 text-sm">Auction State</p>
        <div className={`mt-1 border ${stateColor.replace('text', 'border')} ${stateColor.replace('text', 'bg')}/10 p-2 rounded-md`}>
          <p className={`font-bold text-lg ${stateColor}`}>ALERT: {alertText}</p>
        </div>
      </div>

    </div>
  );
};

export default SentimentWidget;
