package com.hypex.electriplan.tenancy.domain;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.hypex.electriplan.tenancy.service.CurrentCompany;

/**
 * Marks an endpoint (or a whole controller) as working inside one company.
 * Before it runs, the company is resolved and checked, and every transaction
 * it opens is limited to that company. Read it with {@link CurrentCompany}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface CompanyScoped {
}
