import React, { useEffect, useRef, useState } from 'react';
import { createChart } from 'lightweight-charts';

const AuctionWidget = ({ data }) => {
  const chartContainerRef = useRef();
  const chartRef = useRef();
  const seriesRef = useRef();

  useEffect(() => {
    if (chartContainerRef.current) {
      chartRef.current = createChart(chartContainerRef.current, {
        layout: {
          background: { color: '#0A0E17' },
          textColor: '#8B949E',
          fontFamily: "'JetBrains Mono', monospace",
        },
        grid: {
          vertLines: { color: '#1C212E' },
          horzLines: { color: '#1C212E' },
        },
        rightPriceScale: {
          borderColor: '#30363D',
        },
        timeScale: {
          borderColor: '#30363D',
          timeVisible: true,
          secondsVisible: false,
        },
        crosshair: {
          mode: 0, // Magnet
        },
      });

      seriesRef.current = chartRef.current.addLineSeries({
        color: '#2962FF',
        lineWidth: 2,
      });

      const createPriceLine = (price, color, title) => ({
        price,
        color,
        lineWidth: 1,
        lineStyle: 2, // Dashed
        axisLabelVisible: true,
        title,
        axisLabelColor: '#fff',
        axisLabelTextColor: '#000',
      });

      seriesRef.current.createPriceLine(createPriceLine(24580, '#EF5350', 'VAH'));
      seriesRef.current.createPriceLine(createPriceLine(24550, '#FFCA28', 'POC'));
      seriesRef.current.createPriceLine(createPriceLine(24500, '#66BB6A', 'VAL'));

      chartRef.current.timeScale().fitContent();

      return () => {
        chartRef.current.remove();
      };
    }
  }, []);

  const linesRef = useRef({ vah: null, poc: null, val: null });

  useEffect(() => {
    if (seriesRef.current && data?.auctionProfile) {
      // Clear old lines
      if (linesRef.current.vah) seriesRef.current.removePriceLine(linesRef.current.vah);
      if (linesRef.current.poc) seriesRef.current.removePriceLine(linesRef.current.poc);
      if (linesRef.current.val) seriesRef.current.removePriceLine(linesRef.current.val);

      const createPriceLine = (price, color, title) => ({
        price,
        color,
        lineWidth: 1,
        lineStyle: 2,
        axisLabelVisible: true,
        title,
      });

      const { vah, poc, val } = data.auctionProfile;
      if (vah) linesRef.current.vah = seriesRef.current.createPriceLine(createPriceLine(vah, '#EF5350', 'VAH'));
      if (poc) linesRef.current.poc = seriesRef.current.createPriceLine(createPriceLine(poc, '#FFCA28', 'POC'));
      if (val) linesRef.current.val = seriesRef.current.createPriceLine(createPriceLine(val, '#66BB6A', 'VAL'));
    }

    if (seriesRef.current && data?.timestamp && data?.spot) {
      seriesRef.current.update({
        time: data.timestamp / 1000,
        value: data.spot,
      });
    }
  }, [data]);


  return (
    <div className="bg-[#1C212E] p-4 rounded-lg shadow-lg h-full flex flex-col border border-[#30363D]">
      <div className="flex justify-between items-center mb-2">
        <h2 className="text-xl font-bold">{data?.symbol || 'Auction Profile'}</h2>
        <span className="text-xs text-blue-400 font-mono">LIVE</span>
      </div>
      <div className="flex-grow" ref={chartContainerRef} />
      <div className="flex justify-between text-xs pt-2 font-mono">
        <span className="text-gray-400">SPOT: {data?.spot?.toFixed(2)}</span>
        <span className="text-yellow-400">POC: {data?.auctionProfile?.poc?.toFixed(2) || '---'}</span>
      </div>
    </div>
  );
};

export default AuctionWidget;
