package org.bank.account.model;

import org.bank.common.dto.enums.AccountType;

import java.math.BigDecimal;
import java.util.UUID;

/** Immutable snapshot of an {@link AccountProduct}. This, never the entity, is what gets cached and passed around. */
public record ProductView(UUID id, String code, String name, AccountType accountType, BigDecimal minBalance, boolean active) {

    public static ProductView from(AccountProduct product) {
        return new ProductView(product.getId(), product.getCode(), product.getName(), product.getAccountType(),
                product.getMinBalance(), product.isActive());
    }
}
