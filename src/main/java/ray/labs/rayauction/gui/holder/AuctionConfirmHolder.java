package ray.labs.rayauction.gui.holder;

public final class AuctionConfirmHolder extends MenuHolder {

    private final long auctionId;

    public AuctionConfirmHolder(long auctionId) {
        this.auctionId = auctionId;
    }

    public long auctionId() {
        return auctionId;
    }

    @Override
    public String menuId() {
        return "confirm";
    }
}
