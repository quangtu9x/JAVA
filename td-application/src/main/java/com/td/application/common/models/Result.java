package com.td.application.common.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Standard operation result wrapper.
 */
@Getter
@AllArgsConstructor
public class Result<T> {

    private final boolean success;
    private final T data;
    private final String error;

    public static <T> Result<T> success(T data) {
        return new Result<>(true, data, null);
    }

    public static <T> Result<T> failure(String error) {
        return new Result<>(false, null, error);
    }
}