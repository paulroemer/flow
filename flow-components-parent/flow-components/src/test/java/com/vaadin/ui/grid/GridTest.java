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
package com.vaadin.ui.grid;

import org.junit.Assert;
import org.junit.Test;

import com.vaadin.data.selection.SingleSelect;

public class GridTest {
    @Test
    public void singleSelection_selectCurrent_noEvent() {
        Grid<String> grid = new Grid<>();
        grid.setItems("one", "two");

        SingleSelect<Grid<String>, String> singleSelect = grid.asSingleSelect();
        singleSelect.setValue("one");

        singleSelect.addValueChangeListener(event -> Assert
                .fail("Selection change should not be triggered"));

        singleSelect.setValue(singleSelect.getValue());
    }

}
