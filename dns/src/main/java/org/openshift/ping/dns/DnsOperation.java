package org.openshift.ping.dns;

import java.util.concurrent.Callable;

public interface DnsOperation<V> extends Callable<V> {
}
