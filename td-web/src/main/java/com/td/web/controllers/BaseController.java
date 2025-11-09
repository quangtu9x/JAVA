package com.td.web.controllers;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public abstract class BaseController {

    protected <T> ResponseEntity<Result<T>> ok(Result<T> result) {
        return ResponseEntity.ok(result);
    }

    protected <T> ResponseEntity<Result<T>> created(Result<T> result) {
        return ResponseEntity.status(201).body(result);
    }

    protected <T> ResponseEntity<PaginationResponse<T>> ok(PaginationResponse<T> response) {
        return ResponseEntity.ok(response);
    }

    protected ResponseEntity<Void> noContent() {
        return ResponseEntity.noContent().build();
    }

    protected <T> ResponseEntity<T> badRequest(T body) {
        return ResponseEntity.badRequest().body(body);
    }
}