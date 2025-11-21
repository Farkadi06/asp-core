package com.asp.api;

import com.asp.dto.BankResponse;
import com.asp.service.BankDirectoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/banks")
@RequiredArgsConstructor
@Slf4j
public class BankController {

    private final BankDirectoryService bankDirectoryService;

    @GetMapping
    public ResponseEntity<List<BankResponse>> listBanks() {
        log.debug("Listing banks");
        List<BankResponse> banks = bankDirectoryService.listBanks();
        return ResponseEntity.ok(banks);
    }
}

