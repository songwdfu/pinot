/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.query.planner.physical.v2.opt.rules;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.pinot.query.planner.physical.v2.PRelNode;
import org.apache.pinot.query.planner.physical.v2.PinotDataDistribution;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.mockito.Mockito.doReturn;
import static org.testng.Assert.*;


public class LiteModeWorkerAssignmentRuleTest {
  @Test
  public void testAccumulateWorkers() {
    PRelNode leafOne = create(List.of(), true, List.of("0@server-1", "1@server-2"));
    PRelNode leafTwo = create(List.of(), true, List.of("0@server-2", "1@server-1"));
    PRelNode intermediateNode = create(List.of(leafOne, leafTwo), false, List.of("0@server-3", "1@server-4"));
    Set<String> workers = new HashSet<>();
    LiteModeWorkerAssignmentRule.accumulateWorkers(intermediateNode, workers);
    assertEquals(workers, Set.of("server-1", "server-2"));
  }

  @Test
  public void testSampleWorker() {
    List<String> workers = List.of("worker-0", "worker-1", "worker-2");
    Set<String> selectionCandidates = Set.of("0@worker-0", "0@worker-1", "0@worker-2");
    Set<String> selectedWorkers = new HashSet<>();
    for (int iteration = 0; iteration < 1000; iteration++) {
      selectedWorkers.add(LiteModeWorkerAssignmentRule.sampleWorker(workers));
    }
    assertEquals(selectedWorkers, selectionCandidates);
  }

  private PRelNode create(List<PRelNode> inputs, boolean isLeafStage, List<String> workers) {
    // Setup mock pinot data distribution.
    PinotDataDistribution mockPDD = Mockito.mock(PinotDataDistribution.class);
    doReturn(workers).when(mockPDD).getWorkers();
    // Setup mock PRelNode.
    PRelNode current = Mockito.mock(PRelNode.class);
    doReturn(isLeafStage).when(current).isLeafStage();
    doReturn(mockPDD).when(current).getPinotDataDistributionOrThrow();
    doReturn(inputs).when(current).getPRelInputs();
    return current;
  }
}
