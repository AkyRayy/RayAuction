package ray.labs.rayauction.gui.holder;

import java.util.UUID;

import ray.labs.rayauction.gui.ViewState;

public final class AuctionBrowserHolder extends MenuHolder {

    private final UUID viewerId;
    private ViewState state;

    public AuctionBrowserHolder(UUID viewerId, ViewState state) {
        this.viewerId = viewerId;
        this.state = state;
    }

    public UUID viewerId() {
        return viewerId;
    }

    public ViewState state() {
        return state;
    }

    public void state(ViewState state) {
        this.state = state;
    }

    @Override
    public String menuId() {
        return "browser";
    }
}
