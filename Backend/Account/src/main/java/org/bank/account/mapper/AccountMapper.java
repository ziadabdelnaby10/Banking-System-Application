package org.bank.account.mapper;

import org.bank.account.model.Account;
import org.bank.account.model.AccountStatusHistory;
import org.bank.account.model.ProductView;
import org.bank.common.dto.response.AccountBalanceResponse;
import org.bank.common.dto.response.AccountProductResponse;
import org.bank.common.dto.response.AccountResponse;
import org.bank.common.dto.response.AccountStatusHistoryResponse;
import org.bank.common.dto.response.AccountSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Entity to wire-format mapping. Product attributes come from the cached {@link ProductView}, so mapping a
 * page of accounts never touches the database.
 */
@Mapper(config = MapStructConfig.class)
public interface AccountMapper {

    @Mapping(target = "id", source = "account.id")
    @Mapping(target = "productCode", source = "product.code")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "accountType", source = "product.accountType")
    AccountResponse toResponse(Account account, ProductView product);

    @Mapping(target = "id", source = "account.id")
    @Mapping(target = "productCode", source = "product.code")
    @Mapping(target = "accountType", source = "product.accountType")
    AccountSummaryResponse toSummary(Account account, ProductView product);

    @Mapping(target = "accountId", source = "id")
    @Mapping(target = "asOf", source = "balanceAsOf")
    AccountBalanceResponse toBalance(Account account);

    AccountStatusHistoryResponse toHistory(AccountStatusHistory entry);

    AccountProductResponse toProduct(ProductView product);
}
