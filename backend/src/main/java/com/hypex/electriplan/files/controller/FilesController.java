package com.hypex.electriplan.files.controller;

import java.time.Duration;

import com.hypex.electriplan.files.domain.FileNotFoundException;
import com.hypex.electriplan.files.dto.FileContent;
import com.hypex.electriplan.files.service.CompanyFiles;
import com.hypex.electriplan.tenancy.domain.Permission;
import com.hypex.electriplan.tenancy.domain.RequiresPermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** A company's files, to its members: e.g. the image behind a floor plan. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Files", description = "Files a company keeps, such as uploaded floor-plan images.")
public class FilesController {

    private final CompanyFiles files;

    @GetMapping("/api/files/{id}")
    @RequiresPermission(Permission.COMPANY_VIEW)
    @Operation(operationId = "getFile", summary = "A file of the company",
            description = "The bytes, with their type. A file's contents never change, so it may be cached.")
    @ApiResponse(responseCode = "200", description = "The file", content = @Content(mediaType = "image/*",
            schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "404", description = "No such file in this company.",
            content = @Content(schema = @Schema(implementation = FilesErrors.Message.class)))
    ResponseEntity<byte[]> file(@PathVariable String id) {
        FileContent content = CompanyFiles.idFrom(id).flatMap(files::read).orElseThrow(FileNotFoundException::new);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePrivate().immutable())
                .eTag('"' + content.sha256Hex() + '"')
                .body(content.bytes());
    }
}
