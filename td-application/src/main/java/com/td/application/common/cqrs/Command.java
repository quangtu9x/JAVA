package com.td.application.common.cqrs;

/**
 * Marker interface for all commands in the system.
 * Commands represent actions that change the state of the system.
 * Equivalent to IRequest<T> for commands in .NET MediatR
 */
public interface Command<TResult> {
}