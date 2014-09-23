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

package org.apache.slider.server.services.curator;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.ServiceInstanceBuilder;
import org.apache.curator.x.discovery.ServiceType;
import org.apache.curator.x.discovery.UriSpec;
import org.apache.slider.core.persist.JsonSerDeser;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * YARN service for Curator service discovery; the discovery instance's
 * start/close methods are tied to the lifecycle of this service
 * @param <Payload> the payload of the operation
 */
public class RegistryBinderService<Payload> extends CuratorService {
  private static final Logger log =
    LoggerFactory.getLogger(RegistryBinderService.class);

  private final ServiceDiscovery<Payload> discovery;

  private final Map<String, ServiceInstance<Payload>> entries =
    new HashMap<String, ServiceInstance<Payload>>();

  private JsonSerDeser<CuratorServiceInstance> deser =
    new JsonSerDeser<CuratorServiceInstance>(CuratorServiceInstance.class);

  /**
   * Create an instance
   * @param curator  Does not need to be started
   * @param basePath base directory
   * @param discovery discovery instance -not yet started
   */
  public RegistryBinderService(CuratorFramework curator,
                               String basePath,
                               ServiceDiscovery<Payload> discovery) {
    super("RegistryBinderService", curator, basePath);

    this.discovery =
      Preconditions.checkNotNull(discovery, "null discovery arg");
  }


  public ServiceDiscovery<Payload> getDiscovery() {
    return discovery;
  }

  @Override
  protected void serviceStart() throws Exception {
    super.serviceStart();
    discovery.start();
  }

  @Override
  protected void serviceStop() throws Exception {
    closeCuratorComponent(discovery);
    super.serviceStop();
  }

  /**
   * register an instance -only valid once the service is started
   * @param id ID -must be unique
   * @param name name
   * @param url URL
   * @param payload payload (may be null)
   * @return the instance
   * @throws Exception on registration problems
   */
  public ServiceInstance<Payload> register(String name,
                                           String id,
                                           URL url,
                                           Payload payload) throws Exception {
    Preconditions.checkNotNull(id, "null `id` arg");
    Preconditions.checkNotNull(name, "null `name` arg");
    Preconditions.checkState(isInState(STATE.STARTED), "Not started: " + this);

    ServiceInstanceBuilder<Payload> instanceBuilder = builder()
        .name(name)
        .id(id)
        .payload(payload)
        .serviceType(ServiceType.DYNAMIC);
    if (url != null) {
      UriSpec uriSpec = new UriSpec(url.toString());

      int port = url.getPort();
      if (port == 0) {
        throw new IOException("Port undefined in " + url);
      }
      instanceBuilder
          .uriSpec(uriSpec)
          .port(port);
    }
    ServiceInstance<Payload> instance = instanceBuilder.build();
    log.info("registering {}", instance.toString());
    discovery.registerService(instance);
    log.info("registration completed {}", instance.toString());
    synchronized (this) {
      entries.put(id, instance);
    }
    return instance;
  }

  /**
   * Create a builder. This is already pre-prepared with address, registration
   * time and a (random) UUID
   * @return a builder
   * @throws IOException IO problems, including enumerating network ports
   */
  public ServiceInstanceBuilder<Payload> builder() throws
                                                   IOException {
    try {
      return ServiceInstance.builder();
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }


  /**
   * List all instance IDs of a service type
   * @param servicetype service type
   * @return list of matches
   * @throws Exception
   */
  public List<String> instanceIDs(String servicetype) throws Exception {
    Preconditions.checkNotNull(servicetype);
    List<String> instanceIds;
    try {
      instanceIds =
        getCurator().getChildren().forPath(pathForServicetype(servicetype));
    } catch (KeeperException.NoNodeException e) {
      instanceIds = Lists.newArrayList();
    }
    return instanceIds;
  }

  /**
   * List all service types registered
   * @return a list of service types
   * @throws Exception
   */
  public List<String> serviceTypes() throws Exception {
    List<String> types;
    try {
      types =
        getCurator().getChildren().forPath(getBasePath());
    } catch (KeeperException.NoNodeException e) {
      types = Lists.newArrayList();
    }
    return types;
  }


  /**
   * Return a service instance POJO
   *
   * @param servicetype name of the service
   * @param id ID of the instance
   * @return the instance or <code>null</code> if not found
   * @throws Exception errors
   */
  public CuratorServiceInstance<Payload> queryForInstance(String servicetype, String id)
      throws Exception {
    CuratorServiceInstance<Payload> instance = null;
    String path = pathForInstance(servicetype, id);
    try {
      byte[] bytes = getCurator().getData().forPath(path);
      if (bytes!=null &&  bytes.length>0) {
        instance = deser.fromBytes(bytes);
      }
    } catch (KeeperException.NoNodeException ignore) {
      // ignore
    }
    return instance;
  }
  
  /**
   * List all the instances
   * @param servicetype name of the service
   * @return a list of instances and their payloads
   * @throws IOException any problem
   */
  public List<CuratorServiceInstance<Payload>> listInstances(String servicetype) throws
    IOException {
    try {
      List<String> instanceIDs = instanceIDs(servicetype);
      List<CuratorServiceInstance<Payload>> instances =
        new ArrayList<CuratorServiceInstance<Payload>>(instanceIDs.size());
      for (String instanceID : instanceIDs) {
        CuratorServiceInstance<Payload> instance =
          queryForInstance(servicetype, instanceID);
        if (instance != null) {
          instances.add(instance);
        }
      }
      return instances;
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  /**
   * Find an instance with a given ID
   * @param instances instances
   * @param name ID to look for
   * @return the discovered instance or null
   */
  public CuratorServiceInstance<Payload> findByID(List<CuratorServiceInstance<Payload>> instances, String name) {
    Preconditions.checkNotNull(name);
    for (CuratorServiceInstance<Payload> instance : instances) {
      if (instance.id.equals(name)) {
        return instance;
      }
    }
    return null;
  }

  /**
   * Find a single instance -return that value or raise an exception
   * @param serviceType service type
   * @param name the name (required(
   * @return the instance that matches the criteria
   * @throws FileNotFoundException if there were no matches
   * @throws IOException any network problem
   */
  public CuratorServiceInstance<Payload> findInstance(String serviceType,
      String name) throws IOException {
    Preconditions.checkArgument(StringUtils.isNotEmpty(name), "name");
    return findInstances(serviceType, name).get(0);
  }
  /**
   * List registry entries. If a name was given, then the single match is returned
   * -otherwise all entries matching the service type
   * @param serviceType service type
   * @param name an optional name
   * @return the (non-empty) list of instances that match the criteria
   * @throws FileNotFoundException if there were no matches
   * @throws IOException any network problem
   */
  public List<CuratorServiceInstance<Payload>> findInstances(String serviceType,
      String name)
      throws FileNotFoundException, IOException {
    List<CuratorServiceInstance<Payload>> instances =
        listInstances(serviceType);
    if (instances.isEmpty()) {
      throw new FileNotFoundException(
          "No registry entries for service type " + serviceType);
    }
    if (StringUtils.isNotEmpty(name)) {
      CuratorServiceInstance<Payload> foundInstance = findByID(instances, name);
      if (foundInstance == null) {
        throw new FileNotFoundException(
            "No registry entries for service name " + name
            + " and service type " + serviceType);
      }
      instances.clear();
      instances.add(foundInstance);
    }
    return instances;
  }

  /**
   * Enum all service types in the registry
   * @return a possibly empty collection of service types
   * @throws IOException networking
   */
  public Collection<String> getServiceTypes() throws IOException {
    try {
      return getDiscovery().queryForNames();
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

}
