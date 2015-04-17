/**
 *  Copyright 2005-2014 Red Hat, Inc.
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
package org.openshift.ping.examples.helloservlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@SuppressWarnings("serial")
public class HelloServlet extends HttpServlet {
    private static final String CLUSTERED = "CLUSTERED";

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        PrintWriter writer = resp.getWriter();

        writer.write("Hello " + req.getParameter("name") + "!\n\n");

        HttpSession session = req.getSession();

        String clustered = req.getParameter("set_clustered");
        if (clustered != null) {
            session.setAttribute(CLUSTERED, clustered);
            writer.write("Clustered SET: " + session.getAttribute(CLUSTERED));
        }

        clustered = req.getParameter("get_clustered");
        if (clustered != null) {
            writer.write("Clustered GET: " + session.getAttribute(CLUSTERED));
        }
    }
}
