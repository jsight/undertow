/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.undertow.security.impl;


import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import io.undertow.UndertowLogger;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.security.api.AuthenticationState;
import io.undertow.security.idm.Account;
import io.undertow.security.idm.IdentityManager;
import io.undertow.server.HttpCompletionHandler;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.HttpHandlers;
import io.undertow.util.AttachmentKey;
import org.xnio.IoFuture;

/**
 * The internal SecurityContext used to hold the state of security for the current exchange.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Stuart Douglas
 */
public class SecurityContext {

    public static final RuntimePermission PERMISSION = new RuntimePermission("MODIFY_UNDERTOW_SECURITY_CONTEXT");

    public static AttachmentKey<SecurityContext> ATTACHMENT_KEY = AttachmentKey.create(SecurityContext.class);

    private final List<AuthenticationMechanism> authMechanisms = new ArrayList<AuthenticationMechanism>();
    private final IdentityManager identityManager;

    // TODO - We also need to supply a login method that allows app to supply a username and password.
    // Maybe this will need to be a custom mechanism that doesn't exchange tokens with the client but will then
    // be configured to either associate with the connection, the session or some other arbitrary whatever.
    //
    // Do we want multiple to be supported or just one?  Maybe extend the AuthenticationMechanism to allow
    // it to be identified and called.

    private AuthenticationState authenticationState = AuthenticationState.NOT_REQUIRED;
    private Principal authenticatedPrincipal;
    private String mechanismName;
    private Account account;

    public SecurityContext(final IdentityManager identityManager) {
        this.identityManager = identityManager;
        if(System.getSecurityManager() != null) {
            System.getSecurityManager().checkPermission(PERMISSION);
        }
    }

    /**
     * Performs authentication on the request. If the auth succeeds then the next handler will be invoked, otherwise the
     * completion handler will be called.
     * <p/>
     * Invoking this method can result in worker handoff, once it has been invoked the current handler should not modify the
     * exchange.
     *
     * @param exchange The exchange
     * @param completionHandler The completion handler
     * @param nextHandler The next handler to invoke once auth succeeds
     */
    public void authenticate(final HttpServerExchange exchange, final HttpCompletionHandler completionHandler,
            final HttpHandler nextHandler) {
        // TODO - A slight variation will be required if called from a servlet, in that case by being called authentication will
        // automatically become required, also will need to cope with control returning to the caller should it be successful.

        new RequestAuthenticator(authMechanisms.iterator(), completionHandler, exchange, nextHandler).authenticate();
    }

    public void setAuthenticationRequired() {
        authenticationState = AuthenticationState.REQUIRED;
    }

    public AuthenticationState getAuthenticationState() {
        return authenticationState;
    }

    public Principal getAuthenticatedPrincipal() {
        return authenticatedPrincipal;
    }

    /**
     * @return The name of the mechanism used to authenticate the request.
     */
    public String getMechanismName() {
        return mechanismName;
    }

    public boolean isUserInGroup(String group) {
        return identityManager.isUserInGroup(account, group);
    }

    IdentityManager getIdentityManager() {
        // Mechanisms can access this through the AuthenticationMechanism Util class.
        return identityManager;
    }

    public void addAuthenticationMechanism(final AuthenticationMechanism handler) {
        authMechanisms.add(handler);
    }

    public List<AuthenticationMechanism> getAuthenticationMechanisms() {
        return Collections.unmodifiableList(authMechanisms);
    }

    private class RequestAuthenticator {

        private final Iterator<AuthenticationMechanism> mechanismIterator;
        private final HttpCompletionHandler completionHandler;
        private final HttpServerExchange exchange;
        private final HttpHandler nextHandler;

        private RequestAuthenticator(final Iterator<AuthenticationMechanism> handlerIterator,
                final HttpCompletionHandler completionHandler, final HttpServerExchange exchange, final HttpHandler nextHandler) {
            this.mechanismIterator = handlerIterator;
            this.completionHandler = completionHandler;
            this.exchange = exchange;
            this.nextHandler = nextHandler;
        }

