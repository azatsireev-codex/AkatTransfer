package net.akat.bank.transfer;

import java.time.LocalDateTime;

public class Transaction {
    private String player;
    private double amount;
    private LocalDateTime timestamp;
    private String type;
    private String otherPlayer;
    private String message;
    private boolean read;

    public Transaction(String player, String otherPlayer, double amount, LocalDateTime timestamp, String type, String message, boolean read) {
        this.player = player;
        this.otherPlayer = otherPlayer;
        this.amount = amount;
        this.timestamp = timestamp;
        this.type = type;
        this.message = message;
        this.read = read;
    }

    public String getPlayer() {
        return player;
    }

    public String getOtherPlayer() {
        return otherPlayer;
    }

    public double getAmount() {
        return amount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }
}
