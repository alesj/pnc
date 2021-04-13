/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014-2020 Red Hat, Inc., and individual contributors
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
package org.jboss.pnc.spi.exception;

/**
 * Thrown the user attempts to run a build when the a build task using the same configuration is already running.
 */
public class BuildConflictException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * The id of the build task which conflicts with the new request
     */
    private Long buildTaskId;

    public BuildConflictException(String message) {
        super(message);
    }

    public BuildConflictException(String message, Long buildTaskId) {
        super(message);
        this.buildTaskId = buildTaskId;
    }

    public Long getBuildTaskId() {
        return buildTaskId;
    }
}
