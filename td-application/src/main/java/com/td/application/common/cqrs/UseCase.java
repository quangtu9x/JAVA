package com.td.application.common.cqrs;

/**
 * Generic use case contract for application services.
 */
public interface UseCase<TRequest, TResult> {

    TResult execute(TRequest request);
}