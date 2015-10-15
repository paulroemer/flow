/*
 * Copyright 2000-2014 Vaadin Ltd.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.vaadin.annotations.Implemented;
import com.vaadin.annotations.Tag;
import com.vaadin.event.MouseEvents.ClickEvent;
import com.vaadin.event.MouseEvents.ClickListener;
import com.vaadin.event.UIEvents.PollEvent;
import com.vaadin.event.UIEvents.PollListener;
import com.vaadin.event.UIEvents.PollNotifier;
import com.vaadin.hummingbird.kernel.AbstractElementTemplate.Keys;
import com.vaadin.hummingbird.kernel.BasicElementTemplate;
import com.vaadin.hummingbird.kernel.Element;
import com.vaadin.hummingbird.kernel.ElementTemplate;
import com.vaadin.hummingbird.kernel.JsonConverter;
import com.vaadin.hummingbird.kernel.RootNode;
import com.vaadin.hummingbird.kernel.StateNode;
import com.vaadin.navigator.Navigator;
import com.vaadin.server.ClientConnector;
import com.vaadin.server.DefaultErrorHandler;
import com.vaadin.server.ErrorHandler;
import com.vaadin.server.ErrorHandlingRunnable;
import com.vaadin.server.LocaleService;
import com.vaadin.server.UIProvider;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinService;
import com.vaadin.server.VaadinServlet;
import com.vaadin.server.VaadinSession;
import com.vaadin.server.VaadinSession.State;
import com.vaadin.server.communication.PushConnection;
import com.vaadin.shared.Connector;
import com.vaadin.shared.MouseEventDetails;
import com.vaadin.shared.communication.PushMode;
import com.vaadin.shared.ui.ui.DebugWindowServerRpc;
import com.vaadin.shared.ui.ui.UIClientRpc;
import com.vaadin.shared.ui.ui.UIServerRpc;
import com.vaadin.shared.ui.ui.UIState;
import com.vaadin.ui.Component.Focusable;
import com.vaadin.util.ConnectorHelper;
import com.vaadin.util.CurrentInstance;

import elemental.json.JsonArray;
import elemental.json.JsonObject;
import elemental.json.JsonValue;

/**
 * The topmost component in any component hierarchy. There is one UI for every
 * Vaadin instance in a browser window. A UI may either represent an entire
 * browser window (or tab) or some part of a html page where a Vaadin
 * application is embedded.
 * <p>
 * The UI is the server side entry point for various client side features that
 * are not represented as components added to a layout, e.g notifications, sub
 * windows, and executing javascript in the browser.
 * </p>
 * <p>
 * When a new UI instance is needed, typically because the user opens a URL in a
 * browser window which points to e.g. {@link VaadinServlet}, all
 * {@link UIProvider}s registered to the current {@link VaadinSession} are
 * queried for the UI class that should be used. The selection is by default
 * based on the <code>UI</code> init parameter from web.xml.
 * </p>
 * <p>
 * After a UI has been created by the application, it is initialized using
 * {@link #init(VaadinRequest)}. This method is intended to be overridden by the
 * developer to add components to the user interface and initialize
 * non-component functionality. The component hierarchy must be initialized by
 * passing a {@link Component} with the main layout or other content of the view
 * to {@link #setContent(Component)} or to the constructor of the UI.
 * </p>
 *
 * @see #init(VaadinRequest)
 * @see UIProvider
 *
 * @since 7.0
 */
