package com.td.application.common.cqrs;

/**
 * Marker interface for all queries in the system.
 * Queries represent read operations that don't change the state.
 * Equivalent to IRequest<T> for queries in .NET MediatR
 */
public interface Query<TResult> {
}