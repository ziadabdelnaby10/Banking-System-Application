package org.bank.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.bank.account.config.OpenApiConfig;
import org.bank.account.service.AccountService;
import org.bank.common.dto.response.AccountProductResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/account-products")
@Tag(name = "Account products", description = "Read-only product catalogue used when opening accounts.")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class AccountProductController {

    private final AccountService accountService;

    @GetMapping
    @Operation(summary = "List account products",
            description = "Permission: ACCOUNT_VIEW. The catalogue is small and served from memory.")
    @PreAuthorize("hasAuthority('ACCOUNT_VIEW')")
    public List<AccountProductResponse> list() {
        return accountService.listProducts();
    }
}
