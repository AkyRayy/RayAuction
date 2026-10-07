package ray.labs.rayauction.sync;

public interface SyncService {

    void start();

    void stop();

    boolean isActive();

    void publish(SyncEvent event);

    void addListener(SyncListener listener);

    interface SyncListener {

        void onRemoteChange(SyncEvent event);
    }
}
