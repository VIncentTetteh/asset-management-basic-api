package com.assetiq.controllers.v1;

import com.assetiq.dto.CurrencySettingsDto;
import com.assetiq.services.CurrencySettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CurrencySettingsControllerTest {

    @Mock
    private CurrencySettingsService currencySettingsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CurrencySettingsController(currencySettingsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void get_returnsSettingsShapeAndDerivesCanEditFromAuthorities() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "admin@example.com", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ORG_ADMIN"))));
        when(currencySettingsService.get(true))
                .thenReturn(new CurrencySettingsDto("GHS", List.of("EUR", "GHS", "USD"), true));

        mockMvc.perform(get("/api/v1/currency/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("GHS"))
                .andExpect(jsonPath("$.availableCurrencies[0]").value("EUR"))
                .andExpect(jsonPath("$.availableCurrencies.length()").value(3))
                .andExpect(jsonPath("$.canEdit").value(true));
    }

    @Test
    void get_reportsReadOnlyForOrdinaryUsers() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "user@example.com", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(currencySettingsService.get(false))
                .thenReturn(new CurrencySettingsDto("GHS", List.of("GHS"), false));

        mockMvc.perform(get("/api/v1/currency/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canEdit").value(false));
    }

    @Test
    void put_updatesAndReturnsSettings() throws Exception {
        when(currencySettingsService.updateBaseCurrency("usd"))
                .thenReturn(new CurrencySettingsDto("USD", List.of("USD"), true));

        mockMvc.perform(put("/api/v1/currency/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseCurrency\":\"usd\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("USD"));
    }

    @Test
    void put_invalidCurrencyIsBadRequest() throws Exception {
        when(currencySettingsService.updateBaseCurrency("XYZ1"))
                .thenThrow(new IllegalArgumentException("Invalid currency code 'XYZ1'"));

        mockMvc.perform(put("/api/v1/currency/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseCurrency\":\"XYZ1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void put_blankCurrencyFailsValidation() throws Exception {
        mockMvc.perform(put("/api/v1/currency/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseCurrency\":\"\"}"))
                .andExpect(status().isBadRequest());
        verify(currencySettingsService, never()).updateBaseCurrency(anyString());
    }

    @Test
    void put_isGuardedLikeOrganisationUpdate() throws Exception {
        String putGuard = CurrencySettingsController.class
                .getMethod("update", com.assetiq.dto.UpdateBaseCurrencyRequest.class)
                .getAnnotation(PreAuthorize.class).value();
        String orgUpdateGuard = java.util.Arrays.stream(OrganisationController.class.getMethods())
                .filter(m -> m.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class))
                .findFirst().orElseThrow()
                .getAnnotation(PreAuthorize.class).value();

        assertThat(putGuard).isEqualTo(orgUpdateGuard);
    }
}
