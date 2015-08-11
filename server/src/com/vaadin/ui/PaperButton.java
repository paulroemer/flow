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

import com.vaadin.annotations.HTML;
import com.vaadin.annotations.Tag;

/**
 * A generic button component.
 * 
 * @author Vaadin Ltd.
 * @since 3.0
 */
@SuppressWarnings("serial")
@Tag("paper-button")
@HTML("vaadin://bower_components/paper-button/paper-button.html")
public class PaperButton extends Button {

    /**
     * Creates a new push button.
     */
    public PaperButton() {
        getElement().setAttribute("raised", true);
    }

    /**
     * Creates a new push button with the given caption.
     * 
     * @param caption
     *            the Button caption.
     */
    public PaperButton(String caption) {
        this();
        setCaption(caption);
    }

}