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

package com.vaadin.ui;

import java.io.Serializable;
import java.util.Collection;

import com.vaadin.flow.nodefeature.PushConfigurationMap;
import com.vaadin.server.VaadinSession;
import com.vaadin.server.communication.AtmospherePushConnection;
import com.vaadin.shared.communication.PushMode;
import com.vaadin.shared.ui.Transport;

/**
 * Provides method for configuring the push channel.
 *
 * @since 7.1
 * @author Vaadin Ltd
 */
public interface PushConfiguration extends Serializable {

    /**
     * Returns the mode of bidirectional ("push") communication that is used.
     *
     * @return The push mode.
     */
    PushMode getPushMode();

    /**
     * Sets the mode of bidirectional ("push") communication that should be
     * used.
     * <p>
     * Add-on developers should note that this method is only meant for the
     * application developer. An add-on should not set the push mode directly,
     * rather instruct the user to set it.
     * </p>
     *
     * @param pushMode
     *            The push mode to use.
     *
     * @throws IllegalArgumentException
     *             if the argument is null.
     * @throws IllegalStateException
     *             if push support is not available.
     */
    void setPushMode(PushMode pushMode);

    /**
     * Returns the primary transport type for push.
     * <p>
     * Note that if you set the transport type using
     * {@link #setParameter(String, String)} to an unsupported type this method
     * will return null. Supported types are defined by {@link Transport}.
     *
     * @return The primary transport type
     */
    Transport getTransport();

    /**
     * Sets the primary transport type for push.
     * <p>
     * Note that the new transport type will not be used until the push channel
     * is disconnected and reconnected if already active.
     *
     * @param transport
     *            The primary transport type
     */
    void setTransport(Transport transport);

    /**
     * Returns the fallback transport type for push.
     * <p>
     * Note that if you set the transport type using
     * {@link #setParameter(String, String)} to an unsupported type this method
     * will return null. Supported types are defined by {@link Transport}.
     *
     * @return The fallback transport type
     */
    Transport getFallbackTransport();

    /**
     * Sets the fallback transport type for push.
     * <p>
     * Note that the new transport type will not be used until the push channel
     * is disconnected and reconnected if already active.
     *
     * @param fallbackTransport
     *            The fallback transport type
     */
    void setFallbackTransport(Transport fallbackTransport);

    /**
     * Returns the given parameter, if set.
     * <p>
     * This method provides low level access to push parameters and is typically
     * not needed for normal application development.
     *
     * @since 7.1
     * @param parameter
     *            The parameter name
     * @return The parameter value or null if not set
     */
    String getParameter(String parameter);

    /**
     * Returns the parameters which have been defined.
     *
     * @since 7.1
     * @return A collection of parameter names
     */
    Collection<String> getParameterNames();

    /**
     * Sets the given parameter.
     * <p>
     * This method provides low level access to push parameters and is typically
     * not needed for normal application development.
     *
     * @since 7.1
     * @param parameter
     *            The parameter name
     * @param value
     *            The value
     */
    void setParameter(String parameter, String value);

    /**
     * Sets the URL to use for push requests.
     * <p>
     * This is only used when overriding the URL to use. Setting this to null
     * (the default) will use the default URL.
     *
     * @since 7.6
     * @param pushUrl
     *            The push URL to use
     */
    void setPushUrl(String pushUrl);

    /**
     * Returns the URL to use for push requests.
     * <p>
     * This is only used when overriding the URL to use. Returns null (the
     * default) when the default URL is used.
     *
     * @since 7.6
     * @return the URL to use for push requests, or null to use to default
     */
    String getPushUrl();

}

/**
 * The one and only implementation of {@link PushConfiguration}.
 *
 * @author Vaadin Ltd
 */
class PushConfigurationImpl implements PushConfiguration {
    private UI ui;

    PushConfigurationImpl(UI ui) {
        this.ui = ui;
        getPushConfigurationMap().setTransport(Transport.WEBSOCKET_XHR);
        getPushConfigurationMap().setFallbackTransport(Transport.LONG_POLLING);
        getPushConfigurationMap().setPushMode(PushMode.DISABLED);
    }

    private PushConfigurationMap getPushConfigurationMap() {
        return ui.getInternals().getStateTree().getRootNode()
                .getFeature(PushConfigurationMap.class);
    }

    @Override
    public PushMode getPushMode() {
        return getPushConfigurationMap().getPushMode();
    }

    @Override
    public void setPushMode(PushMode pushMode) {
        if (pushMode == null) {
            throw new IllegalArgumentException("Push mode cannot be null");
        }

        VaadinSession session = ui.getSession();

        if (session == null) {
            throw new UIDetachedException(
                    "Cannot set the push mode for a detached UI");
        }

        assert session.hasLock();

        if (pushMode.isEnabled()
                && !session.getService().ensurePushAvailable()) {
            throw new IllegalStateException(
                    "Push is not available. See previous log messages for more information.");
        }

        PushMode oldMode = getPushConfigurationMap().getPushMode();
        if (oldMode != pushMode) {
            getPushConfigurationMap().setPushMode(pushMode);

            if (!oldMode.isEnabled() && pushMode.isEnabled()) {
                // The push connection is initially in a disconnected state;
                // the client will establish the connection
                ui.getInternals()
                        .setPushConnection(new AtmospherePushConnection(ui));
            }
            // Nothing to do here if disabling push;
            // the client will close the connection
        }
    }

    @Override
    public void setPushUrl(String pushUrl) {
        getPushConfigurationMap().setPushUrl(pushUrl);
    }

    @Override
    public String getPushUrl() {
        return getPushConfigurationMap().getPushUrl();
    }

    @Override
    public Transport getTransport() {
        return getPushConfigurationMap().getTransport();
    }

    @Override
    public void setTransport(Transport transport) {
        getPushConfigurationMap().setTransport(transport);
    }

    @Override
    public Transport getFallbackTransport() {
        return getPushConfigurationMap().getFallbackTransport();
    }

    @Override
    public void setFallbackTransport(Transport fallbackTransport) {
        getPushConfigurationMap().setFallbackTransport(fallbackTransport);
    }

    @Override
    public String getParameter(String parameter) {
        return getPushConfigurationMap().getParameter(parameter);
    }

    @Override
    public void setParameter(String parameter, String value) {
        getPushConfigurationMap().setParameter(parameter, value);

    }

    @Override
    public Collection<String> getParameterNames() {
        return getPushConfigurationMap().getParameterNames();
    }

}
