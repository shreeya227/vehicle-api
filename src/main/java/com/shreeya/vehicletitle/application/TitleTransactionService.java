package com.shreeya.vehicletitle.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shreeya.vehicletitle.domain.OutboxEvent;
import com.shreeya.vehicletitle.domain.OwnerDetails;
import com.shreeya.vehicletitle.domain.SubmissionChannel;
import com.shreeya.vehicletitle.domain.TitleTransaction;
import com.shreeya.vehicletitle.domain.TransactionAuditEvent;
import com.shreeya.vehicletitle.domain.TransactionStatus;
import com.shreeya.vehicletitle.infrastructure.persistence.OutboxEventRepository;
import com.shreeya.vehicletitle.infrastructure.persistence.TitleTransactionRepository;
import com.shreeya.vehicletitle.infrastructure.persistence.TransactionAuditRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class TitleTransactionService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,128}");

    private final TitleTransactionRepository transactionRepository;
    private final TransactionAuditRepository auditRepository;
    private final OutboxEventRepository outboxRepository;
    private final RequestFingerprint requestFingerprint;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Clock clock;

    @Autowired
    public TitleTransactionService(TitleTransactionRepository transactionRepository,
                                   TransactionAuditRepository auditRepository,
                                   OutboxEventRepository outboxRepository,
                                   RequestFingerprint requestFingerprint,
                                   ObjectMapper objectMapper,
                                   Validator validator) {
        this(transactionRepository, auditRepository, outboxRepository, requestFingerprint,
                objectMapper, validator, Clock.systemUTC());
    }

    TitleTransactionService(TitleTransactionRepository transactionRepository,
                            TransactionAuditRepository auditRepository,
                            OutboxEventRepository outboxRepository,
                            RequestFingerprint requestFingerprint,
                            ObjectMapper objectMapper,
                            Validator validator,
                            Clock clock) {
        this.transactionRepository = transactionRepository;
        this.auditRepository = auditRepository;
        this.outboxRepository = outboxRepository;
        this.requestFingerprint = requestFingerprint;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.clock = clock;
    }

    @Transactional
    public SubmissionResult submit(String idempotencyKey, SubmitTitleCommand rawCommand,
                                   SubmissionChannel channel, String actor) {
        validateIdempotencyKey(idempotencyKey);
        SubmitTitleCommand command = normalize(rawCommand);
        validate(command);
        String fingerprint = requestFingerprint.calculate(command);

        var existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(fingerprint)) {
                throw new IdempotencyConflictException(idempotencyKey);
            }
            return new SubmissionResult(existing.get(), true);
        }

        Instant now = clock.instant();
        UUID transactionId = UUID.randomUUID();
        OwnerCommand submittedOwner = command.owner();
        OwnerDetails owner = new OwnerDetails(submittedOwner.fullName(), submittedOwner.email(),
                submittedOwner.addressLine1(), submittedOwner.city(), submittedOwner.state(),
                submittedOwner.postalCode());
        TitleTransaction transaction = TitleTransaction.create(transactionId, idempotencyKey,
                fingerprint, command.vin(), command.jurisdiction(), command.transactionType(),
                owner, command.purchasePrice(), now);

        transactionRepository.save(transaction);
        auditRepository.save(new TransactionAuditEvent(transactionId, "TRANSACTION_RECEIVED",
                null, TransactionStatus.RECEIVED, channel, actor,
                "Request accepted through " + channel, now));
        outboxRepository.save(new OutboxEvent(UUID.randomUUID(), transactionId,
                "TITLE_TRANSACTION_RECEIVED", outboxPayload(transaction), now));
        return new SubmissionResult(transaction, false);
    }

    @Transactional(readOnly = true)
    public TitleTransaction get(UUID transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    @Transactional(readOnly = true)
    public List<TitleTransaction> findByVin(String rawVin) {
        String vin = rawVin == null ? "" : rawVin.trim().toUpperCase(Locale.ROOT);
        if (!vin.matches("[A-HJ-NPR-Z0-9]{17}")) {
            throw new InvalidSubmissionException("VIN must contain 17 valid characters");
        }
        return transactionRepository.findByVinOrderByCreatedAtDesc(vin);
    }

    @Transactional(readOnly = true)
    public List<TransactionAuditEvent> auditTrail(UUID transactionId) {
        get(transactionId);
        return auditRepository.findByTransactionIdOrderByOccurredAtAsc(transactionId);
    }

    @Transactional
    public TitleTransaction updateStatus(UUID transactionId, StatusUpdateCommand command,
                                         SubmissionChannel channel, String actor) {
        validate(command);
        TitleTransaction transaction = transactionRepository.findByIdForUpdate(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
        TransactionStatus previous = transaction.getStatus();
        if (previous == command.status()) {
            return transaction;
        }
        if (command.status() == TransactionStatus.SUBMITTED_TO_STATE
                && (command.externalReference() == null || command.externalReference().isBlank())) {
            throw new InvalidSubmissionException(
                    "An external reference is required when submitting a transaction to a state agency");
        }

        Instant now = clock.instant();
        transaction.transitionTo(command.status(), command.externalReference(),
                command.rejectionReason(), now);
        auditRepository.save(new TransactionAuditEvent(transactionId, "STATUS_CHANGED",
                previous, command.status(), channel, actor,
                "Title transaction status updated", now));
        outboxRepository.save(new OutboxEvent(UUID.randomUUID(), transactionId,
                "TITLE_TRANSACTION_STATUS_CHANGED", outboxPayload(transaction), now));
        return transaction;
    }

    private SubmitTitleCommand normalize(SubmitTitleCommand command) {
        if (command == null) {
            throw new InvalidSubmissionException("Request body is required");
        }
        OwnerCommand rawOwner = command.owner();
        OwnerCommand owner = rawOwner == null ? null : new OwnerCommand(
                trim(rawOwner.fullName()), lower(rawOwner.email()), trim(rawOwner.addressLine1()),
                trim(rawOwner.city()), upper(rawOwner.state()), trim(rawOwner.postalCode()));
        return new SubmitTitleCommand(upper(command.vin()), upper(command.jurisdiction()),
                command.transactionType(), owner, command.purchasePrice());
    }

    private void validateIdempotencyKey(String key) {
        if (key == null || !IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new InvalidSubmissionException(
                    "Idempotency key must be 8-128 characters using letters, numbers, '.', '_', ':' or '-'");
        }
    }

    private <T> void validate(T value) {
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            ConstraintViolation<T> first = violations.iterator().next();
            throw new InvalidSubmissionException(first.getPropertyPath() + " " + first.getMessage());
        }
    }

    private String outboxPayload(TitleTransaction transaction) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "transactionId", transaction.getId(),
                    "vin", transaction.getVin(),
                    "jurisdiction", transaction.getJurisdiction(),
                    "status", transaction.getStatus()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize outbox event", exception);
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String lower(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
