/**
 * Reference data: the configurable lists other modules check against, such as
 * electricity distributors. Read through Hibernate (Spring Data JPA) from
 * tables Flyway creates and seeds; adding a row needs no code change.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Reference data")
@org.jspecify.annotations.NullMarked
package com.hypex.electriplan.reference;
