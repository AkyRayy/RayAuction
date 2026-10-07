package ray.labs.rayauction.gui.holder;

public final class MailboxHolder extends MenuHolder {

    private final int page;

    public MailboxHolder(int page) {
        this.page = Math.max(0, page);
    }

    public int page() {
        return page;
    }

    @Override
    public String menuId() {
        return "mailbox";
    }
}
