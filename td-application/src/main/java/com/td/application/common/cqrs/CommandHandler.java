package com.td.application.common.cqrs;

/**
 * Interface for command handlers.
 * Equivalent to IRequestHandler<TCommand, TResult> in .NET MediatR
 */
public interface CommandHandler<TCommand extends Command<TResult>, TResult> {
    
    /**
     * Handles the command and returns the result
     */
    TResult handle(TCommand command);
}