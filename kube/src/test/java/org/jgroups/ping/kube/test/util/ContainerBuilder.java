package org.jgroups.ping.kube.test.util;

import org.jgroups.ping.kube.Container;
import org.jgroups.ping.kube.Port;

public class ContainerBuilder {

    Container container = new Container();

    private ContainerBuilder() {

    }

    public static ContainerBuilder newContainer() {
        return new ContainerBuilder();
    }

    public ContainerBuilder withUnnamedPort(int i) {
        container.addPort(new Port(null, i));
        return this;
    }

    public ContainerBuilder withNamedPort(String portName, int i) {
        container.addPort(new Port(portName, i));
        return this;
    }

    public Container build() {
        return container;
    }
}
