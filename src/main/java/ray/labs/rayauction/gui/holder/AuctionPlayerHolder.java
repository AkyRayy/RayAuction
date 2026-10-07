package ray.labs.rayauction.gui.holder;

import java.util.UUID;

import ray.labs.rayauction.gui.ViewState;

public final class AuctionPlayerHolder extends MenuHolder {

    private final UUID targetId;
    private final String targetName;
    private ViewState state;

    public AuctionPlayerHolder(UUID targetId, String targetName, ViewState state) {
        this.targetId = targetId;
        this.targetName = targetName;
        this.state = state;
    }

    public UUID targetId() {
        return targetId;
    }

    public String targetName() {
        return targetName;
    }

    public ViewState state() {
        return state;
    }

    public void state(ViewState state) {
        this.state = state;
    }

    @Override
    public String menuId() {
        return "player";
    }
}
