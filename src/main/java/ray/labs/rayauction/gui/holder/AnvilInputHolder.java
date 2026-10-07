package ray.labs.rayauction.gui.holder;

public final class AnvilInputHolder extends MenuHolder {

    public enum Purpose {
        PRICE
    }

    private final Purpose purpose;
    private final String sessionId;

    public AnvilInputHolder(Purpose purpose, String sessionId) {
        this.purpose = purpose;
        this.sessionId = sessionId;
    }

    public Purpose purpose() {
        return purpose;
    }

    public String sessionId() {
        return sessionId;
    }

    @Override
    public String menuId() {
        return "anvil";
    }
}
