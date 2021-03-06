/*
 * Copyright 2000-2017 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.vaadin.client.communication.PollConfigurator;
import com.vaadin.client.communication.Poller;
import com.vaadin.client.communication.ReconnectDialogConfiguration;
import com.vaadin.client.flow.RouterLinkHandler;
import com.vaadin.client.flow.StateNode;
import com.vaadin.client.flow.binding.Binder;
import com.vaadin.shared.Version;

import elemental.client.Browser;
import elemental.dom.Element;

/**
 * Main class for an application / UI.
 * <p>
 * Initializes the registry and starts the application.
 */
public class ApplicationConnection {

    private final Registry registry;

    /**
     * Creates an application connection using the given configuration.
     *
     * @param applicationConfiguration
     *            the configuration object for the application
     */
    public ApplicationConnection(
            ApplicationConfiguration applicationConfiguration) {

        registry = new DefaultRegistry(this, applicationConfiguration);
        GWT.setUncaughtExceptionHandler(
                registry.getSystemErrorHandler()::handleError);

        StateNode rootNode = registry.getStateTree().getRootNode();

        // Bind UI configuration objects
        PollConfigurator.observe(rootNode, new Poller(registry));
        ReconnectDialogConfiguration.bind(registry.getConnectionStateHandler());

        new PopStateHandler(registry).bind();

        Element body = Browser.getDocument().getBody();

        rootNode.setDomNode(body);
        Binder.bind(rootNode, body);
        RouterLinkHandler.bind(registry, body);

        Console.log("Starting application "
                + applicationConfiguration.getApplicationId());

        Console.log("Vaadin application servlet version: "
                + applicationConfiguration.getServletVersion());

        if (!applicationConfiguration.getServletVersion()
                .equals(Version.getFullVersion())) {
            Console.error(
                    "Warning: your widget set seems to be built with a different "
                            + "version than the one used on server. Unexpected "
                            + "behavior may occur.");
        }

        String appRootPanelName = applicationConfiguration.getApplicationId();
        // remove the end (window name) of autogenerated rootpanel id
        appRootPanelName = appRootPanelName.replaceFirst("-\\d+$", "");

        publishJavascriptMethods(appRootPanelName);

        registry.getLoadingIndicator().show();
    }

    /**
     * Starts this application.
     * <p>
     * Called by the bootstrapper, which ensures applications are started in
     * order.
     *
     * @param initialUidl
     *            the initial UIDL or null if the server did not provide any
     */
    public void start(ValueMap initialUidl) {
        if (initialUidl == null) {
            // initial UIDL not in DOM, request from server
            registry.getMessageSender().resynchronize();
        } else {
            // initial UIDL provided in DOM, continue as if returned by request

            // Hack to avoid logging an error in endRequest()
            registry.getRequestResponseTracker().startRequest();
            registry.getMessageHandler().handleMessage(initialUidl);
        }
    }

    /**
     * Checks if there is some work to be done on the client side.
     *
     * @return true if the client has some work to be done, false otherwise
     */
    private boolean isActive() {
        return !registry.getMessageHandler().isInitialUidlHandled()
                || registry.getRequestResponseTracker().hasActiveRequest()
                || isExecutingDeferredCommands();
    }

    private native void publishJavascriptMethods(String applicationId)
    /*-{
        var ap = this;
        var client = {};
        client.isActive = $entry(function() {
            return ap.@com.vaadin.client.ApplicationConnection::isActive()();
        });
    
        client.getProfilingData = $entry(function() {
            var smh = ap.@com.vaadin.client.ApplicationConnection::registry.@com.vaadin.client.Registry::getMessageHandler()();
            var pd = [
                smh.@com.vaadin.client.communication.MessageHandler::lastProcessingTime,
                    smh.@com.vaadin.client.communication.MessageHandler::totalProcessingTime
                ];
            if (null != smh.@com.vaadin.client.communication.MessageHandler::serverTimingInfo) {
                pd = pd.concat(smh.@com.vaadin.client.communication.MessageHandler::serverTimingInfo);
            } else {
                pd = pd.concat(-1, -1);
            }
            pd[pd.length] = smh.@com.vaadin.client.communication.MessageHandler::bootstrapTime;
            return pd;
        });
        
        $wnd.vaadin.resolveUri = $entry(function(uriToResolve) {
            var ur = ap.@com.vaadin.client.ApplicationConnection::registry.@com.vaadin.client.Registry::getURIResolver()();
            return ur.@com.vaadin.client.URIResolver::resolveVaadinUri(Ljava/lang/String;)(uriToResolve);
        });
        
        $wnd.vaadin.sendEventMessage = $entry(function(nodeId, eventType, eventData) {
            var sc = ap.@com.vaadin.client.ApplicationConnection::registry.@com.vaadin.client.Registry::getServerConnector()();
            sc.@com.vaadin.client.communication.ServerConnector::sendEventMessage(ILjava/lang/String;Lelemental/json/JsonObject;)(nodeId,eventType,eventData);
        });
    
        client.initializing = false;
    
        $wnd.vaadin.clients[applicationId] = client;
    }-*/;

    /**
     * Checks if deferred commands are (potentially) still being executed as a
     * result of an update from the server. Returns true if a deferred command
     * might still be executing, false otherwise. This will not work correctly
     * if a deferred command is added in another deferred command.
     * <p>
     * Used by the native "client.isActive" function.
     * </p>
     *
     * @return true if deferred commands are (potentially) being executed, false
     *         otherwise
     */
    private boolean isExecutingDeferredCommands() {
        Scheduler s = Scheduler.get();
        if (s instanceof TrackingScheduler) {
            return ((TrackingScheduler) s).hasWorkQueued();
        } else {
            return false;
        }
    }
}
