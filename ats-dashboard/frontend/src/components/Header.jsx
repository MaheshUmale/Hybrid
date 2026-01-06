import React, { useRef, useEffect, useState } from 'react';
import { Aperture, Clock, Wifi, Database, Activity, LogOut } from 'lucide-react';

const Header = ({ data }) => {
  const prevSpot = useRef(null);
  const [spotColor, setSpotColor] = useState('text-gray-300');

  useEffect(() => {
    if (data && data.spot) {
      if (prevSpot.current !== null) {
        if (data.spot > prevSpot.current) {
          setSpotColor('text-green-400');
        } else if (data.spot < prevSpot.current) {
          setSpotColor('text-red-400');
        }
      }
      prevSpot.current = data.spot;
    }
  }, [data]);

  const spot = data?.spot?.toFixed(2) || '0.00';
  const fut = data?.fut?.toFixed(2) || '0.00';
  const basis = (Number(fut) - Number(spot)).toFixed(2);

  // Format timestamp
  const formattedTime = data?.timestamp
    ? new Date(data.timestamp * 1000).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true, timeZone: 'Asia/Kolkata' })
    : '00:00:00 AM';

  const getLatencyColor = (latency) => {
    if (latency <= 50) return 'text-green-500';
    if (latency <= 100) return 'text-yellow-500';
    return 'text-red-500';
  };

  const getDisruptorColor = (fillRate) => {
    if (fillRate <= 50) return 'text-green-500';
    if (fillRate <= 80) return 'text-yellow-500';
    return 'text-red-500';
  }

  return (
    <div className="bg-[#1C212E] p-2 rounded-lg flex justify-between items-center text-sm font-sans">
      {/* Left Section */}
      <div className="flex items-center space-x-4">
        <div className="flex items-center space-x-2">
          <Aperture className="text-cyan-400" size={28} />
          <span className="font-bold text-lg text-white">JULES-HF-ATS</span>
        </div>
        <div className="bg-green-500/20 text-green-300 px-2 py-1 rounded-md text-xs font-semibold">
          LIVE
        </div>
      </div>

      {/* Center Section */}
      <div className="flex items-center space-x-6">
        <div className="flex items-baseline space-x-2">
           <span className="text-gray-400 text-lg">NIFTY 50</span>
           <span className={`font-mono text-3xl font-bold transition-colors duration-200 ${spotColor}`}>{spot}</span>
        </div>
        <div className="font-mono text-xs">
          <p className="text-gray-400">FUT: {fut} ({basis})</p>
        </div>
        <div className="flex items-center space-x-2 text-gray-400">
          <Clock size={16} />
          <span>{formattedTime}</span>
        </div>
      </div>

      {/* Right Section */}
      <div className="flex items-center space-x-6">
        <div className="flex items-center space-x-2 text-xs">
           <Wifi size={16} className={getLatencyColor(data?.wssLatency)} />
           <span className="text-gray-400">WSS:</span>
           <span className="font-mono text-white">{data?.wssLatency || 0}ms</span>
        </div>
        <div className="flex items-center space-x-2 text-xs">
           <Database size={16} className={getLatencyColor(data?.questDbLag)} />
           <span className="text-gray-400">QuestDB:</span>
           <span className="font-mono text-white">{data?.questDbLag || 0}ms</span>
        </div>
        <div className="flex items-center space-x-2 text-xs">
           <Activity size={16} className={getDisruptorColor(data?.disruptor)} />
           <span className="text-gray-400">Disruptor:</span>
           <span className="font-mono text-white">{data?.disruptor || 0}%</span>
        </div>
        <div className="flex items-center space-x-4">
          <span className="text-gray-300">{data?.username || 'Guest'}</span>
          <button className="bg-gray-700 hover:bg-red-600 p-2 rounded-md">
            <LogOut size={16} className="text-white"/>
          </button>
        </div>
      </div>
    </div>
  );
};

export default Header;
