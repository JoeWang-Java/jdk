/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * <p>Provides the core classes for the Java Management Extensions.</p>
 *
 * <p>The Java Management Extensions
 * (JMX) API is a standard
 * API for management and monitoring.  Typical uses include:</p>
 *
 *     <ul>
 *         <li>consulting and changing application configuration</li>
 *
 *         <li>accumulating statistics about application behavior and
 *         making them available</li>
 *
 *         <li>notifying of state changes and erroneous conditions.</li>
 *     </ul>
 *
 *     <p>The JMX API can also be used as part of a solution for
 *     managing systems, networks, and so on.</p>
 *
 *     <p>The API includes remote access, so a remote management
 *         program can interact with a running application for these
 *     purposes.</p>
 *
 *     <h2>MBeans</h2>
 *
 *     <p>The fundamental notion of the JMX API is the <em>MBean</em>.
 *         An MBean is a named <em>managed object</em> representing a
 *         resource.  It has a <em id="mgIface">management interface</em>
 *         which must be <em>public</em> and consist of:</p>
 *
 *     <ul>
 *         <li>named and typed attributes that can be read and/or
 *         written</li>
 *
 *         <li>named and typed operations that can be invoked</li>
 *
 *         <li>typed notifications that can be emitted by the MBean.</li>
 *     </ul>
 *
 *     <p>For example, an MBean representing an application's
 *         configuration could have attributes representing the different
 *         configuration items.  Reading the <code>CacheSize</code>
 *         attribute would return the current value of that item.
 *         Writing it would update the item, potentially changing the
 *         behavior of the running application.  An operation such as
 *         <code>save</code> could store the current configuration
 *         persistently.  A notification such as
 *         <code>ConfigurationChangedNotification</code> could be sent
 *     every time the configuration is changed.</p>
 *
 *     <p>In the standard usage of the JMX API, MBeans are implemented
 *         as Java objects.  However, as explained below, these objects are
 *     not usually referenced directly.</p>
 *
 *
 *     <h3>Standard MBeans</h3>
 *
 *     <p>To make MBean implementation simple, the JMX API includes the
 *         notion of <em>Standard MBeans</em>.  A Standard MBean is one
 *         whose attributes and operations are deduced from a Java
 *         interface using certain naming patterns, similar to those used
 *         by JavaBeans.  For example, consider an interface like this:</p>
 *
 *     <pre>
 * public interface ConfigurationMBean {
 *      public int getCacheSize();
 *      public void setCacheSize(int size);
 *      public long getLastChangedTime();
 *      public void save();
 * }
 *     </pre>
 *
 *     <p>The methods <code>getCacheSize</code> and
 *         <code>setCacheSize</code> define a read-write attribute of
 *         type <code>int</code> called <code>CacheSize</code> (with an
 *     initial capital, unlike the JavaBeans convention).</p>
 *
 *     <p>The method <code>getLastChangedTime</code> defines an
 *         attribute of type <code>long</code> called
 *         <code>LastChangedTime</code>.  This is a read-only attribute,
 *     since there is no method <code>setLastChangedTime</code>.</p>
 *
 *     <p>The method <code>save</code> defines an operation called
 *         <code>save</code>.  It is not an attribute, since its name
 *         does not begin with <code>get</code>, <code>set</code>, or
 *     <code>is</code>.</p>
 *
 *     <p>The exact naming patterns for Standard MBeans are detailed in
 *     the <a href="#spec">JMX Specification</a>.</p>
 *
 *     <p>There are two ways to make a Java object that is an MBean
 *         with this management interface.  One is for the object to be
 *         of a class that has exactly the same name as the Java
 *         interface but without the <code>MBean</code> suffix.  So in
 *         the example the object would be of the class
 *         <code>Configuration</code>, in the same Java package as
 *         <code>ConfigurationMBean</code>.  The second way is to use the
 *         {@link javax.management.StandardMBean StandardMBean}
 *     class.</p>
 *
 *
 *     <h3>MXBeans</h3>
 *
 *     <p>An <em>MXBean</em> is a variant of Standard MBean where complex
 *         types are mapped to a standard set of types defined in the
 *         {@link javax.management.openmbean} package.  MXBeans are appropriate
 *         if you would otherwise need to reference application-specific
 *         classes in your MBean interface.  They are described in detail
 *     in the specification for {@link javax.management.MXBean MXBean}.</p>
 *
 *
 *     <h3>Dynamic MBeans</h3>
 *
 *     <p>A <em>Dynamic MBean</em> is an MBean that defines its
 *         management interface at run-time.  For example, a configuration
 *         MBean could determine the names and types of the attributes it
 *     exposes by parsing an XML file.</p>
 *
 *     <p>Any Java object of a class that implements the {@link
 *         javax.management.DynamicMBean DynamicMBean} interface is a
 *     Dynamic MBean.</p>
 *
 *
 *     <h3>Open MBeans</h3>
 *
 *     <p>An <em>Open MBean</em> is a kind of Dynamic MBean where the
 *         types of attributes and of operation parameters and return
 *         values are built using a small set of predefined Java classes.
 *         Open MBeans facilitate operation with remote management programs
 *         that do not necessarily have access to application-specific
 *         types, including non-Java programs.  Open MBeans are defined by
 *         the package <a href="openmbean/package-summary.html"><code>
 *     javax.management.openmbean</code></a>.</p>
 *
 *
 *     <h3>Model MBeans</h3>
 *
 *     <p>A <em>Model MBean</em> is a kind of Dynamic MBean that acts
 *         as a bridge between the management interface and the
 *         underlying managed resource.  Both the management interface and
 *         the managed resource are specified as Java objects.  The same
 *         Model MBean implementation can be reused many times with
 *         different management interfaces and managed resources, and it can
 *         provide common functionality such as persistence and caching.
 *         Model MBeans are defined by the package
 *         <a href="modelmbean/package-summary.html"><code>
 *     javax.management.modelmbean</code></a>.</p>
 *
 *
 *     <h2>MBean Server</h2>
 *
 *     <p>To be useful, an MBean must be registered in an <em>MBean
 *         Server</em>.  An MBean Server is a repository of MBeans.
 *         Usually the only access to the MBeans is through the MBean
 *         Server.  In other words, code no longer accesses the Java
 *         object implementing the MBean directly, but instead accesses
 *         the MBean by name through the MBean Server.  Each MBean has a
 *         unique name within the MBean Server, defined by the {@link
 *     javax.management.ObjectName ObjectName} class.</p>
 *
 *     <p>An MBean Server is an object implementing the interface
 *         {@link javax.management.MBeanServer MBeanServer}.
 *         The most convenient MBean Server to use is the
 *         <em>Platform MBean Server</em>.  This is a
 *         single MBean Server that can be shared by different managed
 *         components running within the same Java Virtual Machine.  The
 *         Platform MBean Server is accessed with the method {@link
 *     java.lang.management.ManagementFactory#getPlatformMBeanServer()}.</p>
 *
 *     <p>Application code can also create a new MBean Server, or
 *         access already-created MBean Servers, using the {@link
 *     javax.management.MBeanServerFactory MBeanServerFactory} class.</p>
 *
 *
 *     <h3>Creating MBeans in the MBean Server</h3>
 *
 *     <p>There are two ways to create an MBean.  One is to construct a
 *         Java object that will be the MBean, then use the {@link
 *         javax.management.MBeanServer#registerMBean registerMBean}
 *         method to register it in the MBean Server.  The other is to
 *         create and register the MBean in a single operation using one
 *         of the {@link javax.management.MBeanServer#createMBean(String,
 *     javax.management.ObjectName) createMBean} methods.</p>
 *
 *     <p>The <code>registerMBean</code> method is simpler for local
 *         use, but cannot be used remotely.  The
 *         <code>createMBean</code> method can be used remotely, but
 *     sometimes requires attention to class loading issues.</p>
 *
 *     <p>An MBean can perform actions when it is registered in or
 *         unregistered from an MBean Server if it implements the {@link
 *         javax.management.MBeanRegistration MBeanRegistration}
 *     interface.</p>
 *
 *
 *     <h3>Accessing MBeans in the MBean Server</h3>
 *
 *     <p>Given an <code>ObjectName</code> <code>name</code> and an
 *         <code>MBeanServer</code> <code>mbs</code>, you can access
 *     attributes and operations as in this example:</p>
 *
 *     <pre>
 * int cacheSize = mbs.getAttribute(name, "CacheSize");
 * {@link javax.management.Attribute Attribute} newCacheSize =
 *      new Attribute("CacheSize", new Integer(2000));
 * mbs.setAttribute(name, newCacheSize);
 * mbs.invoke(name, "save", new Object[0], new Class[0]);
 *     </pre>
 *
 *     <p id="proxy">Alternatively, if you have a Java interface that
 *         corresponds to the management interface for the MBean, you can use an
 *     <em>MBean proxy</em> like this:</p>
 *
 *     <pre>
 * ConfigurationMBean conf =
 *     {@link javax.management.JMX#newMBeanProxy
 *         JMX.newMBeanProxy}(mbs, name, ConfigurationMBean.class);
 * int cacheSize = conf.getCacheSize();
 * conf.setCacheSize(2000);
 * conf.save();
 *     </pre>
 *
 *     <p>Using an MBean proxy is just a convenience.  The second
 *         example ends up calling the same <code>MBeanServer</code>
 *     operations as the first one.</p>
 *
 *     <p>An MBean Server can be queried for MBeans whose names match
 *         certain patterns and/or whose attributes meet certain
 *         constraints.  Name patterns are constructed using the {@link
 *         javax.management.ObjectName ObjectName} class and constraints
 *         are constructed using the {@link javax.management.Query Query}
 *         class.  The methods {@link
 *         javax.management.MBeanServer#queryNames queryNames} and {@link
 *         javax.management.MBeanServer#queryMBeans queryMBeans} then
 *     perform the query.</p>
 *
 *
 *     <h3>MBean lifecycle</h3>
 *
 *     <p>An MBean can implement the {@link javax.management.MBeanRegistration
 *         MBeanRegistration} interface in order to be told when it is registered
 *         and unregistered in the MBean Server. Additionally, the {@link
 *         javax.management.MBeanRegistration#preRegister preRegister} method
 *         allows the MBean to get a reference to the <code>MBeanServer</code>
 *         object and to get its <code>ObjectName</code> within the MBean
 *     Server.</p>
 *
 *
 *     <h2>Notifications</h2>
 *
 *     <p>A <em>notification</em> is an instance of the {@link
 *         javax.management.Notification Notification} class or a
 *         subclass.  In addition to its Java class, it has a
 *         <em>type</em> string that can distinguish it from other
 *     notifications of the same class.</p>
 *
 *     <p>An MBean that will emit notifications must implement the
 *         {@link javax.management.NotificationBroadcaster
 *         NotificationBroadcaster} or {@link
 *         javax.management.NotificationEmitter NotificationEmitter}
 *         interface.  Usually, it does this by subclassing
 *         {@link javax.management.NotificationBroadcasterSupport
 *         NotificationBroadcasterSupport} or delegating to an instance of
 *     that class. Here is an example:</p>
 *
 *     <pre>
 * public class Configuration <b>extends NotificationBroadcasterSupport</b>
 *         implements ConfigurationMBean {
 *     ...
 *     private void updated() {
 *         Notification n = new Notification(...);
 *         <b>{@link javax.management.NotificationBroadcasterSupport#sendNotification
 *         sendNotification}(n)</b>;
 *     }
 * }
 *     </pre>
 *
 *
 *     <p>Notifications can be received by a <em>listener</em>, which
 *         is an object that implements the {@link
 *         javax.management.NotificationListener NotificationListener}
 *         interface.  You can add a listener to an MBean with the method
 *         {@link
 *         javax.management.MBeanServer#addNotificationListener(ObjectName,
 *         NotificationListener, NotificationFilter, Object)}.
 *         You can optionally supply a <em>filter</em> to this method, to
 *         select only notifications of interest.  A filter is an object
 *         that implements the {@link javax.management.NotificationFilter
 *     NotificationFilter} interface.</p>
 *
 *     <p>An MBean can be a listener for notifications emitted by other
 *         MBeans in the same MBean Server.  In this case, it implements
 *         {@link javax.management.NotificationListener
 *         NotificationListener} and the method {@link
 *         javax.management.MBeanServer#addNotificationListener(ObjectName,
 *     ObjectName, NotificationFilter, Object)} is used to listen.</p>
 *
 *
 *     <h2>Remote Access to MBeans</h2>
 *
 *     <p>An MBean Server can be accessed remotely through a
 *         <em>connector</em>.  A connector allows a remote Java
 *         application to access an MBean Server in essentially the same
 *         way as a local one.  The package
 *         <a href="remote/package-summary.html"><code>
 *     javax.management.remote</code></a> defines connectors.</p>
 *
 *     <p>The JMX specification also defines the notion of an
 *         <em>adaptor</em>.  An adaptor translates between requests in a
 *         protocol such as SNMP or HTML and accesses to an MBean Server.
 *         So for example an SNMP GET operation might result in a
 *     <code>getAttribute</code> on the MBean Server.</p>
 *
 * <h3 id="interop">Interoperability between versions of the JMX
 *   specification</h3>
 *
 *     <p>When a client connects to a server using the JMX Remote
 *         API, it is possible that they do not have the same version
 *         of the JMX specification.  The version of the JMX
 *         specification described here is version 1.4.  Previous
 *         versions were 1.0, 1.1, and 1.2.  (There was no 1.3.)
 *         The standard JMX Remote API is defined to work with version
 *         1.2 onwards, so in standards-based deployment the only
 *         interoperability questions that arise concern version 1.2
 *     onwards.</p>
 *
 *     <p>Every version of the JMX specification continues to
 *         implement the features of previous versions.  So when the
 *         client is running an earlier version than the server, there
 *         should not be any interoperability concerns.</p>
 *
 *     <p>When the client is running a later version than the server,
 *         certain newer features may not be available, as detailed in
 *         the next sections.  The client can determine the server's
 *         version by examining the {@link
 *         javax.management.MBeanServerDelegateMBean#getSpecificationVersion
 *         SpecificationVersion} attribute of the {@code
 *     MBeanServerDelegate}.</p>
 *
 *     <h4 id="interop-1.2">If the remote MBean Server is 1.2</h4>
 *
 * <ul>
 *
 *         <li><p>You cannot use wildcards in a key property of an
 *             {@link javax.management.ObjectName ObjectName}, for
 *             example {@code domain:type=Foo,name=*}. Wildcards that
 *             match whole properties are still allowed, for example
 *         {@code *:*} or {@code *:type=Foo,*}.</p>
 *
 *         <li><p>You cannot use {@link
 *             javax.management.Query#isInstanceOf Query.isInstanceOf}
 *         in a query.</p>
 *
 *         <li><p>You cannot use dot syntax such as {@code
 *             HeapMemoryUsage.used} in the {@linkplain
 *             javax.management.monitor.Monitor#setObservedAttribute
 *             observed attribute} of a monitor, as described in the
 *             documentation for the {@link javax.management.monitor}
 *         package.</p>
 *
 *     </ul>
 *
 *     @see <a id="spec" href="https://jcp.org/aboutJava/communityprocess/mrel/jsr160/index2.html">
 *     JMX Specification, version 1.4</a>
 *
 *     @since 1.5
 */
package javax.management;
