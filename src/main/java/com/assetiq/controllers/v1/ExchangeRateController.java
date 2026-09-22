package com.assetiq.controllers.v1;

import com.assetiq.dto.ExchangeRateDto;
import com.assetiq.services.ExchangeRateService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exchange-rates")
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    public ExchangeRateController(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','MANAGE_EXCHANGE_RATES')")
    public ResponseEntity<ExchangeRateDto> create(@Valid @RequestBody ExchangeRateDto dto) {
        return ResponseEntity.ok(exchangeRateService.create(dto));
    }

    @GetMapping("/{id}")
    // Reading rates is needed by every user who sees a money figure: the display-
    // currency switcher converts client-side, and a 403 here silently left every
    // amount unconverted for all but a few roles. Rows are tenant-scoped; writes
    // below stay restricted.
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ExchangeRateDto> getById(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(exchangeRateService.getById(id));
        } catch (IllegalArgumentException e) {
            // Stays a 404, now with the reason in the body.
            throw new com.assetiq.exceptions.ResourceNotFoundException(e.getMessage());
        }
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ExchangeRateDto>> listAll() {
        return ResponseEntity.ok(exchangeRateService.listAll());
    }

    /**
     * Convert an amount between two currencies.
     * Example: GET /api/v1/exchange-rates/convert?amount=100&from=USD&to=EUR&asOf=2024-01-15
     */
    @GetMapping("/convert")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BigDecimal> convert(
            @RequestParam BigDecimal amount,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        BigDecimal result = exchangeRateService.convert(amount, from, to, asOf);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN','ROLE_ADMIN','MANAGE_EXCHANGE_RATES')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        exchangeRateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
