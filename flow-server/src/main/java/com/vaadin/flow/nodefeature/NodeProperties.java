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
package com.vaadin.flow.nodefeature;

import com.vaadin.flow.nodefeature.BasicTypeValue;
import com.vaadin.flow.nodefeature.ElementData;
import com.vaadin.flow.nodefeature.ShadowRootData;
import com.vaadin.flow.nodefeature.TemplateMap;
import com.vaadin.flow.nodefeature.TextNodeMap;

/**
 * Various node properties' ids.
 *
 * @author Vaadin Ltd.
 */
public final class NodeProperties {

    /**
     * Key for {@link ElementData#getTag()}.
     */
    public static final String TAG = "tag";
    /**
     * Key for {@link TextNodeMap#getText()}.
     */
    public static final String TEXT = "text";
    /**
     * Key for {@link ShadowRootData}.
     */
    public static final String SHADOW_ROOT = "shadowRoot";
    /**
     * Key for {@link TemplateMap#getRootTemplate()}.
     */
    public static final String ROOT_TEMPLATE_ID = "root";
    /**
     * Key for {@link TemplateMap#getModelDescriptor()}.
     */
    public static final String MODEL_DESCRIPTOR = "descriptor";
    /**
     * Key for {@link BasicTypeValue#getValue()}.
     */
    public static final String VALUE = "value";

    /** Key for id property. */
    public static final String ID = "id";

    private NodeProperties() {
    }
}
