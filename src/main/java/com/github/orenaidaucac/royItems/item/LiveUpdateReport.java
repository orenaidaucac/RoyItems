package com.github.orenaidaucac.royItems.item;

/**
 * Report containing real-time statistics of a LiveUpdate operation.
 */
public class LiveUpdateReport {

    private final int inventoryCount;
    private final int enderChestCount;
    private final int failedCount;
    private final int onlinePlayersCount;

    public LiveUpdateReport(int inventoryCount, int enderChestCount, int failedCount, int onlinePlayersCount) {
        this.inventoryCount = inventoryCount;
        this.enderChestCount = enderChestCount;
        this.failedCount = failedCount;
        this.onlinePlayersCount = onlinePlayersCount;
    }

    public int getInventoryCount() {
        return inventoryCount;
    }

    public int getEnderChestCount() {
        return enderChestCount;
    }

    public int getTotalCount() {
        return inventoryCount + enderChestCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public int getOnlinePlayersCount() {
        return onlinePlayersCount;
    }
}
