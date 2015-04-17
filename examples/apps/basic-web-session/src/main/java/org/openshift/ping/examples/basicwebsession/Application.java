package org.openshift.ping.examples.basicwebsession;

import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;
import javax.faces.context.FacesContext;
import javax.servlet.http.HttpSession;

@ManagedBean(name = "app")
@SessionScoped
public class Application {

    private static final String NAME = "name";

    private HttpSession getHttpSession() {
        FacesContext facesContext = FacesContext.getCurrentInstance();
        //System.out.println("********** facesContext: " + facesContext);
        HttpSession httpSession = (HttpSession)facesContext.getExternalContext().getSession(false);
        //System.out.println("********** httpSession: " + httpSession);
        return httpSession;
    }

    public synchronized String getValue() {
        return (String)getHttpSession().getAttribute(NAME);
    }

    public synchronized void setValue(String value) {
        if (value != null) {
            value = value.trim();
            if (value.length() == 0) {
                value = null;
            }
        }
        if (value == null) {
            getHttpSession().removeAttribute(NAME);
        } else {
            getHttpSession().setAttribute(NAME, value);
        }
    }

    public void submit() {
        //System.out.println("[Submit]");
    }

}
