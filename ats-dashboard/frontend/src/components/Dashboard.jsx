import React from 'react';
import { useWebSocketBuffer } from '../hooks/useWebSocketBuffer';
import ScalpingMatrixWidget from './ScalpingMatrixWidget';
import ActiveTradesWidget from './ActiveTradesWidget';

const Dashboard = () => {
  const { getSymbolData, latestData } = useWebSocketBuffer('ws://localhost:7070/data');

  const niftyData = getSymbolData('NSE_INDEX|Nifty 50');

  // If we want signals from ALL symbols, we should probably pass 'latestData' or the whole 'allData' map 
  // to the matrix, but 'niftyData' (which usually drives the tick) contains the signals list 
  // because the backend (DashboardBridge) attaches ALL active signals to every update.
  // So passing 'niftyData' (or any active symbol data) is sufficient.

  return (
    <div className="grid grid-cols-2 gap-6 h-full p-6 bg-[#050505] overflow-hidden">

      {/* LEFT COLUMN: SIGNALS & ALGO SCALPS */}
      <div className="flex flex-col h-full border border-gray-900 rounded-3xl overflow-hidden bg-black shadow-2xl">
        <ScalpingMatrixWidget data={niftyData || latestData} />
      </div>

      {/* RIGHT COLUMN: EXECUTIONS & MANAGEMENT */}
      <div className="flex flex-col h-full border border-gray-900 rounded-3xl overflow-hidden bg-black shadow-2xl">
        <ActiveTradesWidget data={latestData} />
      </div>

    </div>
  );
};

export default Dashboard;
