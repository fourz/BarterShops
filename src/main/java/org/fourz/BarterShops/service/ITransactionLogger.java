package org.fourz.BarterShops.service;

import org.fourz.BarterShops.data.dto.TradeRecordDTO;

/** Hook called after each trade is persisted. Implementations must be non-blocking. */
public interface ITransactionLogger {
    void log(TradeRecordDTO record);
}
