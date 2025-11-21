package com.asp.api;

import com.asp.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
public class PingController {

    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        UUID tenantId = TenantContext.getTenantId();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("tenantId", tenantId != null ? tenantId.toString() : null);

        return ResponseEntity.ok(response);
    }
}

