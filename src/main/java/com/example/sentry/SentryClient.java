package com.example.sentry;

import io.sentry.SentryUncaughtExceptionHandler;
import io.sentry.connection.LockedDownException;
import io.sentry.connection.TooManyRequestsException;
import io.sentry.context.ContextManager;
import io.sentry.event.Event;

public class SentryClient extends io.sentry.SentryClient {
    
    CustomConnection connection;
    private SentryUncaughtExceptionHandler uncaughtExceptionHandler;

    public SentryClient(CustomConnection connection, ContextManager contextManager) {
        super(connection, contextManager);
        this.connection = connection;
    }

    /**
     * Setup and store the {@link SentryUncaughtExceptionHandler} so that it can be
     * disabled on {@link SentryClient#closeConnection()}.
     */
    protected void setupUncaughtExceptionHandler() {
        uncaughtExceptionHandler = SentryUncaughtExceptionHandler.setup();
    }

    @Override
    public void sendEvent(Event event) {
        System.out.println("Sending event here");
        if (event == null) {
            return;
        }

        try {
            connection.send(event);
        } catch (LockedDownException | TooManyRequestsException e) {
            throw e;
        } catch (RuntimeException e) {
            System.out.println("Runtime Exception");
        } finally {
            getContext().setLastEventId(event.getId());
        }

    }


}