        void authenticate() {
            if (mechanismIterator.hasNext()) {
                final AuthenticationMechanism mechanism = mechanismIterator.next();
                IoFuture<AuthenticationMechanism.AuthenticationResult> resultFuture = mechanism.authenticate(exchange, identityManager);
                resultFuture.addNotifier(new IoFuture.Notifier<AuthenticationMechanism.AuthenticationResult, Object>() {
                    @Override
                    public void notify(final IoFuture<? extends AuthenticationMechanism.AuthenticationResult> ioFuture,
                            final Object attachment) {
                        if (ioFuture.getStatus() == IoFuture.Status.DONE) {
                            try {
                                AuthenticationMechanism.AuthenticationResult result = ioFuture.get();
                                switch (result.getOutcome()) {
                                    case AUTHENTICATED:
                                        SecurityContext.this.authenticatedPrincipal = result.getPrinciple();
                                        SecurityContext.this.mechanismName = mechanism.getName();
                                        SecurityContext.this.account = result.getAccount();
                                        SecurityContext.this.authenticationState = AuthenticationState.AUTHENTICATED;

                                        HttpCompletionHandler singleComplete = new SingleMechanismCompletionHandler(mechanism,
                                                exchange, completionHandler);
                                        HttpHandlers.executeHandler(nextHandler, exchange, singleComplete);
                                        break;
                                    case NOT_ATTEMPTED:
                                        // That mechanism didn't attempt at all so see if there is another mechanism to try.
                                        authenticate();
                                        break;
                                    default:
                                        UndertowLogger.REQUEST_LOGGER.debug("authentication not complete, sending challenges.");

                                        // Either authentication failed or the mechanism is in an intermediate state and
                                        // requires
                                        // an additional round trip with the client - either way all mechanisms must now
                                        // complete.
                                        new AllMechanismCompletionHandler(authMechanisms.iterator(), exchange,
                                                completionHandler).handleComplete();
                                }

                            } catch (IOException e) {
                                // will never happen, as state is DONE
                                new AllMechanismCompletionHandler(authMechanisms.iterator(), exchange,
                                        completionHandler).handleComplete();
                            }
                        } else if (ioFuture.getStatus() == IoFuture.Status.FAILED) {
                            UndertowLogger.REQUEST_LOGGER.exceptionWhileAuthenticating(mechanism, ioFuture.getException());
                        } else if (ioFuture.getStatus() == IoFuture.Status.CANCELLED) {
                            // this should never happen
                            new AllMechanismCompletionHandler(authMechanisms.iterator(), exchange, completionHandler)
                                    .handleComplete();
                        }
                    }
                }, null);
            } else {
                if (SecurityContext.this.authenticationState == AuthenticationState.REQUIRED) {
                    // We have run through all of the mechanisms, authentication has not occurred but it is required so send
                    // challenges to the client.
                    new AllMechanismCompletionHandler(authMechanisms.iterator(), exchange, completionHandler)
                            .handleComplete();
                } else {
                    // Authentication was not actually required to the request can proceed.
                    HttpHandlers.executeHandler(nextHandler, exchange, completionHandler);
                }
            }
        }

    }

    /**
     * A {@link HttpCompletionHandler} that is used when
     * {@link AuthenticationMechanism#handleComplete(HttpServerExchange, HttpCompletionHandler)} need to be called on each
     * {@link AuthenticationMechanism} in turn.
     */
    private class AllMechanismCompletionHandler implements HttpCompletionHandler {

        private final Iterator<AuthenticationMechanism> handlerIterator;
        private final HttpServerExchange exchange;
        private final HttpCompletionHandler finalCompletionHandler;

        private AllMechanismCompletionHandler(Iterator<AuthenticationMechanism> handlerIterator, HttpServerExchange exchange,
                HttpCompletionHandler finalCompletionHandler) {
            this.handlerIterator = handlerIterator;
            this.exchange = exchange;
            this.finalCompletionHandler = finalCompletionHandler;
        }

        public void handleComplete() {
            if (handlerIterator.hasNext()) {
                handlerIterator.next().handleComplete(exchange, this);
            } else {
                finalCompletionHandler.handleComplete();
            }

        }

    }

    /**
     * A {@link HttpCompletionHandler} that is used when
     * {@link AuthenticationMechanism#handleComplete(HttpServerExchange, HttpCompletionHandler)} only needs to be called on a
     * single {@link AuthenticationMechanism}.
     */
    private class SingleMechanismCompletionHandler implements HttpCompletionHandler {

        private final AuthenticationMechanism mechanism;
        private final HttpServerExchange exchange;
        private final HttpCompletionHandler completionHandler;

        private SingleMechanismCompletionHandler(AuthenticationMechanism mechanism, HttpServerExchange exchange,
                HttpCompletionHandler completionHandler) {
            this.mechanism = mechanism;
            this.exchange = exchange;
            this.completionHandler = completionHandler;
        }

        public void handleComplete() {
            mechanism.handleComplete(exchange, completionHandler);
        }
    }
}
