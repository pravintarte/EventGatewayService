package com.cs.eventgateway.dto.event;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Supported transaction event types.
 */
@Schema(description = "Supported transaction event types.")
public enum EventType {

    /**
     * Adds funds to the target account balance.
     */
    CREDIT,

    /**
     * Removes funds from the target account balance.
     */
    DEBIT
}
