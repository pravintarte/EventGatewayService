package com.cs.eventgateway.controller;

import com.cs.eventgateway.client.AccountServiceClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read-through account endpoints backed by Account Service.
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Account Service read-through endpoints")
public class AccountController {

    private final AccountServiceClient accountServiceClient;

    /**
     * Gets the current balance for an account from Account Service.
     *
     * @param accountId account id path variable
     * @return Account Service response body and status
     */
    @Operation(
            summary = "Get account balance",
            description = "Proxies the account balance lookup to Account Service."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Account balance returned by Account Service.",
                    content = @Content(schema = @Schema(type = "object"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Account Service did not find the account.",
                    content = @Content(schema = @Schema(type = "object"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "Account Service is unavailable.",
                    content = @Content(schema = @Schema(type = "object"))
            )
    })
    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<String> getBalance(
            @Parameter(
                    name = "accountId",
                    description = "Account id whose balance should be returned.",
                    example = "acct-123",
                    required = true,
                    in = ParameterIn.PATH
            )
            @PathVariable @NotBlank String accountId
    ) {
        log.info("Received account balance request accountId={}", accountId);
        ResponseEntity<String> response = accountServiceClient.getBalance(accountId);
        log.info("Completed account balance request accountId={} status={}", accountId, response.getStatusCode());
        return response;
    }

    /**
     * Gets account details and recent transactions from Account Service.
     *
     * @param accountId account id path variable
     * @return Account Service response body and status
     */
    @Operation(
            summary = "Get account details",
            description = "Proxies the account detail lookup to Account Service."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Account details returned by Account Service.",
                    content = @Content(schema = @Schema(type = "object"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Account Service did not find the account.",
                    content = @Content(schema = @Schema(type = "object"))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "Account Service is unavailable.",
                    content = @Content(schema = @Schema(type = "object"))
            )
    })
    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<String> getAccount(
            @Parameter(
                    name = "accountId",
                    description = "Account id whose details should be returned.",
                    example = "acct-123",
                    required = true,
                    in = ParameterIn.PATH
            )
            @PathVariable @NotBlank String accountId
    ) {
        log.info("Received account detail request accountId={}", accountId);
        ResponseEntity<String> response = accountServiceClient.getAccount(accountId);
        log.info("Completed account detail request accountId={} status={}", accountId, response.getStatusCode());
        return response;
    }
}
