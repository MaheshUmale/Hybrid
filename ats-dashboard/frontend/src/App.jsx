import React from 'react';
import Header from './components/Header';
import Dashboard from './components/Dashboard';
import { useWebSocketBuffer } from './hooks/useWebSocketBuffer';
import './index.css';

const App = () => {
  const { latestData } = useWebSocketBuffer('ws://localhost:7070/data');

  return (
    <div className="h-full w-full flex flex-col overflow-hidden bg-[#050505]">
      <Header data={latestData} />
      <main className="flex-grow overflow-hidden">
        <Dashboard />
      </main>
    </div>
  );
}

export default App;
