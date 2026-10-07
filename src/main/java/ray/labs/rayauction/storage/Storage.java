package ray.labs.rayauction.storage;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

public record Storage(
        DataSource dataSource,
        UnitOfWork unitOfWork,
        AuctionRepository auctions,
        TransactionRepository transactions,
        LimitRepository limits,
        MailRepository mail,
        LedgerRepository ledger,
        StatsRepository stats,
        SyncEventRepository syncEvents,
        Logger logger)
        implements AutoCloseable {

    @Override
    public void close() {
        closeQuietly(unitOfWork);
        closeQuietly(auctions);
        closeQuietly(transactions);
        if (dataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ex) {
                logger.log(Level.WARNING, "cannot close the datasource pool", ex);
            }
        }
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ex) {
            logger.log(Level.WARNING, "cannot close a storage component", ex);
        }
    }
}
