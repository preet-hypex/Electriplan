/**
 * Files the application keeps: uploaded floor-plan images now; drawings,
 * quote PDFs and licence documents later. The bytes live in S3 (an
 * S3-compatible store locally); electriplan.stored_file records each one for
 * its company: purpose, size, type, image size and SHA-256. The same file
 * uploaded twice by a company is stored once.
 *
 * <p>This module is the only part of the system that talks to S3. Others
 * store and read files through {@link com.hypex.electriplan.files.service.CompanyFiles},
 * and people fetch them from {@code GET /api/files/{id}}, members of the
 * file's company only.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Files", allowedDependencies = {"tenancy :: domain", "tenancy :: service"})
@org.jspecify.annotations.NullMarked
package com.hypex.electriplan.files;
