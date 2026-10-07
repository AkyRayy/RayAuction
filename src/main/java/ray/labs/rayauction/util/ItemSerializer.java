package ray.labs.rayauction.util;

import java.util.Base64;

import org.bukkit.inventory.ItemStack;
import ray.labs.rayauction.storage.StorageException;

public final class ItemSerializer {

    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private ItemSerializer() {}

    public static byte[] toBytes(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            throw new StorageException("cannot serialize air");
        }
        return stack.serializeAsBytes();
    }

    public static ItemStack fromBytes(byte[] data) {
        if (data == null || data.length == 0) {
            throw new StorageException("cannot deserialize an empty item payload");
        }
        return ItemStack.deserializeBytes(data);
    }

    public static String toBase64(byte[] data) {
        return ENCODER.encodeToString(data);
    }

    public static byte[] fromBase64(String value) {
        try {
            return DECODER.decode(value);
        } catch (IllegalArgumentException ex) {
            throw new StorageException("invalid base64 item payload", ex);
        }
    }
}
