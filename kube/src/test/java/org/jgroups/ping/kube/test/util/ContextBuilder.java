package org.jgroups.ping.kube.test.util;

import org.jgroups.ping.kube.Context;

public class ContextBuilder {

    private ContainerBuilder container = ContainerBuilder.newContainer();
    private String pingPortName;

    private ContextBuilder() {

    }

    public static ContextBuilder newContext() {
        return new ContextBuilder();
    }

    public Context build() {
        return new Context(container.build(), pingPortName);
    }

    public ContainerBuilder withContainer() {
        return container;
    }

    public ContextBuilder withPingPortName(String pingPortName) {
        this.pingPortName = pingPortName;
        return this;
    }
}
