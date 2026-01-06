import { useState, useEffect, useRef } from 'react';

export const useWebSocketBuffer = (url) => {
  const [dataMap, setDataMap] = useState({});
  const buffer = useRef([]);
  const socket = useRef(null);
  const frameId = useRef(null);

  useEffect(() => {
    let timeoutId;

    const connect = () => {
      console.log(`Attempting connection to ${url}...`);
      const ws = new WebSocket(url);
      socket.current = ws;

      ws.onopen = () => {
        console.log('WebSocket connected to', url);
      };

      ws.onmessage = (event) => {
        try {
          const receivedData = JSON.parse(event.data);
          buffer.current.push(receivedData);
        } catch (e) {
          console.error('Failed to parse WS data:', e);
        }
      };

      ws.onclose = (event) => {
        console.warn(`WebSocket closed: ${event.code} ${event.reason}. Retrying in 2s...`);
        // Only reconnect if we haven't unmounted
        timeoutId = setTimeout(connect, 2000);
      };

      ws.onerror = (error) => {
        console.error('WebSocket error:', error);
      };
    };

    connect();

    const intervalId = setInterval(() => {
      if (buffer.current.length > 0) {
        setDataMap(prev => {
          const next = { ...prev };
          buffer.current.forEach(msg => {
            if (msg.symbol) {
              next[msg.symbol] = msg;
            }
          });
          buffer.current = [];
          return next;
        });
      }
    }, 1000);

    return () => {
      console.log('Cleaning up WebSocket...');
      if (socket.current) {
        socket.current.onclose = null;
        socket.current.close();
      }
      clearTimeout(timeoutId);
      clearInterval(intervalId);
    };
  }, [url]);

  const getSymbolData = (symbol) => dataMap[symbol] || null;
  const latestData = Object.values(dataMap).sort((a, b) => b.timestamp - a.timestamp)[0] || null;

  return { getSymbolData, latestData, allData: dataMap };
};