/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.slider.server.services.workflow;

import org.apache.hadoop.service.Service;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class WorkflowServiceTestBase extends Assert {
  private static final Logger
      log = LoggerFactory.getLogger(WorkflowServiceTestBase.class);


  /**
   * Set the timeout for every test
   */
  @Rule
  public Timeout testTimeout = new Timeout(15000);

  @Before
  public void nameThread() {
    Thread.currentThread().setName("JUnit");
  }

  
  protected void assertInState(Service service, Service.STATE expected) {
    Service.STATE actual = service.getServiceState();
    if (actual != expected) {
      fail("Service " + service.getName() + " in state " + actual
           + " -expected " + expected);
    }
  }

  protected void assertStopped(Service service) {
    assertInState(service, Service.STATE.STOPPED);
  }

  protected void logState(ServiceParent p) {
    logService(p);
    for (Service s : p.getServices()) {
      logService(s);
    }
  }

  protected void logService(Service s) {
    log.info(s.toString());
    Throwable failureCause = s.getFailureCause();
    if (failureCause != null) {
      log.info("Failed in state {} with {}", s.getFailureState(),
          failureCause);
    }
  }

  /**
   * Wait a second for the service parent to stop
   * @param parent the service to wait for
   */
  protected void waitForParentToStop(ServiceParent parent) {
    waitForParentToStop(parent, 1000);
  }

  /**
   * Wait for the service parent to stop
   * @param parent the service to wait for
   * @param timeout time in milliseconds
   */
  protected void waitForParentToStop(ServiceParent parent, int timeout) {
    boolean stop = parent.waitForServiceToStop(timeout);
    if (!stop) {
      logState(parent);
      fail("Service failed to stop : after " + timeout +" millis " + parent);
    }
  }

  protected abstract ServiceParent buildService(Service... services);

  protected ServiceParent startService(Service... services) {
    ServiceParent parent = buildService(services);
    //expect service to start and stay started
    parent.start();
    return parent;
  }

  /**
   * Class to log when an event callback happens
   */
  public static class EventCallbackHandler implements WorkflowEventCallback {
    public volatile boolean notified = false;
    public Object result;

    @Override
    public void eventCallbackEvent(Object parameter) {
      log.info("EventCallback");
      notified = true;
      result = parameter;
    }
  }
}