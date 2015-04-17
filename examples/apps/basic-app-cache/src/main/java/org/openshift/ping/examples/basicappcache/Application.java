package org.openshift.ping.examples.basicappcache;

import javax.annotation.Resource;
import javax.enterprise.context.Dependent;
import javax.enterprise.inject.Produces;
import javax.faces.bean.ApplicationScoped;
import javax.faces.bean.ManagedBean;

import org.infinispan.Cache;
import org.infinispan.manager.EmbeddedCacheManager;

@ManagedBean(name = "app")
@ApplicationScoped
public class Application {

    private static final String NAME = "name";

    @Produces
    @Resource(name="java:jboss/infinispan/app")
    @Dependent
    private EmbeddedCacheManager appCacheManager;

    private Cache<String, String> getCache() {
        //System.out.println("********** appCacheManager: " + appCacheManager);
        Cache<String, String> appCache = appCacheManager.getCache();//"default");
        //System.out.println("********** cache: " + appCache);
        return appCache;
    }

    public synchronized String getValue() {
        return getCache().get(NAME);
    }

    public synchronized void setValue(String value) {
        if (value != null) {
            value = value.trim();
            if (value.length() == 0) {
                value = null;
            }
        }
        if (value == null) {
            getCache().remove(NAME);
        } else {
            getCache().put(NAME, value);
        }
    }

    public void submit() {
        //System.out.println("[Submit]");
    }

}
