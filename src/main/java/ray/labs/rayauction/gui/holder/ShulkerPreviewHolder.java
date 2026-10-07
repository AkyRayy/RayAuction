package ray.labs.rayauction.gui.holder;

public final class ShulkerPreviewHolder extends MenuHolder {

    private final long auctionId;

    public ShulkerPreviewHolder(long auctionId) {
        this.auctionId = auctionId;
    }

    public long auctionId() {
        return auctionId;
    }

    @Override
    public String menuId() {
        return "preview";
    }
}
