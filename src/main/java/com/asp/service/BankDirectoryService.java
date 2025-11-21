package com.asp.service;

import com.asp.domain.Bank;
import com.asp.domain.BankRepository;
import com.asp.dto.BankResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankDirectoryService {

    private final BankRepository bankRepository;

    @Transactional(readOnly = true)
    public List<BankResponse> listBanks() {
        log.debug("Listing all ACTIVE banks");
        List<Bank> banks = bankRepository.findAllByStatus(Bank.BankStatus.ACTIVE);
        
        return banks.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    private BankResponse mapToResponse(Bank bank) {
        return BankResponse.builder()
            .id(bank.getId())
            .name(bank.getName())
            .slug(bank.getSlug())
            .country(bank.getCountry())
            .connectionType(bank.getConnectionType().name())
            .iconUrl(bank.getIconUrl())
            .build();
    }
}

