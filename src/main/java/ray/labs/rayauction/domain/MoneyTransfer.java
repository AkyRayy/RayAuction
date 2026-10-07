package ray.labs.rayauction.domain;

import java.util.UUID;

public record MoneyTransfer(
        Currency currency,
        UUID buyerId,
        Money withdrawn,
        UUID sellerId,
        Money sellerIncome,
        UUID taxAccountId,
        Money tax) {

    public MoneyTransfer {
        taxAccountId = taxAccountId == null ? sellerId : taxAccountId;
    }

    public boolean hasTax() {
        return tax != null && tax.isPositive();
    }

    public boolean hasTaxAccount() {
        return taxAccountId != null;
    }
}
