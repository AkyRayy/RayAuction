package ray.labs.rayauction.domain;

import java.util.UUID;

public record PlayerLimit(UUID playerId, Integer customLimit) {

    public static final int UNLIMITED = -1;

    public int effective(int defaultLimit) {
        return customLimit == null ? defaultLimit : customLimit;
    }

    public boolean isUnlimited(int defaultLimit) {
        return effective(defaultLimit) == UNLIMITED;
    }

    public static int resolve(Integer customLimit, int defaultLimit, int permissionLimit) {
        int base = customLimit == null ? defaultLimit : customLimit;
        if (base == UNLIMITED || permissionLimit == UNLIMITED) {
            return UNLIMITED;
        }
        return Math.max(base, permissionLimit);
    }
}
