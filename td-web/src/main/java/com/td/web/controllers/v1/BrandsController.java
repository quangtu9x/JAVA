package com.td.web.controllers.v1;

import com.td.application.catalog.brands.*;
import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
@Validated
@Tag(name = "Brands", description = "Brand management endpoints")
public class BrandsController extends BaseController {

    private final CreateBrandUseCase createBrandUseCase;
    private final UpdateBrandUseCase updateBrandUseCase;
    private final DeleteBrandUseCase deleteBrandUseCase;
    private final GetBrandUseCase getBrandUseCase;
    private final SearchBrandsUseCase searchBrandsUseCase;

    @PostMapping("/search")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Search brands using available filters")
    public ResponseEntity<PaginationResponse<BrandDto>> searchBrands(
            @Valid @RequestBody SearchBrandsRequest request) {
        var response = searchBrandsUseCase.execute(request);
        return ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Get brand details")
    public ResponseEntity<Result<BrandDto>> getBrand(@PathVariable UUID id) {
        var result = getBrandUseCase.execute(new GetBrandRequest(id));
        return ok(result);
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Create a new brand")
    public ResponseEntity<Result<UUID>> createBrand(@Valid @RequestBody CreateBrandRequest request) {
        var result = createBrandUseCase.execute(request);
        return created(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Update a brand")
    public ResponseEntity<Result<UUID>> updateBrand(
            @PathVariable UUID id, 
            @Valid @RequestBody UpdateBrandRequest request) {
        if (!id.equals(request.getId())) {
            return badRequest(Result.<UUID>failure("Brand ID mismatch"));
        }
        var result = updateBrandUseCase.execute(request);
        return ok(result);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Delete a brand")
    public ResponseEntity<Result<UUID>> deleteBrand(@PathVariable UUID id) {
        var result = deleteBrandUseCase.execute(new DeleteBrandRequest(id));
        return ok(result);
    }
}