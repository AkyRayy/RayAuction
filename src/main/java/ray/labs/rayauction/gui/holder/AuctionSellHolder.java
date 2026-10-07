package ray.labs.rayauction.gui.holder;

public final class AuctionSellHolder extends MenuHolder {

    private final String sessionId;

    public AuctionSellHolder(String sessionId) {
        this.sessionId = sessionId;
    }

    public String sessionId() {
        return sessionId;
    }

    @Override
    public String menuId() {
        return "sell";
    }
}
