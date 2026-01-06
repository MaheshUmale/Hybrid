import React, { useEffect, useRef } from 'react';
import { createChart, ColorType } from 'lightweight-charts';

const TradePanel = ({ data }) => {
  const chartContainerRef = useRef();
  const chartRef = useRef();
  const seriesRef = useRef();

  // Chart setup
  useEffect(() => {
    if (chartContainerRef.current) {
      const chart = createChart(chartContainerRef.current, {
        layout: {
          background: { type: ColorType.Solid, color: '#1C212E' },
          textColor: '#8B949E',
        },
        grid: {
          vertLines: { visible: false },
          horzLines: { color: '#1C212E' },
        },
        rightPriceScale: {
          borderColor: '#30363D',
          scaleMargins: { top: 0.2, bottom: 0.1 },
        },
        timeScale: {
          visible: false,
        },
        crosshair: {
          mode: 0,
        },
      });

      const series = chart.addCandlestickSeries({
        upColor: '#26A69A',
        downColor: '#EF5350',
        borderDownColor: '#EF5350',
        borderUpColor: '#26A69A',
        wickDownColor: '#EF5350',
        wickUpColor: '#26A69A',
      });

      chart.timeScale().fitContent();

      chartRef.current = chart;
      seriesRef.current = series;

      return () => {
        chart.remove();
      };
    }
  }, []);

  // Data update
  useEffect(() => {
    if (seriesRef.current && data?.timestamp && data?.ohlc) {
      seriesRef.current.update({
        time: data.timestamp / 1000,
        ...data.ohlc
      });
    }
  }, [data]);

  const thetaGcr = data?.theta_gcr || 1.22;
  const thetaColor = thetaGcr > 1 ? 'text-green-400' : 'text-red-400';


  return (
    <div className="bg-[#1C212E] p-4 rounded-lg shadow-lg h-full flex flex-col">
      <div className="flex justify-between items-center">
        <h2 className="text-xl font-bold">Trade Panel</h2>
        <span className="text-sm text-gray-400">Strike: @ 24650 CE (Prelim)</span>
      </div>
      <div className="flex-grow my-2" ref={chartContainerRef} />
      <div className="flex justify-between items-center">
        <div className="text-sm">
          <span className="text-gray-400">Theta-GCR: </span>
          <span className={`font-mono font-bold ${thetaColor}`}>{thetaGcr.toFixed(2)}</span>
        </div>
        <div className="flex space-x-2">
          <button className="bg-green-600 hover:bg-green-500 text-white font-bold py-2 px-8 rounded-md transition-colors">
            BUY
          </button>
          <button className="bg-red-600 hover:bg-red-500 text-white font-bold py-2 px-8 rounded-md transition-colors">
            SELL
          </button>
        </div>
      </div>
    </div>
  );
};

export default TradePanel;
