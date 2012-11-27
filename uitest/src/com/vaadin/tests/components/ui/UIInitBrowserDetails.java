/*
@VaadinApache2LicenseForJavaFiles@
 */

package com.vaadin.tests.components.ui;

import com.vaadin.server.Page;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.WebBrowser;
import com.vaadin.tests.components.AbstractTestUI;
import com.vaadin.ui.GridLayout;
import com.vaadin.ui.Label;

public class UIInitBrowserDetails extends AbstractTestUI {

    private GridLayout l = new GridLayout(3, 1);
    private VaadinRequest r;

    @Override
    protected void setup(VaadinRequest request) {
        r = request;
        l.setWidth("100%");
        addComponent(l);

        Page p = getPage();
        WebBrowser wb = p.getWebBrowser();

        addDetail("location", "loc", p.getLocation());

        addDetail("browser window width", "cw", p.getBrowserWindowWidth());
        addDetail("browser window height", "ch", p.getBrowserWindowHeight());
        addDetail("screen width", "sw", wb.getScreenWidth());
        addDetail("screen height", "sh", wb.getScreenHeight());

        addDetail("timezone offset", "tzo", wb.getTimezoneOffset());
        addDetail("raw timezone offset", "rtzo", wb.getRawTimezoneOffset());
        addDetail("dst saving", "dstd", wb.getDSTSavings());
        addDetail("dst in effect", "dston", wb.isDSTInEffect());
        addDetail("current date", "curdate", wb.getCurrentDate());
    }

    @Override
    public String getTestDescription() {
        return "Browser details should be available in UI init";
    }

    @Override
    protected Integer getTicketNumber() {
        return Integer.valueOf(9037);
    }

    private void addDetail(String name, String param, Object value) {
        l.addComponents(new Label(name), new Label(r.getParameter(param)),
                new Label("" + value));
    }
}