package com.remitz.modules.payin.transaction.service;

import com.remitz.common.enums.PaymentMode;
import com.remitz.common.enums.PayinTransactionStatus;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.payin.customer.entity.PayinCustomerEntity;
import com.remitz.modules.payin.customer.repository.PayinCustomerRepository;
import com.remitz.modules.payin.transaction.dto.BeneficiaryDetailsDto;
import com.remitz.modules.payin.transaction.dto.CreatePayinTransactionRequest;
import com.remitz.modules.payin.transaction.dto.CreatePayinTransactionResponse;
import com.remitz.modules.payin.transaction.dto.PayinTransactionDto;
import com.remitz.modules.payin.transaction.entity.PayinBeneficiaryEntity;
import com.remitz.modules.payin.transaction.entity.PayinTransactionEntity;
import com.remitz.modules.payin.transaction.repository.PayinBeneficiaryRepository;
import com.remitz.modules.payin.transaction.repository.PayinTransactionRepository;
import com.remitz.modules.auth.entity.UserEntity;
import com.remitz.modules.auth.repository.UserRepository;
import com.remitz.common.enums.CreatedSource;
import com.remitz.common.enums.DeliveryMethod;
import com.remitz.common.enums.PaymentMethodType;
import com.remitz.common.enums.TransactionStatus;
import com.remitz.common.util.ReferenceNumberGenerator;
import com.remitz.modules.fx.entity.CorridorEntity;
import com.remitz.modules.fx.repository.CorridorRepository;
import com.remitz.modules.transaction.entity.BeneficiaryEntity;
import com.remitz.modules.transaction.entity.TransactionEntity;
import com.remitz.modules.transaction.repository.BeneficiaryRepository;
import com.remitz.modules.transaction.repository.TransactionRepository;
import com.remitz.modules.transaction.entity.PayoutPartner;
import com.remitz.modules.transaction.repository.PayoutPartnerCountryRepository;
import com.remitz.modules.transaction.repository.PayoutPartnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayinTransactionServiceImpl implements PayinTransactionService {

    private final PayinCustomerRepository customerRepository;
    private final PayinBeneficiaryRepository beneficiaryRepository;
    private final PayinTransactionRepository transactionRepository;
    private final BeneficiaryRepository regularBeneficiaryRepository;
    private final UserRepository userRepository;
    private final TransactionRepository regularTransactionRepository;
    private final CorridorRepository corridorRepository;
    private final PayoutPartnerRepository payoutPartnerRepository;
    private final PayoutPartnerCountryRepository payoutPartnerCountryRepository;
    private final com.remitz.modules.transaction.service.TransactionReceiptService transactionReceiptService;

    @Override
    public byte[] generateReceiptPdf(String transactionId) {
        PayinTransactionEntity txn = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("PayinTransaction", "transactionId", transactionId));
        if (txn.getLinkedTransactionId() == null) {
            throw new ResourceNotFoundException("Receipt", "transactionId", transactionId);
        }
        return transactionReceiptService.generatePdfForTransaction(txn.getLinkedTransactionId());
    }

    @Override
    @Transactional
    public CreatePayinTransactionResponse createTransaction(CreatePayinTransactionRequest request) {
        log.info("PayIn transaction request — customerId: {}, paymentMode: {}, amount: {} {}",
                request.getCustomerId(), request.getPaymentMode(),
                request.getAmount(), request.getCurrency());

        // Idempotency check
        if (hasText(request.getExternalReferenceId())) {
            Optional<PayinTransactionEntity> existing =
                    transactionRepository.findByExternalReferenceId(request.getExternalReferenceId().trim());
            if (existing.isPresent()) {
                PayinTransactionEntity txn = existing.get();
                log.info("Idempotent return for externalReferenceId: {} → transactionId: {}",
                        request.getExternalReferenceId(), txn.getTransactionId());
                return CreatePayinTransactionResponse.success(txn.getTransactionId(), txn.getStatus().name(), txn.getCustomerSource().name());
            }
        }

        // Validate payment mode
        PaymentMode paymentMode = resolvePaymentMode(request.getPaymentMode());
        if (paymentMode == null) {
            log.warn("Invalid payment mode: {}", request.getPaymentMode());
            return CreatePayinTransactionResponse.failure("Invalid payment mode");
        }

        // Fetch customer — check payin_customers first, fall back to regular users
        PayinCustomerEntity customer = customerRepository.findByCustomerId(request.getCustomerId())
                .orElseGet(() -> autoRegisterFromRegularUser(request.getCustomerId()));
        if (customer == null) {
            log.warn("Customer not found: {}", request.getCustomerId());
            return CreatePayinTransactionResponse.failure("Customer not found");
        }

        // Resolve beneficiary
        PayinBeneficiaryEntity beneficiary;
        if (hasText(request.getBeneficiaryId())) {
            beneficiary = resolveBeneficiaryById(request.getBeneficiaryId().trim(), customer.getCustomerId());
            if (beneficiary == null) {
                return CreatePayinTransactionResponse.failure("Beneficiary not found or does not belong to this customer");
            }
        } else if (request.getBeneficiaryDetails() != null) {
            // Enforce required beneficiary details by delivery method — never create an
            // incomplete recipient (root cause of blank "Beneficiary Details" popups).
            String validationError = validateBeneficiaryDetails(request.getDeliveryMethod(), request.getBeneficiaryDetails());
            if (validationError != null) {
                log.warn("PayIn beneficiary validation failed: {}", validationError);
                return CreatePayinTransactionResponse.failure(validationError);
            }
            beneficiary = createBeneficiary(request.getBeneficiaryDetails(), customer.getCustomerId());
        } else {
            return CreatePayinTransactionResponse.failure("Either beneficiaryId or beneficiaryDetails is required");
        }

        // Cash collection is immediately processing; card/banking payments are pending confirmation
        PayinTransactionStatus initialStatus = (paymentMode == PaymentMode.CASH_COLLECTION)
                ? PayinTransactionStatus.PROCESSING
                : PayinTransactionStatus.PENDING;

        // Create transaction
        PayinTransactionEntity transaction = PayinTransactionEntity.builder()
                .transactionId(UUID.randomUUID().toString())
                .customerId(customer.getCustomerId())
                .customerSource(customer.getCreatedSource())
                .beneficiaryId(beneficiary.getId())
                .amount(request.getAmount())
                .currency(request.getCurrency().trim().toUpperCase())
                .receiveCurrency(hasText(request.getReceiveCurrency()) ? request.getReceiveCurrency().trim().toUpperCase() : null)
                .receiveAmount(request.getReceiveAmount())
                .deliveryMethod(hasText(request.getDeliveryMethod()) ? request.getDeliveryMethod().trim().toUpperCase() : null)
                .paymentMode(paymentMode)
                .status(initialStatus)
                .externalReferenceId(hasText(request.getExternalReferenceId())
                        ? request.getExternalReferenceId().trim() : null)
                .build();

        PayinTransactionEntity saved = transactionRepository.save(transaction);
        log.info("PayIn transaction created — transactionId: {}, customerId: {}, customerSource: {}",
                saved.getTransactionId(), saved.getCustomerId(), saved.getCustomerSource());

        // Create linked regular transaction so Payout Partner can action it
        createLinkedTransaction(saved, customer, beneficiary, paymentMode);

        return CreatePayinTransactionResponse.success(saved.getTransactionId(), saved.getStatus().name(), saved.getCustomerSource().name());
    }

    private void createLinkedTransaction(PayinTransactionEntity payinTxn, PayinCustomerEntity customer,
                                         PayinBeneficiaryEntity payinBen, PaymentMode paymentMode) {
        try {
            String sendCurrency = payinTxn.getCurrency();
            String receiveCurrency = payinTxn.getReceiveCurrency();
            if (receiveCurrency == null) return;

            // Resolve corridor
            CorridorEntity corridor = corridorRepository
                    .findBySendCurrencyAndReceiveCurrencyAndIsActiveTrue(sendCurrency, receiveCurrency)
                    .orElseGet(() -> corridorRepository
                            .findBySendCurrencyAndReceiveCurrency(sendCurrency, receiveCurrency)
                            .orElse(null));
            if (corridor == null) {
                log.warn("No corridor found for {}->{}, skipping linked transaction", sendCurrency, receiveCurrency);
                return;
            }

            // Resolve sender userId
            Long senderId = userRepository.findByUuid(customer.getCustomerId())
                    .map(u -> u.getId()).orElse(null);
            if (senderId == null) {
                log.warn("Cannot resolve senderId for customerId {}", customer.getCustomerId());
                return;
            }

            // Prefer the mirror created inside createBeneficiary (it has the full USI field
            // set: iban / branch / mobile / address). Only build a stub if no mirror exists
            // (happens when the partner picked an existing beneficiary by id rather than inline-creating).
            BeneficiaryEntity savedBen;
            if (payinBen.getLinkedRegularBeneficiaryId() != null) {
                savedBen = regularBeneficiaryRepository.findById(payinBen.getLinkedRegularBeneficiaryId())
                        .orElse(null);
            } else {
                savedBen = null;
            }
            if (savedBen == null) {
                savedBen = regularBeneficiaryRepository.save(BeneficiaryEntity.builder()
                        .userId(senderId)
                        .fullName(payinBen.getName())
                        .country(corridor.getReceiveCountry() != null ? corridor.getReceiveCountry() : "")
                        .deliveryMethod(resolveDeliveryMethod(payinTxn.getDeliveryMethod()))
                        .bankName(payinBen.getBankName())
                        .accountNumber(payinBen.getAccountNumber())
                        .isFavourite(false)
                        .isBlocked(false)
                        .build());
            } else {
                // Patch fields that depend on the txn we're creating now (country + delivery method).
                if (savedBen.getCountry() == null || savedBen.getCountry().isBlank()) {
                    savedBen.setCountry(corridor.getReceiveCountry() != null ? corridor.getReceiveCountry() : "");
                }
                if (savedBen.getDeliveryMethod() == null) {
                    savedBen.setDeliveryMethod(resolveDeliveryMethod(payinTxn.getDeliveryMethod()));
                }
                savedBen = regularBeneficiaryRepository.save(savedBen);
            }

            java.math.BigDecimal sendAmt = payinTxn.getAmount();
            java.math.BigDecimal receiveAmt = payinTxn.getReceiveAmount() != null ? payinTxn.getReceiveAmount() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal rate = (sendAmt != null && sendAmt.compareTo(java.math.BigDecimal.ZERO) > 0 && receiveAmt.compareTo(java.math.BigDecimal.ZERO) > 0)
                    ? receiveAmt.divide(sendAmt, 8, java.math.RoundingMode.HALF_UP)
                    : java.math.BigDecimal.ONE;

            TransactionStatus linkedStatus = (paymentMode == PaymentMode.CASH_COLLECTION)
                    ? TransactionStatus.FUNDS_RECEIVED
                    : TransactionStatus.PENDING;

            // Auto-assign payout partner by receive currency, fall back to first active partner
            Long payoutPartnerId = payoutPartnerCountryRepository.findAll().stream()
                    .filter(pc -> receiveCurrency.equalsIgnoreCase(pc.getCurrency())
                            && Boolean.TRUE.equals(pc.getIsActive()))
                    .findFirst()
                    .map(pc -> pc.getPartnerId())
                    .orElseGet(() -> payoutPartnerRepository.findAll().stream()
                            .filter(pp -> Boolean.TRUE.equals(pp.getIsActive()))
                            .findFirst()
                            .map(PayoutPartner::getId)
                            .orElse(null));

            TransactionEntity linked = TransactionEntity.builder()
                    .referenceNumber(ReferenceNumberGenerator.generate())
                    .senderId(senderId)
                    .senderName(customer.getFirstName() + " " + customer.getLastName())
                    .senderEmail(customer.getEmail())
                    .beneficiaryId(savedBen.getId())
                    .corridorId(corridor.getId())
                    .status(linkedStatus)
                    .deliveryMethod(resolveDeliveryMethod(payinTxn.getDeliveryMethod()))
                    .sendAmount(sendAmt)
                    .sendCurrency(sendCurrency)
                    .receiveAmount(receiveAmt)
                    .receiveCurrency(receiveCurrency)
                    .exchangeRate(rate)
                    .appliedRate(rate)
                    .feeAmount(java.math.BigDecimal.ZERO)
                    .fxMarginAmount(java.math.BigDecimal.ZERO)
                    .totalDebitAmount(sendAmt)
                    .paymentMethodType(resolvePaymentMethodType(paymentMode))
                    .payinPartnerId(null)
                    .payoutPartnerId(payoutPartnerId)
                    .version(0L)
                    .isRecurring(false)
                    .build();

            TransactionEntity savedLinked = regularTransactionRepository.save(linked);
            payinTxn.setLinkedTransactionId(savedLinked.getId());
            regularTransactionRepository.flush();
            log.info("Linked regular transaction {} created for PayIn txn {}",
                    savedLinked.getReferenceNumber(), payinTxn.getTransactionId());
        } catch (Exception e) {
            log.error("Failed to create linked transaction for PayIn txn {}: {}",
                    payinTxn.getTransactionId(), e.getMessage());
        }
    }

    private PaymentMethodType resolvePaymentMethodType(PaymentMode mode) {
        if (mode == null) return PaymentMethodType.AGENT_CASH;
        return switch (mode) {
            case CREDIT_CARD -> PaymentMethodType.CARD;
            case DEBIT_CARD -> PaymentMethodType.CARD;
            case INTERNET_BANKING -> PaymentMethodType.BANK_TRANSFER;
            default -> PaymentMethodType.AGENT_CASH;
        };
    }

    private DeliveryMethod resolveDeliveryMethod(String raw) {
        if (raw == null) return DeliveryMethod.BANK_DEPOSIT;
        try {
            return DeliveryMethod.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DeliveryMethod.BANK_DEPOSIT;
        }
    }

    private PayinCustomerEntity autoRegisterFromRegularUser(String customerId) {
        UserEntity user = userRepository.findByUuid(customerId).orElse(null);
        if (user == null) return null;
        log.info("Auto-registering regular user {} as PayIn customer", customerId);
        PayinCustomerEntity entity = PayinCustomerEntity.builder()
                .customerId(customerId)
                .firstName(user.getFirstName() != null ? user.getFirstName() : "")
                .lastName(user.getLastName() != null ? user.getLastName() : "")
                .email(user.getEmail())
                .phone(user.getPhone() != null ? user.getPhone() : "")
                .dob(user.getDateOfBirth() != null ? user.getDateOfBirth() : java.time.LocalDate.of(1990, 1, 1))
                .nationality(user.getNationality() != null ? user.getNationality() : "")
                .addressLine1(user.getAddressLine1() != null ? user.getAddressLine1() : "")
                .city(user.getCity() != null ? user.getCity() : "")
                .country(user.getCountry() != null ? user.getCountry() : "")
                .postalCode(user.getPostcode() != null ? user.getPostcode() : "")
                .isVerified(Boolean.TRUE.equals(user.getEmailVerified()))
                .createdSource(CreatedSource.FRONTEND)
                .build();
        return customerRepository.save(entity);
    }

    private PaymentMode resolvePaymentMode(String raw) {
        if (raw == null) return null;
        try {
            return PaymentMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private PayinBeneficiaryEntity resolveBeneficiaryById(String beneficiaryId, String customerId) {
        try {
            if (beneficiaryId.startsWith("r_")) {
                // Regular beneficiary — copy into a transient PayinBeneficiaryEntity
                Long id = Long.parseLong(beneficiaryId.substring(2));
                BeneficiaryEntity reg = regularBeneficiaryRepository.findById(id).orElse(null);
                if (reg == null) return null;
                String accountNum = reg.getAccountNumber() != null ? reg.getAccountNumber()
                        : (reg.getIban() != null ? reg.getIban()
                        : (reg.getMobileNumber() != null ? reg.getMobileNumber() : ""));
                String bankOrProvider = reg.getBankName() != null ? reg.getBankName()
                        : (reg.getMobileProvider() != null ? reg.getMobileProvider() : "");
                PayinBeneficiaryEntity copy = PayinBeneficiaryEntity.builder()
                        .customerId(customerId)
                        .name(reg.getFullName())
                        .bankName(bankOrProvider)
                        .accountNumber(accountNum)
                        .ifscCode(reg.getSwiftBic() != null ? reg.getSwiftBic()
                                : (reg.getSortCode() != null ? reg.getSortCode() : null))
                        .build();
                return beneficiaryRepository.save(copy);
            }
            String rawId = beneficiaryId.startsWith("p_") ? beneficiaryId.substring(2) : beneficiaryId;
            Long id = Long.parseLong(rawId);
            return beneficiaryRepository.findByIdAndCustomerId(id, customerId).orElse(null);
        } catch (NumberFormatException e) {
            log.warn("Invalid beneficiaryId format: {}", beneficiaryId);
            return null;
        }
    }

    private PayinBeneficiaryEntity createBeneficiary(BeneficiaryDetailsDto details, String customerId) {
        // Coalesce the cash-collection sub-fields into the standard bank fields when present
        // — the regular BeneficiaryEntity stores collection-point name/code in bank_name/account_number.
        String bankName = pickFirst(details.getCollectionPointName(), details.getBankName());
        String accountNumber = pickFirst(details.getCollectionPointCode(),
                details.getAccountNumber(), details.getIban());
        String address = pickFirst(details.getCollectionPointAddress(), details.getAddress());
        String city = pickFirst(details.getCollectionPointCity(), details.getBranchCity());

        // 1) Save the legacy PayinBeneficiaryEntity (kept so the partner's beneficiary list keeps working).
        PayinBeneficiaryEntity entity = PayinBeneficiaryEntity.builder()
                .customerId(customerId)
                .name(details.getName().trim())
                .bankName(bankName != null ? bankName : "")
                .accountNumber(accountNumber != null ? accountNumber : "")
                .ifscCode(hasText(details.getIfscCode()) ? details.getIfscCode().trim() : null)
                .build();
        PayinBeneficiaryEntity savedPayin = beneficiaryRepository.save(entity);

        // 2) Also save a regular BeneficiaryEntity with the full USI field set so the
        //    linked transaction (created later) + the USI Money admin page join correctly.
        try {
            Long senderUserId = userRepository.findByUuid(customerId).map(u -> u.getId()).orElse(null);
            if (senderUserId != null) {
                BeneficiaryEntity reg = BeneficiaryEntity.builder()
                        .userId(senderUserId)
                        .fullName(details.getName().trim())
                        .country("")   // resolved by the corridor at txn time
                        .bankName(bankName)
                        .accountNumber(accountNumber)
                        .iban(hasText(details.getIban()) ? details.getIban().trim() : null)
                        .swiftBic(hasText(details.getSwiftBic()) ? details.getSwiftBic().trim() : null)
                        .sortCode(hasText(details.getSortCode()) ? details.getSortCode().trim() : null)
                        .branchState(details.getBranchState())
                        .branchCity(city)
                        .mobileNumber(details.getMobileNumber())
                        .mobileProvider(details.getMobileProvider())
                        .address(address)
                        .isFavourite(false)
                        .isBlocked(false)
                        .build();
                BeneficiaryEntity savedReg = regularBeneficiaryRepository.save(reg);
                // Stash the regular-beneficiary id on the payin entity so createLinkedTransaction can read it.
                savedPayin.setLinkedRegularBeneficiaryId(savedReg.getId());
                beneficiaryRepository.save(savedPayin);
                log.info("Mirrored regular beneficiary {} for payin beneficiary {} (customer {})",
                        savedReg.getId(), savedPayin.getId(), customerId);
            }
        } catch (Exception e) {
            log.warn("Could not mirror regular beneficiary for payin beneficiary {}: {}",
                    savedPayin.getId(), e.getMessage());
        }

        log.info("New PayIn beneficiary created — id: {}, customerId: {}", savedPayin.getId(), customerId);
        return savedPayin;
    }

    private static String pickFirst(String... values) {
        if (values == null) return null;
        for (String v : values) if (v != null && !v.trim().isEmpty()) return v.trim();
        return null;
    }

    @Override
    public List<PayinTransactionDto> listTransactions() {
        return transactionRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<PayinTransactionDto> listProcessingTransactions() {
        return transactionRepository.findByStatus(PayinTransactionStatus.PROCESSING).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PayinTransactionDto markPaid(String transactionId) {
        PayinTransactionEntity txn = transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("PayinTransaction", "transactionId", transactionId));
        if (txn.getStatus() != PayinTransactionStatus.PROCESSING) {
            throw new IllegalStateException("Only PROCESSING transactions can be marked as paid. Current status: " + txn.getStatus());
        }
        txn.setStatus(PayinTransactionStatus.SUCCESS);
        PayinTransactionEntity saved = transactionRepository.save(txn);
        log.info("PayIn transaction {} marked as paid by payout partner", transactionId);
        return toDto(saved);
    }

    private PayinTransactionDto toDto(PayinTransactionEntity e) {
        PayinBeneficiaryEntity ben = beneficiaryRepository.findById(e.getBeneficiaryId()).orElse(null);
        // Surface the TXN reference of the linked regular transaction so every UI shows TXN…
        String referenceNumber = e.getLinkedTransactionId() != null
                ? regularTransactionRepository.findById(e.getLinkedTransactionId())
                        .map(TransactionEntity::getReferenceNumber).orElse(null)
                : null;
        // Full beneficiary details come from the linked regular beneficiary (PayinBeneficiary is minimal).
        BeneficiaryEntity reg = (ben != null && ben.getLinkedRegularBeneficiaryId() != null)
                ? regularBeneficiaryRepository.findById(ben.getLinkedRegularBeneficiaryId()).orElse(null)
                : null;
        return PayinTransactionDto.builder()
                .transactionId(e.getTransactionId())
                .referenceNumber(referenceNumber)
                .customerId(e.getCustomerId())
                .customerSource(e.getCustomerSource() != null ? e.getCustomerSource().name() : null)
                .beneficiaryId(e.getBeneficiaryId())
                .beneficiaryName(ben != null ? ben.getName() : (reg != null ? reg.getFullName() : null))
                .beneficiaryBank(ben != null && ben.getBankName() != null ? ben.getBankName() : (reg != null ? reg.getBankName() : null))
                .beneficiaryAccount(ben != null && ben.getAccountNumber() != null ? ben.getAccountNumber() : (reg != null ? reg.getAccountNumber() : null))
                .beneficiaryPhone(reg != null ? reg.getMobileNumber() : null)
                .beneficiaryCountry(reg != null ? reg.getCountry() : null)
                .beneficiaryCity(reg != null ? reg.getBranchCity() : null)
                .beneficiaryBranch(reg != null ? reg.getSortCode() : null)
                .beneficiarySwift(reg != null ? reg.getSwiftBic() : null)
                .beneficiaryIban(reg != null ? reg.getIban() : null)
                .beneficiaryAddress(reg != null ? reg.getAddress() : null)
                .beneficiaryProvider(reg != null ? reg.getMobileProvider() : null)
                .amount(e.getAmount())
                .currency(e.getCurrency())
                .receiveCurrency(e.getReceiveCurrency())
                .receiveAmount(e.getReceiveAmount())
                .deliveryMethod(e.getDeliveryMethod())
                .paymentMode(e.getPaymentMode() != null ? e.getPaymentMode().name() : null)
                .status(e.getStatus() != null ? e.getStatus().name() : null)
                .externalReferenceId(e.getExternalReferenceId())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /** Reject incomplete beneficiaries by delivery method. Returns an error message, or null if valid. */
    private String validateBeneficiaryDetails(String deliveryMethod, BeneficiaryDetailsDto d) {
        if (d == null) return "Beneficiary details are required";
        if (!hasText(d.getName())) return "Beneficiary full name is required";
        String dm = deliveryMethod == null ? "" : deliveryMethod.toUpperCase();
        if (dm.contains("BANK")) {
            if (!hasText(d.getBankName())) return "Bank name is required for bank transfers";
            if (!hasText(d.getAccountNumber()) && !hasText(d.getIban()))
                return "Account number (or IBAN) is required for bank transfers";
        } else if (dm.contains("MOBILE")) {
            if (!hasText(d.getMobileNumber())) return "Mobile number is required for mobile money";
        } else if (dm.contains("CASH")) {
            if (!hasText(d.getCollectionPointName()) && !hasText(d.getBankName()))
                return "Collection point is required for cash pickup";
        }
        return null;
    }
}
