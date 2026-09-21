package com.assetiq.controllers.v1;

import com.assetiq.dto.CurrencySettingsDto;
import com.assetiq.dto.UpdateBaseCurrencyRequest;
import com.assetiq.services.CurrencySettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant base (reporting) currency. Every money aggregate is converted into this
 * currency. Changing it does not change what the tenant is billed: subscription
 * checkout uses the plan's own currency.
 *
 * <p>Both operations act on the current tenant only; there is deliberately no
 * organisation id in the path.
 */
@RestController
@RequestMapping("/api/v1/currency/settings")
public class CurrencySettingsController {

    private final CurrencySettingsService currencySettingsService;

    public CurrencySettingsController(CurrencySettingsService currencySettingsService) {
        this.currencySettingsService = currencySettingsService;
    }

    /** Current base currency, the currencies with configured rates, and whether the caller may edit. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CurrencySettingsDto> get() {
        boolean canEdit = CurrencySettingsService.canEdit(SecurityContextHolder.getContext().getAuthentication());
        return ResponseEntity.ok(currencySettingsService.get(canEdit));
    }

    /** Change the base currency (ISO-4217). Same authorisation as organisation updates. */
    @PutMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_ORGANIZATION_SETTINGS')")
    public ResponseEntity<CurrencySettingsDto> update(@Valid @RequestBody UpdateBaseCurrencyRequest request) {
        return ResponseEntity.ok(currencySettingsService.updateBaseCurrency(request.baseCurrency()));
    }
}
