package com.example.quant.model;

import java.time.LocalDate;

/**
 * 逐笔交易记录（一次买或卖）。
 */
public class TradeRecord {

    private final LocalDate time;
    private final String direction;   // BUY / SELL
    private final double price;
    private final double quantity;
    private final double commission;
    private final double pnl;         // 本笔平仓时实现的盈亏（买入为 0）
    private final double positionAfter;

    public TradeRecord(LocalDate time, String direction, double price, double quantity,
                       double commission, double pnl, double positionAfter) {
        this.time = time;
        this.direction = direction;
        this.price = price;
        this.quantity = quantity;
        this.commission = commission;
        this.pnl = pnl;
        this.positionAfter = positionAfter;
    }

    public LocalDate getTime() { return time; }
    public String getDirection() { return direction; }
    public double getPrice() { return price; }
    public double getQuantity() { return quantity; }
    public double getCommission() { return commission; }
    public double getPnl() { return pnl; }
    public double getPositionAfter() { return positionAfter; }
}
