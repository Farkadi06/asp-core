package com.asp.config;

import com.asp.tenant.TenantContext;
import com.asp.tenant.TenantConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantTransactionInterceptor {

    private final TenantConnectionService tenantConnectionService;

    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object setTenantIdInTransaction(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            // Set tenant_id in Postgres session at the start of transaction
            tenantConnectionService.setTenantIdForCurrentTransaction(tenantId);
        }
        return joinPoint.proceed();
    }
}

