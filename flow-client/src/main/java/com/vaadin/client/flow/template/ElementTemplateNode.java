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
package com.vaadin.client.flow.template;

import elemental.json.JsonObject;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

/**
 * Client-side representation of an
 * {@link com.vaadin.flow.template.angular.ElementTemplateNode} received from the
 * server. The properties are based on the output of
 * {@link com.vaadin.flow.template.angular.ElementTemplateNode#populateJson(JsonObject)}
 * on the server.
 *
 * @author Vaadin Ltd
 */
@JsType(isNative = true)
public interface ElementTemplateNode extends TemplateNode {
    /**
     * Gets the tag name of the described element.
     *
     * @return the tag name
     */
    @JsProperty
    String getTag();

    /**
     * Gets a map of property bindings, as a JSON object.
     *
     * @see Binding
     * @return a JSON object mapping property names to bindings, or
     *         <code>null</code> if not present.
     */
    @JsProperty
    JsonObject getProperties();

    /**
     * Gets a map of attribute bindings, as a JSON object.
     *
     * @see Binding
     * @return a JSON object mapping attribute names to bindings, or
     *         <code>null</code> if not present.
     */
    @JsProperty
    JsonObject getAttributes();

    /**
     * Gets a map of class name bindings, as a JSON object.
     *
     * @see Binding
     * @return a JSON object mapping class names to bindings, or
     *         <code>null</code> if not present..
     */
    @JsProperty
    JsonObject getClassNames();

    /**
     * Gets a map of event handlers, as a JSON object.
     *
     * @see Binding
     * @return a JSON object mapping event names to event handlers, or
     *         <code>null</code> if not present..
     */
    @JsProperty
    JsonObject getEventHandlers();
}
