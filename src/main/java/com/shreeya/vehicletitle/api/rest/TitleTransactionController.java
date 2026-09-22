package com.shreeya.vehicletitle.api.rest;

import com.shreeya.vehicletitle.application.TitleTransactionService;
import com.shreeya.vehicletitle.domain.SubmissionChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/title-transactions")
@SecurityRequirement(name = "apiKey")
public class TitleTransactionController {

    private final TitleTransactionService service;

    public TitleTransactionController(TitleTransactionService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Submit a title transaction with idempotent retry protection")
    public ResponseEntity<SubmissionResponse> submit(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SubmitTransactionRequest request,
            Authentication authentication) {
        var result = service.submit(idempotencyKey, request.toCommand(), SubmissionChannel.REST,
                authentication.getName());
        SubmissionResponse body = new SubmissionResponse(
                TransactionResponse.from(result.transaction()), result.idempotentReplay());
        if (result.idempotentReplay()) {
            return ResponseEntity.ok(body);
        }
        URI location = URI.create("/api/v1/title-transactions/" + result.transaction().getId());
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get a title transaction")
    public TransactionResponse get(@PathVariable UUID transactionId) {
        return TransactionResponse.from(service.get(transactionId));
    }

    @GetMapping
    @Operation(summary = "Find title transactions by VIN")
    public List<TransactionResponse> findByVin(@RequestParam String vin) {
        return service.findByVin(vin).stream().map(TransactionResponse::from).toList();
    }

    @PatchMapping("/{transactionId}/status")
    @Operation(summary = "Advance a title transaction through its controlled lifecycle")
    public TransactionResponse updateStatus(@PathVariable UUID transactionId,
                                            @Valid @RequestBody StatusUpdateRequest request,
                                            Authentication authentication) {
        return TransactionResponse.from(service.updateStatus(transactionId, request.toCommand(),
                SubmissionChannel.REST, authentication.getName()));
    }

    @GetMapping("/{transactionId}/audit-events")
    @Operation(summary = "Read the immutable audit trail for a title transaction")
    public List<AuditEventResponse> auditTrail(@PathVariable UUID transactionId) {
        return service.auditTrail(transactionId).stream().map(AuditEventResponse::from).toList();
    }
}
