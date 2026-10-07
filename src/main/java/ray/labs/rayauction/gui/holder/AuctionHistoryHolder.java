package ray.labs.rayauction.gui.holder;

import java.util.UUID;

import ray.labs.rayauction.domain.HistoryFilter;

public final class AuctionHistoryHolder extends MenuHolder {

    private final UUID viewerId;
    private HistoryFilter filter;
    private int page;

    public AuctionHistoryHolder(UUID viewerId, HistoryFilter filter, int page) {
        this.viewerId = viewerId;
        this.filter = filter;
        this.page = Math.max(0, page);
    }

    public UUID viewerId() {
        return viewerId;
    }

    public HistoryFilter filter() {
        return filter;
    }

    public void filter(HistoryFilter filter) {
        this.filter = filter;
        this.page = 0;
    }

    public int page() {
        return page;
    }

    public void page(int page) {
        this.page = Math.max(0, page);
    }

    @Override
    public String menuId() {
        return "history";
    }
}
