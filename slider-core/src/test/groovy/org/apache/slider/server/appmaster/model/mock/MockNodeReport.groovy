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

package org.apache.slider.server.appmaster.model.mock

import org.apache.hadoop.yarn.api.records.NodeId
import org.apache.hadoop.yarn.api.records.NodeReport
import org.apache.hadoop.yarn.api.records.NodeState
import org.apache.hadoop.yarn.api.records.Resource

/**
 * Node report for testing
 */
class MockNodeReport extends NodeReport {
  NodeId nodeId;
  NodeState nodeState;
  String httpAddress;
  String rackName;
  Resource used;
  Resource capability;
  int numContainers;
  String healthReport;
  long lastHealthReportTime;
  Set<String> nodeLabels;
}