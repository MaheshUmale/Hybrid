package com.trading.hf;

/**
 * An interface for replaying market data from various sources.
 */
public interface IDataReplayer {

    /**
     * Starts the data replay process. Implementations of this method should
     * handle the specifics of connecting to a data source, reading the data,
     * and publishing it to the application's RingBuffer.
     */
    void start();
}
