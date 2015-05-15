/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.core.builder;

import org.jboss.pnc.core.events.DefaultBuildStatusChangedEvent;
import org.jboss.pnc.core.exception.CoreException;
import org.jboss.pnc.model.BuildConfiguration;
import org.jboss.pnc.model.User;
import org.jboss.pnc.spi.BuildExecution;
import org.jboss.pnc.spi.BuildExecutionType;
import org.jboss.pnc.spi.BuildStatus;
import org.jboss.pnc.spi.events.BuildStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.event.Event;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
* Created by <a href="mailto:matejonnet@gmail.com">Matej Lazar</a> on 2014-12-23.
*/
public class BuildTask implements BuildExecution {

    public static final Logger log = LoggerFactory.getLogger(BuildTask.class);

    public BuildConfiguration buildConfiguration;
    private BuildExecutionType buildTaskType;
    private BuildSetTask buildSetTask;
    BuildStatus status = BuildStatus.NEW;
    private String statusDescription;

    Event<BuildStatusChangedEvent> buildStatusChangedEvent;

    /**
     * A list of builds waiting for this build to complete.
     */
    private Set<BuildTask> waiting;
    private List<BuildTask> requiredBuilds;
    private BuildCoordinator buildCoordinator;

    private String topContentId;

    private String buildSetContentId;

    private String buildContentId;
    private long startTime;

    //User who created the tasks
    private User user;

    BuildTask(BuildCoordinator buildCoordinator, BuildConfiguration buildConfiguration, String topContentId,
              String buildSetContentId, String buildContentId, User user,
              BuildSetTask buildSetTask) {
        this.buildCoordinator = buildCoordinator;
        this.buildConfiguration = buildConfiguration;
        this.buildSetTask = buildSetTask;
        this.buildStatusChangedEvent = buildCoordinator.getBuildStatusChangedEventNotifier();
        this.topContentId = topContentId;
        this.buildSetContentId = buildSetContentId;
        this.buildContentId = buildContentId;
        this.user = user;

        this.startTime = System.currentTimeMillis();
        waiting = new HashSet<>();

        this.buildTaskType = buildSetTask.getBuildTaskType();
    }

    public void setStatus(BuildStatus status) {
        this.status = status;
        notifyStatusUpdate();
        if (status.equals(BuildStatus.DONE)) {
            waiting.forEach((submittedBuild) -> submittedBuild.requiredBuildCompleted(this));
        }
    }

    private void notifyStatusUpdate() {
        BuildStatusChangedEvent buildStatusChanged = new DefaultBuildStatusChangedEvent(this.status, status, buildConfiguration.getId(), this);
        log.debug("Updating build task {} status to {}", this.getId(), buildStatusChanged);
        buildSetTask.taskStatusUpdated(this);
        buildStatusChangedEvent.fire(buildStatusChanged);
    }

    void setRequiredBuilds(List<BuildTask> requiredBuilds) {
        this.requiredBuilds = requiredBuilds;
    }

    private void requiredBuildCompleted(BuildTask completed) {
        requiredBuilds.remove(completed);
        if (requiredBuilds.size() == 0) {
            try {
                buildCoordinator.startBuilding(this);
            } catch (CoreException e) {
                setStatus(BuildStatus.SYSTEM_ERROR);
                setStatusDescription(e.getMessage());
            }
        }
    }

    /**
     * @return current status
     */
    public BuildStatus getStatus() {
        return status;
    }

    /**
     * @return Description of current status. Eg. WAITING: there is no available executor; FAILED: exceptionMessage
     */
    public String getStatusDescription() {
        return statusDescription;
    }

    public BuildConfiguration getBuildConfiguration() {
        return buildConfiguration;
    }

    @Override
    public String getTopContentId() {
        return topContentId;
    }

    @Override
    public String getBuildSetContentId() {
        return buildSetContentId;
    }

    @Override
    public String getBuildContentId() {
        return buildContentId;
    }

    void addWaiting(BuildTask buildTask) {
        waiting.add(buildTask);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BuildTask buildTask = (BuildTask) o;

        return buildConfiguration.equals(buildTask.getBuildConfiguration());

    }

    @Override
    public int hashCode() {
        return buildConfiguration.hashCode();
    }

    void setStatusDescription(String statusDescription) {
        this.statusDescription = statusDescription;
    }


    public Integer getId() {
        return buildConfiguration.getId();
    }

    public String getBuildLog() {
        return null;//TODO reference to progressive log
    }

    @Override
    public String getProjectName() {
        return buildConfiguration.getProject().getName();
    }

    public BuildExecutionType getBuildExecutionType() {
        return buildTaskType;
    }

    public long getStartTime() {
        return startTime;
    }

    public User getUser() {
        return user;
    }
}
