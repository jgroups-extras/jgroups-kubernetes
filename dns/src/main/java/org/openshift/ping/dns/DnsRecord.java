/**
 *  Copyright 2014 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package org.openshift.ping.dns;

import java.util.logging.Level;
import java.util.logging.Logger;

public class DnsRecord implements Comparable<DnsRecord> {
    private static final Logger log = Logger.getLogger(DnsRecord.class.getName());

    private final int priority;
    private final int weight;
    private final int port;
    private final String host;

    public DnsRecord(int priority, int weight, int port, String host) {
        this.priority = priority;
        this.weight = weight;
        this.port = port;
        this.host = host.replaceAll("\\.$", "");
        if (log.isLoggable(Level.FINE)) {
            log.fine(String.format("Created %s", this));
        }
    }

    public int getPriority() {
        return priority;
    }

    public int getWeight() {
        return weight;
    }

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    public static DnsRecord fromString(String input) {
        if (log.isLoggable(Level.FINE)) {
            log.fine(String.format("Creating DnsRecord from [%s]", input));
        }
        String[] splitted = input.split(" ");
        return new DnsRecord(
            Integer.parseInt(splitted[0]),
            Integer.parseInt(splitted[1]),
            Integer.parseInt(splitted[2]),
            splitted[3]
        );
    }

    @Override
    public String toString() {
        return "DnsRecord{" +
            "priority=" + priority +
            ", weight=" + weight +
            ", port=" + port +
            ", host='" + host + '\'' +
            '}';
    }

    @Override
    public int compareTo(DnsRecord o) {
        if (getPriority() < o.getPriority()) {
            return -1;
        } else {
            return 1;
        }
    }

}
