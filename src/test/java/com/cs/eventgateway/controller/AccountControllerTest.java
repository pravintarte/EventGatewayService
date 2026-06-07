package com.cs.eventgateway.controller;

import com.cs.eventgateway.client.AccountServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for Account Service read-through controller endpoints.
 */
class AccountControllerTest {

    @Test
    void getBalance_returnsAccountServiceResponse() {
        AccountServiceClient accountServiceClient = mock(AccountServiceClient.class);
        AccountController controller = new AccountController(accountServiceClient);
        ResponseEntity<String> serviceResponse = ResponseEntity.ok("""
                {"accountId":"acct-123","balance":150.00,"currency":"USD"}
                """);

        when(accountServiceClient.getBalance("acct-123")).thenReturn(serviceResponse);

        ResponseEntity<String> response = controller.getBalance("acct-123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"balance\":150.00");
        verify(accountServiceClient).getBalance("acct-123");
    }

    @Test
    void getAccount_returnsAccountServiceResponse() {
        AccountServiceClient accountServiceClient = mock(AccountServiceClient.class);
        AccountController controller = new AccountController(accountServiceClient);
        ResponseEntity<String> serviceResponse = ResponseEntity.ok("""
                {"accountId":"acct-123","recentTransactions":[]}
                """);

        when(accountServiceClient.getAccount("acct-123")).thenReturn(serviceResponse);

        ResponseEntity<String> response = controller.getAccount("acct-123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"recentTransactions\":[]");
        verify(accountServiceClient).getAccount("acct-123");
    }
}