@Tag("div")
@Implemented("Many features missing. Push + more...")
public abstract class UI extends CssLayout
        implements PollNotifier, Focusable, Layer {

    /**
     * The application to which this UI belongs
     */
    private volatile VaadinSession session;

    /**
     * The id of this UI, used to find the server side instance of the UI form
     * which a request originates. A negative value indicates that the UI id has
     * not yet been assigned by the Application.
     *
     * @see VaadinSession#getNextUIid()
     */
    private int uiId = -1;

    /** Identifies the click event */
    private ConnectorTracker connectorTracker = new ConnectorTracker(this);

    private Page page = new Page(this, getState(false).pageState);

    private LoadingIndicatorConfiguration loadingIndicatorConfiguration = new LoadingIndicatorConfigurationImpl(
            this);

    private UIServerRpc rpc = new UIServerRpc() {
        @Override
        public void click(MouseEventDetails mouseDetails) {
            fireEvent(new ClickEvent(UI.this, mouseDetails));
        }

        @Override
        public void resize(int viewWidth, int viewHeight, int windowWidth,
                int windowHeight) {
            // TODO We're not doing anything with the view dimensions
            getPage().updateBrowserWindowSize(windowWidth, windowHeight, true);
        }

        @Override
        public void poll() {
            fireEvent(new PollEvent(UI.this));
        }
    };
    private DebugWindowServerRpc debugRpc = new DebugWindowServerRpc() {
        @Override
        public void showServerDebugInfo(Connector connector) {
            String info = ConnectorHelper
                    .getDebugInformation((ClientConnector) connector);
            getLogger().info(info);
        }

        @Override
        public void showServerDesign(Connector connector) {
            if (!(connector instanceof Component)) {
                getLogger().severe("Tried to output declarative design for "
                        + connector + ", which is not a component");
                return;
            }
            if (connector instanceof UI) {
                // We want to see the content of the UI, so we can add it to
                // another UI or component container
                connector = ((UI) connector).getContent();
            }

            // FIXME output element hierarchy

        }
    };

    /**
     * Timestamp keeping track of the last heartbeat of this UI. Updated to the
     * current time whenever the application receives a heartbeat or UIDL
     * request from the client for this UI.
     */
    private long lastHeartbeatTimestamp = System.currentTimeMillis();

    private boolean closing = false;

    private PushConfiguration pushConfiguration = new PushConfigurationImpl(
            this);
    private ReconnectDialogConfiguration reconnectDialogConfiguration = new ReconnectDialogConfigurationImpl(
            this);

    private NotificationConfiguration notificationConfiguration = new NotificationConfigurationImpl(
            this);

    /**
     * Tracks which message from the client should come next. First message from
     * the client has id 0.
     */
    private int lastProcessedClientToServerId = -1;

    private Root root;

    /**
     * Creates a new empty UI without a caption. The content of the UI must be
     * set by calling {@link #setContent(Component)} before using the UI.
     */
    public UI() {
        this(null);
    }

    public static class Root extends AbstractComponent {
        private RootNode rootNode;

        public Root() {
            rootNode = new RootNode();
            rootNode.put("containerElement", getElement().getNode());
        }

        public void addLayer(Layer layer) {
            getElement().appendChild(layer.getElement());
            layer.getElement().addClass("layer");
            layer.getElement().setStyle("z-index", layer.getLayerIndex() + "");

        }

        public List<Layer> getLayers() {
            return (List) getChildComponents();
        }

        public RootNode getRootNode() {
            return rootNode;
        }

        public Layer getLayer(int layerIndex) {
            for (Layer layer : getLayers()) {
                if (layer.getLayerIndex() == layerIndex) {
                    return layer;
                }
            }
            return null;
        }

        public Layer createLayer(int layerIndex) {
            assert getLayer(layerIndex) == null;

            Layer layer = new LayerImpl(layerIndex);
            addLayer(layer);
            return layer;
        }

        @Override
        protected VaadinSession getSession() {
            return getUI().getSession();
        }

        @Override
        public UI getUI() {
            return (UI) getLayer(1);
        }

        public Layer ensureLayer(int layerIndex) {
            Layer layer = getLayer(layerIndex);
            if (layer == null) {
                layer = createLayer(layerIndex);
            }
            return layer;

        }
    }

    public static class LayerImpl extends CssLayout implements Layer {
        private int layerIndex;

        public LayerImpl(int layerIndex) {
            this.layerIndex = layerIndex;
        }

        @Override
        public int getLayerIndex() {
            return layerIndex;
        }
    }

    /**
     * Creates a new UI with the given component (often a layout) as its
     * content.
     *
     * @param content
     *            the component to use as this UIs content.
     *
     * @see #setContent(Component)
     */
    public UI(Component content) {
        root = new Root();
        root.addLayer(this);
        getElement().addClass("ui").addClass("v-scrollable");
        registerRpc(rpc);
        registerRpc(debugRpc);
        setSizeFull();

        if (content != null) {
            addComponent(content);
        }

        com.vaadin.ui.JavaScript js = getPage().getJavaScript();
        js.addFunction("vEvent", json -> {
            int nodeId = (int) json.getNumber(0);
            String eventType = json.getString(1);
            JsonObject eventData = json.getObject(2);

            StateNode elementStateNode = getRoot().rootNode.getById(nodeId);

            ElementTemplate template = null;
            if (elementStateNode.containsKey(Keys.TAG)) {
                template = BasicElementTemplate.get();
            } else if (elementStateNode.containsKey(Keys.OVERRIDE_TEMPLATE)) {
                template = (ElementTemplate) elementStateNode
                        .get(Keys.OVERRIDE_TEMPLATE);
                elementStateNode = elementStateNode.getParent();
            }

            if (template == null) {
                throw new RuntimeException("Unable to find template for node");
            }
            Element e = Element.getElement(template, elementStateNode);
            e.dispatchEvent(eventType, eventData);
        });

        js.addFunction("vModelChange", this::handleModelChange);
        js.addFunction("vTemplateEvent", this::handleTemplateEvent);
    }

    @Override
    public int getLayerIndex() {
        return Layer.UI_LAYER_INDEX;
    }

    private void handleModelChange(JsonArray json) {
        RootNode rootNode = root.getRootNode();
        if (rootNode.isDirty()) {
            throw new RuntimeException(
                    "Applying model changes from the client while the tree is dirty is not yet supported.");
        }

        for (int i = 0; i < json.length(); i++) {
            JsonObject change = json.getObject(i);
            int nodeId = (int) change.getNumber("id");
            StateNode node = rootNode.getById(nodeId);
            if (node == null) {
                throw new RuntimeException(
                        "Got change for missing node: " + change.toJson());
            }

            String type = change.getString("type");
            switch (type) {
            case "put": {
                String key = change.getString("key");
                JsonValue value = change.get("value");
                Object decodedValue = JsonConverter
                        .fromJson(JsonConverter.findType(value), value);
                node.put(key, decodedValue);
                break;
            }
            default:
                throw new RuntimeException(
                        "Change type not yet? supported: " + change.toJson());
            }
        }

        // Flush changes without producing any changes to send to the client
        rootNode.commit();
    }

    private void handleTemplateEvent(JsonArray json) {
        int nodeId = (int) json.getNumber(0);
        String methodName = json.getString(2);
        JsonArray parameters = json.getArray(3);

        StateNode node = root.getRootNode().getById(nodeId);
        if (node == null) {
            getLogger().warning(
                    "Got template event for non-existing node id " + nodeId);
            return;
        }

        TemplateCallbackHandler templateEventHandler = node
                .get(TemplateCallbackHandler.class);

        if (templateEventHandler == null) {
            throw new RuntimeException(
                    "No template event handler found for node " + node.getId());
        }

        templateEventHandler.handleCallback(node, methodName, parameters);
    }

    @Override
    protected UIState getState() {
        return (UIState) super.getState();
    }

    @Override
    protected UIState getState(boolean markAsDirty) {
        return (UIState) super.getState(markAsDirty);
    }

    @Override
    public Class<? extends UIState> getStateType() {
        // This is a workaround for a problem with creating the correct state
        // object during build
        return UIState.class;
    }

    /**
     * Overridden to return a value instead of referring to the parent.
     *
     * @return this UI
     *
     * @see com.vaadin.ui.AbstractComponent#getUI()
     */
    @Override
    public UI getUI() {
        return this;
    }

    /**
     * Gets the application object to which the component is attached.
     *
     * <p>
     * The method will return {@code null} if the component is not currently
     * attached to an application.
     * </p>
     *
     * <p>
     * Getting a null value is often a problem in constructors of regular
     * components and in the initializers of custom composite components. A
     * standard workaround is to use {@link VaadinSession#getCurrent()} to
     * retrieve the application instance that the current request relates to.
     * Another way is to move the problematic initialization to
     * {@link #attach()}, as described in the documentation of the method.
     * </p>
     *
     * @return the parent application of the component or <code>null</code>.
     * @see #attach()
     */
    @Override
    public VaadinSession getSession() {
        return session;
    }

    @Deprecated
    public void setContent(Component content) {
        removeAllComponents();
        if (content != null) {
            addComponent(content);
        }
    }

    @Deprecated
    public Component getContent() {
        if (getComponentCount() == 1) {
            return getChildComponents().get(0);
        } else if (getComponentCount() == 0) {
            return null;
        } else {
            getLogger().warning("Using getContent() when "
                    + getClass().getSimpleName()
                    + " has multiple child components may yield unexpected results");
            return getChildComponents().get(0);
        }
    }

    /**
     * Sets the session to which this UI is assigned.
     * <p>
     * This method is for internal use by the framework. To explicitly close a
     * UI, see {@link #close()}.
     * </p>
     *
     * @param session
     *            the session to set
     *
     * @throws IllegalStateException
     *             if the session has already been set
     *
     * @see #getSession()
     */
    public void setSession(VaadinSession session) {
        if (session == null && this.session == null) {
            throw new IllegalStateException(
                    "Session should never be set to null when UI.session is already null");
        } else if (session != null && this.session != null) {
            throw new IllegalStateException(
                    "Session has already been set. Old session: "
                            + getSessionDetails(this.session)
                            + ". New session: " + getSessionDetails(session)
                            + ".");
        } else {
            if (session == null) {
                try {
                    detach();
                } catch (Exception e) {
                    getLogger().log(Level.WARNING,
                            "Error while detaching UI from session", e);
                }
                // Disable push when the UI is detached. Otherwise the
                // push connection and possibly VaadinSession will live on.
                getPushConfiguration().setPushMode(PushMode.DISABLED);
                setPushConnection(null);
            }
            this.session = session;
        }

        if (session != null) {
            attach();
        }
    }

    private static String getSessionDetails(VaadinSession session) {
        if (session == null) {
            return null;
        } else {
            return session.toString() + " for "
                    + session.getService().getServiceName();
        }
    }

    /**
     * Gets the id of the UI, used to identify this UI within its application
     * when processing requests. The UI id should be present in every request to
     * the server that originates from this UI.
     * {@link VaadinService#findUI(VaadinRequest)} uses this id to find the
     * route to which the request belongs.
     * <p>
     * This method is not intended to be overridden. If it is overridden, care
     * should be taken since this method might be called in situations where
     * {@link UI#getCurrent()} does not return this UI.
     *
     * @return the id of this UI
     */
    public int getUIId() {
        return uiId;
    }

    /**
     * Adds a window as a subwindow inside this UI. To open a new browser window
     * or tab, you should instead use a {@link UIProvider}.
     *
     * @param window
     * @throws IllegalArgumentException
     *             if the window is already added to an application
     * @throws NullPointerException
     *             if the given <code>Window</code> is <code>null</code>.
     */
    public void addWindow(Window window)
            throws IllegalArgumentException, NullPointerException {

        if (window == null) {
            throw new NullPointerException("Argument must not be null");
        }

        if (window.isAttached()) {
            throw new IllegalArgumentException(
                    "Window is already attached to an application.");
        }

        attachWindow(window);
    }

    /**
     * Helper method to attach a window.
     *
     * @param w
     *            the window to add
     */
    private void attachWindow(Window w) {
        getWindowLayer().getElement().appendChild(w.getElement());
        // windows.add(w);
        markAsDirty();
    }

    /**
     * Remove the given subwindow from this UI.
     *
     * Since Vaadin 6.5, {@link Window.CloseListener}s are called also when
     * explicitly removing a window by calling this method.
     *
     * Since Vaadin 6.5, returns a boolean indicating if the window was removed
     * or not.
     *
     * @param window
     *            Window to be removed.
     * @return true if the subwindow was removed, false otherwise
     */
    public boolean removeWindow(Window window) {
        getWindowLayer().removeComponent(window);

        // TODO Move to window
        window.fireClose();

        return true;
    }

    /**
     * Gets all the windows added to this UI.
     *
     * @return an unmodifiable collection of windows
     */
    public Collection<Window> getWindows() {
        List<Window> windows = new ArrayList<>();
        for (Component c : getWindowLayer().getChildComponents()) {
            if (c instanceof Window) {
                windows.add((Window) c);
            }
        }
        return windows;
    }

    @Override
    public void focus() {
        super.focus();
    }

    /**
     * Component that should be focused after the next repaint. Null if no focus
     * change should take place.
     */
    private Focusable pendingFocus;

    private boolean resizeLazy = false;

    private Navigator navigator;

    private PushConnection pushConnection = null;

    private LocaleService localeService = new LocaleService(this,
            getState(false).localeServiceState);

    private String embedId;

    private Map<Integer, ElementTemplate> sentTemplates = new HashMap<>();

    private Set<Class<? extends ClientConnector>> resourcesHandled = new HashSet<>();

    /**
     * This method is used by Component.Focusable objects to request focus to
     * themselves. Focus renders must be handled at window level (instead of
     * Component.Focusable) due we want the last focused component to be focused
     * in client too. Not the one that is rendered last (the case we'd get if
     * implemented in Focusable only).
     *
     * To focus component from Vaadin application, use Focusable.focus(). See
     * {@link Focusable}.
     *
     * @param focusable
     *            to be focused on next paint
     */
    public void setFocusedComponent(Focusable focusable) {
        pendingFocus = focusable;
        markAsDirty();
    }

    /**
     * Scrolls any component between the component and UI to a suitable position
     * so the component is visible to the user. The given component must belong
     * to this UI.
     *
     * @param component
     *            the component to be scrolled into view
     * @throws IllegalArgumentException
     *             if {@code component} does not belong to this UI
     */
    public void scrollIntoView(Component component)
            throws IllegalArgumentException {
        if (component.getUI() != this) {
            throw new IllegalArgumentException(
                    "The component to scroll into view must belong to this UI.");
        }
        component.getElement().scrollIntoView();
    }

    /**
     * Internal initialization method, should not be overridden. This method is
     * not declared as final because that would break compatibility with e.g.
     * CDI.
     *
     * @param request
     *            the initialization request
     * @param uiId
     *            the id of the new ui
     * @param embedId
     *            the embed id of this UI, or <code>null</code> if no id is
     *            known
     *
     * @see #getUIId()
     * @see #getEmbedId()
     */
    public void doInit(VaadinRequest request, int uiId, String embedId) {
        if (this.uiId != -1) {
            String message = "This UI instance is already initialized (as UI id "
                    + this.uiId
                    + ") and can therefore not be initialized again (as UI id "
                    + uiId + "). ";

            if (getSession() != null
                    && !getSession().equals(VaadinSession.getCurrent())) {
                message += "Furthermore, it is already attached to another VaadinSession. ";
            }
            message += "Please make sure you are not accidentally reusing an old UI instance.";

            throw new IllegalStateException(message);
        }
        this.uiId = uiId;
        this.embedId = embedId;

        // Actual theme - used for finding CustomLayout templates
        setTheme(request.getParameter("theme"));

        getPage().init(request);

        // Call the init overridden by the application developer
        init(request);

        Navigator navigator = getNavigator();
        if (navigator != null) {
            // Kickstart navigation if a navigator was attached in init()
            navigator.navigateTo(navigator.getState());
        }
    }

    /**
     * Initializes this UI. This method is intended to be overridden by
     * subclasses to build the view and configure non-component functionality.
     * Performing the initialization in a constructor is not suggested as the
     * state of the UI is not properly set up when the constructor is invoked.
     * <p>
     * The {@link VaadinRequest} can be used to get information about the
     * request that caused this UI to be created.
     * </p>
     *
     * @param request
     *            the Vaadin request that caused this UI to be created
     */
    protected abstract void init(VaadinRequest request);

    /**
     * Sets the thread local for the current UI. This method is used by the
     * framework to set the current application whenever a new request is
     * processed and it is cleared when the request has been processed.
     * <p>
     * The application developer can also use this method to define the current
     * UI outside the normal request handling, e.g. when initiating custom
     * background threads.
     * <p>
     * The UI is stored using a weak reference to avoid leaking memory in case
     * it is not explicitly cleared.
     *
     * @param ui
     *            the UI to register as the current UI
     *
     * @see #getCurrent()
     * @see ThreadLocal
     */
    public static void setCurrent(UI ui) {
        CurrentInstance.setInheritable(UI.class, ui);
    }

    /**
     * Gets the currently used UI. The current UI is automatically defined when
     * processing requests to the server. In other cases, (e.g. from background
     * threads), the current UI is not automatically defined.
     * <p>
     * The UI is stored using a weak reference to avoid leaking memory in case
     * it is not explicitly cleared.
     *
     * @return the current UI instance if available, otherwise <code>null</code>
     *
     * @see #setCurrent(UI)
     */
    public static UI getCurrent() {
        return CurrentInstance.get(UI.class);
    }

    /**
     * Set top offset to which the UI should scroll to.
     *
     * @param scrollTop
     */
    public void setScrollTop(int scrollTop) {
        if (scrollTop < 0) {
            throw new IllegalArgumentException(
                    "Scroll offset must be at least 0");
        }
        getElement().setScrollTop(scrollTop);
    }

    /**
     * Set left offset to which the UI should scroll to.
     *
     * @param scrollLeft
     */
    public void setScrollLeft(int scrollLeft) {
        if (scrollLeft < 0) {
            throw new IllegalArgumentException(
                    "Scroll offset must be at least 0");
        }
        getElement().setScrollLeft(scrollLeft);
    }

    /**
     * Should resize operations be lazy, i.e. should there be a delay before
     * layout sizes are recalculated and resize events are sent to the server.
     * Speeds up resize operations in slow UIs with the penalty of slightly
     * decreased usability.
     * <p>
     * Default value: <code>false</code>
     * </p>
     * <p>
     * When there are active window resize listeners, lazy resize mode should be
     * used to avoid a large number of events during resize.
     * </p>
     *
     * @param resizeLazy
     *            true to use a delay before recalculating sizes, false to
     *            calculate immediately.
     */
    public void setResizeLazy(boolean resizeLazy) {
        this.resizeLazy = resizeLazy;
        markAsDirty();
    }

    /**
     * Checks whether lazy resize is enabled.
     *
     * @return <code>true</code> if lazy resize is enabled, <code>false</code>
     *         if lazy resize is not enabled
     */
    public boolean isResizeLazy() {
        return resizeLazy;
    }

    /**
     * Add a click listener to the UI. The listener is called whenever the user
     * clicks inside the UI. Also when the click targets a component inside the
     * UI, provided the targeted component does not prevent the click event from
     * propagating.
     *
     * Use {@link #removeListener(ClickListener)} to remove the listener.
     *
     * @param listener
     *            The listener to add
     */
    public void addClickListener(ClickListener listener) {
        addListener(ClickEvent.class, listener);
    }

    /**
     * Remove a click listener from the UI. The listener should earlier have
     * been added using {@link #addListener(ClickListener)}.
     *
     * @param listener
     *            The listener to remove
     */
    public void removeClickListener(ClickListener listener) {
        removeListener(ClickEvent.class, listener);
    }

    @Override
    public boolean isConnectorEnabled() {
        // TODO How can a UI be invisible? What does it mean?
        return isVisible() && isEnabled();
    }

    public ConnectorTracker getConnectorTracker() {
        return connectorTracker;
    }

    public Page getPage() {
        return page;
    }

    /**
     * Returns the navigator attached to this UI or null if there is no
     * navigator.
     *
     * @return
     */
    public Navigator getNavigator() {
        return navigator;
    }

    /**
     * For internal use only.
     *
     * @param navigator
     */
    public void setNavigator(Navigator navigator) {
        this.navigator = navigator;
    }

    /**
     * Setting the caption of a UI is not supported. To set the title of the
     * HTML page, use Page.setTitle
     *
     * @deprecated As of 7.0, use {@link Page#setTitle(String)}
     */
    @Override
    @Deprecated
    public void setCaption(String caption) {
        throw new UnsupportedOperationException(
                "You can not set the title of a UI. To set the title of the HTML page, use Page.setTitle");
    }

    /**
     * Returns the timestamp of the last received heartbeat for this UI.
     * <p>
     * This method is not intended to be overridden. If it is overridden, care
     * should be taken since this method might be called in situations where
     * {@link UI#getCurrent()} does not return this UI.
     *
     * @see VaadinService#closeInactiveUIs(VaadinSession)
     *
     * @return The time the last heartbeat request occurred, in milliseconds
     *         since the epoch.
     */
    public long getLastHeartbeatTimestamp() {
        return lastHeartbeatTimestamp;
    }

    /**
     * Sets the last heartbeat request timestamp for this UI. Called by the
     * framework whenever the application receives a valid heartbeat request for
     * this UI.
     * <p>
     * This method is not intended to be overridden. If it is overridden, care
     * should be taken since this method might be called in situations where
     * {@link UI#getCurrent()} does not return this UI.
     *
     * @param lastHeartbeat
     *            The time the last heartbeat request occurred, in milliseconds
     *            since the epoch.
     */
    public void setLastHeartbeatTimestamp(long lastHeartbeat) {
        lastHeartbeatTimestamp = lastHeartbeat;
    }

    /**
     * Gets the theme currently in use by this UI
     *
     * @return the theme name
     */
    public String getTheme() {
        return getState(false).theme;
    }

    /**
     * Sets the theme currently in use by this UI
     * <p>
     * Calling this method will remove the old theme (CSS file) from the
     * application and add the new theme.
     * <p>
     * Note that this method is NOT SAFE to call in a portal environment or
     * other environment where there are multiple UIs on the same page. The old
     * CSS file will be removed even if there are other UIs on the page which
     * are still using it.
     *
     * @since 7.3
     * @param theme
     *            The new theme name
     */
    public void setTheme(String theme) {
        if (theme == null) {
            getState().theme = null;
        } else {
            getState().theme = VaadinServlet.stripSpecialChars(theme);
        }
    }

    /**
     * Marks this UI to be {@link #detach() detached} from the session at the
     * end of the current request, or the next request if there is no current
     * request (if called from a background thread, for instance.)
     * <p>
     * The UI is detached after the response is sent, so in the current request
     * it can still update the client side normally. However, after the response
     * any new requests from the client side to this UI will cause an error, so
     * usually the client should be asked, for instance, to reload the page
     * (serving a fresh UI instance), to close the page, or to navigate
     * somewhere else.
     * <p>
     * Note that this method is strictly for users to explicitly signal the
     * framework that the UI should be detached. Overriding it is not a reliable
     * way to catch UIs that are to be detached. Instead, {@code UI.detach()}
     * should be overridden or a {@link DetachListener} used.
     */
    public void close() {
        closing = true;

        boolean sessionExpired = (session == null
                || session.getState() != State.OPEN);
        getRpcProxy(UIClientRpc.class).uiClosed(sessionExpired);
        if (getPushConnection() != null) {
            // Push the Rpc to the client. The connection will be closed when
            // the UI is detached and cleaned up.

            // Can't use UI.push() directly since it checks for a valid session
            if (session != null) {
                session.getService().runPendingAccessTasks(session);
            }
            getPushConnection().push();
        }

    }

    /**
     * Returns whether this UI is marked as closed and is to be detached.
     * <p>
     * This method is not intended to be overridden. If it is overridden, care
     * should be taken since this method might be called in situations where
     * {@link UI#getCurrent()} does not return this UI.
     *
     * @see #close()
     *
     * @return whether this UI is closing.
     */
    public boolean isClosing() {
        return closing;
    }

    /**
     * Called after the UI is added to the session. A UI instance is attached
     * exactly once, before its {@link #init(VaadinRequest) init} method is
     * called.
     *
     * @see Component#attach
     */
    @Override
    public void attach() {
        super.attach();
        getLocaleService().addLocale(getLocale());
    }

    /**
     * Called before the UI is removed from the session. A UI instance is
     * detached exactly once, either:
     * <ul>
     * <li>after it is explicitly {@link #close() closed}.
     * <li>when its session is closed or expires
     * <li>after three missed heartbeat requests.
     * </ul>
     * <p>
     * Note that when a UI is detached, any changes made in the {@code detach}
     * methods of any children or {@link DetachListener}s that would be
     * communicated to the client are silently ignored.
     */
    @Override
    public void detach() {
        super.detach();
    }

    @Override
    public void setTabIndex(int tabIndex) {
        getState().tabIndex = tabIndex;
    }

    @Override
    public int getTabIndex() {
        return getState(false).tabIndex;
    }

    /**
     * Locks the session of this UI and runs the provided Runnable right away.
     * <p>
     * It is generally recommended to use {@link #access(Runnable)} instead of
     * this method for accessing a session from a different thread as
     * {@link #access(Runnable)} can be used while holding the lock of another
     * session. To avoid causing deadlocks, this methods throws an exception if
     * it is detected than another session is also locked by the current thread.
     * </p>
     * <p>
     * This method behaves differently than {@link #access(Runnable)} in some
     * situations:
     * <ul>
     * <li>If the current thread is currently holding the lock of the session,
     * {@link #accessSynchronously(Runnable)} runs the task right away whereas
     * {@link #access(Runnable)} defers the task to a later point in time.</li>
     * <li>If some other thread is currently holding the lock for the session,
     * {@link #accessSynchronously(Runnable)} blocks while waiting for the lock
     * to be available whereas {@link #access(Runnable)} defers the task to a
     * later point in time.</li>
     * </ul>
     * </p>
     *
     * @since 7.1
     *
     * @param runnable
     *            the runnable which accesses the UI
     * @throws UIDetachedException
     *             if the UI is not attached to a session (and locking can
     *             therefore not be done)
     * @throws IllegalStateException
     *             if the current thread holds the lock for another session
     *
     * @see #access(Runnable)
     * @see VaadinSession#accessSynchronously(Runnable)
     */
    public void accessSynchronously(Runnable runnable)
            throws UIDetachedException {
        Map<Class<?>, CurrentInstance> old = null;

        VaadinSession session = getSession();

        if (session == null) {
            throw new UIDetachedException();
        }

        VaadinService.verifyNoOtherSessionLocked(session);

        session.lock();
        try {
            if (getSession() == null) {
                // UI was detached after fetching the session but before we
                // acquired the lock.
                throw new UIDetachedException();
            }
            old = CurrentInstance.setCurrent(this);
            runnable.run();
        } finally {
            session.unlock();
            if (old != null) {
                CurrentInstance.restoreInstances(old);
            }
        }

    }

    /**
     * Provides exclusive access to this UI from outside a request handling
     * thread.
     * <p>
     * The given runnable is executed while holding the session lock to ensure
     * exclusive access to this UI. If the session is not locked, the lock will
     * be acquired and the runnable is run right away. If the session is
     * currently locked, the runnable will be run before that lock is released.
     * </p>
     * <p>
     * RPC handlers for components inside this UI do not need to use this method
     * as the session is automatically locked by the framework during RPC
     * handling.
     * </p>
     * <p>
     * Please note that the runnable might be invoked on a different thread or
     * later on the current thread, which means that custom thread locals might
     * not have the expected values when the runnable is executed. Inheritable
     * values in {@link CurrentInstance} will have the same values as when this
     * method was invoked. {@link UI#getCurrent()},
     * {@link VaadinSession#getCurrent()} and {@link VaadinService#getCurrent()}
     * are set according to this UI before executing the runnable.
     * Non-inheritable CurrentInstance values including
     * {@link VaadinService#getCurrentRequest()} and
     * {@link VaadinService#getCurrentResponse()} will not be defined.
     * </p>
     * <p>
     * The returned future can be used to check for task completion and to
     * cancel the task.
     * </p>
     *
     * @see #getCurrent()
     * @see #accessSynchronously(Runnable)
     * @see VaadinSession#access(Runnable)
     * @see VaadinSession#lock()
     *
     * @since 7.1
     *
     * @param runnable
     *            the runnable which accesses the UI
     * @throws UIDetachedException
     *             if the UI is not attached to a session (and locking can
     *             therefore not be done)
     * @return a future that can be used to check for task completion and to
     *         cancel the task
     */
    public Future<Void> access(final Runnable runnable) {
        VaadinSession session = getSession();

        if (session == null) {
            throw new UIDetachedException();
        }

        return session.access(new ErrorHandlingRunnable() {
            @Override
            public void run() {
                accessSynchronously(runnable);
            }

            @Override
            public void handleError(Exception exception) {
                try {
                    if (runnable instanceof ErrorHandlingRunnable) {
                        ErrorHandlingRunnable errorHandlingRunnable = (ErrorHandlingRunnable) runnable;

                        errorHandlingRunnable.handleError(exception);
                    } else {
                        ConnectorErrorEvent errorEvent = new ConnectorErrorEvent(
                                UI.this, exception);

                        ErrorHandler errorHandler = com.vaadin.server.ErrorEvent
                                .findErrorHandler(UI.this);

                        if (errorHandler == null) {
                            errorHandler = new DefaultErrorHandler();
                        }

                        errorHandler.error(errorEvent);
                    }
                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, e.getMessage(), e);
                }
            }
        });
    }

    /**
     * Retrieves the object used for configuring notifications.
     *
     * @return The instance used for notification configuration
     */
    public NotificationConfiguration getNotificationConfiguration() {
        return notificationConfiguration;
    }

    /**
     * Retrieves the object used for configuring the loading indicator.
     *
     * @return The instance used for configuring the loading indicator
     */
    public LoadingIndicatorConfiguration getLoadingIndicatorConfiguration() {
        return loadingIndicatorConfiguration;
    }

    /**
     * Pushes the pending changes and client RPC invocations of this UI to the
     * client-side.
     * <p>
     * If push is enabled, but the push connection is not currently open, the
     * push will be done when the connection is established.
     * <p>
     * As with all UI methods, the session must be locked when calling this
     * method. It is also recommended that {@link UI#getCurrent()} is set up to
     * return this UI since writing the response may invoke logic in any
     * attached component or extension. The recommended way of fulfilling these
     * conditions is to use {@link #access(Runnable)}.
     *
     * @throws IllegalStateException
     *             if push is disabled.
     * @throws UIDetachedException
     *             if this UI is not attached to a session.
     *
     * @see #getPushConfiguration()
     *
     * @since 7.1
     */
    public void push() {
        VaadinSession session = getSession();

        if (session == null) {
            throw new UIDetachedException("Cannot push a detached UI");
        }
        assert session.hasLock();

        if (!getPushConfiguration().getPushMode().isEnabled()) {
            throw new IllegalStateException("Push not enabled");
        }
        assert pushConnection != null;

        /*
         * Purge the pending access queue as it might mark a connector as dirty
         * when the push would otherwise be ignored because there are no changes
         * to push.
         */
        session.getService().runPendingAccessTasks(session);

        if (!getConnectorTracker().hasDirtyConnectors()) {
            // Do not push if there is nothing to push
            return;
        }

        pushConnection.push();
    }

    /**
     * Returns the internal push connection object used by this UI. This method
     * should only be called by the framework.
     * <p>
     * This method is not intended to be overridden. If it is overridden, care
     * should be taken since this method might be called in situations where
     * {@link UI#getCurrent()} does not return this UI.
     *
     * @return the push connection used by this UI, or {@code null} if push is
     *         not available.
     */
    public PushConnection getPushConnection() {
        assert!(getPushConfiguration().getPushMode().isEnabled()
                && pushConnection == null);
        return pushConnection;
    }

    /**
     * Sets the internal push connection object used by this UI. This method
     * should only be called by the framework.
     * <p>
     * The {@code pushConnection} argument must be non-null if and only if
     * {@code getPushConfiguration().getPushMode().isEnabled()}.
     *
     * @param pushConnection
     *            the push connection to use for this UI
     */
    public void setPushConnection(PushConnection pushConnection) {
        // If pushMode is disabled then there should never be a pushConnection;
        // if enabled there should always be
        assert(pushConnection == null)
                ^ getPushConfiguration().getPushMode().isEnabled();

        if (pushConnection == this.pushConnection) {
            return;
        }

        if (this.pushConnection != null && this.pushConnection.isConnected()) {
            this.pushConnection.disconnect();
        }

        this.pushConnection = pushConnection;
    }

    /**
     * Sets the interval with which the UI should poll the server to see if
     * there are any changes. Polling is disabled by default.
     * <p>
     * Note that it is possible to enable push and polling at the same time but
     * it should not be done to avoid excessive server traffic.
     * </p>
     * <p>
     * Add-on developers should note that this method is only meant for the
     * application developer. An add-on should not set the poll interval
     * directly, rather instruct the user to set it.
     * </p>
     *
     * @param intervalInMillis
     *            The interval (in ms) with which the UI should poll the server
     *            or -1 to disable polling
     */
    public void setPollInterval(int intervalInMillis) {
        getState().pollInterval = intervalInMillis;
    }

    /**
     * Returns the interval with which the UI polls the server.
     *
     * @return The interval (in ms) with which the UI polls the server or -1 if
     *         polling is disabled
     */
    public int getPollInterval() {
        return getState(false).pollInterval;
    }

    @Override
    public void addPollListener(PollListener listener) {
        addListener(PollEvent.class, listener);
    }

    @Override
    public void removePollListener(PollListener listener) {
        removeListener(PollEvent.class, listener);
    }

    /**
     * Retrieves the object used for configuring the push channel.
     *
     * @since 7.1
     * @return The instance used for push configuration
     */
    public PushConfiguration getPushConfiguration() {
        return pushConfiguration;
    }

    /**
     * Retrieves the object used for configuring the reconnect dialog.
     *
     * @since 7.6
     * @return The instance used for reconnect dialog configuration
     */
    public ReconnectDialogConfiguration getReconnectDialogConfiguration() {
        return reconnectDialogConfiguration;
    }

    /**
     * Get the label that is added to the container element, where notification
     * and dialogs are added to.
     *
     * @return the label of the container
     */
    public String getOverlayContainerLabel() {
        return getState(false).overlayContainerLabel;
    }

    /**
     * Sets the label that is added to the container element, where
     * notifications and dialogs are added to.
     * <p>
     * This is helpful for users of assistive devices, as this element is
     * reachable for them.
     * </p>
     *
     * @param overlayContainerLabel
     *            label to use for the container
     */
    public void setOverlayContainerLabel(String overlayContainerLabel) {
        getState().overlayContainerLabel = overlayContainerLabel;
    }

    /**
     * Returns the locale service which handles transmission of Locale data to
     * the client.
     *
     * @since 7.1
     * @return The LocaleService for this UI
     */
    public LocaleService getLocaleService() {
        return localeService;
    }

    private static Logger getLogger() {
        return Logger.getLogger(UI.class.getName());
    }

    /**
     * Gets a string the uniquely distinguishes this UI instance based on where
     * it is embedded. The embed identifier is based on the
     * <code>window.name</code> DOM attribute of the browser window where the UI
     * is displayed and the id of the div element where the UI is embedded.
     *
     * @since 7.2
     * @return the embed id for this UI, or <code>null</code> if no id known
     */
    public String getEmbedId() {
        return embedId;
    }

    /**
     * Gets the last processed server message id.
     *
     * Used internally for communication tracking.
     *
     * @return lastProcessedServerMessageId the id of the last processed server
     *         message
     */
    public int getLastProcessedClientToServerId() {
        return lastProcessedClientToServerId;
    }

    /**
     * Sets the last processed server message id.
     *
     * Used internally for communication tracking.
     *
     * @param lastProcessedServerMessageId
     *            the id of the last processed server message
     */
    public void setLastProcessedClientToServerId(
            int lastProcessedClientToServerId) {
        this.lastProcessedClientToServerId = lastProcessedClientToServerId;
    }

    /**
     * @since
     * @return
     */
    public Set<Class<? extends ClientConnector>> getResourcesHandled() {
        return resourcesHandled;
    }

    public boolean knowsTemplate(ElementTemplate template) {
        return sentTemplates.containsKey(Integer.valueOf(template.getId()));
    }

    public void registerTemplate(ElementTemplate template) {
        sentTemplates.put(Integer.valueOf(template.getId()), template);
    }

    public ElementTemplate getTemplate(int templateId) {
        return sentTemplates.get(Integer.valueOf(templateId));
    }

    private Layer getWindowLayer() {
        Layer layer = root.getLayer(Layer.WINDOW_LAYER_INDEX);
        if (layer == null) {
            layer = root.createLayer(Layer.WINDOW_LAYER_INDEX);
            layer.getElement().addClass("window");
        }
        return layer;
    }

    public Root getRoot() {
        return root;
    }

}