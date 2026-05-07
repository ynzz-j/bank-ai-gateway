package com.bank.ai.gateway.service;

import com.bank.ai.gateway.compliance.ContentFilter.ViolationInfo;

/**
 * 合规拦截异常
 */
public class ComplianceBlockedException extends RuntimeException {

    private final ViolationInfo violation;

    public ComplianceBlockedException(ViolationInfo violation) {
        super("Content blocked by compliance filter: " + violation.matchedWord());
        this.violation = violation;
    }

    public ViolationInfo getViolation() {
        return violation;
    }
}
