package ray.labs.rayauction.storage;

import java.sql.Connection;

public interface Connections {

    Connection acquire();

    void release(Connection connection);
}
