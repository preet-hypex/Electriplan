package com.hypex.electriplan.tenancy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The permission an endpoint needs. Implies {@link CompanyScoped}: the company
 * is resolved first, then the caller's role in it is checked against
 * {@link PermissionMatrix}; without the permission the answer is 403. On a
 * method it overrides one on the controller.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequiresPermission {
    Permission value();
}
